/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.policies;

import javax.annotation.Nullable;
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

/**
 * Holds {@link ImportedEntries} for {@link PermissionEffect}s (grant/revoke).
 */
public interface EffectedImportedEntries extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code EffectedImportedEntries} containing the given {@code includedImportedEntries} and {@code
     * excludedImportedEntries}.
     *
     * @param includedImportedEntries the ImportedEntries which should be granted, may be {@code null}.
     * @param excludedImportedEntries the ImportedEntries which should be revoked, may be {@code null}.
     * @return the new {@code EffectedImportedEntries}.
     */
    static EffectedImportedEntries newInstance(@Nullable final Iterable<String> includedImportedEntries,
            @Nullable final Iterable<String> excludedImportedEntries) {

        return PoliciesModelFactory.newEffectedImportedEntries(includedImportedEntries, excludedImportedEntries);
    }

    /**
     * EffectedImportedEntries is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of EffectedImportedEntries.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the {@link ImportedEntries} which are valid for the passed {@code effect}.
     *
     * @param effect the PermissionEffect for which to return the ImportedEntries.
     * @return the ImportedEntries which are valid for the passed effect.
     * @throws NullPointerException if {@code effect} is {@code null}.
     * @throws IllegalArgumentException if {@code effect} is unknown.
     */
    ImportedEntries getImportedEntries(ImportedEntryEffect effect);

    /**
     * Returns the included {@link ImportedEntries}.
     *
     * @return the included ImportedEntries.
     */
    default ImportedEntries getIncludedImportedEntries() {
        return getImportedEntries(ImportedEntryEffect.INCLUDED);
    }

    /**
     * Returns the excluded {@link ImportedEntries}.
     *
     * @return the excluded ImportedEntries.
     */
    default ImportedEntries getExcludedImportedEntries() {
        return getImportedEntries(ImportedEntryEffect.EXCLUDED);
    }

    /**
     * Returns all non hidden marked fields of this EffectedImportedEntries.
     *
     * @return a JSON object representation of this EffectedImportedEntries including only non hidden marked fields.
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
     * An enumeration of the known {@link JsonField}s of a EffectedImportedEntries.
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
         * JSON field containing the EffectedImportedEntries's {@code included} ImportedEntries.
         */
        public static final JsonFieldDefinition<JsonArray> INCLUDED =
                JsonFactory.newJsonArrayFieldDefinition("included", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the EffectedImportedEntries's {@code excluded} ImportedEntries.
         */
        public static final JsonFieldDefinition<JsonArray> EXCLUDED =
                JsonFactory.newJsonArrayFieldDefinition("excluded", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
