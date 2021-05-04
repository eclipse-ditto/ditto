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
package org.eclipse.ditto.policies.model;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Represents a {@link PolicyEntry}'s Label.
 */
public interface Label extends CharSequence {

    /**
     * Returns a Label for the given character sequence. If the given key value is already a Label, this is
     * immediately properly cast and returned.
     *
     * @param labelValue the character sequence value of the Label to be created.
     * @return a new Label with {@code labelValue} as its value.
     * @throws NullPointerException if {@code labelValue} is {@code null}.
     * @throws IllegalArgumentException if {@code labelValue} is empty.
     */
    static Label of(final CharSequence labelValue) {
        return PoliciesModelFactory.newLabel(labelValue);
    }

    /**
     * Returns the JsonFieldDefinition for this Label.
     *
     * @return the field definition.
     */
    default JsonFieldDefinition<JsonObject> getJsonFieldDefinition() {
        return JsonFactory.newJsonObjectFieldDefinition(this, FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

}
