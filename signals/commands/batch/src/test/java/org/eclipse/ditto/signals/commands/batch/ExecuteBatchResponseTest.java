/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ExecuteBatchResponse}.
 */
public final class ExecuteBatchResponseTest {

    private static final String KNOWN_BATCH_ID = UUID.randomUUID().toString();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(CommandResponse.JsonFields.TYPE, ExecuteBatchResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(BatchCommand.JsonFields.BATCH_ID, KNOWN_BATCH_ID)
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ExecuteBatchResponse.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ExecuteBatchResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullBatchId() {
        ExecuteBatchResponse.of(null, DittoHeaders.empty());
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDittoHeaders() {
        ExecuteBatchResponse.of(KNOWN_BATCH_ID, null);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ExecuteBatchResponse underTest = ExecuteBatchResponse.of(KNOWN_BATCH_ID, DittoHeaders.empty());
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ExecuteBatchResponse underTest =
                ExecuteBatchResponse.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty());

        assertThat(underTest).isNotNull();
    }

    @Test
    public void retrieveCommandResponseName() {
        final String name =
                ExecuteBatchResponse.fromJson(KNOWN_JSON.toString(), DittoHeaders.empty()).getName();
        assertThat(name).isEqualTo(ExecuteBatch.NAME);
    }

}
