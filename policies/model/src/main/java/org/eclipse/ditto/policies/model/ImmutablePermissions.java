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
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;

/**
 * Immutable implementation of {@link Permissions}.
 * <p>
 * Policies repeat a handful of distinct permission sets millions of times, so instances are shared: the permissions
 * are kept as a sorted array, the empty set is a singleton and equal sets resolve to one cached instance.
 * </p>
 */
@Immutable
final class ImmutablePermissions extends AbstractSet<String> implements Permissions {

    private static final ImmutablePermissions NONE = new ImmutablePermissions(new String[0]);
    private static final InternCache<ImmutablePermissions> CACHE = new InternCache<>(256);

    private final String[] permissions;
    private final int hashCode;

    private ImmutablePermissions(final String[] sortedDistinctPermissions) {
        permissions = sortedDistinctPermissions;
        int hash = 0;
        for (final String permission : sortedDistinctPermissions) {
            hash += permission.hashCode();
        }
        hashCode = hash;
    }

    /**
     * Returns an empty set of permissions.
     *
     * @return an empty set of permissions.
     */
    public static Permissions none() {
        return NONE;
    }

    /**
     * Returns a {@code Permissions} object which is initialised with the given permissions.
     *
     * @param permission the mandatory permission to initialise the result with.
     * @param furtherPermissions additional permissions to initialise the result with.
     * @return a {@code Permissions} object which is initialised with {@code permission} and {@code
     * furtherPermissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Permissions of(final String permission, final String... furtherPermissions) {
        checkNotNull(permission, "permission");
        checkNotNull(furtherPermissions, "further permissions");

        final String[] permissions = new String[1 + furtherPermissions.length];
        permissions[0] = permission;
        System.arraycopy(furtherPermissions, 0, permissions, 1, furtherPermissions.length);
        return shared(permissions);
    }

    /**
     * Returns a {@code Permissions} object which is initialised with the given permissions.
     *
     * @param permissions the permissions to initialise the result with.
     * @return a {@code Permissions} object which is initialised with {@code permissions}.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    public static Permissions of(final Collection<String> permissions) {
        checkNotNull(permissions, "permissions");

        if (permissions instanceof ImmutablePermissions) {
            return (ImmutablePermissions) permissions;
        }
        return shared(permissions.toArray(new String[0]));
    }

    private static ImmutablePermissions shared(final String[] ownedPermissions) {
        if (ownedPermissions.length == 0) {
            return NONE;
        }
        for (final String permission : ownedPermissions) {
            checkNotNull(permission, "permission");
        }
        Arrays.sort(ownedPermissions);
        int distinct = 1;
        for (int i = 1; i < ownedPermissions.length; i++) {
            if (!ownedPermissions[i].equals(ownedPermissions[distinct - 1])) {
                ownedPermissions[distinct++] = ownedPermissions[i];
            }
        }
        final String[] sortedDistinct = distinct == ownedPermissions.length
                ? ownedPermissions
                : Arrays.copyOf(ownedPermissions, distinct);
        return CACHE.intern(new ImmutablePermissions(sortedDistinct));
    }

    @Override
    public boolean contains(@Nullable final Object o) {
        for (final String permission : permissions) {
            if (permission.equals(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(final String permission, final String... furtherPermissions) {
        checkNotNull(permission, "permission whose presence is to be checked");
        checkNotNull(furtherPermissions, "further permissions whose presence are to be checked");

        if (!contains(permission)) {
            return false;
        }
        for (final String furtherPermission : furtherPermissions) {
            if (!contains(furtherPermission)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean contains(final Permissions permissions) {
        checkNotNull(permissions, "permissions whose presence is to be checked");

        return containsAll(permissions);
    }

    @Override
    public int size() {
        return permissions.length;
    }

    @Override
    public JsonArray toJson() {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        for (final String permission : permissions) {
            jsonArrayBuilder.add(permission);
        }
        return jsonArrayBuilder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof ImmutablePermissions) {
            final ImmutablePermissions other = (ImmutablePermissions) o;
            return hashCode == other.hashCode && Arrays.equals(permissions, other.permissions);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Nonnull
    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < permissions.length;
            }

            @Override
            public String next() {
                if (index >= permissions.length) {
                    throw new NoSuchElementException();
                }
                return permissions[index++];
            }
        };
    }

    // now all methods from Collection which should not be supported as we have an immutable data structure:

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
