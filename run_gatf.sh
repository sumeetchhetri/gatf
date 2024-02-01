
OS="$(uname)"
SELGRP=seluser
SELUSR=seluser

if id -g "$SELGRP" &>/dev/null; then
    :
else
    if [ "$OS" == "Darwin" ]; then
    	sudo dscl . -create /groups/$SELGRP gid 1201
		#Delete group
		#sudo dscl . -delete /Groups/seluser
		echo "$SELGRP group created"
    else
		groupadd -g 1201 $SELGRP
		echo "$SELGRP group created"
    fi
fi

if id "$SELUSR" &>/dev/null; then
    :
else
    if [ "$OS" == "Darwin" ]; then
    	 sudo /usr/sbin/sysadminctl -addUser $SELUSR -UID 1200 -GID 1201 -shell /var/empty
    	 #Delete user
    	 #sudo /usr/sbin/sysadminctl -deleteUser $SELUSR
    else
    	useradd -g $SELUSR -u 1200 -M -N $SELUSR
    	usermod -L $SELUSR
    fi
    echo "$SELUSR group created"
fi

if [ "$#" -lt 2 ]; then
    echo "Please provide gatf image name (chrome, firefox, vnc) and the gatf home directory, optionally the tmp directory as well"
    exit 1
fi

HOMEDIR=.
TMPDIR=/tmp
IMG=gatf-bin:3.0.0

if ! [ "$1" = "chrome" ] && ! [ "$1" = "firefox" ] && ! [ "$1" = "vnc" ] ; then
	echo "Please provide a valid gatf image namely, chrome, firefox or vnc"
	exit 1
fi
if [ "$1" = "chrome" ]; then
	IMG="gatf-bin:3.0.0"
elif [ "$1" = "firefox" ]; then
	IMG="gatf-bin-ff:3.0.0"
elif [ "$1" = "vnc" ]; then
	IMG="gatf-bin-vnc:3.0.0"
fi

if ! [ -d "$2" ]; then
	echo "Please provide a valid gatf home directory path"
	exit 1
fi

HOMEDIR="$2"

if ! [ -z "$3" ]; then
	if ! [ -d "$3" ]; then
		echo "Please provide a valid gatf tmp directory path (this will be used to read the downloaded files as well as the screenshots)"
		exit 1
	fi
	TMPDIR="$3"
fi

echo "Home Directory is $HOMEDIR, temp directory is $TMPDIR and Image is $IMG"

#chown -R 1200:1201 $HOMEDIR
chmod -R 777 $HOMEDIR

#chown -R 1200:1201 $TMPDIR
chmod -R 777 $TMPDIR

docker run -v /dev/shm:/dev/shm -v $HOMEDIR:/workdir -v $TMPDIR:/tmp -p 9080:9080 -e TZ=Asia/Kolkata -it sumeetchhetri/$IMG
