"""사용자 도메인 테스트 준비 + loadtest/data/user_params.csv 생성. (user-scenario.jmx 용)

하는 일 (loadtest DB 전용)
  1) 이전 실행에서 가입된 테스트 계정(lt-new-*)과 그 알림을 삭제
  2) loadtest1~200 을 ADMIN 으로 (권한 변경·잠금 API 호출용)
  3) loadtest1001~1200 (변경 대상)을 USER · 잠금 해제 상태로 되돌림
  4) user_params.csv 작성: 이메일(loadtest n), 대상 사용자 ID(loadtest n+1000)

실행: 프로젝트 루트에서 `python3 loadtest/scripts/update_user_params.py`  (실행할 때마다 사용)
"""
import os
import subprocess
import sys

USER_COUNT = 200
OUTPUT = "loadtest/data/user_params.csv"
BASE = ["docker", "compose", "--env-file", ".env.dev", "exec", "-T", "postgres",
        "psql", "-U", "everyone", "-d", "loadtest", "-v", "ON_ERROR_STOP=1"]

PREP = """
DO $$ BEGIN
  IF current_database() <> 'loadtest' THEN RAISE EXCEPTION 'loadtest DB 에서만 실행'; END IF;
END $$;
DELETE FROM notifications WHERE receiver_id IN (SELECT id FROM users WHERE email LIKE 'lt-new-%');
DELETE FROM users WHERE email LIKE 'lt-new-%';
UPDATE users SET role = 'ADMIN'
 WHERE substring(email from '^loadtest([0-9]+)@example\\.com$')::int BETWEEN 1 AND 200;
UPDATE users SET role = 'USER', enabled = true
 WHERE substring(email from '^loadtest([0-9]+)@example\\.com$')::int BETWEEN 1001 AND 1200;
"""

QUERY = f"""
SELECT a.email, t.id
FROM (SELECT email, substring(email from '^loadtest([0-9]+)@')::int AS n
      FROM users WHERE email ~ '^loadtest[0-9]+@example\\.com$') a
JOIN (SELECT id, substring(email from '^loadtest([0-9]+)@')::int AS n
      FROM users WHERE email ~ '^loadtest[0-9]+@example\\.com$') t ON t.n = a.n + 1000
WHERE a.n BETWEEN 1 AND {USER_COUNT}
ORDER BY a.n;
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
    if len(rows) != USER_COUNT or any(len(r.split(",")) != 2 for r in rows):
        print(f"행 수가 {USER_COUNT}이 아니에요: {len(rows)}. user_params.csv는 바꾸지 않았어요.", file=sys.stderr)
        sys.exit(1)
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", newline="\n", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    print(f"Done! {len(rows)} rows written to {OUTPUT}")
    print("첫 2행:", *rows[:2], sep="\n  ")


if __name__ == "__main__":
    main()
