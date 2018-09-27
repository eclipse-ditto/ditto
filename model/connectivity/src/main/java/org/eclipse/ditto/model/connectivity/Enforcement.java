/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

public interface Enforcement extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    String getInput();

    Set<String> getMatchers();

    /**
     * Returns all non hidden marked fields of this {@code Source}.
     *
     * @return a JSON object representation of this Source including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code Connection}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the input of the enforcement.
         */
        static final JsonFieldDefinition<String> INPUT =
                JsonFactory.newStringFieldDefinition("input", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} authorization context (list of authorization subjects).
         */
        static final JsonFieldDefinition<JsonArray> MATCHERS =
                JsonFactory.newJsonArrayFieldDefinition("matchers", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }

}
