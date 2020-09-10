#!/bin/bash

export GEOMETRY="${SCREEN_WIDTH}""x""${SCREEN_HEIGHT}""x""${SCREEN_DEPTH}"
rm -f /tmp/.X*lock
/usr/bin/Xvfb ${DISPLAY} -screen 0 ${GEOMETRY} -dpi ${SCREEN_DPI} -ac +extension RANDR &

java -DD_DOCKER=true -DD_JAVA_HOME=${D_JAVA_HOME} -DD_CHROME_DRIVER=${D_CHROME_DRIVER} -DD_GATF_JAR=${D_GATF_JAR} -jar /gatf-alldep.jar "$@" > gatf.log 2>&1
