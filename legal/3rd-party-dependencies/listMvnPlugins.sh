#!/bin/sh
# Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/org/documents/epl-2.0/index.php
#
# SPDX-License-Identifier: EPL-2.0
cd ../../
mvn dependency:list dependency:resolve-plugins -DoutputFile=plugins.txt
find . -name plugins.txt|while read i; do cat $i;done|grep '.*:.*:runtime$'|sort|uniq > legal/3rd-party-dependencies/maven-plugins.txt
find . -name plugins.txt|while read i; do rm $i;done
cd legal/3rd-party-dependencies/

