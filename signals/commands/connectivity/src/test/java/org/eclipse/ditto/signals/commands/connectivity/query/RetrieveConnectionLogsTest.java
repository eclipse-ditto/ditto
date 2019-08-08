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

package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs}.
 */
public class RetrieveConnectionLogsTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Command.JsonFields.TYPE, RetrieveConnectionLogs.TYPE)
            .set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, TestConstants.ID.toString())
            .build();

    @Test
    public void fromJsonReturnsExpected() {
        final RetrieveConnectionLogs expected = RetrieveConnectionLogs.of(TestConstants.ID, DittoHeaders.empty());

        final RetrieveConnectionLogs actual = RetrieveConnectionLogs.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = RetrieveConnectionLogs.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void testEqualsAndHashCode() {
        EqualsVerifier.forClass(RetrieveConnectionLogs.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(RetrieveConnectionLogs.class,
                areImmutable(),
                provided(EntityId.class).isAlsoImmutable());
    }

}