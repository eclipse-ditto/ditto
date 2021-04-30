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
package org.eclipse.ditto.concierge.service.starter;

import org.eclipse.ditto.things.api.ThingSnapshotTaken;
import org.eclipse.ditto.internal.utils.test.GlobalEventRegistryTestCases;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.policies.model.signals.events.ResourceDeleted;
import org.eclipse.ditto.things.model.signals.events.FeatureDeleted;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;

public final class ConciergeServiceGlobalEventRegistryTest extends GlobalEventRegistryTestCases {

    public ConciergeServiceGlobalEventRegistryTest() {
        super(
                ConnectionCreated.class,
                ResourceDeleted.class,
                SubscriptionComplete.class,
                FeatureDeleted.class,
                ThingSnapshotTaken.class
        );
    }

}
