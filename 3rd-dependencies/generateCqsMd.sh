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
echo "# 3rd party dependencies"
echo ""
echo "## Eclipse CQs - Compile"
echo ""
echo "| Group ID  | Artifact ID  | Version  | CQ  |"
echo "|---|---|---|---|---|"
cat compile.txt|cut -d':' -f1,2,4|sed -e 's/:/|/g'|while read i; do echo "|$i| []() |";done
echo ""
echo "## Works-With dependencies"
echo ""
echo "| Group ID  | Artifact ID  | Version  | CQ |"
echo "|---|---|---|---|"
cut provided.txt test.txt|cut -d':' -f1,2,4|sed -e 's/:/|/g'|while read i; do echo "|$i| []() |";done
