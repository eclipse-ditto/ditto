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
package org.eclipse.ditto.things.service.starter;

import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionComplete;
import org.eclipse.ditto.internal.utils.persistentactors.EmptyEvent;
import org.eclipse.ditto.internal.utils.test.GlobalEventRegistryTestCases;
import org.eclipse.ditto.policies.model.signals.events.PolicyModified;
import org.eclipse.ditto.things.api.ThingSnapshotTaken;
import org.eclipse.ditto.things.model.signals.events.FeatureDeleted;
import org.eclipse.ditto.thingsearch.api.events.ThingsOutOfSync;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;

public final class ThingsServiceGlobalEventRegistryTest extends GlobalEventRegistryTestCases {

    public ThingsServiceGlobalEventRegistryTest() {
        super(
                FeatureDeleted.class,
                ThingSnapshotTaken.class,
                EmptyEvent.class,
                PolicyModified.class,
                StreamingSubscriptionComplete.class,
                PolicyModified.class,
                SubscriptionCreated.class,
                ThingsOutOfSync.class
        );
    }

}
