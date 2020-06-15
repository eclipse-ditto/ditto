#!/bin/bash

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

docker build --pull -f dockerfile-snapshot\
  --build-arg TARGET_DIR=concierge/starter/target\
  --build-arg SERVICE_STARTER=ditto-services-concierge-starter\
  --build-arg SERVICE_VERSION=0-SNAPSHOT\
  -t eclipse/ditto-policies:0-SNAPSHOT\
  .

docker build --pull -f dockerfile-snapshot\
  --build-arg TARGET_DIR=gateway/starter/target\
  --build-arg SERVICE_STARTER=ditto-services-gateway-starter\
  --build-arg SERVICE_VERSION=0-SNAPSHOT\
  -t eclipse/ditto-policies:0-SNAPSHOT\
  .

docker build --pull -f dockerfile-snapshot\
  --build-arg TARGET_DIR=policies/starter/target\
  --build-arg SERVICE_STARTER=ditto-services-policies-starter\
  --build-arg SERVICE_VERSION=0-SNAPSHOT\
  -t eclipse/ditto-policies:0-SNAPSHOT\
  .

docker build --pull -f dockerfile-snapshot\
  --build-arg TARGET_DIR=things/starter/target\
  --build-arg SERVICE_STARTER=ditto-services-things-starter\
  --build-arg SERVICE_VERSION=0-SNAPSHOT\
  -t eclipse/ditto-policies:0-SNAPSHOT\
  .

docker build --pull -f dockerfile-snapshot\
  --build-arg TARGET_DIR=things-search/target\
  --build-arg SERVICE_STARTER=ditto-services-things-search\
  --build-arg SERVICE_VERSION=0-SNAPSHOT\
  -t eclipse/ditto-policies:0-SNAPSHOT\
  .

docker build --pull -f dockerfile-snapshot\
  --build-arg TARGET_DIR=connectivity/starter/target\
  --build-arg SERVICE_STARTER=ditto-services-connectivity-starter\
  --build-arg SERVICE_VERSION=0-SNAPSHOT\
  -t eclipse/ditto-policies:0-SNAPSHOT\
  .
