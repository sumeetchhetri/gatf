CHROME_VERSION=142.0
FF_VERSION=146.0
OPERA_VERSION=125.0

# Build images executable first
#  https://mirror.cs.uchicago.edu/google-chrome/pool/main/g/google-chrome-stable/
#  git clone https://github.com/aerokube/images && cd images
#  go install github.com/markbates/pkger/cmd/pkger@latest
#  go generate github.com/aerokube/images
#  go build
#  chmod +x images

#  for chrome find closest chrome driver version and rename the deb file and then try the ./images
#  https://googlechromelabs.github.io/chrome-for-testing/known-good-versions.json
#  https://mirror.cs.uchicago.edu/google-chrome/pool/main/g/google-chrome-stable/
#  Download the deb file with the latest desired MAJOR version
#  MAJOR=136 && VERSION=${MAJOR}.0.7103.113 && wget https://mirror.cs.uchicago.edu/google-chrome/pool/main/g/google-chrome-stable/google-chrome-stable_${VERSION}-1_amd64.deb && ./images chrome -b ./google-chrome-stable_${VERSION}-1_amd64.deb -t sumeetchhetri/chrome:${MAJOR}.0

#  https://ftp.mozilla.org/pub/firefox/releases/
#  MAJOR=140 && VERSION=${MAJOR}.0.4 && wget https://ftp.mozilla.org/pub/firefox/releases/${VERSION}/linux-x86_64/en-US/firefox-${VERSION}.deb && mv firefox-${VERSION}.deb firefox_${VERSION}_amd64.deb && ./images firefox -b ./firefox_${VERSION}_amd64.deb -t sumeetchhetri/firefox:${MAJOR}.0

#  https://get.opera.com/pub/opera/desktop/
#  MAJOR=120 && VERSION=${MAJOR}.0.5543.61 && wget https://get.opera.com/pub/opera/desktop/${VERSION}/linux/opera-stable_${VERSION}_amd64.deb && ./images opera -b ./opera-stable_${VERSION}_amd64.deb -t sumeetchhetri/opera:${MAJOR}.0

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
docker buildx build --platform=linux/amd64 -f Dockerfile-chrome --tag sumeetchhetri/vnc:chrome_$CHROME_VERSION .
#docker tag sumeetchhetri/vnc:chrome_108.0 sumeetchhetri/vnc:chrome_$CHROME_VERSION
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
sed -i'' -e "s|curl gnupg|libcurl4=7.81.0-1ubuntu1.15 gnupg libgdk-pixbuf2.0-0 libgdk-pixbuf-xlib-2.0-0|g" Dockerfile-firefox
mv entrypoint.sh entrypoint_so.sh
sed -i'' -e 's|wait|cd /seltest \&\& npm i http http-proxy \&\& node dyn-proxy.js \&\n\nwait|g' entrypoint_so.sh
docker build --platform=linux/amd64 -f Dockerfile-firefox --tag sumeetchhetri/vnc:firefox_$FF_VERSION .
#docker tag sumeetchhetri/vnc:firefox_109.0 sumeetchhetri/vnc:firefox_$FF_VERSION
docker push sumeetchhetri/vnc:firefox_$FF_VERSION
cd -

rm -rf temp
mkdir temp
cd temp
cp ../dyn-proxy.js .
wget -q https://raw.githubusercontent.com/aerokube/images/master/static/opera/entrypoint.sh
cp ../Dockerfile-selenoid-opera.tmpl Dockerfile-opera
sed -i'' -e 's|_BROWSER_|opera|g' Dockerfile-opera
sed -i'' -e "s|_VERSION_|$OPERA_VERSION|g" Dockerfile-opera
mv entrypoint.sh entrypoint_so.sh
sed -i'' -e 's|wait|cd /seltest \&\& npm i http http-proxy \&\& node dyn-proxy.js \&\n\nwait|g' entrypoint_so.sh
docker build --platform=linux/amd64 -f Dockerfile-opera --tag sumeetchhetri/vnc:opera_$OPERA_VERSION .
docker tag sumeetchhetri/vnc sumeetchhetri/vnc:opera_$OPERA_VERSION
docker push sumeetchhetri/vnc:opera_$OPERA_VERSION
cd -