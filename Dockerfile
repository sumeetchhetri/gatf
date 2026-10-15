# Δ163e: pinned (ubuntu:latest moved to a newer release with gcc 15); matches the ubuntu-24.04 runners
FROM ubuntu:24.04
  
ARG DEBIAN_FRONTEND=noninteractive

WORKDIR /tmp

ENV NODE_MAJOR=20

RUN apt update -yqq && apt install -yqq --no-install-recommends zlib1g-dev ca-certificates ca-certificates curl gnupg openjdk-21-jdk-headless libluajit-5.1-dev luajit libssl-dev git wget unzip net-tools maven build-essential software-properties-common xvfb fluxbox wmctrl && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /etc/apt/keyrings && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
RUN echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_$NODE_MAJOR.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list
RUN printf 'Package: nodejs\nPin: origin deb.nodesource.com\nPin-Priority: 600\n' > /etc/apt/preferences.d/nodesource
RUN apt update -yqq && apt install -yqq nodejs
RUN npm install -g autocannon

RUN git clone https://github.com/wg/wrk.git wrk
WORKDIR /tmp/wrk
# Δ163e: no -march=native (the image runs on other CPUs) and no -flto (the LTO link of the LuaJIT bytecode object failed)
ENV LDFLAGS="-O2"
ENV CFLAGS="-I /usr/include/luajit-2.1 $LDFLAGS"
RUN make WITH_LUAJIT=/usr WITH_OPENSSL=/usr -j "$(nproc)"
RUN cp wrk /usr/local/bin

WORKDIR /tmp
RUN git clone https://github.com/giltene/wrk2.git
WORKDIR /tmp/wrk2
RUN make -j "$(nproc)" && mv wrk /usr/local/bin/wrk2

WORKDIR /tmp
RUN curl -L https://github.com/tsenart/vegeta/releases/download/v12.8.4/vegeta_12.8.4_linux_amd64.tar.gz | tar -xzC /tmp/
RUN mv vegeta /usr/local/bin/
RUN chmod +x /usr/local/bin/wrk /usr/local/bin/wrk2 /usr/local/bin/vegeta

#RUN curl -L https://go.dev/dl/go1.17.3.linux-amd64.tar.gz | tar -xzC /usr/local
#ENV OLD_PATH=$PATH
#ENV PATH=$PATH:/usr/local/go/bin
#RUN go get -u github.com/tsenart/vegeta
#ENV PATH=$OLD_PATH
#RUN rm -rf /usr/local/go go1.17.3.linux-amd64.tar.gz

WORKDIR /tmp
RUN rm -rf wrk wrk2
# Build the CHECKED-OUT sources (the release tag's own tree), with the JDK 21 installed above. This used to
# git-clone GitHub and build the hard-coded tag 5.0.0 with JDK 11, so every image/release shipped old code.
# Tests are skipped here: CI runs them (mvn install) before this image is built.
COPY . /tmp/gatf
RUN cd /tmp/gatf && mvn -B -ntp -DskipTests install \
 && cp "$(ls alldep-jar/target/gatf-alldep-jar-*.jar | grep -Ev -- '-(sources|javadoc)\.jar$' | head -1)" /gatf-alldep.jar \
 && cd / && rm -rf /tmp/gatf /root/.m2
