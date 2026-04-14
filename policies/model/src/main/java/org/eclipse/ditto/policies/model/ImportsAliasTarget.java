/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A target reference within a {@link ImportsAlias}, pointing to a specific entry addition in a policy import.
 * Consists of the imported policy ID and the entry label within that import's entries additions.
 *
 * @since 3.9.0
 */
public interface ImportsAliasTarget extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns the {@link PolicyId} of the imported policy this target refers to.
     *
     * @return the imported policy ID.
     */
    PolicyId getImportedPolicyId();

    /**
     * Returns the {@link Label} of the entry within the import's entries additions.
     *
     * @return the entry label.
     */
    Label getEntryLabel();

    /**
     * ImportsAliasTarget is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns all non-hidden marked fields of this ImportsAliasTarget.
     *
     * @return a JSON object representation including only non-hidden marked fields.
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
     * Known JSON fields of an ImportsAliasTarget.
     */
    final class JsonFields {

        /**
         * JSON field containing the imported policy ID.
         */
        public static final JsonFieldDefinition<String> IMPORT =
                JsonFactory.newStringFieldDefinition("import", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the entry label.
         */
        public static final JsonFieldDefinition<String> ENTRY =
                JsonFactory.newStringFieldDefinition("entry", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
