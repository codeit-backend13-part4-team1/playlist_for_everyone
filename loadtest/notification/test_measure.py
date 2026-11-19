import json
import os
import subprocess
import tempfile
import unittest
from contextlib import nullcontext
from http.client import HTTPResponse
from io import BytesIO
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch
from zipfile import ZipFile

import measure


def connection():
    return {
        "EVERYONE_DB_PORT": "15432",
        "EVERYONE_DB_USERNAME": "temporary-user",
        "EVERYONE_DB_PASSWORD": "temporary-password",
        "EVERYONE_REDIS_PORT": "16379",
    }


class Clock:
    now = 0.0

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class MeasurementTest(unittest.TestCase):
    def test_application_uses_temporary_postgres_and_redis(self):
        with patch.dict(os.environ, {
            "SPRING_FLYWAY_URL": "jdbc:postgresql://other-host/other-db",
            "SPRING_FLYWAY_USER": "other-user",
            "SPRING_FLYWAY_PASSWORD": "other-password",
            "SPRING_FLYWAY_ENABLED": "false",
            "SPRING_CONFIG_LOCATION": "file:/other-config.yml",
            "SPRING_DATASOURCE_HIKARI_JDBC_URL": "jdbc:postgresql://other-host/other-db",
            "SPRING_DATA_REDIS_URL": "redis://other-host:6379",
            "SPRING_DATA_REDIS_CLUSTER_NODES": "other-cluster:6379",
            "SPRING_DATA_REDIS_SENTINEL_MASTER": "other-master",
            "SPRING_DATA_REDIS_SENTINEL_NODES": "other-sentinel:26379",
        }):
            env = measure.application_environment(connection(), Path("jdk"), "admin", "secret")

        self.assertEqual({key for key in env if key.startswith("SPRING_FLYWAY_")}, {"SPRING_FLYWAY_ENABLED"})
        self.assertEqual(env["SPRING_FLYWAY_ENABLED"], "true")
        self.assertEqual(env["SPRING_DATASOURCE_URL"], "jdbc:postgresql://127.0.0.1:15432/loadtest")
        self.assertNotIn("SPRING_CONFIG_LOCATION", env)
        self.assertNotIn("SPRING_DATASOURCE_HIKARI_JDBC_URL", env)
        redis_settings = {key: value for key, value in env.items() if key.startswith("SPRING_DATA_REDIS_")}
        self.assertEqual(redis_settings, {"SPRING_DATA_REDIS_URL": "redis://127.0.0.1:16379"})

    def test_truncated_response_is_not_retried_and_can_be_reconciled(self):
        socket = Mock()
        socket.makefile.return_value = BytesIO(b"HTTP/1.1 201 Created\r\nContent-Length: 20\r\n\r\n{}")
        response = HTTPResponse(socket)
        response.begin()
        owner = measure.Client("http://127.0.0.1")
        owner.cookies = [SimpleNamespace(name="XSRF-TOKEN", value="test-csrf")]
        owner.opener = Mock()
        owner.opener.open.return_value = response
        owner.clone = Mock(return_value=owner)

        rows, _, _, _ = measure.timed_workload(lambda: {"pending": 0}, owner, "request ", 1, 1)
        self.assertEqual(owner.opener.open.call_count, 1)
        self.assertEqual(rows[0]["clientError"], "IncompleteRead")
        self.assertIsNone(rows[0]["httpStatus"])

        with patch.object(measure, "committed_playlists", return_value={"request 1": "playlist"}), \
             patch.object(measure, "jfr_samples", return_value=[{"playlistId": "playlist"}]):
            committed, _, incomplete, unknown, settled = measure.collect_completion(
                "postgres", Path("jdk"), Path("checkout"), 1, "round", "request ", rows
            )

        self.assertEqual(committed, {"request 1": "playlist"})
        self.assertEqual((incomplete, unknown, settled), (0, 0, True))

    def test_missing_notifications_distinguish_completed_from_unfinished(self):
        notifications = [["follower-a", 'Owner님이 "request 1" 플레이리스트를 만들었습니다.']]
        with patch.object(measure, "postgres", return_value=json.dumps(notifications)):
            counts = measure.compare_notifications(
                "postgres", "request ", "Owner", ["follower-a", "follower-b"],
                [{"title": "request 1"}, {"title": "request 2"}],
                {"request 1": "completed", "request 2": "unfinished"}, {"completed"},
            )

        self.assertEqual(counts["missing"], 3)
        self.assertEqual(counts["missingAfterCompletion"], 1)

    def test_committed_request_without_jfr_is_checked_until_deadline(self):
        clock = Clock()
        rows = [{"title": "request 1", "httpStatus": 201}]
        with patch.multiple(measure, time=clock, COMPLETION_WAIT_SECONDS=1), \
             patch.object(measure, "committed_playlists", return_value={"request 1": "playlist"}), \
             patch.object(measure, "jfr_samples", return_value=[]) as dump:
            _, _, incomplete, unknown, settled = measure.collect_completion(
                "postgres", Path("jdk"), Path("checkout"), 1, "round", "request ", rows
            )

        self.assertEqual(clock.now, 1)
        self.assertEqual(dump.call_count, 2)
        self.assertEqual((incomplete, unknown, settled), (1, 0, False))

    def test_timed_out_dump_preserves_last_recording(self):
        with tempfile.TemporaryDirectory() as directory:
            checkout = Path(directory)
            (checkout / measure.JFR_DIRECTORY).mkdir()
            final = checkout / measure.JFR_DIRECTORY / "round.jfr"
            final.write_bytes(b"previous recording")

            def failed_dump(*args, **kwargs):
                (checkout / measure.JFR_DIRECTORY / "round.pending.jfr").write_bytes(b"incomplete recording")
                raise subprocess.TimeoutExpired(args, kwargs["timeout"])

            with patch.object(measure, "command", side_effect=failed_dump), \
                 self.assertRaises(subprocess.TimeoutExpired):
                measure.jfr_samples(Path("jdk"), checkout, 1, "round", measure.time.monotonic() + 1)

            self.assertEqual(final.read_bytes(), b"previous recording")
            self.assertFalse((checkout / measure.JFR_DIRECTORY / "round.pending.jfr").exists())

    def test_unsettled_round_records_partial_results_without_final_actuator_snapshot(self):
        rows = [{"title": "request 1", "httpStatus": 201, "httpMs": 10, "responsePlaylistId": "playlist"}]
        snapshots = Mock(return_value={"hikari": {"max": {"VALUE": 2}}})
        with tempfile.TemporaryDirectory() as directory, patch.multiple(
            measure, table_sizes=lambda db: {}, snapshot=snapshots,
            timed_workload=lambda *args: (rows, 1, [{"pending": 1, "active": 2, "idle": 0}], []),
            collect_completion=lambda *args: ({"request 1": "playlist"}, [], 1, 0, False),
            postgres=lambda *args: "[]",
        ):
            output = Path(directory)
            summary = measure.run_round(
                None, (None, "Owner", ["follower"]), "postgres", SimpleNamespace(java_home=Path("jdk")),
                output / "checkout", output, 1, "round", None, pool=2, concurrency=1, count=1, warmup=0, stress=True,
            )
            raw = json.loads((output / "round-raw.json").read_text())

        self.assertEqual(snapshots.call_count, 1)
        self.assertIsNone(raw["after"])
        self.assertIsNone(summary["hikari"]["acquireCount"])
        self.assertEqual(summary["notifications"]["missing"], 1)
        self.assertEqual(summary["notifications"]["missingAfterCompletion"], 0)
        self.assertFalse(summary["checks"]["roundSettled"])

    def test_unsettled_round_saves_outputs_and_stops_before_next_round(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            args = SimpleNamespace(output=output, java_home=Path("jdk"), port=18081, groups=["normal", "stress"])
            client = Mock()
            client.require.return_value = {"status": "UP"}
            client.call.side_effect = [
                (401, None, 0), (200, {"names": [measure.HIKARI + name for name in measure.POOL_METERS]}, 0),
                (403, None, 0),
            ]
            application = Mock(return_value=nullcontext(("http://127.0.0.1", 1, {})))
            rounds = []

            def prepare(work):
                checkout = work / "checkout"
                (checkout / measure.JFR_DIRECTORY).mkdir(parents=True)
                return checkout

            def round_result(admin, fixture, db, args, checkout, output, pid, label, probe, **options):
                rounds.append(label)
                settled = label == "smoke"
                (output / f"{label}-raw.json").write_text("{}")
                (checkout / measure.JFR_DIRECTORY / f"{label}.jfr").write_bytes(b"recording")
                return {"label": label, "checks": {"observability": True, "roundSettled": settled, "normalScenario": settled}}

            with patch.multiple(
                measure, prepare_checkout=prepare, containers=lambda work: nullcontext(("postgres", "redis", connection())),
                application=application, Client=lambda url: client, make_fixture=lambda *args: (client, "Owner", []),
                local_pool_probe=lambda *args: nullcontext(None), run_round=round_result,
                postgres=lambda *args: "16", command=lambda *args, **kwargs: "redis_version:7",
            ), self.assertRaisesRegex(SystemExit, "r1c1-1 did not settle"):
                measure.main(args)

            self.assertEqual(rounds, ["smoke", "r1c1-1"])
            self.assertEqual(application.call_count, 1)
            self.assertEqual([row["label"] for row in json.loads((output / "summary.json").read_text())], rounds)
            with ZipFile(output / "raw.zip") as archive:
                self.assertEqual(set(archive.namelist()), {"smoke-raw.json", "smoke.jfr", "r1c1-1-raw.json", "r1c1-1.jfr"})
