CHROME_VERSION=109.0
FF_VERSION=109.0
OPERA_VERSION=94.0

set -x
rm -rf temp
mkdir temp
cd temp
cp ../dyn-proxy.js .
wget -q https://raw.githubusercontent.com/aerokube/images/master/static/chrome/entrypoint.sh
cp ../Dockerfile-selenoid-browser.tmpl Dockerfile-chrome
sed -i'' -e 's|_BROWSER_|chrome|g' Dockerfile-chrome
sed -i'' -e "s|_VERSION_|$CHROME_VERSION|g" Dockerfile-chrome
mv entrypoint.sh entrypoint_so.sh
sed -i'' -e 's|wait|cd /seltest \&\& npm i http http-proxy \&\& node dyn-proxy.js \&\n\nwait|g' entrypoint_so.sh
docker build -f Dockerfile-chrome --tag sumeetchhetri/vnc:chrome_$CHROME_VERSION .
docker tag sumeetchhetri/vnc sumeetchhetri/vnc:chrome_$CHROME_VERSION
docker push sumeetchhetri/vnc:chrome_$CHROME_VERSION
cd -

rm -rf temp
mkdir temp
cd temp
cp ../dyn-proxy.js .
wget -q https://raw.githubusercontent.com/aerokube/images/master/static/firefox/selenoid/entrypoint.sh
cp ../Dockerfile-selenoid-browser.tmpl Dockerfile-firefox
sed -i'' -e 's|_BROWSER_|firefox|g' Dockerfile-firefox
sed -i'' -e "s|_VERSION_|$FF_VERSION|g" Dockerfile-firefox
mv entrypoint.sh entrypoint_so.sh
sed -i'' -e 's|wait|cd /seltest \&\& npm i http http-proxy \&\& node dyn-proxy.js \&\n\nwait|g' entrypoint_so.sh
docker build -f Dockerfile-firefox --tag sumeetchhetri/vnc:firefox_$FF_VERSION .
docker tag sumeetchhetri/vnc sumeetchhetri/vnc:firefox_$FF_VERSION
docker push sumeetchhetri/vnc:firefox_$FF_VERSION
cd -

rm -rf temp
mkdir temp
cd temp
cp ../dyn-proxy.js .
wget -q https://raw.githubusercontent.com/aerokube/images/master/static/opera/entrypoint.sh
cp ../Dockerfile-selenoid-browser.tmpl Dockerfile-opera
sed -i'' -e 's|_BROWSER_|opera|g' Dockerfile-opera
sed -i'' -e "s|_VERSION_|$OPERA_VERSION|g" Dockerfile-opera
mv entrypoint.sh entrypoint_so.sh
sed -i'' -e 's|wait|cd /seltest \&\& npm i http http-proxy \&\& node dyn-proxy.js \&\n\nwait|g' entrypoint_so.sh
docker build -f Dockerfile-opera --tag sumeetchhetri/vnc:opera_$OPERA_VERSION .
docker tag sumeetchhetri/vnc sumeetchhetri/vnc:opera_$OPERA_VERSION
docker push sumeetchhetri/vnc:opera_$OPERA_VERSION
cd -
