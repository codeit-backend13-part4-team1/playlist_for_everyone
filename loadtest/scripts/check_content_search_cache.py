"""콘텐츠 검색 캐시(contentSearch)가 언제 쌓이고 언제 비워지는지 Redis 키 수로 확인한다. (부하 없음, 사용자 1명)

확인하는 것 (기대 동작은 코드 기준):
  1. 같은 검색을 두 번 하면 첫 번째는 캐시 미스(키 +1), 두 번째는 적중(키 그대로)
  2. 시청자수순(watcherCount) 정렬은 캐시하지 않는다 (키 그대로)
  3. 리뷰 생성 -> 캐시 전체 삭제
  4. 리뷰 텍스트만 수정 -> 캐시 유지
  5. 리뷰 평점 수정 -> 캐시 전체 삭제
  6. 리뷰 삭제 -> 캐시 전체 삭제

실행: 프로젝트 루트에서 `python3 loadtest/scripts/check_content_search_cache.py`
전제: 서버 실행 중, seed_content.sql 실행 직후 상태 (lt-c-2002 에 loadtest1 의 리뷰가 없어야 한다)
주의: contentSearch 캐시 키를 지운다 (loadtest 용 Redis 에서만 실행). 끝나면 만든 리뷰는 삭제한다.
"""
import http.cookiejar
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"
EMAIL = "loadtest1@example.com"
PASSWORD = "Loadtest1234!"
COMPOSE = ["docker", "compose", "--env-file", ".env.dev", "exec", "-T"]

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
token = {"access": None}


def xsrf():
    for c in jar:
        if c.name == "XSRF-TOKEN":
            return c.value
    return ""


def call(method, path, params=None, form=None, body=None, auth=True):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = None
    headers = {}
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if method != "GET":
        headers["X-XSRF-TOKEN"] = xsrf()
    if auth and token["access"]:
        headers["Authorization"] = "Bearer " + token["access"]
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    start = time.perf_counter()
    try:
        with opener.open(req) as r:
            raw = r.read()
            code = r.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        code = e.code
    ms = (time.perf_counter() - start) * 1000
    try:
        payload = json.loads(raw) if raw else None
    except ValueError:
        payload = raw.decode(errors="replace")
    return code, payload, ms


def redis(*args):
    cmd = COMPOSE + ["redis", "redis-cli"] + list(args)
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout


def cache_keys():
    out = redis("--scan", "--pattern", "contentSearch::*")
    return [k for k in out.splitlines() if k.strip()]


def psql(sql):
    cmd = COMPOSE + ["postgres", "psql", "-U", "everyone", "-d", "loadtest", "-Atc", sql]
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout.strip()


results = []


def check(label, expected, actual):
    ok = expected == actual
    results.append(ok)
    print(f"  {'PASS' if ok else 'FAIL'}  {label}: 기대 {expected}, 실제 {actual}")


def step(title):
    print(f"\n[{title}]")


def search(sort_by, **extra):
    params = {"limit": 20, "sortBy": sort_by, "sortDirection": "DESCENDING", **extra}
    code, _, ms = call("GET", "/api/contents", params=params)
    return code, ms


def main():
    call("GET", "/api/auth/csrf-token", auth=False)
    code, payload, _ = call("POST", "/api/auth/sign-in",
                            form={"username": EMAIL, "password": PASSWORD}, auth=False)
    if code != 200 or not isinstance(payload, dict) or "accessToken" not in payload:
        print(f"로그인 실패: {code} {payload}")
        sys.exit(1)
    token["access"] = payload["accessToken"]

    content_id = psql("select id from contents where provider_item_id = 'lt-c-2002'")
    if not content_id:
        print("lt-c-2002 콘텐츠가 없어요. seed_content.sql 을 먼저 실행하세요.")
        sys.exit(1)

    for k in cache_keys():
        redis("del", k)

    step("1. 같은 검색 두 번 (최신순)")
    code, ms1 = search("createdAt")
    n1 = len(cache_keys())
    code2, ms2 = search("createdAt")
    n2 = len(cache_keys())
    print(f"  응답 {code}/{code2} · 첫 호출 {ms1:.0f}ms · 두 번째 {ms2:.0f}ms")
    check("첫 호출 뒤 캐시 키 수", 1, n1)
    check("두 번째 호출 뒤 캐시 키 수 (적중)", 1, n2)

    step("2. 다른 정렬은 키가 따로 생김 (평점순)")
    search("rate")
    check("평점순 호출 뒤 캐시 키 수", 2, len(cache_keys()))

    step("3. 시청자수순은 캐시 안 함")
    code, ms = search("watcherCount")
    print(f"  응답 {code} · {ms:.0f}ms")
    check("시청자수순 호출 뒤 캐시 키 수", 2, len(cache_keys()))

    step("4. 리뷰 생성 -> 전체 삭제")
    code, review, _ = call("POST", "/api/reviews",
                           body={"contentId": content_id, "text": "캐시 확인용 리뷰", "rating": 4.0})
    print(f"  응답 {code}")
    if code != 201 or not isinstance(review, dict):
        print(f"리뷰 생성 실패: {review}. (이미 만든 리뷰가 있으면 seed_content.sql 을 다시 실행하세요)")
        sys.exit(1)
    review_id = review["id"]
    check("리뷰 생성 뒤 캐시 키 수", 0, len(cache_keys()))

    step("5. 다시 검색하면 미스 (최신순), 그 뒤 텍스트만 수정")
    search("createdAt")
    check("검색 뒤 캐시 키 수", 1, len(cache_keys()))
    code, _, _ = call("PATCH", f"/api/reviews/{review_id}", body={"text": "캐시 확인용 리뷰 수정"})
    print(f"  수정 응답 {code}")
    check("텍스트만 수정 뒤 캐시 키 수 (유지)", 1, len(cache_keys()))

    step("6. 평점 수정 -> 전체 삭제")
    code, _, _ = call("PATCH", f"/api/reviews/{review_id}", body={"rating": 2.0})
    print(f"  수정 응답 {code}")
    check("평점 수정 뒤 캐시 키 수", 0, len(cache_keys()))

    step("7. 다시 검색 후 리뷰 삭제 -> 전체 삭제")
    search("createdAt")
    check("검색 뒤 캐시 키 수", 1, len(cache_keys()))
    code, _, _ = call("DELETE", f"/api/reviews/{review_id}")
    print(f"  삭제 응답 {code}")
    check("리뷰 삭제 뒤 캐시 키 수", 0, len(cache_keys()))

    print(f"\n결과: {sum(results)}/{len(results)} PASS")
    sys.exit(0 if all(results) else 2)


if __name__ == "__main__":
    main()
