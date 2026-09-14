FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace/server
COPY server/ .
COPY LICENSE NOTICE THIRD_PARTY_NOTICES.md /workspace/
COPY LICENSES/ /workspace/LICENSES/
COPY docs/ /workspace/docs/
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew verifyDistributionNotices --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/server/build/libs/signet-campus-server-1.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
