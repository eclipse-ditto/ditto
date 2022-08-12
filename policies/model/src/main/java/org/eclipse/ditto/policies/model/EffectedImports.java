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
 * Holds {@link ImportedLabels} for {@link ImportedEffect}s (included/excluded).
 *
 * @since 3.x.0 TODO ditto#298
 */
public interface EffectedImports extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code EffectedImports} containing the given {@code includedLabels} and {@code
     * excludedLabels}.
     *
     * @param includedLabels the ImportedLabels which should be included, may be {@code null}.
     * @param excludedLabels the ImportedLabels which should be excluded, may be {@code null}.
     * @return the new {@code EffectedImports}.
     */
    static EffectedImports newInstance(@Nullable final Iterable<Label> includedLabels,
            @Nullable final Iterable<Label> excludedLabels) {

        return PoliciesModelFactory.newEffectedImportedLabels(includedLabels, excludedLabels);
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
     * Returns the {@link ImportedLabels} which are valid for the passed {@code effect}.
     *
     * @param effect the ImportedEffect for which to return the ImportedLabels.
     * @return the ImportedLabels which are valid for the passed effect.
     * @throws NullPointerException if {@code effect} is {@code null}.
     * @throws IllegalArgumentException if {@code effect} is unknown.
     */
    ImportedLabels getImportedLabels(ImportedEffect effect);

    /**
     * Returns the included {@link ImportedLabels}.
     *
     * @return the included ImportedLabels.
     */
    default ImportedLabels getIncludedImportedLabels() {
        return getImportedLabels(ImportedEffect.INCLUDED);
    }

    /**
     * Returns the excluded {@link ImportedLabels}.
     *
     * @return the excluded ImportedLabels.
     */
    default ImportedLabels getExcludedImportedLabels() {
        return getImportedLabels(ImportedEffect.EXCLUDED);
    }

    /**
     * Returns all non hidden marked fields of this EffectedImports.
     *
     * @return a JSON object representation of this EffectedImports including only non hidden marked fields.
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
         * JSON field containing the {@link JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the EffectedImports' {@code included} ImportedLabels.
         */
        public static final JsonFieldDefinition<JsonArray> INCLUDED =
                JsonFactory.newJsonArrayFieldDefinition("included", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the EffectedImports' {@code excluded} ImportedLabels.
         */
        public static final JsonFieldDefinition<JsonArray> EXCLUDED =
                JsonFactory.newJsonArrayFieldDefinition("excluded", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
