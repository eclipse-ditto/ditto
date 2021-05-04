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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.JsonTypeNotParsableException;
import org.junit.Before;
import org.junit.Test;

public final class GlobalCommandResponseRegistryTest {

    private GlobalCommandResponseRegistry underTest;
    private DittoHeaders headers;

    @Before
    public void setup() {
        underTest = GlobalCommandResponseRegistry.getInstance();
        headers = DittoHeaders.empty();
    }

    @Test
    public void globalCommandRegistryKnowsJsonTypeTestCommand() {
        assertThat(underTest.getTypes()).contains(TestCommandResponse.TYPE);
    }

    @Test
    public void globalCommandRegistryParsesTestJsonObject() {
        final JsonObject testObject = JsonObject.newBuilder()
                .set("type", TestCommandResponse.TYPE)
                .build();

        final CommandResponse parsedCommand = underTest.parse(testObject, headers);

        assertThat(parsedCommand).isExactlyInstanceOf(TestCommandResponse.class);
        assertThat(parsedCommand.getType()).isEqualTo(TestCommandResponse.TYPE);
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
