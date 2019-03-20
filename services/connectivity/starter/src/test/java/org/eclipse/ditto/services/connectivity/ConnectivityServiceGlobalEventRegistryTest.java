/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity;

import java.util.Arrays;

import org.eclipse.ditto.services.utils.test.GlobalEventRegistryTestCases;
import org.eclipse.ditto.signals.events.batch.BatchExecutionFinished;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.policies.ResourceDeleted;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;

public class ConnectivityServiceGlobalEventRegistryTest extends GlobalEventRegistryTestCases {

    public ConnectivityServiceGlobalEventRegistryTest() {
        super(Arrays.asList(
                BatchExecutionFinished.class,
                ConnectionCreated.class,
                ResourceDeleted.class,
                FeatureDeleted.class
        ));
    }
}
