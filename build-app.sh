#!/bin/bash

# Production build script for Trading Desktop App
# This script builds everything and creates distributable packages

set -e

echo "📦 Trading Desktop App - Production Build"
echo "=========================================="

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Parse command line arguments
PLATFORM="current"
if [ "$1" == "--win" ]; then
    PLATFORM="win"
elif [ "$1" == "--mac" ]; then
    PLATFORM="mac"
elif [ "$1" == "--linux" ]; then
    PLATFORM="linux"
elif [ "$1" == "--all" ]; then
    PLATFORM="all"
fi

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}📦 Installing Electron dependencies...${NC}"
    npm install
fi

# Build backend
echo -e "${BLUE}🔨 Building Spring Boot backend...${NC}"
cd udgaard
./gradlew clean bootJar
cd ..

# Verify JAR
JAR_PATH="udgaard/build/libs/udgaard-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${YELLOW}⚠️  Backend JAR not found at: $JAR_PATH${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Backend built successfully${NC}"

# Build frontend
echo -e "${BLUE}🎨 Building Nuxt frontend...${NC}"
cd asgaard_nuxt
npm install
npm run build
cd ..

# Verify frontend build
if [ ! -d "asgaard_nuxt/.output" ]; then
    echo -e "${YELLOW}⚠️  Frontend build not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Frontend built successfully${NC}"

# Build Electron app
echo -e "${BLUE}📦 Building Electron desktop app...${NC}"

case $PLATFORM in
    win)
        echo "Building for Windows..."
        npm run dist:win
        ;;
    mac)
        echo "Building for macOS..."
        npm run dist:mac
        ;;
    linux)
        echo "Building for Linux..."
        npm run dist:linux
        ;;
    all)
        echo "Building for all platforms..."
        npm run dist
        ;;
    *)
        echo "Building for current platform..."
        npm run dist
        ;;
esac

echo ""
echo -e "${GREEN}✅ Build complete!${NC}"
echo ""
echo "Distributable files are in: dist-electron/"
ls -lh dist-electron/ 2>/dev/null || echo "No files generated yet"

echo ""
echo "Usage: ./build-app.sh [--win|--mac|--linux|--all]"
