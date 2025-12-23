#!/bin/bash
set -e

MAJOR_VERSION=${1:-142}

echo "=== Building Chrome VNC Image for Major Version: ${MAJOR_VERSION} ==="

# Step 1: Find the latest Chrome stable version for this major version
echo "Step 1: Finding latest Chrome ${MAJOR_VERSION}.x version..."
CHROME_VERSION=$(curl -s https://mirror.cs.uchicago.edu/google-chrome/pool/main/g/google-chrome-stable/ | \
  grep -o "google-chrome-stable_${MAJOR_VERSION}\.[0-9.]*-1_amd64\.deb" | \
  sed 's/google-chrome-stable_//g' | sed 's/-1_amd64\.deb//g' | \
  sort -V | tail -1)

if [ -z "$CHROME_VERSION" ]; then
  echo "Error: No Chrome version found for major version ${MAJOR_VERSION}"
  exit 1
fi

echo "Found Chrome version: ${CHROME_VERSION}"

# Step 2: Find the closest ChromeDriver version
echo "Step 2: Finding compatible ChromeDriver version..."
DRIVER_VERSION=$(curl -s https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json | \
  jq -r ".versions[] | select(.version | startswith(\"${MAJOR_VERSION}.\")) | .version" | \
  sort -V | tail -1)

if [ -z "$DRIVER_VERSION" ]; then
  echo "Warning: No exact ChromeDriver found, trying to find any ${MAJOR_VERSION}.x version..."
  DRIVER_VERSION=$(curl -s https://googlechromelabs.github.io/chrome-for-testing/latest-versions-per-milestone.json | \
    jq -r ".milestones.\"${MAJOR_VERSION}\".version" 2>/dev/null || echo "")
fi

if [ -z "$DRIVER_VERSION" ]; then
  echo "Error: No compatible ChromeDriver version found for major version ${MAJOR_VERSION}"
  exit 1
fi

echo "Using ChromeDriver version: ${DRIVER_VERSION}"

# Step 3: Download Chrome .deb file
DEB_FILE="google-chrome-stable_${CHROME_VERSION}-1_amd64.deb"
echo "Step 3: Downloading ${DEB_FILE}..."

if [ -f "$DEB_FILE" ]; then
  echo "File already exists, skipping download"
else
  wget "https://mirror.cs.uchicago.edu/google-chrome/pool/main/g/google-chrome-stable/${DEB_FILE}"
fi

# Step 4: Check if images executable exists
if [ ! -f "./images" ]; then
  echo "Step 4: Building images executable..."
  if [ ! -d "images" ]; then
    git clone https://github.com/aerokube/images
    cd images
  else
    cd images
  fi
  
  go install github.com/markbates/pkger/cmd/pkger@latest
  go generate github.com/aerokube/images
  go build
  chmod +x images
  cd ..
  
  if [ ! -f "./images" ]; then
    cp images/images ./
  fi
fi

# Step 5: Build the Docker image
IMAGE_TAG="sumeetchhetri/chrome:${MAJOR_VERSION}.0"
echo "Step 5: Building Docker image: ${IMAGE_TAG}..."

./images chrome \
  -b "./${DEB_FILE}" \
  -d "${DRIVER_VERSION}" \
  -t "${IMAGE_TAG}"

echo ""
echo "=== Build Complete ==="
echo "Chrome Version: ${CHROME_VERSION}"
echo "ChromeDriver Version: ${DRIVER_VERSION}"
echo "Docker Image: ${IMAGE_TAG}"
echo ""
echo "To run the container:"
echo "docker run -d -p 4444:4444 -p 5900:5900 ${IMAGE_TAG}"
echo ""
echo "VNC: localhost:5900 (password: selenoid)"
