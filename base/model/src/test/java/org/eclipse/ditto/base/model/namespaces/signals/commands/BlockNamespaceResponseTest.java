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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link BlockNamespaceResponse}.
 */
public final class BlockNamespaceResponseTest {

    private static final String NAMESPACE = "com.example.test";
    private static final String RESOURCE_TYPE = "policy";

    private static JsonObject knownJsonRepresentation;
    private static DittoHeaders dittoHeaders;

    @BeforeClass
    public static void initTestConstants() {
        knownJsonRepresentation = JsonFactory.newObjectBuilder()
                .set(NamespaceCommandResponse.JsonFields.TYPE, BlockNamespaceResponse.TYPE)
                .set(NamespaceCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
                .set(NamespaceCommandResponse.JsonFields.NAMESPACE, NAMESPACE)
                .set(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE, RESOURCE_TYPE)
                .build();

        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(String.valueOf(UUID.randomUUID()))
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(BlockNamespaceResponse.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(BlockNamespaceResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final BlockNamespaceResponse responseFromJson =
                BlockNamespaceResponse.fromJson(knownJsonRepresentation, dittoHeaders);

        assertThat(responseFromJson)
                .isEqualTo(BlockNamespaceResponse.getInstance(NAMESPACE, RESOURCE_TYPE, dittoHeaders));
    }

    @Test
    public void toJsonReturnsExpected() {
        final BlockNamespaceResponse underTest =
                BlockNamespaceResponse.getInstance(NAMESPACE, RESOURCE_TYPE, dittoHeaders);

        assertThat(underTest.toJson()).isEqualTo(knownJsonRepresentation);
    }

    @Test
    public void toStringContainsExpected() {
        final BlockNamespaceResponse underTest =
                BlockNamespaceResponse.getInstance(NAMESPACE, RESOURCE_TYPE, dittoHeaders);

        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains(NAMESPACE)
                .contains(RESOURCE_TYPE);
    }

}
