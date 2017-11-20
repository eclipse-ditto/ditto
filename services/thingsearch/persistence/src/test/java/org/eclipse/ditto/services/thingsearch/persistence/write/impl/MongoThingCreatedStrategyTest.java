/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.thingsearch.persistence.ProcessableThingEvent;
import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

public final class MongoThingCreatedStrategyTest extends AbstractMongoEventToPersistenceStrategyTest {

    private final MongoThingCreatedStrategy strategy = new MongoThingCreatedStrategy();

    @Test
    public void thingUpdates() throws Exception {
        final List<Bson> updates = strategy.thingUpdates(thingEvent(), indexLengthRestrictionEnforcer);
        assertThat(updates).hasSize(1);
        verify(indexLengthRestrictionEnforcer).enforceRestrictions(thingEvent().getThingEvent().getThing());
    }

    @Test
    public void policyUpdates() {
        final List<PolicyUpdate> updates = strategy.policyUpdates(thingEvent(), policyEnforcer);
        verifyPolicyUpdatesForSchemaVersion(updates, 1);
        verifyPermissionCallForSchemaVersion(() -> any(ResourceKey.class), ArgumentMatchers::anyString,
                expectedPolicyUpdate());
        ;
    }

    private int expectedPolicyUpdate() {
        return 1
                + thingEvent().getThingEvent().getThing().getFeatures().get().getSize()
                + thingEvent().getThingEvent().getThing().getFeatures().get().stream().map(ft -> ft
                .getProperties().get().getSize()).reduce(0, (a, b) -> a + b)
                + thingEvent().getThingEvent().getThing().getAttributes().get().getSize();
    }


    private ProcessableThingEvent<ThingCreated> thingEvent() {
        if (JsonSchemaVersion.V_1.equals(version)) {
            return ProcessableThingEvent.newInstance(TestConstants.ThingEvent.THING_CREATED_V1, version);
        }
        return ProcessableThingEvent.newInstance(TestConstants.ThingEvent.THING_CREATED, version);
    }

}