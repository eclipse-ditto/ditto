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
package org.eclipse.ditto.model.policies;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

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
