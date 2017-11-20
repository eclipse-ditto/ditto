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
package org.eclipse.ditto.services.thingsearch.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Test for ProcessableThingEvent.
 */
public final class ProcessableThingEventTest {

    @Test(expected = NullPointerException.class)
    public void newInstanceWithNullVersion() throws Exception {
        ProcessableThingEvent.newInstance(TestConstants.ThingEvent.THING_MODIFIED, null);
    }

    @Test(expected = NullPointerException.class)
    public void newInstanceWithNullEvent() throws Exception {
        ProcessableThingEvent.newInstance(null, JsonSchemaVersion.LATEST);
    }

    @Test
    public void newInstance() throws Exception {
        final ThingEvent thingEvent = TestConstants.ThingEvent.THING_MODIFIED;
        final JsonSchemaVersion schemaVersion = JsonSchemaVersion.V_2;
        final ProcessableThingEvent processableThingEvent = ProcessableThingEvent.newInstance(thingEvent,
                schemaVersion);

        assertThat(processableThingEvent)
                .isNotNull();
        assertThat(processableThingEvent.getThingEvent())
                .isEqualTo(thingEvent);
        assertThat(processableThingEvent.getJsonSchemaVersion().toInt())
                .isEqualTo(schemaVersion.toInt());
    }

    @Test
    public void testEqualsAndHashCode() throws Exception {
        EqualsVerifier.forClass(ProcessableThingEvent.class).verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ProcessableThingEvent.class,
                areImmutable(),
                provided(JsonSchemaVersion.class).areAlsoImmutable(),
                /* just declaring ThingEvent as immutable does not work,
                   see https://github.com/MutabilityDetector/MutabilityDetector/issues/104 */
                assumingFields("thingEvent").areNotModifiedAndDoNotEscape());
    }
}