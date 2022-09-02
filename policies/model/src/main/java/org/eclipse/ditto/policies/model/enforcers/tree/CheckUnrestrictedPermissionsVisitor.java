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

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Permissions;

/**
 */
@NotThreadSafe
@ParametersAreNonnullByDefault
final class CheckUnrestrictedPermissionsVisitor extends CheckPermissionsVisitor {

    /**
     * Constructs a new {@code CheckUnrestrictedPermissionsVisitor} object.
     *
     * @param resourcePointer path of the resource to be checked as JSON pointer.
     * @param authSubjectIds the authorization subjects whose permissions are checked.
     * @param expectedPermissions the expected permissions of {@code authSubjectIds} on {@code resourcePointer}
     * regarding this visitor's purpose.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code authSubjectIds} or {@code expectedPermissions} is empty.
     */
    CheckUnrestrictedPermissionsVisitor(final JsonPointer resourcePointer, final Collection<String> authSubjectIds,
            final Permissions expectedPermissions) {

        super(resourcePointer, authSubjectIds, expectedPermissions);
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
        } else if (PointerLocation.BELOW == pointerLocation) {
            weightedPermissions.addRevoked(revokedPermissions, resourceNode.getLevel());
        }
    }

}
