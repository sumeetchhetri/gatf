FROM ubuntu:latest
  
ARG DEBIAN_FRONTEND=noninteractive

WORKDIR /tmp

ENV NODE_MAJOR=20

RUN apt update -yqq && apt install -yqq --no-install-recommends zlib1g-dev ca-certificates ca-certificates curl gnupg openjdk-11-jdk openjdk-11-jre libluajit-5.1-dev luajit libssl-dev git wget unzip net-tools maven build-essential software-properties-common xvfb fluxbox wmctrl && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /etc/apt/keyrings && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
RUN echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_$NODE_MAJOR.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list
RUN printf 'Package: nodejs\nPin: origin deb.nodesource.com\nPin-Priority: 600\n' > /etc/apt/preferences.d/nodesource
RUN apt update -yqq && apt install -yqq nodejs
RUN npm install -g autocannon

RUN git clone https://github.com/wg/wrk.git wrk
WORKDIR /tmp/wrk
ENV LDFLAGS="-O3 -march=native -flto"
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
RUN git clone https://github.com/sumeetchhetri/gatf
#RUN rm -rf alldep-jar plugin sample pom.xml plugins.txt
#COPY alldep-jar /tmp/gatf/alldep-jar
#COPY plugin /tmp/gatf/plugin
#COPY sample /tmp/gatf/sample
#COPY pom.xml /tmp/gatf/
#COPY plugins.txt /tmp/gatf/
RUN cd gatf && git checkout tags/3.0.7 -b v3.0.7 && mvn --quiet install

RUN mv /tmp/gatf/alldep-jar/target/gatf-alldep-jar-3.0.7.jar /gatf-alldep.jar
