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
package org.eclipse.ditto.services.models.connectivity;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.models.connectivity.ConnectionTag}.
 */
public final class ConnectionTagTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.of("connection-tag-test-connection-id");

    private static final long REVISION = 5624191235L;

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ConnectionTag.JsonFields.ID, CONNECTION_ID.toString())
            .set(ConnectionTag.JsonFields.REVISION, REVISION)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionTag.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ConnectionTag.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ConnectionTag underTest = ConnectionTag.of(CONNECTION_ID, REVISION);
        final JsonValue jsonValue = underTest.toJson();

        assertThat(jsonValue).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ConnectionTag underTest = ConnectionTag.fromJson(KNOWN_JSON);

        Assertions.assertThat(underTest).isNotNull();
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(CONNECTION_ID);
        Assertions.assertThat(underTest.getRevision()).isEqualTo(REVISION);
    }

}
