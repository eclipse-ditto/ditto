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
package org.eclipse.ditto.base.model.namespaces.signals.commands;

import static org.eclipse.ditto.base.model.signals.commands.assertions.CommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link BlockNamespace}.
 */
public final class BlockNamespaceTest {

    private static final String NAMESPACE = "com.example.test";

    private static JsonObject knownJsonRepresentation;
    private static DittoHeaders dittoHeaders;

    private BlockNamespace underTest;

    @BeforeClass
    public static void initTestConstants() {
        knownJsonRepresentation = JsonFactory.newObjectBuilder()
                .set(NamespaceCommand.JsonFields.TYPE, BlockNamespace.TYPE)
                .set(NamespaceCommand.JsonFields.NAMESPACE, NAMESPACE)
                .build();

        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(String.valueOf(UUID.randomUUID()))
                .build();
    }

    @Before
    public void setUp() {
        underTest = BlockNamespace.of(NAMESPACE, dittoHeaders);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(BlockNamespace.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(BlockNamespace.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final BlockNamespace commandFromJson = BlockNamespace.fromJson(knownJsonRepresentation, dittoHeaders);

        Assertions.assertThat(commandFromJson).isEqualTo(underTest);
    }

    @Test
    public void toJsonReturnsExpected() {
        assertThat(underTest.toJson()).isEqualTo(knownJsonRepresentation);
    }

    @Test
    public void toStringContainsExpected() {
        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains(NAMESPACE);
    }

}
