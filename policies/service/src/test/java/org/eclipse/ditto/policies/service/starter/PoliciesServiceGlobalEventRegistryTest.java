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
package org.eclipse.ditto.policies.service.starter;

import org.eclipse.ditto.connectivity.model.signals.events.ConnectionDeleted;
import org.eclipse.ditto.internal.utils.persistentactors.EmptyEvent;
import org.eclipse.ditto.internal.utils.test.GlobalEventRegistryTestCases;
import org.eclipse.ditto.policies.model.signals.events.ResourceDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;

public final class PoliciesServiceGlobalEventRegistryTest extends GlobalEventRegistryTestCases {

    public PoliciesServiceGlobalEventRegistryTest() {
        super(ResourceDeleted.class,

                // added due to ditto-model-placeholders
                ThingDeleted.class,
                EmptyEvent.class,

                ConnectionDeleted.class,
                SubscriptionComplete.class
        );
    }

}
