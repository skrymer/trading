# Continuous Integration (CI) Workflow

This document explains the automated testing and build verification workflow for the Trading Platform.

## Overview

The CI workflow runs automatically on:
- **Every push to `main` branch**
- **Every pull request to `main` branch**

Location: `.github/workflows/ci.yml`

## Workflow Jobs

### 1. Backend Tests (`backend-test`)

**Runs on:** Ubuntu Latest
**Duration:** ~2-5 minutes

**Steps:**
1. ✅ Checkout code
2. ✅ Setup Java 21 with Gradle cache
3. ✅ Run unit tests (`./gradlew test`)
4. ✅ Build backend JAR (`./gradlew bootJar`)
5. ✅ Upload test results (on failure)

**What it checks:**
- All Kotlin unit tests pass
- Backend compiles successfully
- JAR can be built
- No compilation errors

**Artifacts:**
- Test results (available for 7 days)
- Build reports (on failure, available for 7 days)

---

### 2. Frontend Tests (`frontend-test`)

**Runs on:** Ubuntu Latest
**Duration:** ~3-6 minutes

**Steps:**
1. ✅ Checkout code
2. ✅ Setup Node.js 18 with npm cache
3. ✅ Install dependencies
4. ✅ Run TypeScript type checking (`npm run typecheck`)
5. ✅ Run ESLint (`npm run lint`)
6. ✅ Build frontend (`npm run build`)

**What it checks:**
- No TypeScript type errors
- Code follows ESLint rules (no trailing commas, 1TBS)
- Frontend builds successfully
- No missing imports or components

---

### 3. Integration Build (`integration-test`)

**Runs on:** Ubuntu Latest
**Duration:** ~5-8 minutes
**Depends on:** Backend and Frontend tests must pass first

**Steps:**
1. ✅ Checkout code
2. ✅ Setup Node.js and Java
3. ✅ Install all dependencies
4. ✅ Run full build (`npm run build:all`)
5. ✅ Verify backend JAR exists
6. ✅ Verify frontend build exists

**What it checks:**
- Backend and frontend integrate correctly
- Build script works end-to-end
- All artifacts are created properly

---

### 4. Code Quality (`code-quality`)

**Runs on:** Ubuntu Latest
**Duration:** ~2-4 minutes

**Steps:**
1. ✅ Checkout code
2. ✅ Setup Java 21
3. ✅ Run Kotlin compilation check
4. ✅ Check code style (continues on error)

**What it checks:**
- Kotlin code compiles
- No syntax errors
- Code style compliance (optional)

---

## How to View CI Results

### For Commits to Main

1. Go to: `https://github.com/YOUR_USERNAME/trading/actions`
2. Click on the latest "Continuous Integration" workflow
3. See all 4 jobs and their status

### For Pull Requests

1. Create a pull request
2. GitHub automatically runs CI
3. See status checks at the bottom of the PR
4. ✅ Green checkmark = All tests passed
5. ❌ Red X = Tests failed (click for details)

---

## Local Testing

Before pushing code, run these locally to catch issues early:

### Backend Tests
```bash
cd udgaard
./gradlew test
./gradlew bootJar
```

### Frontend Tests
```bash
cd asgaard
npm run typecheck
npm run lint
npm run build
```

### Full Build
```bash
# From project root
npm run build:all
```

---

## Common CI Failures

### Backend Test Failures

**Symptom:** `backend-test` job fails

**Possible causes:**
- Unit test failures
- Compilation errors in Kotlin code
- Missing dependencies in `build.gradle`
- Database entity issues (JPA validation)

**How to fix:**
1. Check test logs in GitHub Actions
2. Run `./gradlew test` locally to reproduce
3. Fix failing tests or code
4. Push again

---

### Frontend Type Errors

**Symptom:** `frontend-test` job fails on typecheck

**Possible causes:**
- TypeScript type errors
- Missing type definitions
- Incorrect prop types in Vue components
- Missing imports

**How to fix:**
1. Run `npm run typecheck` in `asgaard/`
2. Fix type errors shown
3. Push again

---

### Frontend Lint Errors

**Symptom:** `frontend-test` job fails on lint

**Possible causes:**
- Trailing commas (forbidden by ESLint config)
- Incorrect brace style
- Unused variables
- Missing semicolons

**How to fix:**
1. Run `npm run lint` in `asgaard/`
2. Fix linting errors
3. Push again

---

### Integration Build Failures

**Symptom:** `integration-test` job fails

**Possible causes:**
- Backend or frontend didn't build properly
- Missing files in build output
- Build script errors

**How to fix:**
1. Run `npm run build:all` locally
2. Check for errors in console
3. Verify `udgaard/build/libs/` has JAR
4. Verify `asgaard/.output/` exists
5. Fix issues and push again

---

## CI Workflow Features

### ✅ Caching

**Gradle Cache:**
- Caches downloaded dependencies
- Speeds up Java/Kotlin builds
- Automatic via `actions/setup-java@v4`

**NPM Cache:**
- Caches `node_modules`
- Speeds up npm installs
- Automatic via `actions/setup-node@v4`

### ✅ Parallel Execution

Jobs run in parallel for speed:
- `backend-test` and `frontend-test` run simultaneously
- `code-quality` runs independently
- `integration-test` waits for backend/frontend to pass

**Total time:** ~5-8 minutes (vs. ~15-20 minutes sequential)

### ✅ Artifact Upload

Failed tests automatically upload:
- Test results (JUnit XML)
- Build reports (HTML)
- Retention: 7 days

**How to access:**
1. Go to failed workflow run
2. Scroll to "Artifacts" section
3. Download test results/reports

---

## Branch Protection (Recommended)

To enforce CI passing before merge:

1. Go to: `Settings` → `Branches`
2. Add rule for `main` branch
3. Enable: "Require status checks to pass before merging"
4. Select all 4 jobs:
   - `backend-test`
   - `frontend-test`
   - `integration-test`
   - `code-quality`
5. Save

Now pull requests **must** pass all tests before merge!

---

## CI/CD Pipeline Status

| Feature | Status | Notes |
|---------|--------|-------|
| Automated Testing | ✅ Complete | Runs on all PRs and main pushes |
| Backend Tests | ✅ Complete | Gradle test + build |
| Frontend Tests | ✅ Complete | Typecheck + lint + build |
| Integration Build | ✅ Complete | Full app build verification |
| Code Quality | ✅ Complete | Kotlin compilation check |
| Automated Releases | ✅ Complete | Tag-based (see `release.yml`) |
| Code Signing | ⏳ Pending | Need certificates |
| Auto-Updates | ⏳ Pending | Future enhancement |
| Security Scanning | ❌ Not Started | Future enhancement |
| Dependency Updates | ❌ Not Started | Future enhancement |

---

## Next Steps

### Immediate
- [x] Create CI workflow
- [ ] Test CI on a pull request
- [ ] Enable branch protection rules

### Short Term
- [ ] Add code coverage reporting
- [ ] Add security scanning (Snyk/GitHub Security)
- [ ] Set up Dependabot for dependency updates
- [ ] Add performance benchmarking

### Long Term
- [ ] Add E2E testing (Playwright/Cypress)
- [ ] Add visual regression testing
- [ ] Set up staging environment
- [ ] Add release notifications (Slack/Discord)

---

## Monitoring

### GitHub Actions Dashboard
```
https://github.com/YOUR_USERNAME/trading/actions
```

### Status Badge

Add to README.md:
```markdown
![CI](https://github.com/YOUR_USERNAME/trading/actions/workflows/ci.yml/badge.svg)
```

Shows current CI status on repository homepage.

---

## Troubleshooting

### Workflow not running?

**Check:**
1. Workflow file is in `.github/workflows/ci.yml`
2. GitHub Actions is enabled (Settings → Actions)
3. Push was to `main` or PR targets `main`

### Jobs stuck pending?

**Possible causes:**
- GitHub Actions quota exceeded (free tier: 2000 min/month)
- Runner availability issues
- Wait a few minutes and check again

### Cache issues?

**Solution:**
```yaml
# In workflow file, clear cache by changing cache key
cache: 'gradle-v2'  # Increment version
```

Or manually delete caches in Settings → Actions → Caches

---

## Cost

**Free Tier:**
- 2000 minutes/month for private repos
- Unlimited for public repos

**This workflow uses:**
- ~5-8 minutes per run
- ~250 runs/month estimate = ~2000 minutes
- Stays within free tier!

---

## Support

If CI fails unexpectedly:
1. Check workflow logs in Actions tab
2. Download test artifacts for detailed errors
3. Run tests locally to reproduce
4. Check this documentation for common issues

For CI workflow improvements or questions, see:
- GitHub Actions docs: https://docs.github.com/actions
- Workflow syntax: https://docs.github.com/actions/reference/workflow-syntax-for-github-actions
