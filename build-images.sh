#!/usr/bin/env bash

set -e

# Copyright (c) 2020 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0

################################################################################
# This script builds Docker images consisting of the "fat-jars" of the         #
# specified services (SERVICES).                                               #
################################################################################

# Directory of the script
SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Array of the services to build Docker images for.
# The pattern is MODULE_NAME:IMAGE_NAME as both can differ from each other.
SERVICES=(
  "gateway:gateway:org.eclipse.ditto.gateway.service.starter.GatewayService"
  "policies:policies:org.eclipse.ditto.policies.service.starter.PoliciesService"
  "things:things:org.eclipse.ditto.things.service.starter.ThingsService"
  "thingsearch:things-search:org.eclipse.ditto.thingsearch.service.starter.SearchService"
  "connectivity:connectivity:org.eclipse.ditto.connectivity.service.ConnectivityService:--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/sun.security.util=ALL-UNNAMED"
)
: "${HTTP_PROXY_LOCAL:=$HTTP_PROXY}"
: "${HTTPS_PROXY_LOCAL:=$HTTPS_PROXY}"
: "${CONTAINER_REGISTRY:=eclipse}"
: "${DOCKER_BIN:=docker}"
: "${DOCKERFILE:="dockerfile-snapshot"}"
: "${SERVICE_VERSION:="0-SNAPSHOT"}"
: "${IMAGE_VERSION:="${SERVICE_VERSION}"}"
: "${BAKE_POSTGRES_EXTENSIONS:=false}"

print_usage() {
  printf "%s [-uPh] [-p HTTP(S) PROXY HOST:PORT]\n" "$1"
  printf "  -u  build the ditto-ui as docker image\n" "$1"
  printf "  -P  push the built docker images\n" "$1"
  printf "  -h  display help\n" "$1"
  printf "  BAKE_POSTGRES_EXTENSIONS=true (env) bakes the PostgreSQL extension JARs of this build into the\n"
  printf "      things, policies, connectivity and things-search images (snapshot dockerfiles only).\n"
  printf "      Refuses to bake a shaded extension JAR whose classes differ from the module JAR it bundles\n"
  printf "      (stale after an incremental build): rebuild with mvn clean install -DskipTests -DskipITs\n"
  printf "  CHECK_ONLY=1 (env) with BAKE_POSTGRES_EXTENSIONS=true: run only that staleness check, build nothing\n"
}

print_used_proxies() {
  printf "Using HTTP_PROXY=%s\n" "$HTTP_PROXY_LOCAL"
  printf "Using HTTPS_PROXY=%s\n" "$HTTPS_PROXY_LOCAL"
}

# The shaded PostgreSQL extension JARs and the Ditto module JAR each one bundles (its shade whitelist):
# EXTENSION_MODULE_DIR:MODULE_DIR:MODULE_ARTIFACT_ID, all under internal/utils/.
POSTGRES_EXTENSIONS=(
  "postgres-client-extension:postgres-client:ditto-internal-utils-postgres-client"
  "postgres-persistence-extension:persistence-r2dbc:ditto-internal-utils-persistence-r2dbc"
  "postgres-search-extension:search-r2dbc:ditto-internal-utils-search-r2dbc"
)

# Prints every .class entry of a JAR as "<path> <length> <crc-32>" (columns 8, 1 and 7 of `unzip -lv`). The
# CRC-32 is that of the uncompressed bytes, so entries of two JARs compare regardless of how each was deflated.
list_class_entries() {
  unzip -lv "$1" | awk 'NF >= 8 && $NF ~ /\.class$/ { print $NF, $1, $7 }'
}

# Refuses to bake a shaded extension JAR whose classes are not those of the module JAR it bundles.
#
# An incremental `mvn install` (no `clean`) on a tree with earlier build output can leave the shaded JARs stale
# while every build step reports success: the extension module's own sources rarely change, so jar:jar finds the
# artifact already in target/ "up to date" and keeps it -- but that artifact is the PREVIOUS shaded JAR (shade
# replaces the thin JAR in place). shade:shade then merges it with the freshly built module JAR and keeps the
# first copy of every overlapping class, i.e. the stale one. The images would run the previous build's classes.
# The extension poms set maven-jar-plugin's forceCreation to remove the cause; this check catches every other way
# of getting there (a partial `-pl` build of the module alone, a copied-in JAR, ...).
#
# Compares length + CRC-32 of EVERY class of the module JAR with the shaded JAR's copy. Any difference or
# missing class fails the build before the first docker build. Only runs when BAKE_POSTGRES_EXTENSIONS=true.
check_postgres_extension_jars() {
  local entry extension module artifact shaded_jar module_jar jar module_classes shaded_classes stale stale_count
  local failed=0
  printf "\nChecking the shaded PostgreSQL extension JARs against the module JARs they bundle ...\n"
  for entry in "${POSTGRES_EXTENSIONS[@]}"; do
    IFS=: read -r extension module artifact <<< "$entry"
    shaded_jar="$SCRIPTDIR/internal/utils/$extension/target/ditto-$extension-$SERVICE_VERSION.jar"
    module_jar="$SCRIPTDIR/internal/utils/$module/target/$artifact-$SERVICE_VERSION.jar"
    for jar in "$shaded_jar" "$module_jar"; do
      if [ ! -f "$jar" ]; then
        printf "  ERROR: <%s> does not exist -- the tree is not built.\n" "${jar#"$SCRIPTDIR/"}" >&2
        failed=1
        continue 2
      fi
    done
    module_classes=$(list_class_entries "$module_jar" | awk 'END { print NR }')
    shaded_classes=$(list_class_entries "$shaded_jar" | awk 'END { print NR }')
    if [ "$module_classes" -eq 0 ] || [ "$shaded_classes" -eq 0 ]; then
      printf "  ERROR: could not list the classes of <%s> (%s) and <%s> (%s).\n" \
        "${module_jar#"$SCRIPTDIR/"}" "$module_classes" "${shaded_jar#"$SCRIPTDIR/"}" "$shaded_classes" >&2
      failed=1
      continue
    fi
    # "S <class> <length> <crc>" lines of the shaded JAR first, then "M ..." lines of the module JAR: a module
    # class without an identical shaded copy is stale (or missing).
    stale=$( { list_class_entries "$shaded_jar" | sed 's/^/S /'; list_class_entries "$module_jar" | sed 's/^/M /'; } |
      awk '$1 == "S" { shaded[$2] = $3 " " $4; next }
           !($2 in shaded) { print "      " $2 " (missing in the shaded JAR)"; next }
           shaded[$2] != $3 " " $4 { print "      " $2 " (differs)" }')
    if [ -z "$stale" ]; then
      printf "  OK    %s: all %s classes of %s match\n" "${shaded_jar#"$SCRIPTDIR/"}" "$module_classes" "$artifact"
    else
      stale_count=$(printf '%s\n' "$stale" | awk 'NF { n++ } END { print n + 0 }')
      {
        printf "  STALE %s:\n" "${shaded_jar#"$SCRIPTDIR/"}"
        printf "        %s of %s classes of %s are not the module JAR's (first 10):\n" \
          "$stale_count" "$module_classes" "${module_jar#"$SCRIPTDIR/"}"
        printf '%s\n' "$stale" | head -n 10
      } >&2
      failed=1
    fi
  done
  if [ "$failed" -ne 0 ]; then
    {
      printf "\nERROR: refusing to bake stale PostgreSQL extension JARs into the images (the services would run the\n"
      printf "classes of a previous build while every build step reported success). Rebuild the tree with\n"
      printf "\n    mvn clean install -DskipTests -DskipITs\n\n"
      printf "and run %s again. An incremental 'mvn install' without 'clean' is what leaves the shaded JARs stale.\n" "$0"
    } >&2
    return 1
  fi
  printf "\n"
  return 0
}

build_docker_image() {
  module_name_base=$(echo "$1" | awk -F ":" '{ print $1 }')
  module_name=$(printf "ditto-%s" "$module_name_base")
  image_tag=$(printf "${CONTAINER_REGISTRY}/ditto-%s" "$(echo "$1" | awk -F ":" '{ print $2 }')")
  jvm_args=$(echo "$1" | awk -F ":" '{ print $4 }')
  main_class=$(echo "$1" | awk -F ":" '{ print $3 }')
  if [ -n "$NO_DOCKER_CACHE" ]; then
    no_cache_option=--no-cache
  fi;

  # Always (re)create the extensions staging dir the dockerfile COPYs; populate it with the Postgres
  # extension JARs (must come from the same reactor build as the allinone JARs — a boot self-check
  # fails fast on mismatch) when BAKE_POSTGRES_EXTENSIONS=true.
  extensions_dir="$SCRIPTDIR/$module_name_base/service/target/extensions"
  rm -rf "$extensions_dir"
  mkdir -p "$extensions_dir"
  if [[ "$BAKE_POSTGRES_EXTENSIONS" == "true" ]]; then
    case "$module_name_base" in
      policies|things|connectivity)
        cp "$SCRIPTDIR/internal/utils/postgres-client-extension/target/ditto-postgres-client-extension-$SERVICE_VERSION.jar" \
          "$SCRIPTDIR/internal/utils/postgres-persistence-extension/target/ditto-postgres-persistence-extension-$SERVICE_VERSION.jar" \
          "$extensions_dir/"
        ;;
      thingsearch)
        cp "$SCRIPTDIR/internal/utils/postgres-client-extension/target/ditto-postgres-client-extension-$SERVICE_VERSION.jar" \
          "$SCRIPTDIR/internal/utils/postgres-search-extension/target/ditto-postgres-search-extension-$SERVICE_VERSION.jar" \
          "$extensions_dir/"
        ;;
    esac
  fi
  printf "\nBuilding Docker image <%s> for service module <%s> with jvm_args <%s>\n" \
    "$image_tag" \
    "$module_name" \
    "$jvm_args"

    $DOCKER_BIN build --pull -f $SCRIPTDIR/$DOCKERFILE \
      --build-arg HTTP_PROXY="$HTTP_PROXY_LOCAL" \
      --build-arg HTTPS_PROXY="$HTTPS_PROXY_LOCAL" \
      --build-arg TARGET_DIR="$module_name_base"/service/target \
      --build-arg SERVICE_STARTER="$module_name"-service \
      --build-arg SERVICE_VERSION=$SERVICE_VERSION \
      --build-arg JVM_CMD_ARGS="$jvm_args" \
      --build-arg MAIN_CLASS="$main_class" \
      -t "$image_tag":$IMAGE_VERSION \
      $no_cache_option \
      "$SCRIPTDIR"

  if [[ "$PUSH_CONTAINERS" == "true" ]]; then
    $DOCKER_BIN push "$image_tag":$IMAGE_VERSION
  fi
}

build_ditto_ui_docker_image() {
  image_tag=$(printf "${CONTAINER_REGISTRY}/ditto-ui")
  printf "\nBuilding Docker image <%s> for Ditto-UI\n" \
    "$image_tag"

    $DOCKER_BIN build --pull -f $SCRIPTDIR/ui/Dockerfile \
        --build-arg HTTP_PROXY="$HTTP_PROXY_LOCAL" \
        --build-arg HTTPS_PROXY="$HTTPS_PROXY_LOCAL" \
        -t "$image_tag":$IMAGE_VERSION \
        "${SCRIPTDIR}/ui"

    if [[ "$PUSH_CONTAINERS" == "true" ]]; then
      $DOCKER_BIN push "$image_tag":$IMAGE_VERSION
    fi
}

build_all_docker_images() {
  # Stale shaded extension JARs are refused BEFORE the first docker build, once for all images.
  if [[ "$BAKE_POSTGRES_EXTENSIONS" == "true" ]]; then
    check_postgres_extension_jars || exit 1
  fi
  if [ -n "$CHECK_ONLY" ]; then
    printf "CHECK_ONLY is set: building no image.\n"
    return 0
  fi
  for i in "${SERVICES[@]}"; do
    build_docker_image "$i"
  done
  if [[ "$BUILD_UI" == "true" ]]; then
    build_ditto_ui_docker_image
  fi
}

set_proxies() {
  HTTP_PROXY_LOCAL="http://$1"
  printf "Set HTTP_PROXY to %s\n" "$HTTP_PROXY_LOCAL"
  HTTPS_PROXY_LOCAL="https://$1"
  printf "Set HTTPS_PROXY to %s\n" "$HTTPS_PROXY_LOCAL"
}

evaluate_script_arguments() {
  while getopts "p:uhP" opt; do
    case ${opt} in
    p)
      set_proxies "$OPTARG"
      ;;
    u)
      BUILD_UI="true"
      ;;
    P)
      PUSH_CONTAINERS="true"
      ;;
    h | *)
      print_usage "$0"
      return 1
      ;;
    esac
  done
  return 0
}

# Here the programme begins
if [ 0 -eq $# ]; then
  print_used_proxies
  build_all_docker_images
elif evaluate_script_arguments $@; then
  build_all_docker_images
fi
