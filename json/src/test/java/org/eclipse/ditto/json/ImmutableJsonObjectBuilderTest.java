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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonObjectBuilder}.
 */
@SuppressWarnings({"ClassWithTooManyFields", "ConstantConditions"})
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableJsonObjectBuilderTest {

    private static JsonKey fooKey;
    private static JsonKey barKey;
    private static JsonKey bazKey;
    private static JsonPointer pointer;
    private static JsonField eddard;
    private static JsonField robert;
    private static JsonField john;
    private static JsonField cersei;
    private static JsonField hodor;

    @Mock
    private JsonValue jsonValueMock;

    private ImmutableJsonObjectBuilder underTest;

    @BeforeClass
    public static void initTestConstants() {
        fooKey = JsonFactory.newKey("foo");
        barKey = JsonFactory.newKey("bar");
        bazKey = JsonFactory.newKey("baz");
        pointer = JsonFactory.newPointer(fooKey, barKey, bazKey);
        eddard = createField("Eddard", "Stark");
        robert = createField("Robert", "Baratheon");
        john = createField("John", "Snow");
        cersei = createField("Cersei", "Lannister");
        hodor = createField("Hodor", "");
    }

    private static JsonField createField(final CharSequence key, final String value) {
        return JsonFactory.newField(JsonFactory.newKey(key), JsonFactory.newValue(value));
    }

    @Before
    public void setUp() {
        underTest = ImmutableJsonObjectBuilder.newInstance();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonObjectBuilder.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToInvokeSetAllWithNullIterator() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.setAll(null))
                .withMessage("The %s must not be null!", "JSON fields to be set")
                .withNoCause();
    }

    @Test
    public void setAllSetsAllSpecifiedFieldsWithoutDuplicates() {
        final Collection<JsonField> fieldsToBeSet = new ArrayList<>();
        Collections.addAll(fieldsToBeSet, eddard, cersei, robert, john, cersei, hodor, hodor);

        underTest.setAll(fieldsToBeSet);

        assertThat(underTest).containsExactly(eddard, cersei, robert, john, hodor);
    }

    @Test
    public void removeAllReturnsEmptyObject() {
        underTest.set(eddard);
        underTest.set(robert);
        underTest.set(john);

        assertThat(underTest).isNotEmpty();

        underTest.removeAll();

        assertThat(underTest).isEmpty();
    }

    @Test
    public void tryToSetValueWithEmptyKey() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.set("", eddard.getValue()))
                .withMessage("The key or pointer must not be empty!")
                .withNoCause();
    }

    @Test
    public void tryToSetValueWithNullJsonPointer() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.set((CharSequence) null, cersei.getValue(), Objects::nonNull))
                .withMessage("The %s must not be null!", "key or pointer char sequence")
                .withNoCause();
    }

    @Test
    public void tryToSetValueWithSlashString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.set("/", eddard.getValue()))
                .withMessage("The key or pointer must not be empty!")
                .withNoCause();
    }

    @Test
    public void setValueWithSlashAsJsonKey() {
        final JsonKey key = JsonFactory.newKey("/");
        final JsonField expected = JsonFactory.newField(key, cersei.getValue());

        underTest.set(key, expected.getValue());

        assertThat(underTest).containsOnly(expected);
    }

    @Test
    public void doNotSetFieldIfPredicateEvaluatesToFalse() {
        underTest.set(pointer, jsonValueMock, jsonValue -> false);

        assertThat(underTest).isEmpty();
    }

    @Test
    public void doSetFieldIfPredicateEvaluatesToTrue() {
        final JsonField bazField = JsonFactory.newField(bazKey, eddard.getValue());
        final JsonField barField = JsonFactory.newField(barKey, JsonFactory.newObject().set(bazField));
        final JsonField expectedField = JsonFactory.newField(fooKey, JsonFactory.newObject().set(barField));

        underTest.set(pointer, eddard.getValue(), jsonValue -> true);

        assertThat(underTest).containsExactly(expectedField);
    }

    @Test
    public void setWithJsonPointerWorksAsExpected1() {
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(fooKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(barKey, ImmutableJsonObjectBuilder.newInstance()
                                .set(bazKey, ImmutableJsonObjectBuilder.newInstance()
                                        .set(hodor)
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject actualJsonObject = underTest
                .set(pointer.addLeaf(hodor.getKey()), hodor.getValue())
                .build();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void setWithJsonPointerWorksAsExpected2() {
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(fooKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(barKey, ImmutableJsonObjectBuilder.newInstance()
                                .set(bazKey, ImmutableJsonObjectBuilder.newInstance()
                                        .set(john)
                                        .set(robert)
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject actualJsonObject = underTest
                .set(pointer.addLeaf(john.getKey()), john.getValue())
                .set(pointer.addLeaf(robert.getKey()), robert.getValue())
                .build();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void setWithJsonPointerWorksAsExpected3() {
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(fooKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(barKey, ImmutableJsonObjectBuilder.newInstance()
                                .set(bazKey, true)
                                .build())
                        .build())
                .build();

        final JsonObject actualJsonObject = underTest
                .set(pointer.addLeaf(fooKey), "bar") // This JSON object gets replaced by boolean true
                .set(pointer, true)
                .build();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void setWithJsonPointerWorksAsExpected4() {
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(fooKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(barKey, ImmutableJsonObjectBuilder.newInstance()
                                .set(bazKey, ImmutableJsonObjectBuilder.newInstance()
                                        .set(eddard)
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonObject actualJsonObject = underTest
                .set(pointer, JsonFactory.nullLiteral()) // This JSON null literal gets replaced by a JSON object
                .set(pointer.addLeaf(eddard.getKey()), eddard.getValue())
                .build();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void setWithJsonPointerAndPredicateWorksAsExpected() {
        final JsonObject expectedJsonObject = JsonFactory.newObject();

        final JsonObject actualJsonObject = underTest
                .set(pointer, "bar", field -> false)
                .build();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void removeWithJsonKeyWorksAsExpected() {
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(john)
                .set(hodor)
                .build();

        final JsonObject actualJsonObject = underTest
                .set(eddard)
                .set(john)
                .set(hodor)
                .remove(eddard.getKey())
                .build();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void removeWithJsonPointerWorksAsExpected() {
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(fooKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(barKey, eddard.getValue())
                        .build())
                .set(robert)
                .set(cersei)
                .build();

        final JsonObject actualJsonObject = underTest
                .set(fooKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(bazKey, john.getValue())
                        .set(barKey, eddard.getValue())
                        .build())
                .set(robert)
                .set(cersei)
                .remove(JsonFactory.newPointer(fooKey, bazKey))
                .build();

        assertThat(actualJsonObject).isEqualTo(expectedJsonObject);
    }

    @Test
    public void removeWithEmptyStringDoesNothing() {
        final JsonObjectBuilder underTest = ImmutableJsonObjectBuilder.newInstance()
                .set(JsonFactory.newKey("/"), "Boo!")
                .set(bazKey, john.getValue())
                .set(barKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(robert)
                        .build());

        final JsonObject beforeRemoval = underTest.build();

        underTest.remove("");

        final JsonObject afterRemoval = underTest.build();

        assertThat(afterRemoval).isEqualTo(beforeRemoval);
    }

    @Test
    public void removeSlashOnlyKeyWorksAsExpected() {
        final JsonKey key = JsonFactory.newKey("/");
        final JsonObjectBuilder underTest = ImmutableJsonObjectBuilder.newInstance()
                .set(key, "Boo!")
                .set(bazKey, john.getValue())
                .set(barKey, ImmutableJsonObjectBuilder.newInstance()
                        .set(robert)
                        .build());

        final JsonObject beforeRemoval = underTest.build();

        underTest.remove(key);

        final JsonObject afterRemoval = underTest.build();

        assertThat(afterRemoval).isEqualTo(beforeRemoval.remove(key));
    }

    @Test
    public void removePreviouslyAddedObjectByPointer() {
        final JsonPointer pointer = JsonFactory.newPointer(fooKey, bazKey);

        final JsonObject jsonObject = underTest
                .set(fooKey, ImmutableJsonObjectBuilder.newInstance()
                        .set("lastName", "nemo")
                        .set("name", "lilith")
                        .set(bazKey, JsonFactory.newObject("{\"bar\": \"baz\"}"))
                        .build())
                .set(robert)
                .set(cersei)
                .remove(pointer)
                .build();

        assertThat(jsonObject.getValue(pointer)).isEmpty();
    }

    @Test
    public void setNestedObjectWithImplicitDefinition() {
        final JsonKey payloadKey = JsonFactory.newKey("payload");
        final JsonPointer fooPointer = JsonFactory.newPointer(payloadKey, fooKey);
        final JsonPointer barPointer = JsonFactory.newPointer(payloadKey, barKey);
        final String fooValue = "foo";
        final String barValue = "bar";
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(fooPointer, fooValue)
                .set(barPointer, barValue)
                .build();

        final Marker marker = new Marker();
        final JsonFieldDefinition<String> foo = JsonFactory.newStringFieldDefinition(fooPointer, marker);
        final JsonFieldDefinition<String> bar = JsonFactory.newStringFieldDefinition(barPointer, marker);

        underTest.set(foo, fooValue);
        underTest.set(bar, barValue);

        final JsonObject actualJsonObject = underTest.build();

        assertThat(actualJsonObject).isEqualToIgnoringFieldDefinitions(expectedJsonObject);
    }

    @Test
    public void setFieldsWithPartialExcludeBasedOnFieldDefinition() {
        final JsonFieldDefinition<String> fooDefinition = JsonFactory.newStringFieldDefinition(fooKey);
        final JsonFieldDefinition<String> barDefinition = JsonFactory.newStringFieldDefinition(barKey);
        final JsonFieldDefinition<String> bazDefinition = JsonFactory.newStringFieldDefinition(bazKey);
        final JsonField fooField = JsonFactory.newField(fooKey, john.getValue(), fooDefinition);
        final JsonField barField = JsonFactory.newField(barKey, robert.getValue(), barDefinition);
        final JsonField bazField = JsonFactory.newField(bazKey, cersei.getValue(), bazDefinition);
        final JsonObject expectedJsonObject = ImmutableJsonObjectBuilder.newInstance()
                .set(barKey, robert.getValue())
                .set(bazKey, cersei.getValue())
                .build();

        final JsonObject actualJsonObject = underTest
                .set(barField, hasFieldDefinition(barDefinition))
                .set(bazField, hasFieldDefinition(bazDefinition))
                .set(fooField, hasFieldDefinition(fooDefinition).negate()) // negate the "foo" field definition on purpose to exclude it from the result
                .build();

        assertThat(actualJsonObject).isEqualToIgnoringFieldDefinitions(expectedJsonObject);
    }

    @Test
    public void setIntValueWithPredicateWhichEvaluatesToTrue() {
        final JsonKey intKey = JsonFactory.newKey("myIntValue");
        final int intValue = 42;

        final ImmutableJsonObjectBuilder underTest = ImmutableJsonObjectBuilder.newInstance();
        underTest.set(intKey, intValue, field -> field.getValue().asInt() > 0);

        assertThat(underTest.build())
                .hasSize(1)
                .contains(intKey, intValue);
    }

    private static Predicate<JsonField> hasFieldDefinition(final JsonFieldDefinition<?> definition) {
        return field -> field.getDefinition()
                .filter(definition::equals)
                .isPresent();
    }

    private static final class Marker implements JsonFieldMarker, Predicate<JsonField> {

        private final Predicate<JsonField> predicate;

        private Marker() {
            predicate = jsonField -> jsonField.isMarkedAs(this);
        }

        @Override
        public boolean test(final JsonField jsonField) {
            return predicate.test(jsonField);
        }
    }

}
