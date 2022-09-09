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
package org.eclipse.ditto.internal.models.streaming;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;
import org.mutabilitydetector.unittesting.matchers.reasons.FieldAssumptions;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link BatchedEntityIdWithRevisions}.
 */
public final class BatchedEntityIdWithRevisionsTest {

    private static final Class<EntityIdWithRevision> KNOWN_CLASS = EntityIdWithRevision.class;
    private static final String KNOWN_TYPE = BatchedEntityIdWithRevisions.typeOf(KNOWN_CLASS);
    private static final List<EntityIdWithRevision> KNOWN_ELEMENTS = Collections.emptyList();

    private static final JsonArray KNOWN_ELEMENTS_JSON = JsonFactory.newArray();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(BatchedEntityIdWithRevisions.JSON_TYPE, KNOWN_TYPE)
            .set(BatchedEntityIdWithRevisions.JSON_ELEMENTS, KNOWN_ELEMENTS_JSON)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(BatchedEntityIdWithRevisions.class, areImmutable(),
                FieldAssumptions.named(Collections.singleton("elements"))
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(BatchedEntityIdWithRevisions.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final BatchedEntityIdWithRevisions underTest =
                BatchedEntityIdWithRevisions.of(KNOWN_CLASS, KNOWN_ELEMENTS);
        final JsonObject actualJson = underTest.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final Function<JsonObject, EntityIdWithRevision> deserializer = jsonObject -> {
            throw new IllegalStateException("should not be called");
        };
        final Jsonifiable<?> underTest = BatchedEntityIdWithRevisions.deserializer(deserializer).apply(KNOWN_JSON);

        final BatchedEntityIdWithRevisions expectedCommand =
                BatchedEntityIdWithRevisions.of(KNOWN_CLASS, KNOWN_ELEMENTS);

        assertThat(underTest).isNotNull();
        assertThat(underTest).isEqualTo(expectedCommand);
    }

}
