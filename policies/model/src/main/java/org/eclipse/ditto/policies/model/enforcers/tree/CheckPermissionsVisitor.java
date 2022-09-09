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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Permissions;

/**
 * Abstract base implementation for visitors which check permissions on
 * {@link org.eclipse.ditto.policies.model.enforcers.tree.ResourceNode}s.
 */
@ParametersAreNonnullByDefault
abstract class CheckPermissionsVisitor implements Visitor<Boolean> {

    private final Collection<String> authSubjectIds;
    private final Permissions expectedPermissions;

    private final Function<JsonPointer, PointerLocation> pointerLocationEvaluator;
    private final WeightedPermissions weightedPermissions;
    private boolean collectPermissions;

    /**
     * Constructs a new {@code CheckPermissionsVisitor} object.
     *
     * @param resourcePointer path of the resource to be checked as JSON pointer.
     * @param authSubjectIds the authorization subjects whose permissions are checked.
     * @param expectedPermissions the expected permissions of {@code authSubjectIds} on {@code resourcePointer}
     * regarding this visitor's purpose.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code authSubjectIds} or {@code expectedPermissions} is empty.
     */
    protected CheckPermissionsVisitor(final JsonPointer resourcePointer, final Collection<String> authSubjectIds,
            final Permissions expectedPermissions) {

        this.authSubjectIds = argumentNotEmpty(authSubjectIds, "authorization subject IDS");
        this.expectedPermissions = argumentNotEmpty(expectedPermissions, "expected permissions");

        pointerLocationEvaluator = new PointerLocationEvaluator(resourcePointer);
        weightedPermissions = new WeightedPermissions();
        collectPermissions = false;
    }

    @Override
    public void visitTreeNode(final PolicyTreeNode node) {
        if (PolicyTreeNode.Type.SUBJECT == node.getType()) {
            visitSubjectNode(node);
        } else {
            visitResourceNode((ResourceNode) node);
        }
    }

    private void visitSubjectNode(final PolicyTreeNode subjectNode) {
        collectPermissions = authSubjectIds.contains(subjectNode.getName());
    }

    private void visitResourceNode(final ResourceNode resourceNode) {
        if (collectPermissions) {
            aggregateWeightedPermissions(resourceNode, weightedPermissions);
        }
    }

    /**
     * Adds the weighted permissions of the specified resource node to the specified collection.
     *
     * @param resourceNode the resource node which provides its particular permissions.
     * @param weightedPermissions the weighted permissions to be used for aggregation.
     */
    protected abstract void aggregateWeightedPermissions(ResourceNode resourceNode,
            WeightedPermissions weightedPermissions);

    /**
     * Returns the location of the path of the specified resource node in relation to the target resource path of
     * this visitor.
     *
     * @param resourceNode the resource node whose path is compared to the target resource path of this visitor.
     * @return the location.
     */
    protected final PointerLocation getLocationInRelationToTargetPointer(final ResourceNode resourceNode) {
        return pointerLocationEvaluator.apply(resourceNode.getAbsolutePointer());
    }

    @Override
    public Boolean get() {
        return determineResult();
    }

    private boolean determineResult() {
        final Map<String, WeightedPermission> granted =
                weightedPermissions.getGrantedWithHighestWeight(expectedPermissions);

        return granted.size() == expectedPermissions.size() && !isAnyRevokedWithHigherWeight(granted);
    }

    private boolean isAnyRevokedWithHigherWeight(final Map<String, WeightedPermission> granted) {
        final Map<String, WeightedPermission> revoked =
                weightedPermissions.getRevokedWithHighestWeight(expectedPermissions);

        for (final String expectedPermission : expectedPermissions) {
            final WeightedPermission grantedPermission = granted.get(expectedPermission);
            final WeightedPermission revokedPermission = revoked.get(expectedPermission);

            if (isRevokedWithHigherWeight(grantedPermission, revokedPermission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRevokedWithHigherWeight(@Nullable final WeightedPermission grantedPermission,
            @Nullable final WeightedPermission revokedPermission) {

        boolean result = false;

        if (null != revokedPermission) {
            if (null != grantedPermission) {
                final int revokedPermissionWeight = revokedPermission.getWeight();
                final int grantedPermissionWeight = grantedPermission.getWeight();
                if (grantedPermissionWeight <= revokedPermissionWeight) {
                    result = true;
                }
            } else {
                result = true;
            }
        }
        return result;
    }

}
