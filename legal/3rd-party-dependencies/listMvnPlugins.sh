#!/bin/sh
# Copyright (c) 2017 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
cd ../../
mvn dependency:list dependency:resolve-plugins -DoutputFile=plugins.txt
find . -name plugins.txt|while read i; do cat $i;done|grep '.*:.*:runtime$'| tr -d '[:blank:]'|sort|uniq > legal/3rd-party-dependencies/maven-plugins.txt
find . -name plugins.txt|while read i; do rm $i;done
cd legal/3rd-party-dependencies/

