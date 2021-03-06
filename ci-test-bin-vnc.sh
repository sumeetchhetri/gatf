#!/bin/bash

CONTAINER_NAME=`date +%s`
mkdir temp && chmod -Rf 777 temp
cd temp
docker run --rm -d --name "${CONTAINER_NAME}" -p 9080:9080 -p 6080:80 -p 5900:5900 -v `pwd`/sample:/workdir -i sumeetchhetri/gatf-bin-vnc
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
echo "Waiting for vnc-web to listen on port 6080..."
COUNTER=0
while ! nc -z localhost 6080; do   
  sleep 1
  COUNTER=$((COUNTER+1))
  if [ "$COUNTER" = 60 ]
  then
  	echo "exiting...."
  	exit
  fi
done
echo "gatf listening on port 6080..."
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
docker logs ${CONTAINER_NAME}
docker kill ${CONTAINER_NAME}
cd -
rm -rf temp