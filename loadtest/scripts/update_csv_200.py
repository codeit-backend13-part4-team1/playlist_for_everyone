"""loadtest/data/users.csv 를 현재 loadtest DB 기준으로 새로 만든다.

- 계정: loadtest1 ~ loadtest200
- 구독 대상: 계정마다 서로 다른 플레이리스트 1개
  - 소유자가 loadtest201 이상인 계정이라 본인 소유일 수 없다
  - loadtest1~200 중 누구도 이미 구독하지 않은 플레이리스트만 고른다
  - 소유자가 서로 다른 플레이리스트만 고른다 (같은 행에 동시 요청이 몰리지 않게)

실행: 프로젝트 루트에서 `python3 loadtest/scripts/update_csv_200.py`
전제: postgres 컨테이너가 떠 있고 loadtest DB 가 시드된 상태 (시드를 다시 돌렸다면 반드시 다시 실행)
"""
import os
import subprocess
import sys

USER_COUNT = 200
OUTPUT = "loadtest/data/users.csv"

SQL = f"""
WITH u AS (
  SELECT id, email,
         substring(email from '^loadtest([0-9]+)@')::int AS n
  FROM users
  WHERE email ~ '^loadtest[0-9]+@example\\.com$'
    AND substring(email from '^loadtest([0-9]+)@')::int BETWEEN 1 AND {USER_COUNT}
),
cand AS (
  SELECT DISTINCT ON (p.owner_id) p.id
  FROM playlists p
  JOIN users o ON o.id = p.owner_id
  WHERE o.email ~ '^loadtest[0-9]+@example\\.com$'
    AND substring(o.email from '^loadtest([0-9]+)@')::int > {USER_COUNT}
    AND NOT EXISTS (
      SELECT 1 FROM subscriptions s
      WHERE s.playlist_id = p.id
        AND s.subscriber_id IN (SELECT id FROM u)
    )
  ORDER BY p.owner_id, p.id
),
picked AS (
  SELECT id, row_number() OVER (ORDER BY id) AS rn
  FROM cand
),
numbered AS (
  SELECT email, n, row_number() OVER (ORDER BY n) AS rn
  FROM u
)
SELECT numbered.email, picked.id
FROM numbered
JOIN picked USING (rn)
ORDER BY numbered.n;
"""

CMD = [
    "docker", "compose", "--env-file", ".env.dev", "exec", "-T", "postgres",
    "psql", "-U", "everyone", "-d", "loadtest", "-At", "-F", ",", "-v", "ON_ERROR_STOP=1",
    "-c", SQL,
]


def main() -> None:
    try:
        result = subprocess.run(CMD, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        print("DB 조회 실패:\n" + (e.stderr or e.stdout), file=sys.stderr)
        sys.exit(1)

    rows = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if len(rows) != USER_COUNT:
        print(
            f"행 수가 {USER_COUNT}이 아니에요: {len(rows)}. "
            "loadtest 계정/시드 상태를 확인하세요. users.csv는 바꾸지 않았어요.",
            file=sys.stderr,
        )
        sys.exit(1)

    # 줄 끝은 LF 로 고정한다.
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", newline="\n") as f:
        f.write("\n".join(rows) + "\n")

    print(f"Done! {len(rows)} rows written to {OUTPUT}")
    print("첫 2행:", *rows[:2], sep="\n  ")


if __name__ == "__main__":
    main()