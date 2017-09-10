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
package org.eclipse.ditto.services.models.things.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TakeSnapshotResponse}.
 */
public final class TakeSnapshotResponseTest {

    private static final Long SNAPSHOT_ID = 239858529L;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(CommandResponse.JsonFields.TYPE, TakeSnapshotResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt())
            .set(TakeSnapshotResponse.JSON_SNAPSHOT_REVISION, SNAPSHOT_ID)
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(TakeSnapshotResponse.class,
                areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TakeSnapshotResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test
    public void toJsonReturnsExpected() {
        final TakeSnapshotResponse underTest =
                TakeSnapshotResponse.of(SNAPSHOT_ID, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final TakeSnapshotResponse underTest =
                TakeSnapshotResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getSnapshotRevision()).isEqualTo(SNAPSHOT_ID);
    }

}
