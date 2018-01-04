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
package org.eclipse.ditto.services.models.streaming;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions.JSON_TYPE;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
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
            .set(JSON_TYPE, KNOWN_TYPE)
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
