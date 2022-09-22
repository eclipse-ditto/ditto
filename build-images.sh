#!/bin/bash

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

print_usage() {
  printf "%s [-p HTTP(S) PROXY HOST:PORT]\n" "$1"
}

print_used_proxies() {
  printf "Using HTTP_PROXY=%s\n" "$HTTP_PROXY_LOCAL"
  printf "Using HTTPS_PROXY=%s\n" "$HTTPS_PROXY_LOCAL"
}

build_docker_image() {
  module_name_base=$(echo "$1" | awk -F ":" '{ print $1 }')
  module_name=$(printf "ditto-%s" "$module_name_base")
  image_tag=$(printf "${CONTAINER_REGISTRY}/ditto-%s" "$(echo "$1" | awk -F ":" '{ print $2 }')")
  jvm_args=$(echo "$1" | awk -F ":" '{ print $4 }')
  main_class=$(echo "$1" | awk -F ":" '{ print $3 }')
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
  for i in "${SERVICES[@]}"; do
    build_docker_image "$i"
  done
  build_ditto_ui_docker_image
}

set_proxies() {
  HTTP_PROXY_LOCAL="http://$1"
  printf "Set HTTP_PROXY to %s\n" "$HTTP_PROXY_LOCAL"
  HTTPS_PROXY_LOCAL="https://$1"
  printf "Set HTTPS_PROXY to %s\n" "$HTTPS_PROXY_LOCAL"
}

evaluate_script_arguments() {
  while getopts "p:hP" opt; do
    case ${opt} in
    p)
      set_proxies "$OPTARG"
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
elif evaluate_script_arguments "$@"; then
  build_all_docker_images
fi
