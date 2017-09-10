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
package org.eclipse.ditto.signals.events.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

/**
 * Unit test for {@link ThingEventRegistry}.
 */
public class ThingEventRegistryTest {


    @Test
    public void parseThingEvent() {
        final ThingEventRegistry eventRegistry = ThingEventRegistry.newInstance();

        final ThingCreated event =
                ThingCreated.of(TestConstants.Thing.THING, TestConstants.Thing.REVISION_NUMBER,
                        TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = event.toJson(FieldType.regularOrSpecial());

        final Event parsedEvent = eventRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedEvent).isEqualTo(event);
    }

}
