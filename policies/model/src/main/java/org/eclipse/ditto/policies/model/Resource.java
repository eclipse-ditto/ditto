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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Represents a single Resource (a {@link JsonPointer}) in the {@link Resources} of a {@link PolicyEntry}.
 * A Resource has a {@code Type} which determines the entity governed by this policy and {@link Permissions} which are
 * granted or revoked on this Resource.
 */
public interface Resource extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code Resource} with the specified {@code resourceKey} and {@code effectedPermissions}.
     *
     * @param resourceKey the ResourceKey of the new Resource to create.
     * @param effectedPermissions the EffectedPermissions of the new Resource to create.
     * @return the new {@code Resource}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Resource newInstance(final ResourceKey resourceKey, final EffectedPermissions effectedPermissions) {
        return PoliciesModelFactory.newResource(resourceKey, effectedPermissions);
    }

    /**
     * Returns a new {@code Resource} with the specified {@code resourceKey} and {@code effectedPermissions}.
     *
     * @param resourceKey the ResourceKey of the new Resource to create.
     * @param effectedPermissions the EffectedPermissions of the new Resource to create.
     * @return the new {@code Resource}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Resource newInstance(final ResourceKey resourceKey, final JsonValue effectedPermissions) {
        return PoliciesModelFactory.newResource(resourceKey, effectedPermissions);
    }

    /**
     * Returns a new {@code Resource} with the specified {@code resourceType}, {@code resourcePath} and
     * {@code effectedPermissions}.
     *
     * @param resourceType the type of the new Resource to create.
     * @param resourcePath the path of the new Resource to create.
     * @param effectedPermissions the EffectedPermissions of the new Resource to create.
     * @return the new {@code Resource}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code resourceType} is empty.
     */
    static Resource newInstance(final CharSequence resourceType, final CharSequence resourcePath,
            final EffectedPermissions effectedPermissions) {

        return PoliciesModelFactory.newResource(resourceType, resourcePath, effectedPermissions);
    }

    /**
     * Returns the supported JSON schema versions of this resource. <em>Resource is only available in JsonSchemaVersion
     * V_2.</em>
     *
     * @return the supported schema versions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the ResourceKey of this Resource.
     *
     * @return the resource key.
     */
    ResourceKey getResourceKey();

    /**
     * Returns the type of this Resource.
     *
     * @return the type.
     */
    String getType();

    /**
     * Returns the path of this Resource.
     *
     * @return the path.
     */
    JsonPointer getPath();

    /**
     * Returns the full qualified path (type:path) of this Resource.
     *
     * @return the full qualified path.
     */
    String getFullQualifiedPath();

    /**
     * Returns the {@link EffectedPermissions} (containing granted and revoked ones) for this Resource.
     *
     * @return the effected permissions.
     */
    EffectedPermissions getEffectedPermissions();

    /**
     * Returns all non-hidden marked fields of this Resource.
     *
     * @return a JSON object representation of this Resource including only non-hidden marked fields.
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
     * An enumeration of the known {@link JsonField}s of a Resource.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion}.
         *
         * @deprecated as of 2.3.0 this field definition is not used anymore.
         */
        @Deprecated
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION = JsonFactory.newIntFieldDefinition(
                JsonSchemaVersion.getJsonKey(),
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2
        );

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
