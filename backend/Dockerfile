FROM gradle:8.10.2-jdk17@sha256:c2900027f3f0681c2cbfb09d527813851ad67aeafbb409997297efa2df20e748 AS build

WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre@sha256:1824944ef1bd572d1ff0952afeb2fec7931d77c972c4fbc4dfcdf89f758fb490

WORKDIR /app
COPY --from=build /workspace/build/install/backend/ ./

ENV PORT=8080
EXPOSE 8080

USER 65532:65532

ENTRYPOINT ["/app/bin/backend"]
