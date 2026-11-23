"""loadtest/data/review_params.csv 를 현재 loadtest DB 기준으로 만든다. (review-scenario.jmx, content-search-scenario.jmx 용)

각 행: 이메일, 사용자 ID, 리뷰많은콘텐츠 ID, 일반콘텐츠 ID, 리뷰생성 대상 콘텐츠 ID,
       수정 대상 리뷰 ID, 바꿀 평점, 삭제 대상 리뷰 ID  (loadtest1 ~ loadtest200)

- 리뷰많은콘텐츠: lt-c-1 (리뷰 5,000건)
- 일반콘텐츠: lt-c-(n+1) (리뷰 20건)
- 생성 대상: lt-c-(2001+n) (시드에서 리뷰가 없는 콘텐츠라 (콘텐츠, 작성자) 중복 제약에 걸리지 않는다)
- 수정 대상: 사용자 n 이 lt-c-1 에 쓴 리뷰. 바꿀 평점은 현재 평점과 다른 값(평점 변경이 되도록)
- 삭제 대상: 사용자 n 이 lt-c-n 에 쓴 리뷰 (n=1 은 lt-c-1)

생성·삭제가 데이터를 바꾸므로 실행 전마다 seed_content.sql -> 이 스크립트 순서로 실행한다.

실행: 프로젝트 루트에서 `python3 loadtest/scripts/update_review_params.py`
전제: seed_content.sql 을 실행한 상태
"""
import os
import subprocess
import sys

USER_COUNT = 200
OUTPUT = "loadtest/data/review_params.csv"

SQL = f"""
WITH hot AS (SELECT id FROM contents WHERE provider_item_id = 'lt-c-1'),
u AS (
  SELECT id, email, substring(email from '^loadtest([0-9]+)@')::int AS n
  FROM users WHERE email ~ '^loadtest[0-9]+@example\\.com$'
)
SELECT u.email, u.id,
       (SELECT id FROM hot),
       (SELECT id FROM contents WHERE provider_item_id = 'lt-c-' || (u.n + 1)),
       (SELECT id FROM contents WHERE provider_item_id = 'lt-c-' || (2001 + u.n)),
       r1.id,
       CASE WHEN r1.rating = 1.0 THEN 2.0 ELSE 1.0 END,
       r2.id
FROM u
JOIN reviews r1 ON r1.author_id = u.id AND r1.content_id = (SELECT id FROM hot)
JOIN reviews r2 ON r2.author_id = u.id
               AND r2.content_id = (SELECT id FROM contents
                                    WHERE provider_item_id = 'lt-c-' || (CASE WHEN u.n = 1 THEN 1 ELSE u.n END))
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
        if len(parts) != 8 or not all(p.strip() for p in parts):
            print(f"예상하지 못한 행(시드가 안 됐거나 이미 지워졌을 수 있어요. seed_content.sql 을 다시 실행하세요): {line!r}", file=sys.stderr)
            sys.exit(1)
        rows.append(",".join(p.strip() for p in parts))

    if len(rows) != USER_COUNT:
        print(f"행 수가 {USER_COUNT}이 아니에요: {len(rows)}. seed_content.sql 을 먼저 실행했는지 확인하세요. review_params.csv는 바꾸지 않았어요.", file=sys.stderr)
        sys.exit(1)

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", newline="\n", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")

    print(f"Done! {len(rows)} rows written to {OUTPUT}")
    print("첫 2행:", *rows[:2], sep="\n  ")


if __name__ == "__main__":
    main()
