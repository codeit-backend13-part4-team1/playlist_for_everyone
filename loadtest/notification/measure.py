"""Run the #223 synchronous notification baseline against disposable local services."""

import argparse
import base64
import copy
import http.cookiejar
import json
import math
import os
import platform
import re
import secrets
import select
import shutil
import signal
import subprocess
import tempfile
import threading
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from datetime import datetime, timezone
from http.client import IncompleteRead
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlencode
from urllib.request import HTTPCookieProcessor, ProxyHandler, Request, build_opener
from uuid import UUID
from zipfile import ZIP_DEFLATED, ZipFile

BASELINE = "d58951e6680b6f8cb09fb83889fde069f85c7308"
INSTRUMENTATION_BASE = "288227a7"
INSTRUMENTATION = "7fd8c279e93570492d8cb0c3e43d291e2b8a4929"
NOTIFICATION_JAVA = "src/main/java/com/codeit/sb13/playlist_for_everyone/domain/notification"
JFR_EVENT = "everyone.PlaylistNotificationTiming"
JFR_DIRECTORY = Path(".notification-jfr")
JFR_CONFIG = JFR_DIRECTORY / "notification-only.jfc"
COMPLETION_WAIT_SECONDS = 30
HIKARI_ACQUISITION_SCOPE = "Actuator snapshot delta including the administrator authentication queries"
METRICS = "everyone.notification."
HIKARI = "hikaricp.connections."
SOURCE = "source:playlist_created"
FAILURE_STAGES = ("lookup", "publish", "save", "sse")
POOL_METERS = ("max", "active", "idle", "pending", "acquire", "timeout")
NOTIFICATION_TITLE = "팔로우한 사용자의 새 플레이리스트"
SCRIPT_DIRECTORY = Path(__file__).resolve().parent
ROOT = SCRIPT_DIRECTORY.parents[1]


def command(*args, **kwargs):
    return subprocess.check_output(args, text=True, **kwargs).strip()


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


class Client:
    def __init__(self, base_url):
        self.base_url = base_url
        self.cookies = http.cookiejar.CookieJar()
        self.opener = build_opener(ProxyHandler({}), HTTPCookieProcessor(self.cookies))
        self.token = None

    def clone(self):
        client = Client(self.base_url)
        client.token = self.token
        for cookie in self.cookies:
            client.cookies.set_cookie(copy.copy(cookie))
        return client

    def call(self, method, path, *, body=None, form=None, timeout=15):
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if method == "POST":
            headers["X-XSRF-TOKEN"] = next(
                unquote(cookie.value) for cookie in self.cookies if cookie.name == "XSRF-TOKEN"
            )
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        if form is not None:
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            data = urlencode(form).encode()

        started = time.monotonic()
        try:
            with self.opener.open(Request(self.base_url + path, data, headers, method=method), timeout=timeout) as response:
                status = response.status
                raw = response.read()
        except HTTPError as error:
            with error:
                status = error.code
                raw = error.read()
        elapsed_ms = (time.monotonic() - started) * 1000
        try:
            response = json.loads(raw) if raw else None
        except ValueError:
            response = None
        return status, response, elapsed_ms

    def require(self, method, path, expected, **kwargs):
        status, body, _ = self.call(method, path, **kwargs)
        if status != expected:
            raise RuntimeError(f"{method} {path.split('?')[0]} returned HTTP {status}, expected {expected}")
        return body

    def csrf(self):
        self.require("GET", "/api/auth/csrf-token", 204)

    def login(self, email, password):
        self.csrf()
        body = self.require(
            "POST", "/api/auth/sign-in", 200, form={"username": email, "password": password}
        )
        self.token = body["accessToken"]


def make_account(base_url, name):
    client = Client(base_url)
    email = name.lower() + "@local.example"
    password = secrets.token_urlsafe(24) + "aA1!"
    client.csrf()
    body = client.require("POST", "/api/users", 201, body={"email": email, "name": name, "password": password})
    client.login(email, password)
    return client, str(UUID(body["id"]))


def meter(client, name, tag=None, *, optional=False):
    path = "/actuator/metrics/" + name
    if tag:
        path += "?" + urlencode({"tag": tag})
    status, body, _ = client.call("GET", path)
    if status == 404 and optional:
        return {}
    if status != 200:
        raise RuntimeError(f"Actuator metric {name} returned HTTP {status}")
    return {measurement["statistic"]: measurement["value"] for measurement in body["measurements"]}


def snapshot(client):
    return {
        "publishAttempts": meter(client, METRICS + "publish.attempts", SOURCE, optional=True).get("COUNT", 0),
        "processing": meter(client, METRICS + "processing.duration", SOURCE, optional=True),
        "saved": meter(client, METRICS + "saved", optional=True).get("COUNT", 0),
        "ssePublished": meter(client, METRICS + "sse.published", optional=True).get("COUNT", 0),
        "failures": {
            stage: meter(client, METRICS + "failures", "stage:" + stage, optional=True).get("COUNT", 0)
            for stage in FAILURE_STAGES
        },
        "hikari": {suffix: meter(client, HIKARI + suffix) for suffix in POOL_METERS},
    }


def postgres(container, query, *, timeout=None):
    return command("docker", "exec", container, "psql", "-XAt", "-v", "ON_ERROR_STOP=1",
                   "-U", "monitoring223", "-d", "loadtest", "-c", query, timeout=timeout)


def table_sizes(container):
    return json.loads(postgres(container, """
        select json_build_object('users', (select count(*) from users),
          'follows', (select count(*) from follows),
          'playlists', (select count(*) from playlists),
          'notifications', (select count(*) from notifications))
    """))


def make_fixture(base_url, database, tag, receivers):
    name = f"Baseline{tag}R{receivers}"
    owner, owner_id = make_account(base_url, name)
    follower_ids = []
    for index in range(1, receivers + 1):
        follower, follower_id = make_account(base_url, f"{name}Follower{index}")
        follower.require("POST", "/api/follows", 201, body={"followeeId": owner_id})
        follower_ids.append(follower_id)
    actual_ids = postgres(database, f"select follower_id from follows where followee_id = '{owner_id}'::uuid")
    if Counter(actual_ids.splitlines()) != Counter(follower_ids):
        raise RuntimeError("Prepared follower IDs do not match the database")
    return owner, name, follower_ids


def percentiles(values):
    ordered = sorted(values)
    return {
        f"p{p}": round(ordered[math.ceil(len(ordered) * p / 100) - 1], 3) if ordered else None
        for p in (50, 95, 99)
    }


def prepare_checkout(work):
    checkout = work / "checkout"
    checkout.mkdir()
    with subprocess.Popen(["git", "archive", BASELINE], cwd=ROOT, stdout=subprocess.PIPE) as archive:
        subprocess.run(["tar", "-xf", "-", "-C", str(checkout)], stdin=archive.stdout, check=True)
        archive.stdout.close()
        if archive.wait() != 0:
            raise RuntimeError("Could not materialize the baseline checkout")
    patch = work / "instrumentation.patch"
    patch.write_text(command("git", "diff", "--binary", INSTRUMENTATION_BASE, INSTRUMENTATION, "--",
                             NOTIFICATION_JAVA, cwd=ROOT) + "\n")
    for change in (patch, SCRIPT_DIRECTORY / "jfr.patch"):
        subprocess.run(["git", "apply", "--check", str(change)], cwd=checkout, check=True)
        subprocess.run(["git", "apply", str(change)], cwd=checkout, check=True)
    (checkout / JFR_DIRECTORY).mkdir()
    shutil.copyfile(SCRIPT_DIRECTORY / JFR_CONFIG.name, checkout / JFR_CONFIG)
    return checkout


@contextmanager
def containers(work):
    suffix = secrets.token_hex(5)
    database = "notification223-pg-" + suffix
    redis = "notification223-redis-" + suffix
    password = secrets.token_urlsafe(32)
    created = []
    env_path = work / "postgres.env"
    with open(env_path, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as env_file:
        env_file.write(f"POSTGRES_USER=monitoring223\nPOSTGRES_DB=loadtest\nPOSTGRES_PASSWORD={password}\n")
    try:
        command("docker", "run", "-d", "--rm", "--pull=never", "--name", database,
                "--env-file", str(env_path), "-p", "127.0.0.1::5432", "postgres:16")
        created.append(database)
        command("docker", "run", "-d", "--rm", "--pull=never", "--name", redis,
                "-p", "127.0.0.1::6379", "redis:7.4-alpine")
        created.append(redis)
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            ready = subprocess.run(
                ["docker", "exec", database, "pg_isready", "-h", "127.0.0.1", "-d", "loadtest", "-U", "monitoring223"],
                capture_output=True,
            )
            if ready.returncode == 0 and command("docker", "exec", redis, "redis-cli", "PING") == "PONG":
                break
            time.sleep(.5)
        else:
            raise RuntimeError("Temporary PostgreSQL and Redis did not start")
        yield database, redis, {
            "EVERYONE_DB_HOST": "127.0.0.1",
            "EVERYONE_DB_NAME": "loadtest",
            "EVERYONE_DB_PORT": command("docker", "port", database, "5432/tcp").rsplit(":", 1)[1],
            "EVERYONE_DB_USERNAME": "monitoring223",
            "EVERYONE_DB_PASSWORD": password,
            "EVERYONE_REDIS_HOST": "127.0.0.1",
            "EVERYONE_REDIS_PORT": command("docker", "port", redis, "6379/tcp").rsplit(":", 1)[1],
        }
    finally:
        env_path.unlink(missing_ok=True)
        if created:
            subprocess.run(["docker", "stop", "-t", "5", *reversed(created)], check=True, stdout=subprocess.DEVNULL)


def app_options(port, pool, timeout_ms):
    options = [
        "--server.address=127.0.0.1", f"--server.port={port}",
        "--logging.level.org.hibernate.SQL=off", "--logging.level.org.hibernate.orm.jdbc.batch=warn",
        "--logging.level.com.codeit.sb13.playlist_for_everyone=info",
        f"--spring.datasource.hikari.maximum-pool-size={pool}",
        "--spring.datasource.hikari.register-mbeans=true",
        "--spring.datasource.hikari.pool-name=notification-baseline",
    ]
    if timeout_ms is not None:
        options.append(f"--spring.datasource.hikari.connection-timeout={timeout_ms}")
    return options


@contextmanager
def application(checkout, output, args, env, pool, timeout_ms, label):
    log = output / (label + ".log")
    options = app_options(args.port, pool, timeout_ms)
    gradle = ["./gradlew", "--offline", "--no-daemon", "bootRun", "--args=" + " ".join(options)]
    with open(log, "xb", opener=lambda path, flags: os.open(path, flags, 0o600)) as log_file:
        process = subprocess.Popen(gradle, cwd=checkout, env=env, stdout=log_file,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    try:
        client = Client(f"http://127.0.0.1:{args.port}")
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError(f"Gradle bootRun exited with {process.returncode}. See {log}")
            match = re.search(r"Starting PlaylistForEveryoneApplication using Java ([^ ]+) with PID (\d+)", log.read_text())
            if match:
                try:
                    status, health, _ = client.call("GET", "/actuator/health", timeout=2)
                    if status == 200 and health["status"] == "UP":
                        break
                except (URLError, TimeoutError, OSError):
                    pass
            time.sleep(.5)
        else:
            raise RuntimeError(f"Application readiness timed out. See {log}")
        pid = int(match.group(2))
        jcmd = args.java_home / "bin" / "jcmd"
        command(jcmd, str(pid), "JFR.start", "name=notification-baseline", f"settings=./{JFR_CONFIG}")
        yield client.base_url, pid, {"runtime": match.group(1), "gradle": gradle}
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=5)


def playlist_body(title):
    return {"title": title, "description": "Synchronous notification baseline"}


@contextmanager
def local_pool_probe(java_home, pid):
    source = SCRIPT_DIRECTORY / "PoolProbe.java"
    launch = [java_home / "bin" / "java", "--add-modules", "jdk.attach", source, str(pid)]
    with subprocess.Popen(launch, text=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE) as probe:
        try:
            if not select.select([probe.stdout], [], [], 30)[0] or probe.stdout.readline().strip() != "ready":
                raise RuntimeError("Local Hikari JMX probe did not connect")

            def sample():
                probe.stdin.write("sample\n")
                probe.stdin.flush()
                if not select.select([probe.stdout], [], [], 5)[0]:
                    raise RuntimeError("Local Hikari JMX sample timed out")
                pending, active, idle = map(int, probe.stdout.readline().split())
                return {"pending": pending, "active": active, "idle": idle}

            yield sample
        finally:
            probe.stdin.close()
            try:
                probe.wait(timeout=5)
            except subprocess.TimeoutExpired:
                probe.kill()
                probe.wait()


def timed_workload(sample, owner, prefix, concurrency, requests):
    pending_samples = []
    probe_errors = []
    stop = threading.Event()
    barrier = threading.Barrier(concurrency + 1)

    def sample_pool():
        while not stop.is_set():
            tick = time.monotonic()
            try:
                values = sample()
                pending_samples.append({"elapsedMs": round((tick - started) * 1000, 3), **values})
            except (RuntimeError, ValueError, OSError) as error:
                probe_errors.append(type(error).__name__)
                break
            stop.wait(max(0, .1 - (time.monotonic() - tick)))

    def worker(index):
        client = owner.clone()
        barrier.wait()
        results = []
        for sequence in range(index + 1, requests + 1, concurrency):
            title = f"{prefix}{sequence}"
            request_started = time.monotonic()
            try:
                status, body, elapsed = client.call("POST", "/api/playlists", body=playlist_body(title))
                response_id = str(UUID(body["id"])) if status == 201 else None
                client_error = None
            except (URLError, OSError, IncompleteRead) as error:
                status, response_id = None, None
                elapsed = (time.monotonic() - request_started) * 1000
                client_error = type(error).__name__
            results.append({
                "sequence": sequence, "worker": index + 1, "title": title,
                "httpStatus": status, "httpMs": round(elapsed, 6),
                "requestStartMs": round((request_started - started) * 1000, 6),
                "responsePlaylistId": response_id, "clientError": client_error,
            })
        return results

    with ThreadPoolExecutor(max_workers=concurrency) as workers:
        futures = [workers.submit(worker, index) for index in range(concurrency)]
        sampler = threading.Thread(target=sample_pool)
        started = time.monotonic()
        sampler.start()
        barrier.wait()
        try:
            results = sorted((row for future in futures for row in future.result()), key=lambda row: row["sequence"])
        finally:
            duration = time.monotonic() - started
            stop.set()
            sampler.join()
    return results, duration, pending_samples, probe_errors


def time_remaining(deadline):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise TimeoutError("Measurement completion deadline reached")
    return remaining


def jfr_samples(java_home, checkout, pid, label, deadline):
    # Keep the last complete recording if the next dump reaches the deadline.
    relative_dump = JFR_DIRECTORY / f"{label}.pending.jfr"
    dump = checkout / relative_dump
    try:
        command(java_home / "bin" / "jcmd", str(pid), "JFR.dump", "name=notification-baseline",
                f"filename=./{relative_dump}", timeout=time_remaining(deadline))
        payload = command(java_home / "bin" / "jfr", "print", "--json", "--events", JFR_EVENT, str(dump),
                          timeout=time_remaining(deadline))
        events = json.loads(payload)["recording"]["events"]
        dump.replace(checkout / JFR_DIRECTORY / f"{label}.jfr")
    finally:
        dump.unlink(missing_ok=True)
    return [event["values"] for event in events]


def committed_playlists(db, prefix, *, timeout):
    return json.loads(postgres(db, f"""
        select coalesce(json_object_agg(title, id), '{{}}'::json)
        from playlists where title like '{prefix}%'
    """, timeout=timeout))


def collect_completion(db, java_home, checkout, pid, label, prefix, results):
    deadline = time.monotonic() + COMPLETION_WAIT_SECONDS
    no_response = {row["title"] for row in results if row["httpStatus"] is None}
    committed, events = {}, []
    try:
        while True:
            committed = committed_playlists(db, prefix, timeout=time_remaining(deadline))
            events = jfr_samples(java_home, checkout, pid, label, deadline)
            completed_ids = {event["playlistId"] for event in events}
            if completed_ids.issuperset(committed.values()) and not (no_response - committed.keys()):
                break
            time.sleep(min(.5, time_remaining(deadline)))
    except (TimeoutError, subprocess.TimeoutExpired):
        settled = False
    else:
        settled = True

    playlist_ids = set(committed.values())
    events = [event for event in events if event["playlistId"] in playlist_ids]
    incomplete = len(playlist_ids - {event["playlistId"] for event in events})
    unknown = len(no_response - committed.keys())
    return committed, events, incomplete, unknown, settled


def compare_notifications(db, prefix, name, followers, results, committed, completed_ids):
    payload = postgres(db, f"""
        select coalesce(json_agg(json_build_array(receiver_id, content)), '[]'::json)
        from notifications where title = '{NOTIFICATION_TITLE}' and content like '{name}님이 "{prefix}%'
    """)
    actual = Counter(map(tuple, json.loads(payload)))
    expected = Counter()
    completed_expected = Counter()
    for row in results:
        playlist_id = committed.get(row["title"])
        if playlist_id is None:
            continue
        content = f'{name}님이 "{row["title"]}" 플레이리스트를 만들었습니다.'
        pairs = [(follower_id, content) for follower_id in followers]
        expected.update(pairs)
        if playlist_id in completed_ids:
            completed_expected.update(pairs)
    return {
        "expected": expected.total(), "savedRows": actual.total(), "matchedRows": (actual & expected).total(),
        "missing": (expected - actual).total(), "unexpectedOrDuplicate": (actual - expected).total(),
        "missingAfterCompletion": (completed_expected - actual).total(),
    }


def run_round(admin, fixture, db, args, checkout, output, pid, label, sample_pool, *, pool, concurrency, count, warmup, stress=False):
    owner, name, followers = fixture
    tag = checkout.parent.name.rsplit("-", 1)[-1]
    for index in range(1, warmup + 1):
        owner.require("POST", "/api/playlists", 201, body=playlist_body(f"N223 {tag} {label} warmup {index}"))
    prefix = f"N223 {tag} {label} request "
    starting_rows = table_sizes(db)
    before = snapshot(admin)
    results, duration, pool_samples, pool_probe_errors = timed_workload(sample_pool, owner, prefix, concurrency, count)
    committed, events, incomplete, unknown, settled = collect_completion(
        db, args.java_home, checkout, pid, label, prefix, results
    )
    saved = compare_notifications(db, prefix, name, followers, results, committed,
                                  {event["playlistId"] for event in events})
    after = snapshot(admin) if settled else None

    events_by_id = {}
    for event in events:
        events_by_id.setdefault(event["playlistId"], []).append(event)
    http_times = []
    notification_times = []
    for result in results:
        result["committedPlaylistId"] = committed.get(result["title"])
        observed = events_by_id.get(result["committedPlaylistId"], [])
        result["jfrEvents"] = len(observed)
        result["notificationListenerCount"] = sum(event["listenerCount"] for event in observed)
        result["notificationListenerMs"] = round(sum(event["listenerNanos"] for event in observed) / 1e6, 6) if observed else None
        if result["httpStatus"] == 201:
            http_times.append(result["httpMs"])
        if len(observed) == 1 and result["notificationListenerCount"] == len(followers):
            notification_times.append(result["notificationListenerMs"])
    success = len(http_times)
    jfr_count = sum(event["listenerCount"] for event in events)
    if settled:
        failures = {stage: after["failures"][stage] - before["failures"][stage] for stage in FAILURE_STAGES}
        processing_count = after["processing"].get("COUNT", 0) - before["processing"].get("COUNT", 0)
        publish_attempts = after["publishAttempts"] - before["publishAttempts"]
        saved_counter = after["saved"] - before["saved"]
        sse = after["ssePublished"] - before["ssePublished"]
        timeouts = after["hikari"]["timeout"]["COUNT"] - before["hikari"]["timeout"]["COUNT"]
        acquire_count = after["hikari"]["acquire"]["COUNT"] - before["hikari"]["acquire"]["COUNT"]
        acquire_time = round(after["hikari"]["acquire"]["TOTAL_TIME"] - before["hikari"]["acquire"]["TOTAL_TIME"], 6)
    else:
        failures = dict.fromkeys(FAILURE_STAGES)
        processing_count = publish_attempts = saved_counter = sse = None
        timeouts = acquire_count = acquire_time = None
    total_expected = saved["expected"]
    summary = {
        "label": label, "receivers": len(followers), "concurrency": concurrency, "pool": pool,
        "warmup": warmup, "requests": count, "successfulHttp": success, "committed": len(committed),
        "startingTableRows": starting_rows,
        "httpStatuses": dict(Counter(str(row["httpStatus"] or "no_response") for row in results)),
        "httpErrorRatePct": round((count - success) * 100 / count, 3),
        "wallSeconds": round(duration, 6), "successfulRequestsPerSecond": round(success / duration, 3),
        "httpSuccessMs": percentiles(http_times), "httpSuccessSamples": success,
        "notificationPerRequestMs": percentiles(notification_times), "notificationSamples": len(notification_times),
        "notifications": {
            **saved, "savedCounter": saved_counter, "publishAttempts": publish_attempts, "processingCount": processing_count,
            "jfrListenerCount": jfr_count, "ssePublished": sse, "failures": failures,
            "committedWithNoCompletionEvent": incomplete, "unknownCommitAfterNoResponse": unknown,
            "duplicateCompletionEvents": sum(len(items) - 1 for items in events_by_id.values()),
        },
        "hikari": {
            "sampleCount": len(pool_samples), "pendingPeak": max((sample["pending"] for sample in pool_samples), default=None),
            "activePeak": max((sample["active"] for sample in pool_samples), default=None),
            "idleLow": min((sample["idle"] for sample in pool_samples), default=None), "timeouts": timeouts,
            "acquireCount": acquire_count, "acquireTotalSeconds": acquire_time,
        },
    }
    observability_ok = bool(pool_samples) and not pool_probe_errors and before["hikari"]["max"]["VALUE"] == pool
    normal_ok = (
        settled and success == len(committed) == count
        and all(row["responsePlaylistId"] == row["committedPlaylistId"] for row in results)
        and saved["missing"] == saved["unexpectedOrDuplicate"] == 0
        and publish_attempts == processing_count == jfr_count == saved_counter == saved["savedRows"] == sse == total_expected
        and len(notification_times) == count
        and incomplete == unknown == timeouts == summary["notifications"]["duplicateCompletionEvents"] == 0
        and not any(failures.values())
    )
    summary["checks"] = {"observability": observability_ok, "roundSettled": settled,
                         "normalScenario": None if stress else normal_ok}
    write_json(output / f"{label}-raw.json", {
        "requests": results, "poolSamples": pool_samples, "poolProbeErrors": pool_probe_errors,
        "before": before, "after": after,
    })
    print(json.dumps(summary, ensure_ascii=False), flush=True)
    return summary


def application_environment(connection, java_home, admin_email, admin_password):
    env = {
        name: value for name, value in os.environ.items()
        if not name.startswith(("SPRING_FLYWAY_", "SPRING_CONFIG_", "SPRING_DATASOURCE_"))
    }
    for variable in ("SPRING_APPLICATION_JSON", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        env.pop(variable, None)
    return env | connection | {
        "JAVA_HOME": str(java_home), "SPRING_PROFILES_ACTIVE": "dev",
        "EVERYONE_ADMIN_ENABLED": "true", "EVERYONE_ADMIN_NAME": "Notification Baseline",
        "EVERYONE_ADMIN_EMAIL": admin_email, "EVERYONE_ADMIN_PASSWORD": admin_password,
        "EVERYONE_DOCKER_COMPOSE_ENABLED": "false", "EVERYONE_COLLECTION_SCHEDULE_ENABLED": "false",
        "EVERYONE_SSE_TRANSPORT": "redis", "EVERYONE_SSE_SUBSCRIPTION_ENABLED": "true",
        "EVERYONE_S3_BUCKET": "local-placeholder", "EVERYONE_TMDB_ACCESS_TOKEN": "local-placeholder",
        "JWT_SECRET_KEY": base64.b64encode(secrets.token_bytes(48)).decode(),
        "JWT_REFRESH_COOKIE_SECURE": "false", "JWT_ACCESS_TOKEN_VALIDITY": "3h",
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{connection['EVERYONE_DB_PORT']}/loadtest",
        "SPRING_DATASOURCE_USERNAME": connection["EVERYONE_DB_USERNAME"],
        "SPRING_DATASOURCE_PASSWORD": connection["EVERYONE_DB_PASSWORD"],
        "SPRING_FLYWAY_ENABLED": "true",
        "SPRING_DATA_REDIS_URL": f"redis://127.0.0.1:{connection['EVERYONE_REDIS_PORT']}",
    }


def main(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise RuntimeError("Choose an empty results directory")
    with tempfile.TemporaryDirectory(prefix="notification223-") as directory:
        work = Path(directory)
        checkout = prepare_checkout(work)
        with containers(work) as (db, redis, connection):
            tag = secrets.token_hex(4)
            admin_email = "notification223@local.example"
            admin_password = secrets.token_urlsafe(24) + "aA1!"
            env = application_environment(connection, args.java_home, admin_email, admin_password)
            redis_info = command("docker", "exec", redis, "redis-cli", "INFO", "server")
            metadata = {
                "recordedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "baseline": BASELINE, "instrumentation": INSTRUMENTATION, "port": args.port,
                "environment": {
                    "host": platform.platform(), "architecture": platform.machine(), "logicalCpus": os.cpu_count(),
                    "python": platform.python_version(), "postgres": postgres(db, "show server_version"),
                    "redis": next(line.split(":", 1)[1] for line in redis_info.splitlines() if line.startswith("redis_version:")),
                    "docker": command("docker", "version", "--format", "{{.Server.Version}}"),
                    "postgresImage": command("docker", "inspect", "--format", "{{.Image}}", db),
                    "redisImage": command("docker", "inspect", "--format", "{{.Image}}", redis),
                    "profile": "dev", "database": "loadtest", "sseTransport": "redis",
                },
                "scenarios": args.groups, "requestTimeoutSeconds": 15,
                "completionWaitSeconds": COMPLETION_WAIT_SECONDS,
                "hikariProbeIntervalMs": 100, "hikariProbe": "JVM-local Hikari JMX; Actuator snapshots before and after the workload",
                "hikariAcquisitionScope": HIKARI_ACQUISITION_SCOPE,
                "percentiles": "nearest-rank",
                "normalPoolSize": 10, "stressPoolSize": 2, "stressConnectionTimeoutMs": 1000,
                "normalConnectionTimeoutMs": 30000,
                "jdk": command(args.java_home / "bin" / "java", "-version", stderr=subprocess.STDOUT).splitlines()[0],
                "measurement": {
                    "http": "response body fully read; latency percentiles include 201 responses",
                    "notification": "sum of per-recipient listener samples grouped by committed playlist ID; percentiles include complete recipient sample sets",
                    "throughput": "201 responses divided by workload wall time, concurrent Hikari sampling enabled",
                },
            }
            summaries = []
            stop_reason = None
            for pool, timeout, label in ((10, 30000, "normal"), (2, 1000, "stress")):
                if label not in args.groups:
                    continue
                with application(checkout, output, args, env, pool, timeout, label) as (base_url, pid, launch):
                    metadata[label + "Launch"] = launch
                    admin = Client(base_url)
                    health_status = admin.require("GET", "/actuator/health", 200)["status"]
                    anonymous_metrics, _, _ = admin.call("GET", "/actuator/metrics")
                    admin.login(admin_email, admin_password)
                    admin_metrics, index, _ = admin.call("GET", "/actuator/metrics")
                    one = make_fixture(base_url, db, tag + label, 1) if label == "normal" else None
                    ten = make_fixture(base_url, db, tag + label, 10)
                    ordinary_metrics, _, _ = (one or ten)[0].call("GET", "/actuator/metrics")
                    listed = [HIKARI + suffix for suffix in POOL_METERS]
                    if (health_status, anonymous_metrics, ordinary_metrics, admin_metrics) != ("UP", 401, 403, 200):
                        raise RuntimeError("Actuator authentication or health check failed")
                    if not all(name in index["names"] for name in listed):
                        raise RuntimeError("Required Hikari meters are missing")
                    metadata[label + "Preflight"] = {
                        "healthHttp": 200, "status": health_status, "anonymousMetricsHttp": anonymous_metrics,
                        "userMetricsHttp": ordinary_metrics, "adminMetricsHttp": admin_metrics, "hikariMeters": listed,
                    }
                    write_json(output / "conditions.json", metadata)
                    print(f"{label} preflight verified, starting measurements", flush=True)
                    with local_pool_probe(args.java_home, pid) as sample_pool:
                        cases = (
                            (("r1c1", one, 1), ("r10c1", ten, 1), ("r10c5", ten, 5))
                            if label == "normal" else (("r10c5p2", ten, 5),)
                        )
                        rounds = [
                            (f"{scenario}-{number}", fixture, concurrency, 25 if label == "stress" else 100, 10)
                            for number in range(1, 4) for scenario, fixture, concurrency in cases
                        ]
                        if label == "normal":
                            rounds.insert(0, ("smoke", one, 1, 1, 0))
                        for round_label, fixture, concurrency, count, warmup in rounds:
                            result = run_round(
                                admin, fixture, db, args, checkout, output, pid, round_label, sample_pool,
                                pool=pool, concurrency=concurrency, count=count, warmup=warmup,
                                stress=label == "stress",
                            )
                            summaries.append(result)
                            write_json(output / "summary.json", summaries)
                            if not result["checks"]["roundSettled"]:
                                stop_reason = f"{round_label} did not settle before the completion deadline"
                                break
                            if round_label == "smoke" and not all(result["checks"].values()):
                                stop_reason = "Single-request measurement failed counters or JFR cross-check"
                                break
                if stop_reason:
                    break
            with ZipFile(output/"raw.zip", "w", compression=ZIP_DEFLATED) as archive:
                for row in summaries:
                    round_label = row["label"]
                    archive.write(output / f"{round_label}-raw.json", arcname=f"{round_label}-raw.json")
                    recording = checkout / JFR_DIRECTORY / f"{round_label}.jfr"
                    if recording.is_file():
                        archive.write(recording, arcname=recording.name)
            if stop_reason:
                raise SystemExit(f"{stop_reason}. See summary.json and raw.zip")
            if not all(row["checks"]["observability"] and row["checks"]["normalScenario"] is not False for row in summaries):
                raise SystemExit("A measurement or counter check failed. See summary.json")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, required=True, help="JDK 17 installation")
    parser.add_argument("--output", type=Path, required=True, help="Empty result directory")
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--groups", choices=("normal", "stress"), nargs="+", default=["normal", "stress"])
    main(parser.parse_args())
