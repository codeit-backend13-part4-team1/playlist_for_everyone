# 알림 비동기 적용 전 측정

알림의 비동기 적용 전 기준값을 재현한다. 비동기 적용 후 비교는 #168, 수신자 페이징 비교는 #222에서 다룬다. [#223 알림 모니터링](https://github.com/codeit-backend13-part4-team1/playlist_for_everyone/issues/223) · [측정 결과](results.md)

## 실행 순서

1. 기존 알림 단위·통합 테스트와 DM 알림 연동 테스트를 실행한다.

   ```bash
   ./gradlew test --rerun --tests '*domain.notification.*' \
     --tests '*DirectMessageSseEventIntegrationTest' \
     --tests '*domain.playlist.*Notification*' --console=plain
   ```

2. Docker와 JDK 17, Python 3.10 이상을 준비한다. 로컬 Docker 이미지 `postgres:16`, `redis:7.4-alpine`과 Gradle `bootRun`에 필요한 의존성 캐시를 사용한다. [측정 결과의 환경](conditions.json)
3. 저장소 루트에서 측정을 실행한다. 아래는 macOS 예시다. Windows에서는 [Git Bash에서 WSL2로 실행](#windows에서-실행)을 참고한다. `--port`의 기본값은 `18081`이다.

   ```bash
   python3 loadtest/notification/measure.py \
     --java-home "$(/usr/libexec/java_home -v 17)" \
     --output "$(mktemp -d)"
   ```

   스크립트는 임시 작업 디렉터리에 `d58951e6`을 풀고 `7fd8c279`의 알림 계측과 측정용 JFR 패치를 적용한다. 루프백 주소에 새 PostgreSQL·Redis를 띄우고 Flyway로 빈 `loadtest` 스키마를 준비한다. 전용 계정을 만든 뒤 HTTP API로 팔로우를 등록한다. 종료 시 이번 실행에서 만든 서버와 컨테이너를 정리한다. [실행 스크립트](../../../loadtest/notification/measure.py) · [JFR 패치](../../../loadtest/notification/jfr.patch)

## Windows에서 실행

Windows에서는 **Git Bash를 열고 `wsl.exe`로 WSL2에 들어간 뒤** 측정 명령을 실행한다. `measure.py`는 JMX 수집기의 표준 출력 파이프를 `select()`로 읽고, 종료 시 `os.killpg()`로 서버 프로세스 그룹을 정리한다. Git Bash에서 Windows용 Python을 사용하면 두 동작을 지원하지 않는다. [Python `select` 문서](https://docs.python.org/3/library/select.html) · [Python `os.killpg` 문서](https://docs.python.org/3/library/os.html#os.killpg)

1. Docker Desktop에서 WSL2 백엔드와 사용할 Linux 배포판의 WSL 통합을 활성화한다. 해당 배포판 안에 Linux용 JDK 17과 Python 3.10 이상을 준비한다. [Docker Desktop WSL2 설정](https://docs.docker.com/desktop/features/wsl/)
2. Git Bash에서 `wsl.exe`를 실행한다. 이후 명령은 WSL2 셸 안에서 실행한다. 아래 저장소 경로와 JDK 경로는 설치 위치에 맞게 바꾼다.

   ```bash
   cd ~/projects/playlist_for_everyone
   jdk17_home=/usr/lib/jvm/java-17-openjdk-amd64
   "$jdk17_home/bin/java" -version
   python3 --version
   docker info
   python3 loadtest/notification/measure.py \
     --java-home "$jdk17_home" \
     --output "$(mktemp -d)"
   ```

기록된 측정은 macOS에서 수행했다. WSL2에서의 전체 실행은 별도 검증이 필요하다.

## 측정 조건

| 수신자 | 동시 요청 | Hikari 풀 | 획득 제한 | 준비 / 측정 요청 | 반복 |
|---:|---:|---:|---:|---:|---:|
| 1명 | 1개 | 10개 | 30초 | 10 / 100 | 3회 |
| 10명 | 1개 | 10개 | 30초 | 10 / 100 | 3회 |
| 10명 | 5개 | 10개 | 30초 | 10 / 100 | 3회 |
| 10명 | 5개 | 2개 | 1초 | 10 / 25 | 3회 |

정상 조건은 표 순서로 한 회차씩 번갈아 실행한다. 풀 2개 조건은 서버를 재시작해 따로 측정한다. 회차마다 준비 요청 10건을 순차 전송하고 초기 지표를 다시 읽는다. 동시 요청 5개 조건에서는 작업자 5명이 각자 맡은 요청을 순차 전송한다. 플레이리스트 제목을 회차와 요청마다 다르게 만들어 DB 커밋과 알림을 대조한다. 실행 중 생성된 데이터는 같은 임시 DB에 쌓이며, 회차별 시작 건수를 원본에 기록한다.

## 알림 처리 흐름

플레이리스트 커밋 후 같은 스레드에서 팔로워 목록을 조회하고 수신자별로 알림 요청을 발행한다. 각 알림은 `REQUIRES_NEW` 트랜잭션으로 저장하며, 저장 커밋 후 SSE 발행을 시도한다. 이 경로에서는 HTTP 응답에 알림 처리 시간이 포함될 수 있다.

## 지표를 읽는 방법

- HTTP 지연은 `POST /api/playlists` 요청 시작부터 응답 본문 수신 완료까지다. 지연 백분위수는 HTTP 201 응답을 집계한다. 초당 완료 요청은 전체 측정 구간의 201 응답 수를 기준으로 계산하며, 측정 중 Hikari 조회가 함께 실행된다.
- 알림 지연은 수신자별 알림 리스너의 시작부터 저장 실패 또는 커밋 후 SSE 발행 시도가 끝날 때까지를 합한 값이다. 플레이리스트 이벤트 한 건의 합계와 수신자 표본 수를 JFR 이벤트 하나에 담는다. 백분위수는 수신자 수만큼 표본이 모인 요청의 합계를 사용한다.
- 예상 알림 수는 실제 커밋된 플레이리스트 수에 준비한 수신자 수를 곱해 구한다. 플레이리스트별 알림을 각 수신자와 대조한다. 응답 없이 종료된 호출은 재전송 없이 최대 30초까지 커밋 및 JFR 완료 신호를 확인한 뒤 상태를 기록한다. 완료 신호가 있는 요청의 알림 누락은 별도 건수로 남긴다.
- 알림 발행 시도와 처리 완료 표본은 `source`, 실패는 `stage`로 조회한다. 사용자 ID·이름·본문은 태그에 포함하지 않는다. 측정에 사용한 플레이리스트 ID는 요청과 JFR 기록을 대조하는 데 사용한다.
- Hikari 사용 중·유휴·대기 연결은 로컬 JMX에서 100ms 간격으로 조회한다. 회차 전후에는 Actuator 누적값을 읽어 획득·타임아웃을 기록한다. 표본 사이의 짧은 대기는 관측에서 빠질 수 있다. [JMX 수집기](../../../loadtest/notification/PoolProbe.java)
- SSE 성공은 알림 저장 커밋 이후 서버 발행까지다. 동기 처리의 알림 전용 실행기·대기열·제출 거부 지표는 해당 없음으로 기록한다.

## #168에서 재사용할 기준

같은 네 가지 조건과 준비·측정 요청 수를 사용한다. HTTP 시간, 알림 리스너 시간 합계, 수신자 대조, 종료 후 미완료 수를 같은 경계에서 수집한다. 이 측정의 JFR 패치는 동기 처리 스레드에서 리스너 시간을 합산한다. 비동기 측정에서는 요청과 처리 작업을 플레이리스트 ID로 연결해 동일한 합계를 만든다. 실행기 대기와 실행 시간은 별도 지표로 남긴다.
