-- loadtest/data/review_params.csv 를 만든다. (review-scenario.jmx, content-search-scenario.jmx 용)
-- 각 행: 이메일, 사용자 ID, 리뷰많은콘텐츠 ID, 일반콘텐츠 ID, 리뷰생성 대상 콘텐츠 ID,
--        수정 대상 리뷰 ID, 바꿀 평점, 삭제 대상 리뷰 ID  (loadtest1 ~ loadtest200)
--
-- 리뷰많은콘텐츠: lt-c-1 (리뷰 5,000건)
-- 일반콘텐츠: lt-c-(n+1) (리뷰 20건)
-- 생성 대상: lt-c-(2001+n) (시드에서 리뷰가 없는 콘텐츠라 (콘텐츠, 작성자) 중복 제약에 걸리지 않는다)
-- 수정 대상: 사용자 n 이 lt-c-1 에 쓴 리뷰. 바꿀 평점은 현재 평점과 다른 값(평점 변경이 되도록)
-- 삭제 대상: 사용자 n 이 lt-c-n 에 쓴 리뷰 (n=1 은 lt-c-1)
--
-- 전제: seed_content.sql 을 실행한 상태. 생성·삭제가 데이터를 바꾸므로 실행 전마다 시드를 다시 넣는다.
-- 실행(프로젝트 루트): README 의 mkrp 함수 참고. psql 을 -At -F , 로 실행해 CSV 로 저장한다.
WITH hot AS (SELECT id FROM contents WHERE provider_item_id = 'lt-c-1'),
u AS (
  SELECT id, email, substring(email from '^loadtest([0-9]+)@')::int AS n
  FROM users WHERE email ~ '^loadtest[0-9]+@example\.com$'
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
WHERE u.n BETWEEN 1 AND 200
ORDER BY u.n;
