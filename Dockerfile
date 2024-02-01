FROM ubuntu:latest
  
ARG DEBIAN_FRONTEND=noninteractive

WORKDIR /tmp

RUN apt update -yqq && apt install -yqq --no-install-recommends zlib1g-dev ca-certificates curl openjdk-11-jdk openjdk-11-jre libluajit-5.1-dev luajit libssl-dev git wget unzip net-tools build-essential maven && rm -rf /var/lib/apt/lists/*

ARG NODE_VERSION=16.13.1
ARG NODE_PACKAGE=node-v$NODE_VERSION-linux-x64
ARG NODE_HOME=/opt/$NODE_PACKAGE
ENV NODE_PATH $NODE_HOME/lib/node_modules
ENV PATH $NODE_HOME/bin:$PATH
RUN curl -L https://nodejs.org/dist/v$NODE_VERSION/$NODE_PACKAGE.tar.gz | tar -xzC /opt/
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
RUN cd gatf && git checkout tags/3.0.0 -b v3.0.0 && mvn --quiet install

RUN mv /tmp/gatf/alldep-jar/target/gatf-alldep-jar-3.0.0.jar /gatf-alldep.jar
