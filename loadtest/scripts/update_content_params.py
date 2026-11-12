"""loadtest/data/content_params.csv 를 현재 loadtest DB 기준으로 만든다. (content-scenario.jmx 용)

각 행: 이메일, 사용자 ID, 리뷰많은콘텐츠 ID, 일반콘텐츠 ID, 삭제대상 콘텐츠 ID  (loadtest1 ~ loadtest200)
삭제 테스트는 대상 콘텐츠를 지우므로 실행 전마다 seed_content.sql -> 이 스크립트 순서로 실행한다.

실행: 프로젝트 루트에서 `python3 loadtest/scripts/update_content_params.py`
"""
import os
import subprocess
import sys

USER_COUNT = 200
OUTPUT = "loadtest/data/content_params.csv"

SQL = f"""
SELECT u.email, u.id,
       (SELECT id FROM contents WHERE provider_item_id = 'lt-c-1'),
       (SELECT id FROM contents WHERE provider_item_id = 'lt-c-' || (u.n + 1)),
       (SELECT id FROM contents WHERE title = 'LT-DEL-' || u.n)
FROM (SELECT id, email, substring(email from '^loadtest([0-9]+)@')::int AS n
      FROM users WHERE email ~ '^loadtest[0-9]+@example\\.com$') u
WHERE u.n BETWEEN 1 AND {USER_COUNT}
ORDER BY u.n;
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

    rows = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.rstrip("\r").split(",")
        if len(parts) != 5 or not all(p.strip() for p in parts):
            print(f"예상하지 못한 행(삭제 대상이 이미 지워졌을 수 있어요. seed_content.sql 을 다시 실행하세요): {line!r}", file=sys.stderr)
            sys.exit(1)
        rows.append(",".join(p.strip() for p in parts))

    if len(rows) != USER_COUNT:
        print(f"행 수가 {USER_COUNT}이 아니에요: {len(rows)}. seed_content.sql 을 먼저 실행했는지 확인하세요. content_params.csv는 바꾸지 않았어요.", file=sys.stderr)
        sys.exit(1)

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", newline="\n", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")

    print(f"Done! {len(rows)} rows written to {OUTPUT}")
    print("첫 2행:", *rows[:2], sep="\n  ")


if __name__ == "__main__":
    main()
