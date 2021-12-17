#!/bin/bash

java -DD_DOCKER=true -DD_JAVA_HOME=${D_JAVA_HOME} -DD_CHROME_DRIVER=${D_CHROME_DRIVER} -DD_GATF_JAR=${D_GATF_JAR} -jar /gatf-alldep.jar -configtool 9080 0.0.0.0 /workdir

#docker run -v /Users/sumeetc/Projects/GitHub/gatf/alldep-jar/sample:/workdir -p 9080:9080 -e TZ=Asia/Kolkata -it sumeetchhetri/gatf-bin:latest