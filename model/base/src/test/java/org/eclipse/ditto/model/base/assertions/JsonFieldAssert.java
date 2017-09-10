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
package org.eclipse.ditto.model.base.assertions;

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
