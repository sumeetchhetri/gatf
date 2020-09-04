#!/bin/bash

docker run --rm -d -p 9080:9080 -p 4444:4444 -p 5900:5900 -v sample:/workdir -i sumeetchhetri/gatf-bin-vnc
echo "Waiting for gatf to listen on port 9080..."
COUNTER=0
while ! nc -z localhost 9080; do   
  sleep 1
  COUNTER=$((COUNTER+1))
  if [ "$COUNTER" = 60 ]
  then
  	echo "exiting...."
  	exit
  fi
done
echo "gatf listening on port 9080..."
echo "Waiting for selenium to listen on port 4444..."
COUNTER=0
while ! nc -z localhost 4444; do   
  sleep 1
  COUNTER=$((COUNTER+1))
  if [ "$COUNTER" = 60 ]
  then
  	echo "exiting...."
  	exit
  fi
done
echo "gatf listening on port 4444..."
echo "Waiting for vnc to listen on port 5900..."
COUNTER=0
while ! nc -z localhost 5900; do   
  sleep 1
  COUNTER=$((COUNTER+1))
  if [ "$COUNTER" = 60 ]
  then
  	echo "exiting...."
  	exit
  fi
done
echo "gatf listening on port 5900..."