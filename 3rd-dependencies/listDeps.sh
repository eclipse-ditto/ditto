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
mvn dependency:list -DexcludeGroupIds=org.eclipse.ditto,rubygems -Dsort=true -DoutputFile=dependencies.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:compile'|sort|uniq > 3rd-dependencies/compile.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:test'|sort|uniq > 3rd-dependencies/test.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:provided'|sort|uniq > 3rd-dependencies/provided.txt
find . -name dependencies.txt|while read i; do rm $i;done
cd 3rd-dependencies/
cat compile.txt|cut -d':' -f1-4|while read i; do grep -h $i provided.txt;done|sort|uniq|while read x; do sed -i.bak -e s/$x// provided.txt ;done
sed -i.bak '/^[[:space:]]*$/d' provided.txt
cat compile.txt provided.txt|cut -d':' -f1-4|while read i; do grep -h $i test.txt;done|sort|uniq|while read x; do sed -i.bak -e s/$x// test.txt ;done
sed -i.bak '/^[[:space:]]*$/d' test.txt
rm *.bak

