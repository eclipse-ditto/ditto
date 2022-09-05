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
package org.eclipse.ditto.base.model.signals.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.JsonTypeNotParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link GlobalCommandRegistry}.
 */
public final class GlobalCommandRegistryTest {

    private GlobalCommandRegistry underTest;
    private DittoHeaders headers;

    @Before
    public void setup() {
        underTest = GlobalCommandRegistry.getInstance();
        headers = DittoHeaders.empty();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(GlobalCommandRegistry.class, areImmutable());
    }

    @Test
    public void globalCommandRegistryKnowsJsonTypeTestCommand() {
        assertThat(underTest.getTypes()).contains(TestCommand.TYPE);
    }

    @Test
    public void globalCommandRegistryParsesTestJsonObject() {
        final JsonObject testObject = JsonObject.newBuilder()
                .set("type", TestCommand.TYPE)
                .build();

        final Command parsedCommand = underTest.parse(testObject, headers);

        assertThat(parsedCommand).isExactlyInstanceOf(TestCommand.class);
        assertThat(parsedCommand.getType()).isEqualTo(TestCommand.TYPE);
    }

    @Test
    public void globalCommandRegistryWrapsExceptionInDittoJsonException() {
        final JsonObject testObject = JsonObject.newBuilder().build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> underTest.parse(testObject, headers));
    }

    @Test
    public void globalCommandRegistryThrowsJsonTypeNotParsableException() {
        final String type = "dfg";
        final JsonObject testObject = JsonObject.newBuilder()
                .set("type", type)
                .build();

        assertThatExceptionOfType(JsonTypeNotParsableException.class)
                .isThrownBy(() -> underTest.parse(testObject, headers));
    }

}
