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
package org.eclipse.ditto.signals.events.base.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;
import org.junit.Before;
import org.junit.Test;

public class GlobalEventRegistryTest {

    private GlobalEventRegistry underTest;
    private DittoHeaders headers;
    private String testType;

    @Before
    public void setup() {
        underTest = GlobalEventRegistry.getInstance();
        headers = DittoHeaders.empty();
        testType = TestEvent.TYPE;
    }
    @Test
    public void assertImmutability() {
        assertInstancesOf(GlobalEventRegistry.class, areImmutable());
    }

    @Test
    public void globalEventRegistryKnowsJsonTypeTestEvent() {
        assertThat(underTest.getTypes()).contains(testType);
    }

    @Test
    public void globalEventRegistryParsesTestJsonObject() {

        final JsonObject testObject = JsonObject.newBuilder()
                .set("type", testType)
                .build();

        final Event parsedEvent = underTest.parse(testObject, headers);

        assertThat(parsedEvent).isExactlyInstanceOf(TestEvent.class);
        assertThat(parsedEvent.getType()).isEqualTo(testType);
    }

    @Test
    public void globalEventRegistryWrapsExceptionInDittoJsonException() {
        final JsonObject testObject = JsonObject.newBuilder().build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> underTest.parse(testObject, headers));
    }

    @Test
    public void globalEventRegistryThrowsJsonTypeNotParsableException() {
        final String type = "dfg";
        final JsonObject testObject = JsonObject.newBuilder()
                .set("type", type)
                .build();

        assertThatExceptionOfType(JsonTypeNotParsableException.class)
                .isThrownBy(() -> underTest.parse(testObject, headers));
    }
}
