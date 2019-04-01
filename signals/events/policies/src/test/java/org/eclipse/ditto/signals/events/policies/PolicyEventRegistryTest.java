/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.events.policies;

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

/**
 * Unit test for {@link PolicyEventRegistry}.
 */
public class PolicyEventRegistryTest {


    @Test
    public void parsePolicyEvent() {
        final PolicyEventRegistry eventRegistry = PolicyEventRegistry.newInstance();

        final PolicyCreated event = PolicyCreated.of(TestConstants.Policy.POLICY, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = event.toJson(FieldType.regularOrSpecial());

        final Event parsedEvent = eventRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedEvent).isEqualTo(event);
    }

}
