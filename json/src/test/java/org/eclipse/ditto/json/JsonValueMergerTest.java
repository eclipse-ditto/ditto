/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.json;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.json.JsonValueMerger}.
 */
public class JsonValueMergerTest {

    private static JsonValue merge(final JsonValue value1, final JsonValue value2) {
        return JsonValueMerger.mergeJsonValues(value1, value2);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonValueMerger.class, areImmutable());
    }

    @Test
    public void mergeFieldsFromBothObjectsFilterNullValues() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("title", "Goodbye!")
                .set("author", JsonFactory.newObjectBuilder()
                        .set("givenName", "John")
                        .set("familyName", "Doe")
                        .build())
                .set("tags", JsonFactory.newArray("[\"example\",\"sample\"]"))
                .set("content", "This will be unchanged")
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("title", "Hello!")
                .set("phoneNumber", "+01-123-456-7890")
                .set("author", JsonFactory.newObjectBuilder()
                        .set("givenName", "John")
                        .set("familyName", JsonValue.nullLiteral())
                        .build())
                .set("tags", JsonFactory.newArray("[\"example\"]"))
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("title", "Hello!")
                .set("author", JsonFactory.newObjectBuilder()
                        .set("givenName", "John")
                        .build())
                .set("tags", JsonFactory.newArray("[\"example\"]"))
                .set("content", "This will be unchanged")
                .set("phoneNumber", "+01-123-456-7890")
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    // Test Cases conform to RFC 7396
    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase1() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "b")
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", "c")
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("a", "c")
                .build();

        final JsonValue mergedObject =
                merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase2() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "b").build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("b", "c").build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("a", "b")
                .set("b", "c")
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase3() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "b")
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", JsonValue.nullLiteral())
                .build();

        final JsonObject expectedObject = JsonObject.empty();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase4() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "b")
                .set("b", "c")
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", JsonValue.nullLiteral())
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("b", "c")
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase5() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArray("[\"b\"]"))
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", "c")
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("a", "c")
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase6() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "c")
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArray("[\"b\"]"))
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArray("[\"b\"]"))
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase7() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("b", "c")
                        .build())
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("b", "d")
                        .set("c", JsonValue.nullLiteral())
                        .build())
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("b", "d")
                        .build())
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase8() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("b", "c")
                        .build())
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArray("[1]"))
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArray("[1]"))
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase9() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "foo")
                .build();

        final JsonValue objectToPatch = JsonValue.nullLiteral();

        final JsonValue expectedObject = JsonValue.nullLiteral();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase10() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "foo")
                .build();

        final JsonValue objectToPatch = JsonFactory.newValue("bar");

        final JsonValue expectedObject = JsonFactory.newValue("bar");

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase11() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("e", JsonValue.nullLiteral())
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", 1)
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("e", JsonValue.nullLiteral())
                .set("a", 1)
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase12() {
        final JsonArray originalObject = JsonFactory.newArray("[1,2]");

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", "b")
                .set("c", JsonValue.nullLiteral())
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder()
                .set("a", "b")
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase13() {
        final JsonObject originalObject = JsonObject.empty();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("bb", JsonFactory.newObjectBuilder()
                                .set("ccc", JsonValue.nullLiteral())
                                .build())
                        .build())
                .build();

        final JsonValue expectedObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("bb", JsonObject.empty())
                        .build())
                .build();

        final JsonValue mergedObject = merge(objectToPatch, originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }
}
