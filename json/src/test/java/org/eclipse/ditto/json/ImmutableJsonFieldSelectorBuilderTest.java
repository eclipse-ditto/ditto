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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableJsonFieldSelectorBuilder}.
 */
public final class ImmutableJsonFieldSelectorBuilderTest {

    private static JsonParseOptions jsonParseOptions;
    private static String knownSelectorString;
    private static String pointerAString;
    private static String pointerBString;
    private static JsonPointer pointerA;
    private static JsonPointer pointerB;
    private static JsonFieldDefinition<JsonObject> fieldDefinitionA;
    private static JsonFieldDefinition<JsonObject> fieldDefinitionB;

    private ImmutableJsonFieldSelectorBuilder underTest;

    @BeforeClass
    public static void initTestConstants() {
        jsonParseOptions = JsonParseOptions.newBuilder().withUrlDecoding().build();
        knownSelectorString = "/a,/b";
        pointerAString = "/a";
        pointerBString = "/b";
        pointerA = JsonFactory.newPointer(pointerAString);
        pointerB = JsonFactory.newPointer(pointerBString);
        fieldDefinitionA = JsonFactory.newJsonObjectFieldDefinition(pointerA);
        fieldDefinitionB = JsonFactory.newJsonObjectFieldDefinition(pointerB);
    }


    @Before
    public void setUp() {
        underTest = ImmutableJsonFieldSelectorBuilder.newInstance();
    }

    @Test
    public void buildWithoutAddingAnything() {
        final JsonFieldSelector actual = underTest.build();
        final JsonFieldSelector expected = createSelector();

        assertThat(actual).isEqualTo(expected);
    }

    @SuppressWarnings("OverlyStrongTypeCast")
    @Test
    public void addPointersAsVarArgsWithNullParam() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addPointer(pointerA, (JsonPointer[]) null))
                .withMessage("The %s must not be null!", "further JSON pointers to be added")
                .withNoCause();
    }


    @Test
    public void addingNullJsonPointerHasNoEffect() {
        underTest.addPointer(null);

        assertThat(underTest).isEmpty();
    }

    @Test
    public void addPointersAsVarArgs() {
        final JsonFieldSelector actual = underTest.addPointer(pointerA, pointerB).build();
        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void addPointersAsNullIterable() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addPointers(null))
                .withMessage("The %s must not be null!", "JSON pointers to be added")
                .withNoCause();
    }

    @Test
    public void addPointersAsIterable() {
        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString);

        final JsonFieldSelector actual = underTest.addPointers(Arrays.asList(pointerA, pointerB)).build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void addPointerStringsAsNullVarArgs() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addPointerString(pointerAString, (String[]) null))
                .withMessage("The %s must not be null!", "further JSON pointer strings to be added")
                .withNoCause();
    }

    @Test
    public void addPointerStringsAsVarArgs() {
        final JsonFieldSelector actual = underTest.addPointerString(pointerAString, pointerBString).build();

        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void addPointerStringsAsNullIterable() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addPointerStrings(null))
                .withMessage("The %s must not be null!", "JSON pointer strings to be added")
                .withNoCause();
    }

    @Test
    public void addPointerStringsAsIterable() {
        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString);

        final JsonFieldSelector actual =
                underTest.addPointerStrings(Arrays.asList(pointerAString, pointerBString)).build();

        assertThat(actual).isEqualTo(expected);
    }


    @SuppressWarnings("OverlyStrongTypeCast")
    @Test
    public void addFieldDefinitionsAsNullVarArgs() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addFieldDefinition(fieldDefinitionA, (JsonFieldDefinition<?>[]) null))
                .withMessage("The %s must not be null!", "further JSON field definitions to be added")
                .withNoCause();
    }

    @Test
    public void addFieldDefinitionsAsVarArgs() {
        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString);

        final JsonFieldSelector actual = underTest.addFieldDefinition(fieldDefinitionA, fieldDefinitionB).build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void addFieldDefinitionsAsNullIterable() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addFieldDefinitions(null))
                .withMessage("The %s must not be null!", "JSON field definitions")
                .withNoCause();
    }

    @Test
    public void addFieldDefinitionsAsIterable() {
        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString);

        final JsonFieldSelector actual =
                underTest.addFieldDefinitions(Arrays.asList(fieldDefinitionA, fieldDefinitionB)).build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void addNullFieldSelector() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addFieldSelector(null))
                .withMessage("The %s must not be null!", "JSON field selector")
                .withNoCause();
    }

    @Test
    public void addFieldSelector() {
        final String pointerCString = "/c";
        final JsonPointer pointerC = JsonFactory.newPointer(pointerCString);

        underTest.addPointerString(pointerCString);
        underTest.addFieldSelector(ImmutableJsonFieldSelector.of(Arrays.asList(pointerA, pointerB)));

        final JsonFieldSelector actual = underTest.build();

        assertThat(actual).containsExactly(pointerC, pointerA, pointerB);
    }

    @Test
    public void addFieldSelectorStringWithNullOptions() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.addFieldSelectorString(knownSelectorString, null))
                .withMessage("The %s must not be null!", "JSON parse options")
                .withNoCause();
    }

    @Test
    public void addFieldSelectorStringWithNullFieldSelectorString() {
        final JsonFieldSelector actual = underTest.addFieldSelectorString(null, jsonParseOptions).build();

        assertThat(actual).isEmpty();
    }

    @Test
    public void addFieldSelectorStringWithNonNullFieldSelectorString() {
        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString);

        final JsonFieldSelector actual =
                underTest.addFieldSelectorString(knownSelectorString, jsonParseOptions).build();

        // only the pointers must be the same, the String representation seems to be different
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void removePointerWorksAsExpected() {
        final JsonFieldSelector expected = createSelector(pointerBString);

        final JsonFieldSelector actual = underTest.addPointer(pointerA, pointerB)
                .removePointer(pointerA)
                .build();

        assertThat(actual).isEqualTo(expected);
    }


    @Test
    public void removePointerViaIteratorResetsJsonSelectorString() {
        final JsonFieldSelector jsonFieldSelector = createSelector(pointerAString, pointerBString);
        final String fieldSelectorString = jsonFieldSelector.toString();

        final JsonFieldSelector fieldSelector1 = underTest.addFieldSelectorString(fieldSelectorString)
                .build();

        assertThat(fieldSelector1.toString()).hasToString(fieldSelectorString);

        final Iterator<JsonPointer> pointerIterator = underTest.iterator();
        while (pointerIterator.hasNext()) {
            final JsonPointer jsonPointer = pointerIterator.next();
            if (jsonPointer.equals(pointerA)) {
                pointerIterator.remove();
            }
        }

        final JsonFieldSelector fieldSelector2 = underTest.build();

        assertThat(fieldSelector2.toString()).isNotEqualTo(fieldSelectorString);
    }

    @Test
    public void multipleAdd() {
        final String pointerCString = "/c";
        final JsonFieldSelector expected = createSelector(pointerAString, pointerBString, pointerCString);

        final JsonFieldSelector actual = underTest.addPointer(pointerA, pointerB)
                .addPointer(pointerA, JsonFactory.newPointer(pointerCString))
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    private static JsonFieldSelector createSelector(final String... pointerStrings) {
        final List<JsonPointer> pointers = Stream.of(pointerStrings)
                .map(JsonFactory::newPointer)
                .collect(Collectors.toList());
        return ImmutableJsonFieldSelector.of(pointers);
    }

}
