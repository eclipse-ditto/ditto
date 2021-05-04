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

import java.util.Set;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * This Set is dedicated to hold permissions only. Additionally to the methods defined in {@link java.util.Set} this type also
 * implements {@link Jsonifiable} which means that a JSON representation can be obtained. This representation is a
 * JSON array where the available permissions are either contained in the array or not.
 */
public interface Permissions extends Set<String>, Jsonifiable<JsonArray> {

    /**
     * Returns a new immutable instance of {@code Permissions} containing the given permissions.
     *
     * @param permission the mandatory permission to be contained in the result.
     * @param furtherPermissions additional permissions to be contained in the result.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Permissions newInstance(final String permission, final String... furtherPermissions) {
        return PoliciesModelFactory.newPermissions(permission, furtherPermissions);
    }

    /**
     * Returns a new immutable instance of {@code Permissions} containing no permissions.
     *
     * @return the new {@code Permissions}.
     */
    static Permissions none() {
        return PoliciesModelFactory.noPermissions();
    }

    /**
     * Permissions is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of Permissions.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
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
    boolean contains(String permission, String... furtherPermissions);

    /**
     * Indicates whether this set of permissions contains the specified permission(s). The result is {@code true} if
     * <em>all</em> specified permissions are contained in this set.
     *
     * @param permissions the permissions to be contained
     * @return {@code true} if this set contains each specified permission, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean contains(Permissions permissions);

}
