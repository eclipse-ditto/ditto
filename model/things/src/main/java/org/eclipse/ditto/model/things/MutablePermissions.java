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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * A mutable implementation of {@link Permissions}.
 */
@NotThreadSafe
final class MutablePermissions extends AbstractSet<Permission> implements Permissions {

    private final Set<Permission> values;

    /**
     * Constructs a new {@code MutablePermissions} object containing the given values.
     *
     * @param permissions the items of the result.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    MutablePermissions(final Set<Permission> permissions) {
        values = checkNotNull(permissions, "permissions");
    }

    /**
     * Constructs a new {@code MutablePermissions} object which contains the given values.
     *
     * @param permissions the values to initialise the new permissions with.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    public MutablePermissions(final Collection<Permission> permissions) {
        checkNotNull(permissions, "permissions");

        if (!permissions.isEmpty()) {
            values = EnumSet.copyOf(permissions);
        } else {
            values = EnumSet.noneOf(Permission.class);
        }
    }

    /**
     * Returns a new empty set of permissions.
     *
     * @return a new empty set of permissions.
     */
    public static Permissions none() {
        return new MutablePermissions(EnumSet.noneOf(Permission.class));
    }

    /**
     * Returns a new {@code Permissions} object which is initialised with all known {@link Permission}s.
     *
     * @return a new {@code Permissions} object which is initialised with all known {@code Permission}s.
     */
    public static Permissions all() {
        return new MutablePermissions(EnumSet.allOf(Permission.class));
    }

    /**
     * Returns a new {@code Permissions} object which is initialised with the given permissions.
     *
     * @param permission the mandatory permission to initialise the result with.
     * @param furtherPermissions additional permissions to initialise the result with.
     * @return a new {@code Permissions} object which is initialised with {@code permission} and {@code
     * furtherPermissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Permissions of(final Permission permission, final Permission... furtherPermissions) {
        return new MutablePermissions(EnumSet.of(permission, furtherPermissions));
    }

    @Override
    public boolean contains(final Permission permission, final Permission... furtherPermissions) {
        checkNotNull(permission, "permission whose presence is to be checked");
        checkNotNull(furtherPermissions, "further permissions whose presence are to be checked");

        return values.containsAll(EnumSet.of(permission, furtherPermissions));
    }

    /**
     * Adds the specified permission to this set.
     *
     * @param permission the permission to be added.
     * @return {@code true} if this set did not already contain {@code permission}.
     * @throws NullPointerException if {@code permission} is {@code null}.
     */
    @Override
    public boolean add(final Permission permission) {
        checkNotNull(permission, "permission to be added");
        return values.add(permission);
    }

    @Override
    public Iterator<Permission> iterator() {
        return values.iterator();
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        // explicitly DO NOT use the schemaVersion in the predicate - as there is no schema version on the field definitions
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        for (final Permission permission : Permission.values()) {
            jsonObjectBuilder.set(permission.toJsonKey(), JsonFactory.newValue(values.contains(permission)));
        }
        return jsonObjectBuilder.build();
    }

    @Override
    public JsonObject toJson(final JsonPointer pointer) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        for (final Permission permission : Permission.values()) {
            final JsonKey permissionJsonKey = permission.toJsonKey();
            if (Objects.equals(permissionJsonKey, pointer)) {
                jsonObjectBuilder.set(permissionJsonKey, JsonFactory.newValue(values.contains(permission)));
            }
        }
        return jsonObjectBuilder.build();
    }

}
