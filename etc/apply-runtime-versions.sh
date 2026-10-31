#!/bin/bash

# Pins the dependencies listed in a properties file (<groupId>:<artifactId>=<version>) as managed
# dependencies in the pom.xml of the given directory using dependency:add.
#
# Usage: apply-runtime-versions.sh <directory> [<properties file>]

root=$(cd "$(dirname "$0")/.." && pwd)
dir=${1:?Usage: $0 <directory> [<properties file>]}
file=${2:-$root/etc/runtime-versions.properties}

while IFS='=' read -r ga version; do

  [[ -z "$ga" || "$ga" == \#* ]] && continue

  (cd "$dir" && "$root/mvnw" -B -N dependency:add -Dgav="$ga:$version" -Dmanaged=true) || exit 1

done < "$file"
