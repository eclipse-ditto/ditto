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
package org.eclipse.ditto.model.policiesenforcers.trie;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.ImmutableEffectedSubjectIds;

/**
 * Index of granted/revoked permissions and subjects for a policy resource.
 */
final class GrantRevokeIndex {

    private final PermissionSubjectsMap grantMap;
    private final PermissionSubjectsMap revokeMap;

    /**
     * Creates an empty {@code GrantRevokeIndex}.
     */
    GrantRevokeIndex() {
        this(new PermissionSubjectsMap(), new PermissionSubjectsMap());
    }

    /**
     * Creates a {@code GrantRevokeIndex} and initialize its grant-map and its revoke-map.
     *
     * @param grantMap The grant-map field of the constructed object.
     * @param revokeMap The revoke-map field of the constructed object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    GrantRevokeIndex(final PermissionSubjectsMap grantMap, final PermissionSubjectsMap revokeMap) {
        this.grantMap = checkNotNull(grantMap, "grant map");
        this.revokeMap = checkNotNull(revokeMap, "revoke map");
    }

    /**
     * Returns the map of permissions granted to subjects.
     *
     * @return The grant-map.
     */
    PermissionSubjectsMap getGranted() {
        return grantMap;
    }

    /**
     * Returns the map of permissions revoked from subjects.
     *
     * @return The revoke-map.
     */
    PermissionSubjectsMap getRevoked() {
        return revokeMap;
    }

    /**
     * Copy this {@code GrantRevokeIndex} and decrease the weight of each permission-subject pair by 1.
     *
     * @return A copy of {@code this} with decremented weight.
     */
    GrantRevokeIndex copyWithDecrementedWeight() {
        return new GrantRevokeIndex(grantMap.copyWithDecrementedWeight(), revokeMap.copyWithDecrementedWeight());
    }

    /**
     * Mutate this object according to an overriding {@code GrantRevokeIndex}.
     *
     * @param update The {@code GrantRevokeIndex} to override {@code this}: grants in {@code update} are added to the
     * grant-set and removed from the revoke-set of this object, and revokes in {@code update} are deleted from the
     * grant-set and added to the revoke-set of this object.
     * @return This object after the mutation.
     * @throws NullPointerException if {@code update} is {@code null}.
     */
    GrantRevokeIndex overrideBy(final GrantRevokeIndex update) {
        checkNotNull(update, "update");
        grantMap.addAllEntriesFrom(update.grantMap).removeAllEntriesFrom(update.revokeMap);
        revokeMap.removeAllEntriesFrom(update.grantMap).addAllEntriesFrom(update.revokeMap);
        return this;
    }

    /**
     * Check whether each of the given permissions is granted to some of the given authorization subject such that
     * none of the permissions is revoked from any of the subject IDs with the same or a greater weight.
     *
     * @param subjectIds Authorization subject IDs to check.
     * @param permissions Permissions to check.
     * @return Result of the check.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean hasPermissions(final Set<String> subjectIds, final Collection<String> permissions) {
        final Optional<Integer> grantWeight =
                grantMap.getMaxNonemptyWeightForAllPermissions(subjectIds, permissions);

        final Optional<Integer> revokeWeight =
                revokeMap.getMaxWeightForAllPermissions(subjectIds, permissions);

        return grantWeight.isPresent() && (!revokeWeight.isPresent() || revokeWeight.get() < grantWeight.get());
    }

    /**
     * Returns the set of authorization subject IDs for whom <em>all</em> of the given permissions are granted, and the
     * set of authorization subject IDs for whom <em>any</em> of the given permissions are revoked.
     *
     * @param permissions Permissions to check.
     * @return An object containing the two sets of authorization subjects.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    EffectedSubjectIds getEffectedSubjectIds(final Set<String> permissions) {
        final Set<String> grantedSubjectIds = getGrantedSubjectIds(permissions);
        final Set<String> revokedSubjectIds = getRevokedSubjectIds(permissions);
        return ImmutableEffectedSubjectIds.of(grantedSubjectIds, revokedSubjectIds);
    }

    /**
     * Returns the set of subjects granted at this level.
     *
     * @param permissions Permissions to check.
     * @return An object containing the two sets of authorization subjects.
     */
    Set<String> getGrantedSubjectIds(final Set<String> permissions) {
        checkNotNull(permissions, "permissions to check");
        return grantMap.getSubjectIntersect(permissions).keySet();
    }

    Set<String> getRevokedSubjectIds(final Set<String> permissions) {
        checkNotNull(permissions, "permissions to check");
        return revokeMap.getSubjectUnion(permissions).keySet();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GrantRevokeIndex that = (GrantRevokeIndex) o;
        return Objects.equals(grantMap, that.grantMap) &&
                Objects.equals(revokeMap, that.revokeMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grantMap, revokeMap);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "grantMap=" + grantMap +
                ", revokeMap=" + revokeMap +
                "]";
    }

}
