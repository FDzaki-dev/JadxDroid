#!/usr/bin/env bash
# Shared CI helpers. Usage in a workflow step: `source scripts/ci/lib.sh`
mkdir -p ci-logs

# ci_fail <step-label> <message>: GitHub annotation + persistent line in ci-logs/errors.log, then exit 1.
ci_fail() {
  echo "::error::$2"
  echo "[$1] $2" >> ci-logs/errors.log
  exit 1
}
