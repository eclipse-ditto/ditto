/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public final class JsonMergePatchTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonMergePatch.class,
                areImmutable(),
                provided(JsonValue.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(JsonMergePatch.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void computesDiffForSingleValue() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .build();

        final JsonMergePatch expected = JsonMergePatch.of(newValue);
        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
        assertThat(expected.applyOn(oldValue)).isEqualTo(newValue);
    }

    @Test
    public void computesDiffWhenNewValueIsNotAnObject() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .build();

        final JsonValue newValue = JsonValue.of("bumlux");

        final JsonMergePatch expected = JsonMergePatch.of(newValue);
        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
        assertThat(expected.applyOn(oldValue)).isEqualTo(newValue);
    }

    @Test
    public void computesDiffWhenOldValueIsNotAnObject() {
        final JsonValue oldValue = JsonValue.of("bumlux");

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .build();

        final JsonMergePatch expected = JsonMergePatch.of(newValue);
        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
        assertThat(expected.applyOn(oldValue)).isEqualTo(newValue);
    }

    @Test
    public void computesDiffWhenFieldWasDeleted() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .set("Bum", "Lux")
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .build();

        final JsonMergePatch expected = JsonMergePatch.of(newValue.setValue("Bum", JsonValue.nullLiteral()));
        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
        assertThat(expected.applyOn(oldValue)).isEqualTo(newValue);
    }

    @Test
    public void computesDiffForSingleValueOutOfMultipleValues() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .set("Bum", "Lux")
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .set("Bum", "Lux")
                .build();

        final JsonMergePatch expected = JsonMergePatch.of(JsonObject.newBuilder()
                .set("Test", "Bar")
                .build());

        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
        assertThat(expected.applyOn(oldValue)).isEqualTo(newValue);
    }

    @Test
    public void computesDiffForMultipleValues() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .set("Bum", "Lux")
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .set("Bum", "Luxes")
                .build();

        final JsonMergePatch expected = JsonMergePatch.of(newValue);

        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
        assertThat(expected.applyOn(oldValue)).isEqualTo(newValue);
    }

    @Test
    public void computesDiffForNested() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("nested", JsonObject.newBuilder()
                        .set("Test", "Foo")
                        .set("Bum", "Lux")
                        .build())
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("nested", JsonObject.newBuilder()
                        .set("Test", "Bar")
                        .set("Bum", "Lux")
                        .build())
                .build();

        final JsonMergePatch expected = JsonMergePatch.of(JsonObject.newBuilder()
                .set("nested", JsonObject.newBuilder()
                        .set("Test", "Bar")
                        .build())
                .build());

        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
        assertThat(expected.applyOn(oldValue)).isEqualTo(newValue);
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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase9() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "foo")
                .build();

        final JsonValue objectToPatch = JsonValue.nullLiteral();

        final JsonValue expectedObject = JsonValue.nullLiteral();

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void mergeFieldsFromBothObjectsRFC7396_TestCase10() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", "foo")
                .build();

        final JsonValue objectToPatch = JsonFactory.newValue("bar");

        final JsonValue expectedObject = JsonFactory.newValue("bar");

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

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

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void removeFieldsUsingRegexWithNullValue() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("2023-04-01", JsonValue.of(true))
                        .set("2023-04-02", JsonValue.of("hello"))
                        .set("2023-04-03", JsonValue.of("darkness"))
                        .set("2023-04-04", JsonValue.of("my"))
                        .set("2023-04-05", JsonValue.of("old"))
                        .set("2023-04-06", JsonValue.of("friend"))
                        .build())
                .set("b", JsonFactory.newObjectBuilder()
                        .set("2023-04-01", JsonValue.of(true))
                        .set("2023-04-02", JsonValue.of("hello"))
                        .set("2023-04-03", JsonValue.of("darkness"))
                        .set("2023-04-04", JsonValue.of("my"))
                        .set("2023-04-05", JsonValue.of("old"))
                        .set("2023-04-06", JsonValue.of("friend"))
                        .build())
                .set("c", JsonFactory.newObjectBuilder()
                        .set("some", JsonValue.of(true))
                        .set("2023-04-02", JsonValue.of("hello"))
                        .set("totally-other", JsonValue.of("darkness"))
                        .set("foo", JsonValue.of("my"))
                        .build())
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("{{ ~2023-04-.*~ }}", JsonValue.nullLiteral())
                        .set("2023-05-01", JsonValue.of("new"))
                        .set("2023-05-02", JsonValue.of("catch"))
                        .set("2023-05-03", JsonValue.of("phrase"))
                        .build())
                .set("b", JsonFactory.newObjectBuilder()
                        .set("{{ ~2023-04-01~ }}", JsonValue.nullLiteral())
                        .set("{{ ~^2023-04-03$~ }}", JsonValue.nullLiteral())
                        .set("{{ ~[0-9]{4}-04-.+4~ }}", JsonValue.nullLiteral())
                        .set("2023-05-01", JsonValue.of("new"))
                        .set("2023-05-02", JsonValue.of("catch"))
                        .set("2023-05-03", JsonValue.of("phrase"))
                        .build())
                .set("c", JsonFactory.newObjectBuilder()
                        .set("{{ ~.*~ }}", JsonValue.nullLiteral())
                        .set("2023-05-01", JsonValue.of("new"))
                        .set("2023-05-02", JsonValue.of("catch"))
                        .set("2023-05-03", JsonValue.of("phrase"))
                        .build())
                .build();

        final JsonValue expectedObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newObjectBuilder()
                        .set("2023-05-01", JsonValue.of("new"))
                        .set("2023-05-02", JsonValue.of("catch"))
                        .set("2023-05-03", JsonValue.of("phrase"))
                        .build())
                .set("b", JsonFactory.newObjectBuilder()
                        .set("2023-04-02", JsonValue.of("hello"))
                        .set("2023-04-05", JsonValue.of("old"))
                        .set("2023-04-06", JsonValue.of("friend"))
                        .set("2023-05-01", JsonValue.of("new"))
                        .set("2023-05-02", JsonValue.of("catch"))
                        .set("2023-05-03", JsonValue.of("phrase"))
                        .build())
                .set("c", JsonFactory.newObjectBuilder()
                        .set("2023-05-01", JsonValue.of("new"))
                        .set("2023-05-02", JsonValue.of("catch"))
                        .set("2023-05-03", JsonValue.of("phrase"))
                        .build())
                .build();

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }

    @Test
    public void removeFieldsUsingRegexWithNullValueWithHierarchy() {
        final JsonObject originalObject = JsonFactory.newObjectBuilder()
                .set("first", JsonFactory.newObjectBuilder()
                        .set("second", JsonFactory.newObjectBuilder()
                                .set("third", JsonFactory.newObjectBuilder()
                                        .set("something-on-third", "foobar3")
                                        .set("another-on-third", false)
                                        .build())
                                .set("something-on-second", "foobar2")
                                .set("another-on-second", false)
                                .build())
                        .set("something-on-first", "foobar1")
                        .set("another-on-first", 42)
                        .build()
                )
                .build();

        final JsonObject objectToPatch = JsonFactory.newObjectBuilder()
                .set("first", JsonFactory.newObjectBuilder()
                        .set("{{ ~seco.*~ }}", JsonValue.nullLiteral())
                        .set("second", JsonFactory.newObjectBuilder()
                                .set("another-on-second", true)
                                .build()
                        )
                        .build()
                )
                .build();

        final JsonValue expectedObject = JsonFactory.newObjectBuilder()
                .set("first", JsonFactory.newObjectBuilder()
                        .set("second", JsonFactory.newObjectBuilder()
                                .set("another-on-second", true)
                                .build())
                        .set("something-on-first", "foobar1")
                        .set("another-on-first", 42)
                        .build()
                )
                .build();

        final JsonValue mergedObject = JsonMergePatch.of(objectToPatch).applyOn(originalObject);

        Assertions.assertThat(mergedObject).isEqualTo(expectedObject);
    }


}
