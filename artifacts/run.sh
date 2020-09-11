#!/bin/bash

launch_xvfb() {
	export GEOMETRY="${SCREEN_WIDTH}""x""${SCREEN_HEIGHT}""x""${SCREEN_DEPTH}"
	rm -f /tmp/.X*lock
	/usr/bin/Xvfb ${DISPLAY} -screen 0 ${GEOMETRY} -dpi ${SCREEN_DPI} -ac +extension RANDR &
}

launch_window_manager() {
    local timeout=${XVFB_TIMEOUT:-5}

    # Start and wait for either fluxbox to be fully up or we hit the timeout.
    fluxbox &
    local loopCount=0
    until wmctrl -m > /dev/null 2>&1
    do
        loopCount=$((loopCount+1))
        sleep 1
        if [ ${loopCount} -gt ${timeout} ]
        then
            echo "${G_LOG_E} fluxbox failed to start."
            exit 1
        fi
    done
}

launch_xvfb

launch_window_manager

java -DD_DOCKER=true -DD_JAVA_HOME=${D_JAVA_HOME} -DD_CHROME_DRIVER=${D_CHROME_DRIVER} -DD_GATF_JAR=${D_GATF_JAR} -jar /gatf-alldep.jar "$@" > /workdir/gatf.log 2>&1
