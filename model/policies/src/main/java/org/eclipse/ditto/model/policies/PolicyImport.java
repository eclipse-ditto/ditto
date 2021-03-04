/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.policies;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents a single import of another {@link Policy} based on its {@code policyId} and {@link EffectedImports}
 * containing optional includes and excludes.
 */
public interface PolicyImport extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code PolicyImport} with the specified {@code resourceKey} and {@code effectedPermissions}.
     *
     * @param importedPolicyId TODO TJ doc
     * @param isProtected
     * @param effectedImports the EffectedImports of the new PolicyImport to create.
     * @return the new {@code PolicyImport}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PolicyImport newInstance(final PolicyId importedPolicyId, final boolean isProtected,
            final EffectedImports effectedImports) {
        return PoliciesModelFactory.newPolicyImport(importedPolicyId, isProtected, effectedImports);
    }

    /**
     * Subject is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of Subject.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the Policy ID of the Policy to import.
     *
     * @return the Policy ID of the Policy to import.
     */
    PolicyId getImportedPolicyId();

    /**
     * Returns whether this import is a protected one meaning that it may only be written once - afterwards noone is
     * able to change or delete it again.
     *
     * @return whether this import is a protected one.
     */
    boolean isProtected();

    /**
     * Returns the {@link EffectedImports} (containing included and excluded ones) for this PolicyImport.
     *
     * @return the effected imported entries.
     */
    EffectedImports getEffectedImports();

    /**
     * Returns all non hidden marked fields of this PolicyImport.
     *
     * @return a JSON object representation of this PolicyImport including only non hidden marked fields.
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
     * An enumeration of the known {@link JsonField}s of a PolicyImport.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion} of a PolicyImport.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the PolicyImport's {@code protected} value.
         */
        public static final JsonFieldDefinition<Boolean> PROTECTED =
                JsonFactory.newBooleanFieldDefinition("protected", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
