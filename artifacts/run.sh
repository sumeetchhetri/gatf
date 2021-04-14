#!/bin/bash

launch_xvfb() {
	export GEOMETRY="${SCREEN_WIDTH}""x""${SCREEN_HEIGHT}""x""${SCREEN_DEPTH}"
	#rm -f /tmp/.X*lock
	#/usr/bin/Xvfb ${DISPLAY} -screen 0 ${GEOMETRY} -dpi ${SCREEN_DPI} -ac +extension RANDR &
	# Set defaults if the user did not specify envs.
  	export DISPLAY=${XVFB_DISPLAY:-:1}
	local screen=${XVFB_SCREEN:-0}
	local resolution=${GEOMETRY:-1280x1024x24}
	local timeout=${XVFB_TIMEOUT:-10}
	# Start and wait for either Xvfb to be fully up, or we hit the timeout.
	/usr/bin/Xvfb ${DISPLAY} -screen ${screen} ${resolution} -dpi ${SCREEN_DPI} &
	local loopCount=0
	until xdpyinfo -display ${DISPLAY} > /dev/null 2>&1
	do
		loopCount=$((loopCount+1))
		sleep 1
		if [ ${loopCount} -gt ${timeout} ]
		then
		    echo "[ERROR] xvfb failed to start."
		    exit 1
		fi
	done
}

launch_window_manager() {
    local timeout=${XVFB_TIMEOUT:-10}

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

java -DD_DOCKER=true -DSCREEN_WIDTH=${SCREEN_WIDTH} -DSCREEN_HEIGHT=${SCREEN_HEIGHT} -DD_JAVA_HOME=${D_JAVA_HOME} -DD_CHROME_DRIVER=${D_CHROME_DRIVER} -DD_GATF_JAR=${D_GATF_JAR} -jar /gatf-alldep.jar "$@" > /workdir/gatf.log 2>&1
