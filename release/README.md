# Release & CI/CD Documentation

This directory contains documentation for the CI/CD pipeline.

## Files

### CI/CD Pipeline
- **`CI_WORKFLOW.md`** - Comprehensive guide to the automated testing workflow
  - Explains all 4 CI jobs (backend-test, frontend-test, integration-test, code-quality)
  - Common failures and troubleshooting
  - Local testing instructions
  - Branch protection setup

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

## Workflow Files

Actual workflow configurations are in `.github/workflows/`:
- `.github/workflows/ci.yml` - Continuous Integration (testing)
