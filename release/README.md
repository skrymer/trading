# Release & CI/CD Documentation

This directory contains documentation for the CI/CD pipeline and release process.

## Files

### CI/CD Pipeline
- **`CI_WORKFLOW.md`** - Comprehensive guide to the automated testing workflow
  - Explains all 4 CI jobs (backend-test, frontend-test, integration-test, code-quality)
  - Common failures and troubleshooting
  - Local testing instructions
  - Branch protection setup

### Deployment & Releases
- **`DEPLOYMENT.md`** - Full deployment guide for creating releases
  - Detailed step-by-step release process
  - Code signing setup (macOS, Windows)
  - Auto-updates future enhancement
  - Troubleshooting and rollback procedures

- **`RELEASE_QUICKSTART.md`** - Quick 3-step release guide
  - TL;DR for creating releases quickly
  - Common release scenarios (stable, beta, patch)
  - Quick troubleshooting tips

## Quick Links

### Continuous Integration (Automated Testing)
```bash
# CI runs automatically on:
# - Push to main
# - Pull requests

# View CI results:
https://github.com/YOUR_USERNAME/trading/actions
```

See: `CI_WORKFLOW.md`

### Continuous Deployment (Releases)
```bash
# Create a release:
git tag v1.0.0
git push origin v1.0.0

# Download releases:
https://github.com/YOUR_USERNAME/trading/releases
```

See: `DEPLOYMENT.md` or `RELEASE_QUICKSTART.md`

## Workflow Files

Actual workflow configurations are in `.github/workflows/`:
- `.github/workflows/ci.yml` - Continuous Integration (testing)
- `.github/workflows/release.yml` - Continuous Deployment (releases)

## CI/CD Status

| Component | Status | Documentation |
|-----------|--------|---------------|
| Automated Testing | ✅ Complete | `CI_WORKFLOW.md` |
| Automated Releases | ✅ Complete | `DEPLOYMENT.md` |
| Code Signing | ⏳ Pending | `DEPLOYMENT.md` (Advanced section) |
| Auto-Updates | ⏳ Pending | `DEPLOYMENT.md` (Future section) |

## Getting Started

1. **For developers**: Read `CI_WORKFLOW.md` to understand automated testing
2. **For releases**: Read `RELEASE_QUICKSTART.md` for quick release guide
3. **For detailed deployment**: Read `DEPLOYMENT.md` for comprehensive info
