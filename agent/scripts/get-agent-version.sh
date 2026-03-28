#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
AGENT_POM="$PROJECT_ROOT/agent/pom.xml"

awk '
    /<\/parent>/ { after_parent=1; next }
    after_parent && /<version>/ {
        gsub(/.*<version>|<\/version>.*/, "", $0)
        print
        exit
    }
' "$AGENT_POM"
