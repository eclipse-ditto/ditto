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
package org.eclipse.ditto.policies.model;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * Holds imported {@link ImportedLabels} for {@link EffectedImports}s.
 *
 * @since 3.1.0
 */
public interface EffectedImports extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code EffectedImports} containing the given {@code importedLabels}.
     *
     * @param importedLabels the labels of policy entries which should be imported, may be {@code null}.
     * @return the new {@code EffectedImports}.
     */
    static EffectedImports newInstance(@Nullable final Iterable<Label> importedLabels){

        return PoliciesModelFactory.newEffectedImportedLabels(importedLabels);
    }

    /**
     * EffectedImports is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of EffectedImports.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the {@link ImportedLabels}.
     *
     * @return the ImportedLabels.
     * @throws NullPointerException if {@code effect} is {@code null}.
     * @throws IllegalArgumentException if {@code effect} is unknown.
     */
    ImportedLabels getImportedLabels();

    /**
     * Returns all non-hidden marked fields of this EffectedImports.
     *
     * @return a JSON object representation of this EffectedImports including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@link JsonField}s of a EffectedImports.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the labels of imported policy entries.
         */
        public static final JsonFieldDefinition<JsonArray> ENTRIES =
                JsonFactory.newJsonArrayFieldDefinition("entries", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
