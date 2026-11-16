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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Permissions;

/**
 * Checks whether the authorization subjects have the expected permissions granted on the resource or on any of its
 * sub resources. A grant on a sub resource only counts if none of the authorization subjects has the same permission
 * revoked on that sub resource, as a revoke wins over a grant on the same level.
 */
@NotThreadSafe
@ParametersAreNonnullByDefault
final class CheckPartialPermissionsVisitor extends CheckPermissionsVisitor {

    private final Map<JsonPointer, SubResourcePermissions> subResourcePermissions;

    /**
     * Constructs a new {@code CheckPartialPermissionsVisitor} object.
     *
     * @param resourcePointer path of the resource to be checked as JSON pointer.
     * @param authSubjectIds the authorization subjects whose permissions are checked.
     * @param expectedPermissions the expected permissions of {@code authSubjectIds} on {@code resourcePointer}
     * regarding this visitor's purpose.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code authSubjectIds} or {@code expectedPermissions} is empty.
     */
    CheckPartialPermissionsVisitor(final JsonPointer resourcePointer, final Collection<String> authSubjectIds,
            final Permissions expectedPermissions) {

        super(resourcePointer, authSubjectIds, expectedPermissions);
        subResourcePermissions = new HashMap<>();
    }

    @Override
    protected void aggregateWeightedPermissions(final ResourceNode resourceNode,
            final WeightedPermissions weightedPermissions) {

        final EffectedPermissions effectedPermissions = resourceNode.getPermissions();
        final Permissions grantedPermissions = effectedPermissions.getGrantedPermissions();
        final Permissions revokedPermissions = effectedPermissions.getRevokedPermissions();

        final PointerLocation pointerLocation = getLocationInRelationToTargetPointer(resourceNode);
        if (PointerLocation.ABOVE == pointerLocation || PointerLocation.SAME == pointerLocation) {
            weightedPermissions.addGranted(grantedPermissions, resourceNode.getLevel());
            weightedPermissions.addRevoked(revokedPermissions, resourceNode.getLevel());
        } else if (PointerLocation.BELOW == pointerLocation &&
                (!grantedPermissions.isEmpty() || !revokedPermissions.isEmpty())) {
            // the grants and revokes of all authorization subjects on a sub resource have to be known before the
            // grants of that sub resource can be aggregated
            subResourcePermissions.computeIfAbsent(resourceNode.getAbsolutePointer(),
                            pointer -> new SubResourcePermissions(resourceNode.getLevel()))
                    .add(grantedPermissions, revokedPermissions);
        }
    }

    @Override
    protected void completeWeightedPermissions(final WeightedPermissions weightedPermissions) {
        for (final SubResourcePermissions permissions : subResourcePermissions.values()) {
            weightedPermissions.addGrantedUnlessRevoked(permissions.granted, permissions.revoked, permissions.level);
        }
    }

    /**
     * The granted and revoked permissions of all authorization subjects on one sub resource.
     */
    private static final class SubResourcePermissions {

        private final int level;
        private final Set<String> granted;
        private final Set<String> revoked;

        private SubResourcePermissions(final int level) {
            this.level = level;
            granted = new HashSet<>();
            revoked = new HashSet<>();
        }

        private void add(final Collection<String> grantedPermissions, final Collection<String> revokedPermissions) {
            granted.addAll(grantedPermissions);
            revoked.addAll(revokedPermissions);
        }

    }

}
