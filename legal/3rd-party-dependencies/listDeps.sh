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
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:compile'| tr -d '[:blank:]'| sed -e 's/(optional)//' -e 's/:compile.*/:compile/'|sort|uniq > legal/3rd-party-dependencies//compile.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:runtime'| tr -d '[:blank:]'| sed -e 's/(optional)//' -e 's/:runtime.*/:runtime/'|sort|uniq > legal/3rd-party-dependencies//runtime.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:test'| tr -d '[:blank:]'| sed -e 's/(optional)//' -e 's/:test.*/:test/'|sort|uniq > legal/3rd-party-dependencies//test.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep '.*:.*:provided'| tr -d '[:blank:]'| sed -e 's/(optional)//' -e 's/:provided.*/:provided/'|sort|uniq > legal/3rd-party-dependencies//provided.txt

# Cleanup temp files
find . -name dependencies.txt|while read i; do rm $i;done

cd legal/3rd-party-dependencies/

# exclude compile dependencies from provided.txt + sort + remove duplicates
cat compile.txt runtime.txt|cut -d':' -f1-4|while read i; do grep -h $i provided.txt;done|sort|uniq|while read x; do sed -i.bak -e s/$x// provided.txt ;done
sed -i.bak '/^[[:space:]]*$/d' provided.txt
# exclude compile+provided dependencies from test.txt + sort + remove duplicates
cat compile.txt provided.txt runtime.txt|cut -d':' -f1-4|while read i; do grep -h $i test.txt;done|sort|uniq|while read x; do sed -i.bak -e s/$x// test.txt ;done
sed -i.bak '/^[[:space:]]*$/d' test.txt
rm *.bak

