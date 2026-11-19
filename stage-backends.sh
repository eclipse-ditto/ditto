#!/usr/bin/env bash

# Copyright (c) 2026 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

################################################################################
# Stages the persistence backend extension JARs of one service into           #
# <dest>/mongodb/ and <dest>/postgresql/, the layout the Docker images carry  #
# in /opt/ditto/backends/ (DITTO_PERSISTENCE_BACKEND selects the folder).     #
# This script holds the only copy of the service -> backend JAR mapping.      #
################################################################################

# Directory of the script (the repository root)
SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# The extension modules (under internal/utils/) whose shaded JARs this script stages.
EXTENSION_MODULES="mongo-persistence-extension mongo-search-extension postgres-client-extension postgres-persistence-extension postgres-search-extension"

usage() {
  printf "usage: stage-backends.sh <gateway|policies|things|thingsearch|connectivity> <version> <dest> | --maven-modules\n" >&2
  exit 2
}

if [ $# -eq 1 ] && [ "$1" = "--maven-modules" ]; then
  modules=""
  for ext in $EXTENSION_MODULES; do
    modules="${modules:+$modules,}internal/utils/$ext"
  done
  printf "%s\n" "$modules"
  exit 0
fi

[ $# -eq 3 ] || usage
service="$1"
version="$2"
dest="$3"

# The extension modules each backend folder of the service gets.
case "$service" in
  policies|things|connectivity)
    mongodb_extensions="mongo-persistence-extension"
    postgresql_extensions="postgres-client-extension postgres-persistence-extension"
    ;;
  thingsearch)
    mongodb_extensions="mongo-persistence-extension mongo-search-extension"
    postgresql_extensions="postgres-client-extension postgres-search-extension"
    ;;
  gateway)
    mongodb_extensions=""
    postgresql_extensions=""
    ;;
  *)
    usage
    ;;
esac

jar_path() {
  printf "internal/utils/%s/target/ditto-%s-%s.jar" "$1" "$1" "$version"
}

# Preflight: every source JAR must exist before <dest> is touched.
missing=0
for ext in $mongodb_extensions $postgresql_extensions; do
  jar="$(jar_path "$ext")"
  if [ ! -f "$SCRIPTDIR/$jar" ]; then
    printf "ERROR: %s does not exist -- build the backend extension modules first (mvn install -DskipTests -DskipITs -pl \$(stage-backends.sh --maven-modules) -am).\n" "$jar" >&2
    missing=1
  fi
done
[ "$missing" -eq 0 ] || exit 1

for backend in mongodb postgresql; do
  rm -rf "${dest:?}/$backend"
  mkdir -p "$dest/$backend"
done

stage() {
  local backend="$1" ext names=""
  shift
  for ext in "$@"; do
    cp "$SCRIPTDIR/$(jar_path "$ext")" "$dest/$backend/"
    names="${names:+$names }ditto-$ext-$version.jar"
  done
  if [ -z "$names" ]; then
    : > "$dest/$backend/.keep"
    names=".keep"
  fi
  printf "staged %s/%s: %s\n" "$dest" "$backend" "$names"
}

# shellcheck disable=SC2086 # the extension lists are space-separated words
stage mongodb $mongodb_extensions
# shellcheck disable=SC2086
stage postgresql $postgresql_extensions
