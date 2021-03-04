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
package org.eclipse.ditto.json.assertions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Specific assertion for {@link JsonPointer} objects.
 */
public final class JsonPointerAssert extends AbstractAssert<JsonPointerAssert, JsonPointer> {

    /**
     * Constructs a new {@code JsonPointerAssert} object.
     *
     * @param actual the actual {@link JsonPointer} to be verified.
     */
    JsonPointerAssert(final JsonPointer actual) {
        super(actual, JsonPointerAssert.class);
    }

    /**
     * Verifies that the actual JSON pointer has the expected level count.
     *
     * @param expectedLevelCount the expected level count.
     * @return this assert to allow method chaining.
     */
    public JsonPointerAssert hasLevelCount(final int expectedLevelCount) {
        isNotNull();
        final int actualSize = actual.getLevelCount();

        Assertions.assertThat(actualSize)
                .overridingErrorMessage("Expected JSON object to have size <%d> but was <%d>", expectedLevelCount, actualSize)
                .isEqualTo(expectedLevelCount);

        return this;
    }

    /**
     * Verifies that the actual JSON pointer is empty.
     *
     * @return this assert to allow method chaining.
     */
    public JsonPointerAssert isEmpty() {
        isNotNull();

        Assertions.assertThat(actual.isEmpty())
                .overridingErrorMessage("Expected JSON object to be empty but it was not.")
                .isTrue();

        return this;
    }

    /**
     * Verifies that the actual JSON pointer is <em>not</em> empty.
     *
     * @return this assert to allow method chaining.
     */
    public JsonPointerAssert isNotEmpty() {
        isNotNull();

        Assertions.assertThat(actual.isEmpty())
                .overridingErrorMessage("Expected JSON object not to be empty but it was.")
                .isFalse();

        return this;
    }

    /**
     * Verifies that the actual JSON pointer is equal to the given one.
     *
     * @param expectedJsonPointer the given value to compare the actual JSON pointer to.
     * @return this assert to allow method chaining.
     */
    @Override
    public JsonPointerAssert isEqualTo(final Object expectedJsonPointer) {
        isNotNull();

        Assertions.assertThat((Object) actual).isEqualTo(expectedJsonPointer);

        return myself;
    }

    /**
     * Verifies that the JSON pointer contains all specified keys.
     *
     * @param expectedJsonKey the mandatory key whose existence is checked.
     * @param furtherExpectedJsonKeys further optional keys whose existence in the JSON object is checked.
     * @return this assertion object.
     */
    public JsonPointerAssert containsKey(final CharSequence expectedJsonKey,
            final CharSequence... furtherExpectedJsonKeys) {

        isNotNull();

        final Collection<CharSequence> allExpectedJsonKeys = merge(expectedJsonKey, furtherExpectedJsonKeys);

        final List<CharSequence> missingKeys = new ArrayList<>();

        for (final CharSequence expJsonKey : allExpectedJsonKeys) {
            if (!containsKey(expJsonKey)) {
                missingKeys.add(expJsonKey);
            }
        }

        Assertions.assertThat(missingKeys)
                .overridingErrorMessage("Expected JSON pointer to contain key(s) <%s> but it did not contain <%s>",
                        allExpectedJsonKeys, missingKeys)
                .isEmpty();

        return myself;
    }

    private static List<CharSequence> merge(final CharSequence charSequence,
            final CharSequence... furtherCharSequences) {

        final List<CharSequence> result = new ArrayList<>(1 + furtherCharSequences.length);
        result.add(charSequence);
        Collections.addAll(result, furtherCharSequences);

        return result;
    }
    
    private boolean containsKey(final CharSequence expectedJsonKey) {
        for (final JsonKey existingJsonKey : actual) {
            if (existingJsonKey.equals(JsonKey.of(expectedJsonKey))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies that the actual JSON pointer does not contain the specified key(s).
     *
     * @param unexpectedKey the key which should not be contained in the JSON pointer.
     * @param furtherUnexpectedKeys further keys which should not be contained in the JSON pointer.
     * @return this assert to allow method chaining.
     */
    public JsonPointerAssert doesNotContainKey(final CharSequence unexpectedKey,
            final CharSequence... furtherUnexpectedKeys) {

        isNotNull();

        final List<CharSequence> allUnexpectedKeys = merge(unexpectedKey, furtherUnexpectedKeys);

        final List<CharSequence> existingKeys = new ArrayList<>();

        for (final CharSequence unexpKey : allUnexpectedKeys) {
            if (containsKey(unexpectedKey)) {
                existingKeys.add(unexpKey);
            }
        }

        Assertions.assertThat(existingKeys)
                .overridingErrorMessage("Expected JSON pointer not to contain key(s) <%s> but it contained <%s>",
                        allUnexpectedKeys, existingKeys)
                .isEmpty();

        return myself;
    }

    /**
     * Verifies that the actual JSON pointer has the specified root.
     *
     * @param expectedRoot the expected root of the actual JSON pointer.
     * @return this assert to allow method chaining.
     */
    public JsonPointerAssert hasRoot(final JsonKey expectedRoot) {
        isNotNull();
        final Optional<JsonKey> actualRoot = actual.getRoot();
        Assertions.assertThat(actualRoot)
                .overridingErrorMessage("Expected <%s> to have root <%s> but it had <%s>", actual, expectedRoot,
                        actualRoot.orElse(null))
                .contains(expectedRoot);
        return myself;
    }

    /**
     * Verifies that the actual JSON pointer has the specified leaf.
     *
     * @param expectedLeaf the expected leaf of the actual JSON pointer.
     * @return this assert to allow method chaining.
     */
    public JsonPointerAssert hasLeaf(final JsonKey expectedLeaf) {
        isNotNull();
        final Optional<JsonKey> actualLeaf = actual.getLeaf();
        Assertions.assertThat(actualLeaf)
                .overridingErrorMessage("Expected <%s> to have leaf <%s> but it had <%s>", actual, expectedLeaf,
                        actualLeaf.orElse(null))
                .contains(expectedLeaf);
        return myself;
    }

}
