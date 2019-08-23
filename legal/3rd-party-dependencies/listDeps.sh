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
mvn dependency:list -DexcludeGroupIds=org.eclipse.ditto,rubygems -Dsort=true -DoutputFile=dependencies.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:compile'|sort|uniq > legal/3rd-party-dependencies//compile.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:runtime'|sort|uniq > legal/3rd-party-dependencies//runtime.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:test'|sort|uniq > legal/3rd-party-dependencies//test.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:provided'|sort|uniq > legal/3rd-party-dependencies//provided.txt
find . -name dependencies.txt|while read i; do rm $i;done
cd legal/3rd-party-dependencies/
cat compile.txt|cut -d':' -f1-4|while read i; do grep -h $i provided.txt;done|sort|uniq|while read x; do sed -i.bak -e s/$x// provided.txt ;done
sed -i.bak '/^[[:space:]]*$/d' provided.txt
cat compile.txt provided.txt|cut -d':' -f1-4|while read i; do grep -h $i test.txt;done|sort|uniq|while read x; do sed -i.bak -e s/$x// test.txt ;done
sed -i.bak '/^[[:space:]]*$/d' test.txt
rm *.bak

