#!/bin/bash

# Development startup script for Trading Desktop App
# This script builds the backend and starts Electron in dev mode

set -e

echo "🚀 Trading Desktop App - Development Setup"
echo "=========================================="

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if npm dependencies are installed
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}📦 Installing Electron dependencies...${NC}"
    npm install
fi

# Build backend JAR
echo -e "${BLUE}🔨 Building Spring Boot backend...${NC}"
cd udgaard
./gradlew bootJar
cd ..

# Check if JAR was created
JAR_PATH="udgaard/build/libs/udgaard-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${YELLOW}⚠️  Backend JAR not found at: $JAR_PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Backend built successfully${NC}"

# Start Electron
echo -e "${BLUE}🖥️  Starting Electron app...${NC}"
echo ""
echo "Note: Make sure Nuxt dev server is running on port 3000"
echo "      In another terminal: cd asgaard && npm run dev"
echo ""

npm run dev
