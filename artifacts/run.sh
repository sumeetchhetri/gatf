#!/bin/bash

java -DD_JAVA_HOME=${D_JAVA_HOME} -DD_CHROME_DRIVER=${D_CHROME_DRIVER} -DD_GATF_JAR=${D_GATF_JAR} -jar /gatf-alldep.jar "$@" > gatf.log 2>&1
