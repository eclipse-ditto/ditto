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
package org.eclipse.ditto.model.policiesenforcers.tree;

import java.util.Collection;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Permissions;

/**
 */
@NotThreadSafe
@ParametersAreNonnullByDefault
final class CheckPartialPermissionsVisitor extends CheckPermissionsVisitor {

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
            weightedPermissions.addGranted(grantedPermissions, resourceNode.getLevel());
        }
    }

}
