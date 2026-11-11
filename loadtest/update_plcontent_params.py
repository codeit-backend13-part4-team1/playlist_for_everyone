"""loadtest/plcontent_params.csv 생성. (playlist-content-scenario.jmx 용)

각 행: 이메일, 본인 플레이리스트 ID, 추가할 콘텐츠 A, 추가할 콘텐츠 B  (loadtest1 ~ loadtest200)
  - 플레이리스트: 해당 사용자가 소유한 플레이리스트 중 ID가 가장 작은 것
  - 콘텐츠: lt-c-(5000+n), lt-c-(5200+n)  (아무 플레이리스트에도 담겨 있지 않은 시드 콘텐츠)
  - 이전 실행이 중간에 끊겨 남은 연결이 있으면 먼저 지운다.

전제: seed_content.sql 을 실행한 상태 (lt-c-* 콘텐츠 존재)
실행: 프로젝트 루트에서 `python3 loadtest/update_plcontent_params.py` (실행할 때마다 사용)
"""
import subprocess
import sys

USER_COUNT = 200
OUTPUT = "loadtest/plcontent_params.csv"
BASE = ["docker", "compose", "--env-file", ".env.dev", "exec", "-T", "postgres",
        "psql", "-U", "everyone", "-d", "loadtest", "-v", "ON_ERROR_STOP=1"]

PREP = """
DO $$ BEGIN
  IF current_database() <> 'loadtest' THEN RAISE EXCEPTION 'loadtest DB 에서만 실행'; END IF;
END $$;
DELETE FROM playlist_contents
 WHERE content_id IN (SELECT id FROM contents
                      WHERE provider_item_id ~ '^lt-c-[0-9]+$'
                        AND substring(provider_item_id from 6)::int BETWEEN 5001 AND 5400);
"""

QUERY = f"""
SELECT u.email, p.id, ca.id, cb.id
FROM (SELECT id, email, substring(email from '^loadtest([0-9]+)@')::int AS n
      FROM users WHERE email ~ '^loadtest[0-9]+@example\\.com$') u
JOIN (SELECT DISTINCT ON (owner_id) owner_id, id FROM playlists ORDER BY owner_id, id) p ON p.owner_id = u.id
JOIN contents ca ON ca.provider_item_id = 'lt-c-' || (5000 + u.n)
JOIN contents cb ON cb.provider_item_id = 'lt-c-' || (5200 + u.n)
WHERE u.n BETWEEN 1 AND {USER_COUNT}
ORDER BY u.n;
"""


def run(args, sql):
    try:
        return subprocess.run(BASE + args + ["-c", sql], capture_output=True, text=True, check=True).stdout
    except subprocess.CalledProcessError as e:
        print("DB 실행 실패:\n" + (e.stderr or e.stdout), file=sys.stderr)
        sys.exit(1)


def main() -> None:
    run([], PREP)
    out = run(["-At", "-F", ","], QUERY)
    rows = [line.strip() for line in out.splitlines() if line.strip()]
    if len(rows) != USER_COUNT or any(len(r.split(",")) != 4 for r in rows):
        print(f"행 수가 {USER_COUNT}이 아니에요: {len(rows)}. seed_content.sql 을 먼저 실행했는지 확인하세요. plcontent_params.csv는 바꾸지 않았어요.", file=sys.stderr)
        sys.exit(1)
    with open(OUTPUT, "w", newline="\n", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    print(f"Done! {len(rows)} rows written to {OUTPUT}")
    print("첫 2행:", *rows[:2], sep="\n  ")


if __name__ == "__main__":
    main()
