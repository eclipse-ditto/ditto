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
package org.eclipse.ditto.model.things;

import java.util.Set;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * This set is dedicated to hold {@link Permission}s only. Additionally to the methods defined in {@link Set} this type
 * also implements {@link Jsonifiable} which means that a JSON representation can be obtained. This representation is a
 * JSON object where all constants of {@code Permission} is a JSON key and the value is a JSON boolean literal which
 * indicates whether that permission is contained in this set or not.
 */
public interface Permissions extends Set<Permission>, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new <em>mutable</em> {@link Permissions} containing the given permissions.
     *
     * @param permission the mandatory permission to be contained in the result.
     * @param furtherPermissions additional permissions to be contained in the result.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Permissions newInstance(final Permission permission, final Permission... furtherPermissions) {
        return AccessControlListModelFactory.newPermissions(permission, furtherPermissions);
    }

    /**
     * Permissions is only available in JsonSchemaVersion V_1.
     *
     * @return the supported JsonSchemaVersions of Permissions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_1};
    }

    /**
     * Indicates whether this set of permissions contains the specified permission(s). The result is {@code true} if
     * <em>all</em> specified permissions are contained in this set.
     *
     * @param permission the permission whose presence in this set is to be tested.
     * @param furtherPermissions additional permissions whose presence in this set is to be tested.
     * @return {@code true} if this set contains each specified permission, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean contains(Permission permission, Permission... furtherPermissions);

    /**
     * Returns all non hidden marked fields of this permissions.
     *
     * @return a JSON object representation of this permissions including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

}
