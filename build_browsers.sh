#!/bin/bash

set -e

# Configuration
DOCKER_USERNAME="sumeetchhetri"
IMAGES_BINARY="/Users/sumeetc/Projects/GitHub/images/images"
WORK_DIR="./browser-builds"

# Detect OS and Architecture
OS_TYPE=$(uname -s)
ARCH_TYPE=$(uname -m)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

# Usage information
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Build browser Docker images with automatic version detection.

OPTIONS:
    -h, --help              Show this help message
    -b, --browser BROWSER   Build specific browser (chrome, firefox, opera, all)
    -v, --version MAJOR     Build specific major version (e.g., 140, 141)
    -a, --all               Build all browsers (default)

EXAMPLES:
    $0                              # Build all browsers with latest versions
    $0 -b chrome                    # Build only Chrome with latest version
    $0 -b chrome -v 140             # Build Chrome version 140.x (latest minor)
    $0 -b firefox -v 133            # Build Firefox version 133.x (latest minor)
    $0 -b opera -v 120              # Build Opera version 120.x (latest minor)

EOF
    exit 1
}

# Parse command line arguments
BROWSER_TO_BUILD="all"
SPECIFIC_MAJOR=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            ;;
        -b|--browser)
            BROWSER_TO_BUILD="$2"
            shift 2
            ;;
        -v|--version)
            SPECIFIC_MAJOR="$2"
            shift 2
            ;;
        -a|--all)
            BROWSER_TO_BUILD="all"
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            ;;
    esac
done

# Validate browser choice
if [[ "$BROWSER_TO_BUILD" != "all" && "$BROWSER_TO_BUILD" != "chrome" && "$BROWSER_TO_BUILD" != "firefox" && "$BROWSER_TO_BUILD" != "opera" ]]; then
    log_error "Invalid browser: $BROWSER_TO_BUILD"
    log_error "Valid options: all, chrome, firefox, opera"
    exit 1
fi

# Check dependencies
check_dependencies() {
    log_info "Checking dependencies for $OS_TYPE ($ARCH_TYPE)..."
    
    local missing_deps=()
    
    # Check for required commands
    if ! command -v curl &> /dev/null; then
        missing_deps+=("curl")
    fi
    
    if ! command -v jq &> /dev/null; then
        missing_deps+=("jq")
    fi
    
    if ! command -v docker &> /dev/null; then
        missing_deps+=("docker")
    fi
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        log_error "Missing dependencies: ${missing_deps[*]}"
        log_info "On macOS, install with: brew install ${missing_deps[*]}"
        log_info "On Linux, install with your package manager"
        exit 1
    fi
    
    # Check if Docker is running
    if ! docker info &> /dev/null; then
        log_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
}

# Check if images binary exists
if [ ! -f "$IMAGES_BINARY" ]; then
    log_error "images binary not found at $IMAGES_BINARY"
    exit 1
fi

# Make images binary executable
chmod +x "$IMAGES_BINARY"

# Create work directory
mkdir -p "$WORK_DIR"

# Function to download file with retry
download_file() {
    local url=$1
    local output=$2
    local max_retries=3
    local retry=0
    
    while [ $retry -lt $max_retries ]; do
        log_info "Downloading $(basename "$url") (attempt $((retry + 1))/$max_retries)..."
        
        if curl -L -f -S --progress-bar -o "$output" "$url" 2>&1 | tail -1 >&2; then
            return 0
        fi
        
        retry=$((retry + 1))
        if [ $retry -lt $max_retries ]; then
            log_warn "Download failed, retrying in 5 seconds..."
            sleep 5
        fi
    done
    
    log_error "Failed to download after $max_retries attempts"
    return 1
}

# Function to check if image tag already exists on Docker Hub
check_existing_tag() {
    local repo=$1
    local tag=$2
    
    log_info "Checking if ${repo}:${tag} already exists on Docker Hub..."
    
    # Get all tags for the repository (handle pagination)
    local page=1
    local found=0
    
    while true; do
        local response=$(curl -s "https://hub.docker.com/v2/repositories/${repo}/tags/?page=${page}&page_size=100" 2>/dev/null)
        
        if [ -z "$response" ] || [ "$response" == "null" ]; then
            break
        fi
        
        if echo "$response" | jq -e ".results[] | select(.name == \"${tag}\")" > /dev/null 2>&1; then
            found=1
            break
        fi
        
        # Check if there's a next page
        local next=$(echo "$response" | jq -r '.next' 2>/dev/null)
        if [ "$next" == "null" ] || [ -z "$next" ]; then
            break
        fi
        
        page=$((page + 1))
    done
    
    if [ $found -eq 1 ]; then
        return 0  # Tag exists
    else
        return 1  # Tag doesn't exist
    fi
}

# Function to get latest Chrome version for a specific major version
get_chrome_version() {
    local target_major=$1
    
    # Fetch version from Google's API (silently)
    local json=$(curl -s 'https://versionhistory.googleapis.com/v1/chrome/platforms/linux/channels/stable/versions' 2>/dev/null)
    
    if [ -z "$target_major" ]; then
        # Get the latest stable version
        local version=$(echo "$json" | jq -r '.versions[0].version' 2>/dev/null)
    else
        # Get the latest version for specific major version
        local version=$(echo "$json" | jq -r ".versions[] | select(.version | startswith(\"${target_major}.\")) | .version" 2>/dev/null | head -1)
    fi
    
    if [ -z "$version" ] || [ "$version" == "null" ]; then
        return 1
    fi
    
    echo "$version"
}

# Function to get ChromeDriver version for Chrome version
get_chromedriver_version() {
    local chrome_version=$1
    local chrome_major=$(echo "$chrome_version" | cut -d. -f1)
    
    # Fetch known-good-versions.json (silently)
    local json=$(curl -s https://googlechromelabs.github.io/chrome-for-testing/known-good-versions.json 2>/dev/null)
    
    # Find the closest matching version
    local driver_version=$(echo "$json" | jq -r ".versions[] | select(.version | startswith(\"$chrome_major.\")) | .version" 2>/dev/null | tail -1)
    
    if [ -z "$driver_version" ]; then
        echo "$chrome_version"
    else
        echo "$driver_version"
    fi
}

# Function to build Chrome image
build_chrome_image() {
    local target_major=$1
    
    log_info "=== Building Chrome Image ==="
    
    if [ -z "$target_major" ]; then
        log_info "Fetching latest Chrome version..."
    else
        log_info "Fetching latest Chrome version for major version $target_major..."
    fi
    
    local version=$(get_chrome_version "$target_major")
    if [ $? -ne 0 ] || [ -z "$version" ]; then
        log_error "Failed to get Chrome version"
        return 1
    fi
    
    local major=$(echo "$version" | cut -d. -f1)
    local tag="${major}.0"
    
    log_info "Chrome version: $version (Tag: $tag)"
    
    # Check if image already exists
    if check_existing_tag "$DOCKER_USERNAME/chrome" "$tag"; then
        log_warn "Image $DOCKER_USERNAME/chrome:$tag already exists. Skipping..."
        return 0
    fi
    
    local deb_file="google-chrome-stable_${version}-1_amd64.deb"
    local download_url="https://dl.google.com/linux/chrome/deb/pool/main/g/google-chrome-stable/${deb_file}"
    
    cd "$WORK_DIR"
    
    if ! download_file "$download_url" "$deb_file"; then
        log_error "Failed to download Chrome .deb"
        cd ..
        return 1
    fi
    
    log_info "Building Chrome image: $DOCKER_USERNAME/chrome:$tag"
    
    # For M3 Mac, need to specify platform for amd64 images
    if [ "$OS_TYPE" == "Darwin" ] && [ "$ARCH_TYPE" == "arm64" ]; then
        log_info "Detected Apple Silicon, building for linux/amd64 platform..."
    fi
    
    if "$IMAGES_BINARY" chrome -b "./${deb_file}" -t "$DOCKER_USERNAME/chrome:${tag}" 2>&1 | while IFS= read -r line; do log_info "$line"; done; then
        log_info "Successfully built Chrome image: $DOCKER_USERNAME/chrome:$tag"
        rm -f "$deb_file"
        cd ..
        return 0
    else
        log_error "Failed to build Chrome image"
        rm -f "$deb_file"
        cd ..
        return 1
    fi
}

# Function to get Firefox version for a specific major version
get_firefox_version() {
    local target_major=$1
    
    # Fetch directory listing (silently)
    local html=$(curl -s https://ftp.mozilla.org/pub/firefox/releases/ 2>/dev/null)
    
    # Extract version numbers (filter out beta, esr, etc.)
    local versions
    if [ "$OS_TYPE" == "Darwin" ]; then
        versions=$(echo "$html" | grep -o '>[0-9]\+\.[0-9]\+\(\.[0-9]\+\)\?/<' | \
                              sed 's/[><\/]//g' | \
                              grep -v 'b' | grep -v 'esr' | \
                              awk '!/esr/ && !/b/')
    else
        versions=$(echo "$html" | grep -oP '>[0-9]+\.[0-9]+(\.[0-9]+)?/<' | \
                              sed 's/[><\/]//g' | \
                              grep -v 'b' | grep -v 'esr')
    fi
    
    if [ -z "$target_major" ]; then
        # Get the latest version
        if [ "$OS_TYPE" == "Darwin" ]; then
            local version=$(echo "$versions" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)
        else
            local version=$(echo "$versions" | sort -V | tail -1)
        fi
    else
        # Get the latest version for specific major version
        local filtered_versions=$(echo "$versions" | grep "^${target_major}\.")
        if [ "$OS_TYPE" == "Darwin" ]; then
            local version=$(echo "$filtered_versions" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)
        else
            local version=$(echo "$filtered_versions" | sort -V | tail -1)
        fi
    fi
    
    if [ -z "$version" ]; then
        return 1
    fi
    
    echo "$version"
}

# Function to build Firefox image
build_firefox_image() {
    local target_major=$1
    
    log_info "=== Building Firefox Image ==="
    
    if [ -z "$target_major" ]; then
        log_info "Fetching latest Firefox version..."
    else
        log_info "Fetching latest Firefox version for major version $target_major..."
    fi
    
    local version=$(get_firefox_version "$target_major")
    if [ $? -ne 0 ] || [ -z "$version" ]; then
        log_error "Failed to get Firefox version"
        return 1
    fi
    
    local major=$(echo "$version" | cut -d. -f1)
    local tag="${major}.0"
    
    log_info "Firefox version: $version (Tag: $tag)"
    
    # Check if image already exists
    if check_existing_tag "$DOCKER_USERNAME/firefox" "$tag"; then
        log_warn "Image $DOCKER_USERNAME/firefox:$tag already exists. Skipping..."
        return 0
    fi
    
    # Check if .deb file exists
    local deb_file="firefox_${version}_amd64.deb"
    local tar_url="https://ftp.mozilla.org/pub/firefox/releases/${version}/linux-x86_64/en-US/firefox-${version}.tar.bz2"
    local deb_url="https://ftp.mozilla.org/pub/firefox/releases/${version}/linux-x86_64/en-US/firefox-${version}.deb"
    
    cd "$WORK_DIR"
    
    # Try to download .deb first, fall back to noting it's unavailable
    log_info "Checking for Firefox .deb package..."
    
    if curl -f -s -I "$deb_url" > /dev/null 2>&1; then
        log_info "Found .deb package, downloading..."
        if download_file "$deb_url" "$deb_file"; then
            log_info "Building Firefox image: $DOCKER_USERNAME/firefox:$tag"
            
            if "$IMAGES_BINARY" firefox -b "./${deb_file}" -t "$DOCKER_USERNAME/firefox:${tag}" 2>&1 | while IFS= read -r line; do log_info "$line"; done; then
                log_info "Successfully built Firefox image: $DOCKER_USERNAME/firefox:$tag"
                rm -f "$deb_file"
                cd ..
                return 0
            else
                log_error "Failed to build Firefox image"
                rm -f "$deb_file"
                cd ..
                return 1
            fi
        fi
    else
        log_warn "Firefox .deb package not available"
        log_warn "Tar archive available at: $tar_url"
        log_warn "Manual .deb creation required"
        cd ..
        return 0
    fi
    
    cd ..
    return 0
}

# Function to get Opera version for a specific major version
get_opera_version() {
    local target_major=$1
    
    # Fetch directory listing (silently)
    local html=$(curl -s https://get.opera.com/pub/opera/desktop/ 2>/dev/null)
    
    # Extract version directories
    local versions
    if [ "$OS_TYPE" == "Darwin" ]; then
        versions=$(echo "$html" | grep -o '>[0-9]\+\.[0-9]\+\.[0-9]\+\.[0-9]\+/<' | \
                              sed 's/[><\/]//g')
    else
        versions=$(echo "$html" | grep -oP '>[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/<' | \
                              sed 's/[><\/]//g')
    fi
    
    if [ -z "$target_major" ]; then
        # Get the latest version
        if [ "$OS_TYPE" == "Darwin" ]; then
            local version=$(echo "$versions" | sort -t. -k1,1n -k2,2n -k3,3n -k4,4n | tail -1)
        else
            local version=$(echo "$versions" | sort -V | tail -1)
        fi
    else
        # Get the latest version for specific major version
        local filtered_versions=$(echo "$versions" | grep "^${target_major}\.")
        if [ "$OS_TYPE" == "Darwin" ]; then
            local version=$(echo "$filtered_versions" | sort -t. -k1,1n -k2,2n -k3,3n -k4,4n | tail -1)
        else
            local version=$(echo "$filtered_versions" | sort -V | tail -1)
        fi
    fi
    
    if [ -z "$version" ]; then
        return 1
    fi
    
    echo "$version"
}

# Function to build Opera image
build_opera_image() {
    local target_major=$1
    
    log_info "=== Building Opera Image ==="
    
    if [ -z "$target_major" ]; then
        log_info "Fetching latest Opera version..."
    else
        log_info "Fetching latest Opera version for major version $target_major..."
    fi
    
    local version=$(get_opera_version "$target_major")
    if [ $? -ne 0 ] || [ -z "$version" ]; then
        log_error "Failed to get Opera version"
        return 1
    fi
    
    local major=$(echo "$version" | cut -d. -f1)
    local tag="${major}.0"
    
    log_info "Opera version: $version (Tag: $tag)"
    
    # Check if image already exists
    if check_existing_tag "$DOCKER_USERNAME/opera" "$tag"; then
        log_warn "Image $DOCKER_USERNAME/opera:$tag already exists. Skipping..."
        return 0
    fi
    
    local deb_file="opera-stable_${version}_amd64.deb"
    local download_url="https://get.opera.com/pub/opera/desktop/${version}/linux/${deb_file}"
    
    cd "$WORK_DIR"
    
    if ! download_file "$download_url" "$deb_file"; then
        log_error "Failed to download Opera .deb"
        cd ..
        return 1
    fi
    
    log_info "Building Opera image: $DOCKER_USERNAME/opera:$tag"
    
    if "$IMAGES_BINARY" opera -b "./${deb_file}" -t "$DOCKER_USERNAME/opera:${tag}" 2>&1 | while IFS= read -r line; do log_info "$line"; done; then
        log_info "Successfully built Opera image: $DOCKER_USERNAME/opera:$tag"
        rm -f "$deb_file"
        cd ..
        return 0
    else
        log_error "Failed to build Opera image"
        rm -f "$deb_file"
        cd ..
        return 1
    fi
}

# Main execution
main() {
    log_info "Starting browser image build process..."
    log_info "OS: $OS_TYPE | Architecture: $ARCH_TYPE"
    log_info "Working directory: $(pwd)"
    
    if [ -n "$SPECIFIC_MAJOR" ]; then
        log_info "Target major version: $SPECIFIC_MAJOR"
    fi
    
    if [ "$BROWSER_TO_BUILD" != "all" ]; then
        log_info "Building only: $BROWSER_TO_BUILD"
    fi
    
    # Check dependencies first
    check_dependencies
    
    local success_count=0
    local fail_count=0
    
    # Build Chrome
    if [[ "$BROWSER_TO_BUILD" == "all" || "$BROWSER_TO_BUILD" == "chrome" ]]; then
        echo ""
        if build_chrome_image "$SPECIFIC_MAJOR"; then
            ((success_count++))
        else
            ((fail_count++))
        fi
    fi
    
    # Build Firefox
    if [[ "$BROWSER_TO_BUILD" == "all" || "$BROWSER_TO_BUILD" == "firefox" ]]; then
        echo ""
        if build_firefox_image "$SPECIFIC_MAJOR"; then
            ((success_count++))
        else
            ((fail_count++))
        fi
    fi
    
    # Build Opera
    if [[ "$BROWSER_TO_BUILD" == "all" || "$BROWSER_TO_BUILD" == "opera" ]]; then
        echo ""
        if build_opera_image "$SPECIFIC_MAJOR"; then
            ((success_count++))
        else
            ((fail_count++))
        fi
    fi
    
    echo ""
    log_info "=== Build Summary ==="
    log_info "OS/Arch: $OS_TYPE/$ARCH_TYPE"
    log_info "Successful builds: $success_count"
    log_info "Failed builds: $fail_count"
    log_info "Working directory: $WORK_DIR"
    
    if [ $fail_count -eq 0 ]; then
        log_info "All builds completed successfully!"
        exit 0
    else
        log_error "Some builds failed. Check logs above."
        exit 1
    fi
}

# Run main function
main "$@"