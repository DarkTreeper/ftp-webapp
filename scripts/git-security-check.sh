#!/bin/bash
# Git Security Pre-commit Hook
# Verhindert versehentliche Commits von Secrets und sensiblen Dateien
#
# Installation:
#   cp scripts/git-security-check.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit

set -e

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

FAILED=0

echo -e "${YELLOW}🔐 Git Security Pre-commit Check${NC}"
echo "---"

# 1. Prüfe auf .env Dateien
if git diff --cached --name-only | grep -E "^\.env($|\.)" > /dev/null; then
  echo -e "${RED}❌ ERROR: .env files detected in staging!${NC}"
  echo "   These files contain credentials and must not be committed."
  echo "   Unstage with: git reset HEAD .env"
  FAILED=1
fi

# 2. Prüfe auf hardcodierte Passwords in Java-Dateien
if git diff --cached -- '*.java' | grep -i "password\s*=.*['\"]" | grep -v "passwordEncoder" | grep -v "getPassword" > /dev/null; then
  echo -e "${RED}❌ WARNING: Possible hardcoded password in Java files${NC}"
  echo "   Use environment variables instead."
  FAILED=1
fi

# 3. Prüfe auf gehackte Credentials in Commits
if git diff --cached | grep -E "(FTP_PASSWORD|APP_PASSWORD|secret|SECRET)[[:space:]]*[=:][[:space:]]*[^$]" > /dev/null; then
  echo -e "${RED}❌ WARNING: Possible credential leak detected${NC}"
  FAILED=1
fi

# 4. Prüfe auf lokale Testdaten-Verzeichnisse
if git diff --cached --name-only | grep -E "^local-data/" > /dev/null; then
  echo -e "${YELLOW}⚠️  WARNING: local-data/ files detected${NC}"
  echo "   These are usually test files and should be ignored."
fi

if git diff --cached --name-only | grep -E "^demo-remote-data/" > /dev/null; then
  echo -e "${YELLOW}⚠️  WARNING: demo-remote-data/ files detected${NC}"
  echo "   Demo data should be lightweight. Add .gitkeep instead."
fi

# 5. Prüfe auf .class und target/ Dateien
if git diff --cached --name-only | grep -E "\.(class|jar)$|^target/" > /dev/null; then
  echo -e "${RED}❌ ERROR: Compiled files in staging!${NC}"
  echo "   Add to .gitignore: /target/ and *.class"
  FAILED=1
fi

# 6. Prüfe auf IDE-Konfigurationsdateien
if git diff --cached --name-only | grep -E "^\.idea/|\.vscode/|\.settings/" > /dev/null; then
  echo -e "${YELLOW}⚠️  WARNING: IDE config detected${NC}"
  echo "   Consider excluding these from Git."
fi

echo "---"

if [ $FAILED -eq 0 ]; then
  echo -e "${GREEN}✅ Security check passed!${NC}"
  exit 0
else
  echo -e "${RED}❌ Security check failed! Fix issues above before committing.${NC}"
  echo "   To bypass (NOT recommended): git commit --no-verify"
  exit 1
fi

