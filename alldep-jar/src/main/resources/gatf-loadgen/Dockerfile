# GATF load-generator tools image: wrk + wrk2 (amd64 only) + vegeta, as a long-running container that
# GATF (or you) `docker exec` into. Needs no build context, so GATF can build it on demand:
#   docker build -t gatf/loadgen:1 - < Dockerfile
#   docker run -d --name gatf-loadgen --network host gatf/loadgen:1      (Linux)
#   docker run -d --name gatf-loadgen gatf/loadgen:1                     (macOS / Windows Docker Desktop)
FROM ubuntu:24.04 AS build
ARG TARGETARCH
ARG WRK_REF=4.2.0
ARG WRK2_REF=44a94c17d8e6a0bac8559b53da76848e430cb7a7
ARG VEGETA_VERSION=12.12.0
RUN apt-get update \
 && apt-get install -y --no-install-recommends build-essential ca-certificates curl unzip perl libssl-dev zlib1g-dev \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
# wrk bundles OpenSSL + LuaJIT 2.1 → builds on amd64 and arm64
RUN mkdir -p /out \
 && curl -fsSL "https://codeload.github.com/wg/wrk/tar.gz/refs/tags/${WRK_REF}" | tar xz \
 && make -C "wrk-${WRK_REF}" \
 && install -m755 "wrk-${WRK_REF}/wrk" /out/wrk
# wrk2 bundles LuaJIT 2.0 (no arm64 support) → amd64 only; GATF falls back to vegeta -rate elsewhere
RUN arch="${TARGETARCH:-$(dpkg --print-architecture)}"; \
    if [ "$arch" = "amd64" ]; then \
      curl -fsSL "https://codeload.github.com/giltene/wrk2/tar.gz/${WRK2_REF}" | tar xz \
      && make -C "wrk2-${WRK2_REF}" \
      && install -m755 "wrk2-${WRK2_REF}/wrk" /out/wrk2; \
    else echo "wrk2 skipped on $arch (bundled LuaJIT 2.0 has no arm64 support)"; fi
RUN arch="${TARGETARCH:-$(dpkg --print-architecture)}"; f="vegeta_${VEGETA_VERSION}_linux_${arch}.tar.gz"; \
    curl -fsSLO "https://github.com/tsenart/vegeta/releases/download/v${VEGETA_VERSION}/$f" \
 && curl -fsSL "https://github.com/tsenart/vegeta/releases/download/v${VEGETA_VERSION}/vegeta_${VEGETA_VERSION}_checksums.txt" \
      | grep " $f\$" | sha256sum -c - \
 && tar xzf "$f" vegeta && install -m755 vegeta /out/vegeta

FROM ubuntu:24.04
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates libssl3t64 zlib1g \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -m -u 10001 gatf && mkdir -p /tmp/gatf && chown gatf /tmp/gatf
COPY --from=build /out/ /usr/local/bin/
USER gatf
WORKDIR /tmp/gatf
LABEL org.opencontainers.image.title="gatf-loadgen" org.opencontainers.image.description="wrk/wrk2/vegeta for GATF docker-exec delegation"
# idle PID 1 that exits promptly on `docker stop`
CMD ["sh", "-c", "trap 'exit 0' TERM INT; while :; do sleep 3600 & wait $!; done"]
