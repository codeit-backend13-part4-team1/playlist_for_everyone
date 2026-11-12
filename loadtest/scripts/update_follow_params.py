"""loadtest/data/follow_params.csv 를 현재 loadtest DB 기준으로 만든다. (follow-scenario.jmx 용)

각 행: 이메일, 이미 팔로우 중인 대상 ID, 새로 팔로우할 대상 ID, 인기 계정 ID, 보통 계정 ID
- 사용자: loadtest1 ~ loadtest200
- 이미 팔로우 중인 대상: loadtest(9800+i)  (seed_follow.sql 이 만든 관계)
- 새로 팔로우할 대상:    loadtest(9600+i)  (시드에 없는 관계)
- 인기 계정: loadtest1 (팔로워 가장 많음), 보통 계정: loadtest5000

실행: 프로젝트 루트에서 `python3 loadtest/scripts/update_follow_params.py`
전제: seed_follow.sql 을 실행한 상태 (시드를 다시 돌렸다면 다시 실행)
"""
import os
import subprocess
import sys

USER_COUNT = 200
OUTPUT = "loadtest/data/follow_params.csv"

SQL = f"""
WITH u AS (
  SELECT id, email, substring(email from '^loadtest([0-9]+)@')::int AS n
  FROM users
  WHERE email ~ '^loadtest[0-9]+@example\\.com$'
)
SELECT me.email, a.id, b.id, pop.id, mid.id
FROM u me
JOIN u a   ON a.n = me.n + 9800
JOIN u b   ON b.n = me.n + 9600
JOIN u pop ON pop.n = 1
JOIN u mid ON mid.n = 5000
WHERE me.n BETWEEN 1 AND {USER_COUNT}
  AND EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = me.id AND f.followee_id = a.id)
  AND NOT EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = me.id AND f.followee_id = b.id)
ORDER BY me.n;
"""

CMD = [
    "docker", "compose", "--env-file", ".env.dev", "exec", "-T", "postgres",
    "psql", "-U", "everyone", "-d", "loadtest", "-At", "-F", "|", "-v", "ON_ERROR_STOP=1",
    "-c", SQL,
]


def main() -> None:
    try:
        result = subprocess.run(CMD, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        print("DB 조회 실패:\n" + (e.stderr or e.stdout), file=sys.stderr)
        sys.exit(1)

    rows = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.rstrip("\r").split("|")
        if len(parts) != 5 or not all(p.strip() for p in parts):
            print(f"예상하지 못한 행: {line!r}", file=sys.stderr)
            sys.exit(1)
        rows.append(",".join(p.strip() for p in parts))

    if len(rows) != USER_COUNT:
        print(
            f"행 수가 {USER_COUNT}이 아니에요: {len(rows)}. seed_follow.sql 을 먼저 실행했는지 확인하세요. "
            "follow_params.csv는 바꾸지 않았어요.",
            file=sys.stderr,
        )
        sys.exit(1)

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", newline="\n", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")

    print(f"Done! {len(rows)} rows written to {OUTPUT}")
    print("첫 2행:", *rows[:2], sep="\n  ")


if __name__ == "__main__":
    main()
