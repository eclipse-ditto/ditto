#!/bin/sh
#
# Copyright (c) 2021 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#
cd ../

mvn dependency:list -DincludeGroupIds=org.eclipse.ditto -Dsort=true -DoutputFile=dependencies.txt
find . -name dependencies.txt|while read i; do cat $i;done|grep 'org.eclipse.ditto:' |tr -d '[:blank:]'|sed -e 's/(optional)//' -e 's/:compile.*/:compile/'|awk -F ':' '{print $2}'|sort|uniq > bom/ditto-modules.txt

# Cleanup temp files
find . -name dependencies.txt|while read i; do rm $i;done

cd bom/
echo "Checking for missing dependencies from BOM. If no lines appear below, all dependencies were included. Missing from BOM are:"

while read p; do
  if grep -Fq "<artifactId>$p" pom.xml
  then
    # do nothing
    printf ""
  else
    echo "org.eclipse.ditto:$p is not present in pom!"
  fi
done <ditto-modules.txt

rm ditto-modules.txt
