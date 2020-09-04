FROM ubuntu:20.04
  
ARG DEBIAN_FRONTEND=noninteractive

# Set the working directory to /app
WORKDIR /tmp

RUN apt update -y && apt install -y --no-install-recommends default-jdk git wget unzip maven net-tools && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/sumeetchhetri/gatf
RUN rm -rf alldep-jar plugin sample pom.xml plugins.txt
COPY alldep-jar /tmp/gatf/alldep-jar
COPY plugin /tmp/gatf/plugin
COPY sample /tmp/gatf/sample
COPY pom.xml /tmp/gatf/
COPY plugins.txt /tmp/gatf/
RUN cd gatf && mvn package

RUN mv /tmp/gatf/alldep-jar/target/gatf-alldep-jar-1.0.6.jar /gatf-alldep.jar
