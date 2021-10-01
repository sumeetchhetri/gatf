FROM ubuntu:20.04
  
ARG DEBIAN_FRONTEND=noninteractive

# Set the working directory to /app
WORKDIR /tmp

RUN apt update -yqq && apt install -yqq --no-install-recommends ca-certificates curl openjdk-11-jdk openjdk-11-jre libluajit-5.1-dev luajit libssl-dev git wget unzip net-tools build-essential maven && rm -rf /var/lib/apt/lists/*

ARG NODE_VERSION=14.18.0
ARG NODE_PACKAGE=node-v$NODE_VERSION-linux-x64
ARG NODE_HOME=/opt/$NODE_PACKAGE
ENV NODE_PATH $NODE_HOME/lib/node_modules
ENV PATH $NODE_HOME/bin:$PATH
RUN curl https://nodejs.org/dist/v$NODE_VERSION/$NODE_PACKAGE.tar.gz | tar -xzC /opt/
RUN npm install -g autocannon

RUN git clone https://github.com/wg/wrk.git wrk
WORKDIR /tmp/wrk
ENV LDFLAGS="-O3 -march=native -flto"
ENV CFLAGS="-I /usr/include/luajit-2.1 $LDFLAGS"
RUN make WITH_LUAJIT=/usr WITH_OPENSSL=/usr -j "$(nproc)"
RUN cp wrk /usr/local/bin

WORKDIR /tmp
RUN git clone https://github.com/sumeetchhetri/gatf
RUN rm -rf alldep-jar plugin sample pom.xml plugins.txt
COPY alldep-jar /tmp/gatf/alldep-jar
COPY plugin /tmp/gatf/plugin
COPY sample /tmp/gatf/sample
COPY pom.xml /tmp/gatf/
COPY plugins.txt /tmp/gatf/
RUN cd gatf && mvn package

RUN mv /tmp/gatf/alldep-jar/target/gatf-alldep-jar-1.0.6.jar /gatf-alldep.jar
