FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM python:3.12-slim-bookworm AS engine-build

ENV JUPEDSIM_VERSION=hwalro-v1.4.2.2

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential cmake ninja-build git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
RUN git clone --depth 1 --branch ${JUPEDSIM_VERSION} \
        https://github.com/gugukorn/jupedsim-hwalro.git /src/jupedsim \
    && sed -i 's/build_args += \["-j"\]/build_args += ["-j2"]/' /src/jupedsim/setup.py \
    && python -m pip wheel --no-cache-dir --no-deps --wheel-dir /wheels /src/jupedsim

FROM python:3.12-slim-bookworm

COPY --from=eclipse-temurin:17-jre-jammy /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=engine-build /wheels /wheels
RUN python -m pip install --no-cache-dir --no-deps /wheels/*.whl \
    && rm -rf /wheels
COPY engine/requirements.txt engine/requirements.txt
RUN python -m pip install --no-cache-dir --requirement engine/requirements.txt
COPY engine engine
COPY --from=build /workspace/build/libs/*.jar app.jar

RUN addgroup --system hwalro && adduser --system --ingroup hwalro hwalro

ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

USER hwalro

EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]