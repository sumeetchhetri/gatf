
SELGRP=seluser
SELUSR=seluser
if ! [ $(getent group $SELGRP) ]; then
	groupadd -g 1201 $SELGRP
	echo "$SELGRP group created"
fi

if getent passwd $SELUSR > /dev/null 2>&1; then
	:
else
    useradd -g $SELUSR -u 1200 -M -N $SELUSR
    usermod -L $SELUSR
fi

if ! [ $# -eq 2 ]; then
    echo "Please provide gatf image name (chrome, firefox, vnc) and the gatf home directory, optionally the tmp directory as well"
fi

HOMEDIR=.
TMPDIR=/tmp
IMG=gatf-bin:1.1.0
if [ ! -z "$1" ]; then
	if ! [ "$1" = "chrome" ] && ! [ "$1" = "firefox" ] && ! [ "$1" = "vnc" ] ; then
		echo "Please provide a valid gatf image namely, chrome, firefox or vnc"
	fi
	if [ "$1" = "chrome" ]; then
		IMG="gatf-bin:1.1.0"
	elif if [ "$1" = "firefox" ]; then
		IMG="gatf-bin-ff:1.1.0"
	elif if [ "$1" = "vnc" ]; then
		IMG="gatf-bin-vnc:1.1.0"
	fi
fi

if [ ! -z "$2" ]; then
	if [ -d "$2" ]; then
		echo "Please provide a valid gatf home directory path"
	fi
	HOMEDIR="$1"
	chown -R 1200:1201 $HOMEDIR
	chmod -R 777 $HOMEDIR
fi

if [ ! -z "$3" ]; then
	if [ -d "$3" ]; then
		echo "Please provide a valid gatf tmp directory path (this will be used to read the downloaded files as well as the screenshots)"
	fi
	TMPDIR="$1"
	chown -R 1200:1201 $TMPDIR
	chmod -R 777 $TMPDIR
fi

docker run -v /dev/shm:/dev/shm -v $HOMEDIR:/workdir -v $TMPDIR:/tmp -e TZ=Asia/Kolkata -it sumeetchhetri/$IMG
