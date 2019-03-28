/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.starter;

import java.util.Arrays;

import org.eclipse.ditto.services.utils.test.GlobalEventRegistryTestCases;
import org.eclipse.ditto.signals.events.batch.BatchExecutionFinished;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.policies.ResourceDeleted;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;

public class ConciergeServiceGlobalEventRegistryTest extends GlobalEventRegistryTestCases {

    public ConciergeServiceGlobalEventRegistryTest() {
        super(Arrays.asList(
                BatchExecutionFinished.class,
                ConnectionCreated.class,
                ResourceDeleted.class,
                FeatureDeleted.class
        ));
    }
}
