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
package org.eclipse.ditto.signals.commands.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.junit.Before;
import org.junit.Test;

public class GlobalCommandResponseRegistryTest {

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
