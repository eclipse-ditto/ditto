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
 * Holds {@link Permissions} for {@link PermissionEffect}s (grant/revoke).
 */
public interface EffectedPermissions extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code EffectedPermissions} containing the given {@code grantedPermissions} and {@code
     * revokedPermissions}.
     *
     * @param grantedPermissions the Permissions which should be granted, may be {@code null}.
     * @param revokedPermissions the Permissions which should be revoked, may be {@code null}.
     * @return the new {@code EffectedPermissions}.
     */
    static EffectedPermissions newInstance(@Nullable final Iterable<String> grantedPermissions,
            @Nullable final Iterable<String> revokedPermissions) {

        return PoliciesModelFactory.newEffectedPermissions(grantedPermissions, revokedPermissions);
    }

    /**
     * EffectedPermissions is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of EffectedPermissions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the {@link Permissions} which are valid for the passed {@code effect}.
     *
     * @param effect the PermissionEffect for which to return the Permissions.
     * @return the Permissions which are valid for the passed effect.
     * @throws NullPointerException if {@code effect} is {@code null}.
     * @throws IllegalArgumentException if {@code effect} is unknown.
     */
    Permissions getPermissions(PermissionEffect effect);

    /**
     * Returns the granted {@link Permissions}.
     *
     * @return the granted Permissions.
     */
    default Permissions getGrantedPermissions() {
        return getPermissions(PermissionEffect.GRANT);
    }

    /**
     * Returns the revoked {@link Permissions}.
     *
     * @return the revoked Permissions.
     */
    default Permissions getRevokedPermissions() {
        return getPermissions(PermissionEffect.REVOKE);
    }

    /**
     * Returns all non-hidden marked fields of this EffectedPermissions.
     *
     * @return a JSON object representation of this EffectedPermissions including only non-hidden marked fields.
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
     * An enumeration of the known {@link JsonField}s of a EffectedPermissions.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion}.
         *
         * @deprecated as of 2.3.0 this field definition is not used anymore.
         */
        @Deprecated
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(),
                        FieldType.SPECIAL,
                        FieldType.HIDDEN,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the EffectedPermissions's {@code grant}ed Permissions.
         */
        public static final JsonFieldDefinition<JsonArray> GRANT =
                JsonFactory.newJsonArrayFieldDefinition("grant", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the EffectedPermissions's {@code revoke}d Permissions.
         */
        public static final JsonFieldDefinition<JsonArray> REVOKE =
                JsonFactory.newJsonArrayFieldDefinition("revoke", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
