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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.ImmutableEffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import akka.event.LoggingAdapter;

@RunWith(Parameterized.class)
public abstract class AbstractMongoEventToPersistenceStrategyTest {

    @Parameterized.Parameter
    public JsonSchemaVersion version;

    PolicyEnforcer policyEnforcer;

    IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;

    @Parameterized.Parameters(name = "v{0}")
    public static List<JsonSchemaVersion> versions() {
        return Arrays.asList(JsonSchemaVersion.values());
    }

    @Before
    public void setUpMocks() {
        policyEnforcer = Mockito.mock(PolicyEnforcer.class);
        indexLengthRestrictionEnforcer = IndexLengthRestrictionEnforcer.newInstance(Mockito.mock(LoggingAdapter.class));

        final EffectedSubjectIds effectedSubjectIds = ImmutableEffectedSubjectIds.of(Collections.emptyList(),
                Collections.emptyList());
        when(policyEnforcer.getSubjectIdsWithPermission(any(ResourceKey.class), anyString(), any()))
                .thenReturn(effectedSubjectIds);
    }


    // TODO: check the actual policy-updates, *not* the expected count only
    void verifyPolicyUpdatesForSchemaVersion(final List<PolicyUpdate> updates, final int expectedForV2) {
        if (isV1()) {
            assertThat(updates).isEmpty();
        } else {
            assertThat(updates).hasSize(expectedForV2);
        }
    }

    private boolean isV1() {
        return version.equals(JsonSchemaVersion.V_1);
    }

    <T extends ThingEvent> T setVersion(final T thingEvent) {
        final DittoHeaders versionedHeader =
                thingEvent.getDittoHeaders().toBuilder().schemaVersion(version).build();
        @SuppressWarnings("unchecked")
        final T versionedEvent = (T) thingEvent.setDittoHeaders(versionedHeader);
        return versionedEvent;
    }

}
