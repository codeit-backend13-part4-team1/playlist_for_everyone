"""loadtest/list_params.csv 를 현재 loadtest DB 기준으로 만든다. (playlist-list-variants.jmx 용)

각 행: 이메일, 사용자 ID, 흔한 키워드, 희귀 키워드
- 이메일/사용자 ID: loadtest1 ~ loadtest200 (프로필의 소유자 필터·구독 필터에 쓴다)
- 흔한 키워드: 제목의 첫 단어 중 가장 많이 나오는 것 (모든 행이 같은 값)
- 희귀 키워드: 무작위로 고른 플레이리스트의 전체 제목 (사용자마다 다름)

실행: 프로젝트 루트에서 `python3 loadtest/update_list_params.py`
전제: postgres 컨테이너가 떠 있고 loadtest DB 가 시드된 상태 (시드를 다시 돌렸다면 다시 실행)
"""
import subprocess
import sys

USER_COUNT = 200
OUTPUT = "loadtest/list_params.csv"

SQL = f"""
WITH u AS (
  SELECT id, email,
         substring(email from '^loadtest([0-9]+)@')::int AS n
  FROM users
  WHERE email ~ '^loadtest[0-9]+@example\\.com$'
    AND substring(email from '^loadtest([0-9]+)@')::int BETWEEN 1 AND {USER_COUNT}
),
clean AS (
  SELECT title FROM playlists
  WHERE title <> '' AND title !~ '[,"|%_]'
),
common AS (
  SELECT split_part(title, ' ', 1) AS k
  FROM clean
  WHERE split_part(title, ' ', 1) <> ''
  GROUP BY 1
  ORDER BY count(*) DESC
  LIMIT 1
),
rare AS (
  SELECT title, row_number() OVER () AS rn
  FROM (SELECT title FROM clean ORDER BY random() LIMIT {USER_COUNT}) t
),
numbered AS (
  SELECT id, email, n, row_number() OVER (ORDER BY n) AS rn
  FROM u
)
SELECT numbered.email, numbered.id, (SELECT k FROM common), rare.title
FROM numbered
JOIN rare USING (rn)
ORDER BY numbered.n;
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
        if len(parts) != 4 or not all(p.strip() for p in parts):
            print(f"예상하지 못한 행: {line!r}", file=sys.stderr)
            sys.exit(1)
        rows.append(",".join(p.strip() for p in parts))

    if len(rows) != USER_COUNT:
        print(
            f"행 수가 {USER_COUNT}이 아니에요: {len(rows)}. "
            "loadtest 계정/시드 상태를 확인하세요. list_params.csv는 바꾸지 않았어요.",
            file=sys.stderr,
        )
        sys.exit(1)

    # 줄 끝은 LF 로 고정한다.
    with open(OUTPUT, "w", newline="\n", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")

    print(f"Done! {len(rows)} rows written to {OUTPUT}")
    print("첫 2행:", *rows[:2], sep="\n  ")


if __name__ == "__main__":
    main()
