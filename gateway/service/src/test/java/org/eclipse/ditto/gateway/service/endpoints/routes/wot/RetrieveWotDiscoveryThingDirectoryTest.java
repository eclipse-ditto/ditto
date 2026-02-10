/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.wot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveWotDiscoveryThingDirectory}.
 */
public final class RetrieveWotDiscoveryThingDirectoryTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("test-correlation-id")
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveWotDiscoveryThingDirectory.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullHeadersThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveWotDiscoveryThingDirectory.of(null));
    }

    @Test
    public void ofCreatesExpectedCommand() {
        final RetrieveWotDiscoveryThingDirectory command = RetrieveWotDiscoveryThingDirectory.of(DITTO_HEADERS);

        assertThat(command.getDittoHeaders()).isEqualTo(DITTO_HEADERS);
        assertThat(command.getType()).isEqualTo(RetrieveWotDiscoveryThingDirectory.TYPE);
        assertThat(command.getCategory()).isEqualTo(Command.Category.QUERY);
    }

    @Test
    public void typeHasExpectedFormat() {
        assertThat(RetrieveWotDiscoveryThingDirectory.TYPE)
                .contains("retrieveWotDiscoveryThingDirectory")
                .startsWith("common.commands:");
    }

    @Test
    public void fromJsonToJson() {
        final RetrieveWotDiscoveryThingDirectory original = RetrieveWotDiscoveryThingDirectory.of(DITTO_HEADERS);
        final JsonObject serialized = original.toJson();
        final RetrieveWotDiscoveryThingDirectory deserialized =
                RetrieveWotDiscoveryThingDirectory.fromJson(serialized, DITTO_HEADERS);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    public void setDittoHeadersReturnsNewInstance() {
        final RetrieveWotDiscoveryThingDirectory original = RetrieveWotDiscoveryThingDirectory.of(DITTO_HEADERS);
        final DittoHeaders newHeaders = DittoHeaders.newBuilder()
                .correlationId("new-correlation-id")
                .build();

        final RetrieveWotDiscoveryThingDirectory withNewHeaders = original.setDittoHeaders(newHeaders);

        assertThat(withNewHeaders).isNotSameAs(original);
        assertThat(withNewHeaders.getDittoHeaders()).isEqualTo(newHeaders);
    }

    @Test
    public void toStringContainsExpectedInformation() {
        final RetrieveWotDiscoveryThingDirectory command = RetrieveWotDiscoveryThingDirectory.of(DITTO_HEADERS);

        assertThat(command.toString())
                .contains(RetrieveWotDiscoveryThingDirectory.class.getSimpleName())
                .contains(RetrieveWotDiscoveryThingDirectory.TYPE);
    }

}
