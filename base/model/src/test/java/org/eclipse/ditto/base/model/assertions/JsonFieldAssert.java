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
package org.eclipse.ditto.base.model.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.ditto.json.JsonField;


/**
 * Specific assertion for {@link JsonField} objects.
 */
public final class JsonFieldAssert extends AbstractAssert<JsonFieldAssert, JsonField> {

    /**
     * Constructs a new {@code JsonFieldAssert} object.
     *
     * @param actual the actual value.
     */
    JsonFieldAssert(final JsonField actual) {
        super(actual, JsonFieldAssert.class);
    }

    /**
     * Checks if the actual value has the expected key name.
     *
     * @param expectedKeyName the expected position.
     * @return this assert to allow method chaining.
     */
    public JsonFieldAssert hasKeyName(final String expectedKeyName) {
        isNotNull();
        final String actualKeyName = actual.getKeyName();
        assertThat(actualKeyName).overridingErrorMessage("Expected JSON field's key name to be <%s> but was <%s>",
                expectedKeyName, actualKeyName).isEqualTo(expectedKeyName);
        return this;
    }
}
