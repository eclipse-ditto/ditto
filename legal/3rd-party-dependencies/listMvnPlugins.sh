#!/bin/sh
# Copyright (c) 2017 Bosch Software Innovations GmbH.
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/org/documents/epl-2.0/index.php
#
# Contributors:
#    Bosch Software Innovations GmbH - initial contribution
cd ..
mvn dependency:list dependency:resolve-plugins -DoutputFile=plugins.txt
find . -name plugins.txt|while read i; do cat $i;done|grep '.*:.*:runtime$'|sort|uniq > 3rd-dependencies/maven-plugins.txt
find . -name plugins.txt|while read i; do rm $i;done
cd 3rd-dependencies/

