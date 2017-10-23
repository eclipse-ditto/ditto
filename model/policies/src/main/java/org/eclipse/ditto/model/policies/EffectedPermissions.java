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
     * Returns all non hidden marked fields of this EffectedPermissions.
     *
     * @return a JSON object representation of this EffectedPermissions including only non hidden marked fields.
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
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
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
