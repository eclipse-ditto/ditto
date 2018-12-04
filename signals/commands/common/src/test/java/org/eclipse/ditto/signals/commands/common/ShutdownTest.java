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
package org.eclipse.ditto.signals.commands.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.signals.commands.common.Shutdown}.
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
    public void getIdReturnsEmptyString() {
        assertThat(underTest.getId()).isEmpty();
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
    public void toJsonWithoutSchemaVersionAndPredicateReturnsExpected() {
        assertThat(underTest.toJson()).isEqualTo(knownJsonRepresentation);
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