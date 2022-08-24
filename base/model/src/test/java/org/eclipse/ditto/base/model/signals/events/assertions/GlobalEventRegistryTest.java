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
package org.eclipse.ditto.base.model.signals.events.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.JsonTypeNotParsableException;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

public class GlobalEventRegistryTest {

    private GlobalEventRegistry<?> underTest;
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

        final Event<?> parsedEvent = underTest.parse(testObject, headers);

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
