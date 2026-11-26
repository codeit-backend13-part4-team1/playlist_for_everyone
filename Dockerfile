FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src src
# 테스트는 CI에서 진행한다 이미지 빌드에서는 제외한다.
RUN ./gradlew bootJar --no-daemon -x test\
    && find build/libs -name "*.jar" ! -name "*-plain.jar" -exec cp {} app.jar \;

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app \
 && mkdir -p /app/.logs && chown -R app:app /app

COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app

ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
