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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;

/**
 * Immutable implementation of {@link Permissions}.
 */
@Immutable
final class ImmutablePermissions extends AbstractSet<String> implements Permissions {

    private final Set<String> permissions;

    private ImmutablePermissions(final Set<String> permissions) {
        checkNotNull(permissions, "permissions");
        this.permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
    }

    /**
     * Returns a new empty set of permissions.
     *
     * @return a new empty set of permissions.
     */
    public static Permissions none() {
        return new ImmutablePermissions(Collections.emptySet());
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
    public static Permissions of(final String permission, final String... furtherPermissions) {
        checkNotNull(permission, "permission");
        checkNotNull(furtherPermissions, "further permissions");

        final HashSet<String> permissions = new HashSet<>(1 + furtherPermissions.length);
        permissions.add(permission);
        Collections.addAll(permissions, furtherPermissions);

        return new ImmutablePermissions(permissions);
    }

    /**
     * Returns a new {@code Permissions} object which is initialised with the given permissions.
     *
     * @param permissions the permissions to initialise the result with.
     * @return a new {@code Permissions} object which is initialised with {@code permissions}.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    public static Permissions of(final Collection<String> permissions) {
        checkNotNull(permissions, "permissions");

        final HashSet<String> permissionSet = new HashSet<>();

        if (!permissions.isEmpty()) {
            permissionSet.addAll(permissions);
        }

        return new ImmutablePermissions(permissionSet);
    }

    @Override
    public boolean contains(final String permission, final String... furtherPermissions) {
        checkNotNull(permission, "permission whose presence is to be checked");
        checkNotNull(furtherPermissions, "further permissions whose presence are to be checked");

        final HashSet<String> permissionSet = new HashSet<>();
        permissionSet.add(permission);
        permissionSet.addAll(Arrays.asList(furtherPermissions));

        return permissions.containsAll(permissionSet);
    }

    @Override
    public boolean contains(final Permissions permissions) {
        checkNotNull(permissions, "permissions whose presence is to be checked");

        return this.permissions.containsAll(permissions);
    }

    @Override
    public int size() {
        return permissions.size();
    }

    @Override
    public JsonArray toJson() {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        permissions.forEach(jsonArrayBuilder::add);
        return jsonArrayBuilder.build();
    }

    // now all methods from Collection which should not be supported as we have an immutable data structure:

    @Nonnull
    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            private final Iterator<String> i = permissions.iterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public String next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void forEachRemaining(final Consumer<? super String> action) {
                // Use backing collection version
                i.forEachRemaining(action);
            }
        };
    }

    @Override
    public boolean add(final String e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(@Nonnull final Collection<? extends String> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@Nonnull final Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@Nonnull final Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(final Predicate<? super String> filter) {
        throw new UnsupportedOperationException();
    }

}
