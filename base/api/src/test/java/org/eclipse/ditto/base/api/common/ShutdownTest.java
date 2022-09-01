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
package org.eclipse.ditto.base.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Shutdown}.
 */
public final class ShutdownTest {

    private static ShutdownReason knownShutdownReason;
    private static DittoHeaders dittoHeaders;
    private static JsonObject knownJsonRepresentation;

    private Shutdown underTest;

    @BeforeClass
    public static void initTestConstants() {
        knownShutdownReason = ShutdownReasonFactory.getPurgeNamespaceReason("com.example.test");
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(String.valueOf(UUID.randomUUID()))
                .build();
        knownJsonRepresentation = JsonFactory.newObjectBuilder()
                .set(Shutdown.JsonFields.TYPE, Shutdown.TYPE)
                .set(Shutdown.JsonFields.REASON, knownShutdownReason.toJson())
                .build();
    }

    @Before
    public void setUp() {
        underTest = Shutdown.getInstance(knownShutdownReason, dittoHeaders);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(Shutdown.class, areImmutable(), provided(ShutdownReason.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Shutdown.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullReason() {
        assertThatNullPointerException()
                .isThrownBy(() -> Shutdown.getInstance(null, dittoHeaders))
                .withMessageContaining("ShutdownReason")
                .withMessageContaining("must not be null")
                .withNoCause();
    }

    @Test
    public void getReasonReturnsExpected() {
        assertThat(underTest.getReason()).isEqualTo(knownShutdownReason);
    }

    @Test
    public void getCategoryReturnsModify() {
        assertThat(underTest.getCategory()).isEqualTo(Command.Category.MODIFY);
    }

    @Test
    public void getTypeReturnsExpected() {
        assertThat(underTest.getType()).isEqualTo(Shutdown.TYPE);
    }

    @Test
    public void getDittoHeadersReturnsExpected() {
        assertThat(underTest.getDittoHeaders()).isEqualTo(dittoHeaders);
    }

    @Test
    public void getResourceTypeReturnsCommonCommandResourceType() {
        assertThat(underTest.getResourceType()).isEqualTo(CommonCommand.RESOURCE_TYPE);
    }

    @Test
    public void fromJsonWorksAsExpected() {
        assertThat(Shutdown.fromJson(knownJsonRepresentation, dittoHeaders)).isEqualTo(underTest);
    }

    @Test
    public void fromJsonWithoutReason() {
        final JsonObject knownJsonWithoutReason = knownJsonRepresentation.toBuilder()
                .remove(Shutdown.JsonFields.REASON)
                .build();

        final Shutdown shutdown = Shutdown.fromJson(knownJsonWithoutReason, dittoHeaders);
        assertThat(shutdown.getReason()).isEqualTo(ShutdownNoReason.INSTANCE);
    }

    @Test
    public void toJsonWithoutSchemaVersionAndPredicateReturnsExpected() {
        assertThat(underTest.toJson()).isEqualTo(knownJsonRepresentation);
    }

    @Test
    public void toJsonWithoutReason() {
        final Shutdown shutdown = Shutdown.getInstance(ShutdownNoReason.INSTANCE, dittoHeaders);
        final JsonObject expectedJson = knownJsonRepresentation.toBuilder()
                .remove(Shutdown.JsonFields.REASON)
                .build();

        assertThat(shutdown.toJson()).isEqualTo(expectedJson);
    }

    @Test
    public void toStringContainsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains(underTest.getType())
                .contains(underTest.getDittoHeaders().toString())
                .contains(underTest.getCategory().toString())
                .contains(knownShutdownReason.toString());
    }

}
