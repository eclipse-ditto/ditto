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
package org.eclipse.ditto.thingsearch.model.assertions;

import java.util.Collection;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.model.PropertySearchFilter;

/**
 * An assert for {@link org.eclipse.ditto.thingsearch.model.SearchFilter}.
 */
public final class PropertySearchFilterAssert
        extends SearchFilterAssert<PropertySearchFilterAssert, PropertySearchFilter> {

    /**
     * Constructs a new {@code PropertySearchFilterAssert} object.
     *
     * @param actual the search query to be checked.
     */
    public PropertySearchFilterAssert(final PropertySearchFilter actual) {
        super(actual, PropertySearchFilterAssert.class);
    }

    public PropertySearchFilterAssert hasPath(final JsonPointer expectedPath) {
        isNotNull();
        final JsonPointer actualPath = actual.getPath();
        Assertions.assertThat((Object) actualPath)
                .overridingErrorMessage("Expected path of PropertySearchFilter to be \n<%s> but it was \n<%s>",
                        expectedPath,
                        actualPath)
                .isEqualTo(expectedPath);
        return this;
    }

    public PropertySearchFilterAssert hasNoValue() {
        isNotNull();
        final Collection<JsonValue> actualValues = actual.getValues();
        Assertions.assertThat(actualValues)
                .overridingErrorMessage("Expected PropertySearchFilterAssert not to have any values but it had <%s>",
                        actualValues)
                .isEmpty();
        return this;
    }

    public PropertySearchFilterAssert hasOnlyValue(final JsonValue... expectedValues) {
        isNotNull();
        final Collection<JsonValue> actualValues = actual.getValues();
        Assertions.assertThat(actualValues)
                .overridingErrorMessage("Expected PropertySearchFilter to have value(s) \n<%s> but it had \n<%s>",
                        expectedValues, actualValues)
                .containsOnly(expectedValues);
        return this;
    }

    public PropertySearchFilterAssert hasOnlyValue(final String... expectedValues) {
        return hasOnlyValue(Stream.of(expectedValues)
                .map(JsonFactory::newValue)
                .toArray(JsonValue[]::new));
    }

    public PropertySearchFilterAssert hasOnlyValue(final Boolean... expectedValues) {
        return hasOnlyValue(Stream.of(expectedValues)
                .map(JsonFactory::newValue)
                .toArray(JsonValue[]::new));
    }

    public PropertySearchFilterAssert hasOnlyValue(final Integer... expectedValues) {
        return hasOnlyValue(Stream.of(expectedValues)
                .map(JsonFactory::newValue)
                .toArray(JsonValue[]::new));
    }

    public PropertySearchFilterAssert hasOnlyValue(final Long... expectedValues) {
        return hasOnlyValue(Stream.of(expectedValues)
                .map(JsonFactory::newValue)
                .toArray(JsonValue[]::new));
    }

    public PropertySearchFilterAssert hasOnlyValue(final Double... expectedValues) {
        return hasOnlyValue(Stream.of(expectedValues)
                .map(JsonFactory::newValue)
                .toArray(JsonValue[]::new));
    }

}
