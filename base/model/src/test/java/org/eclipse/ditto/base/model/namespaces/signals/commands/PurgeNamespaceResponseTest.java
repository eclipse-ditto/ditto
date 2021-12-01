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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PurgeNamespaceResponse}.
 */
public final class PurgeNamespaceResponseTest {

    private static final String NAMESPACE = "com.example.test";
    private static final String RESOURCE_TYPE = "policy";

    private DittoHeaders dittoHeaders;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PurgeNamespaceResponse.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PurgeNamespaceResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void successfulResponseToJson() {
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, PurgeNamespaceResponse.TYPE)
                .set(CommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
                .set(NamespaceCommandResponse.JsonFields.NAMESPACE, NAMESPACE)
                .set(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE, RESOURCE_TYPE)
                .build();
        final PurgeNamespaceResponse underTest =
                PurgeNamespaceResponse.successful(NAMESPACE, RESOURCE_TYPE, dittoHeaders);

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromJsonReturnsExpectedSuccessful() {
        final PurgeNamespaceResponse purgeNamespaceResponse =
                PurgeNamespaceResponse.successful(NAMESPACE, RESOURCE_TYPE, dittoHeaders);

        final PurgeNamespaceResponse responseFromJson = PurgeNamespaceResponse.fromJson(purgeNamespaceResponse.toJson(),
                purgeNamespaceResponse.getDittoHeaders());

        assertThat(responseFromJson).isEqualTo(purgeNamespaceResponse);
    }

    @Test
    public void failedResponseToJson() {
        final JsonObject jsonObject = JsonObject.newBuilder()
                .set(CommandResponse.JsonFields.TYPE, PurgeNamespaceResponse.TYPE)
                .set(CommandResponse.JsonFields.STATUS, HttpStatus.INTERNAL_SERVER_ERROR.getCode())
                .set(NamespaceCommandResponse.JsonFields.NAMESPACE, NAMESPACE)
                .set(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE, RESOURCE_TYPE)
                .build();
        final PurgeNamespaceResponse underTest = PurgeNamespaceResponse.failed(NAMESPACE, RESOURCE_TYPE, dittoHeaders);

        assertThat(underTest.toJson()).isEqualTo(jsonObject);
    }

    @Test
    public void fromJsonReturnsExpectedFailed() {
        final PurgeNamespaceResponse purgeNamespaceResponse =
                PurgeNamespaceResponse.failed(NAMESPACE, RESOURCE_TYPE, dittoHeaders);

        final PurgeNamespaceResponse responseFromJson = PurgeNamespaceResponse.fromJson(purgeNamespaceResponse.toJson(),
                purgeNamespaceResponse.getDittoHeaders());

        assertThat(responseFromJson).isEqualTo(purgeNamespaceResponse);
    }

    @Test
    public void toStringContainsExpected() {
        final PurgeNamespaceResponse underTest = PurgeNamespaceResponse.failed(NAMESPACE, RESOURCE_TYPE, dittoHeaders);

        assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains(NAMESPACE)
                .contains(RESOURCE_TYPE)
                .contains(String.valueOf(underTest.isSuccessful()));
    }

}
