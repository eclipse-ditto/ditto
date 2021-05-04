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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable and unsorted collection of granted and revoked {@link WeightedPermission}s.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class WeightedPermissions {

    private final Set<WeightedPermission> granted;
    private final Set<WeightedPermission> revoked;

    /**
     * Constructs a new {@code WeightedPermissions} object.
     */
    public WeightedPermissions() {
        granted = new HashSet<>();
        revoked = new HashSet<>();
    }

    /**
     * Returns all granted permissions. <em>Caution:</em> changes on the result are reflected back into this collection.
     *
     * @return the permissions.
     */
    public Set<WeightedPermission> getAllGranted() {
        return granted;
    }

    /**
     * Returns of all granted permissions those with the highest weight and only those which are part of the
     * passed-in collection. The key of the returned map is the permission.
     *
     * @param permissions the permissions which should be included in the result.
     * @return all granted permissions with the highest weight.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    public Map<String, WeightedPermission> getGrantedWithHighestWeight(final Collection<String> permissions) {
        return getWithHighestWeight(getAllGranted(), permissions);
    }

    private static Map<String, WeightedPermission> getWithHighestWeight(
            final Collection<WeightedPermission> allWeightedPermissions,
            final Collection<String> expectedPermissions) {

        final Map<String, WeightedPermission> result = new HashMap<>(expectedPermissions.size());

        final Map<String, List<WeightedPermission>> groupedByPermission = allWeightedPermissions.stream()
                .collect(Collectors.groupingBy(WeightedPermission::getPermission));

        for (final String expectedPermission : expectedPermissions) {
            final List<WeightedPermission> weightedPermissions = groupedByPermission.get(expectedPermission);
            if (null != weightedPermissions) {
                weightedPermissions.stream()
                        .sorted(Comparator.comparingInt(WeightedPermission::getWeight).reversed())
                        .findFirst()
                        .ifPresent(wp -> result.put(wp.getPermission(), wp));
            }
        }

        return result;
    }

    /**
     * Adds the specified permission as granted permission to this collection.
     *
     * @param weightedPermission the granted weighted permission to be added.
     * @throws NullPointerException if {@code weightedPermission} is {@code null}.
     */
    public void addGranted(final WeightedPermission weightedPermission) {
        checkNotNull(weightedPermission, "granted permission to be added");
        granted.add(weightedPermission);
    }

    /**
     * Adds the specified permission as granted permission with the specified weight to this collection.
     *
     * @param permission the granted permission to be added.
     * @param weight the weight of {@code permission}.
     * @throws NullPointerException if {@code permission} is {@code null}.
     * @throws IllegalArgumentException if {@code permission} is empty.
     * @see #addGranted(WeightedPermission)
     */
    public void addGranted(final CharSequence permission, final int weight) {
        addGranted(WeightedPermission.of(permission, weight));
    }

    /**
     * Adds the specified permissions as granted permissions with the specified weight to this collection.
     *
     * @param permissions the granted permissions to be added.
     * @param weight the weight of each of {@code permissions}.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     * @see #addGranted(WeightedPermission)
     */
    public void addGranted(final Iterable<String> permissions, final int weight) {
        for (final CharSequence permission : permissions) {
            addGranted(permission, weight);
        }
    }

    /**
     * Returns all revoked permissions. <em>Caution:</em> changes on the result are reflected back into this collection.
     *
     * @return the permissions.
     */
    public Set<WeightedPermission> getAllRevoked() {
        return revoked;
    }

    /**
     * Returns of all revoked permissions those with the highest weight and only those which are part of the
     * passed-in collection. The key of the returned map is the permission.
     *
     * @param permissions the permissions which should be included in the result.
     * @return all revoked permissions with the highest weight.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    public Map<String, WeightedPermission> getRevokedWithHighestWeight(final Collection<String> permissions) {
        return getWithHighestWeight(getAllRevoked(), permissions);
    }

    /**
     * Adds the specified permission as revoked permission to this collection.
     *
     * @param weightedPermission the revoked weighted permission to be added.
     * @throws NullPointerException if {@code weightedPermission} is {@code null}.
     */
    public void addRevoked(final WeightedPermission weightedPermission) {
        checkNotNull(weightedPermission, "revoked permission to be added");
        revoked.add(weightedPermission);
    }

    /**
     * Adds the specified permission as revoked permission with the specified weight to this collection.
     *
     * @param permission the revoked permission to be added.
     * @param weight the weight of {@code permission}.
     * @throws NullPointerException if {@code permission} is {@code null}.
     * @throws IllegalArgumentException if {@code permission} is empty.
     * @see #addGranted(WeightedPermission)
     */
    public void addRevoked(final CharSequence permission, final int weight) {
        addRevoked(WeightedPermission.of(permission, weight));
    }

    /**
     * Adds the specified permissions as revoked permissions with the specified weight to this collection.
     *
     * @param permissions the revoked permissions to be added.
     * @param weight the weight of each of {@code permissions}.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     * @see #addGranted(WeightedPermission)
     */
    public void addRevoked(final Iterable<String> permissions, final int weight) {
        for (final CharSequence permission : permissions) {
            addRevoked(permission, weight);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "granted=" + granted +
                ", revoked=" + revoked +
                "]";
    }

}
