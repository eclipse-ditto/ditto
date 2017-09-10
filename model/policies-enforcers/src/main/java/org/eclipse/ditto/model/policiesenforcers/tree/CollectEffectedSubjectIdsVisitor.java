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
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.ImmutableEffectedSubjectIds;

/**
 */
@NotThreadSafe
@ParametersAreNonnullByDefault
final class CollectEffectedSubjectIdsVisitor implements Visitor<EffectedSubjectIds> {

    private final Permissions expectedPermissions;

    private final Function<JsonPointer, PointerLocation> pointerLocationEvaluator;
    private final ImmutableEffectedSubjectIds.Builder effectedSubjectIdsBuilder;
    private final Collection<ResourceNodeEvaluator> evaluators;
    private ResourceNodeEvaluator currentEvaluator;

    /**
     * Constructs a new {@code CollectEffectedSubjectIdsVisitor} object.
     *
     * @param resourcePointer
     * @param expectedPermissions
     * @throws NullPointerException if any argument is {@code null}.
     */
    CollectEffectedSubjectIdsVisitor(final JsonPointer resourcePointer, final Permissions expectedPermissions) {
        this.expectedPermissions = expectedPermissions;

        pointerLocationEvaluator = new PointerLocationEvaluator(resourcePointer);
        effectedSubjectIdsBuilder = ImmutableEffectedSubjectIds.getBuilder();
        evaluators = new HashSet<>();
        currentEvaluator = null;
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
        final String currentSubjectId = subjectNode.getName();

        // We know that we visit each SubjectNode exactly once.
        currentEvaluator = new ResourceNodeEvaluator(currentSubjectId);
        evaluators.add(currentEvaluator);
    }

    private void visitResourceNode(final ResourceNode resourceNode) {
        currentEvaluator.aggregateWeightedPermissions(resourceNode);
    }

    @Override
    public EffectedSubjectIds get() {

        // populate effectedSubjectIdsBuilder via side effect
        evaluators.forEach(ResourceNodeEvaluator::evaluate);
        return effectedSubjectIdsBuilder.build();
    }

    /**
     * The permissions have to be evaluated for each subject ID separately as this visitor's task is to collect
     * subject IDs. This class aggregates and evaluates the permissions for a particular subject ID. If the subject
     * ID is granted or revoked an expected permission it is added to the effectedSubjectIdsBuilder via side effect.
     */
    @NotThreadSafe
    private final class ResourceNodeEvaluator {

        private final String subjectId;
        private final WeightedPermissions weightedPermissionsForSubjectId;

        private ResourceNodeEvaluator(final String subjectId) {
            this.subjectId = subjectId;
            weightedPermissionsForSubjectId = new WeightedPermissions();
        }

        private void aggregateWeightedPermissions(final ResourceNode resourceNode) {
            final PointerLocation pointerLocation = getLocationInRelationToTargetPointer(resourceNode);
            if (PointerLocation.ABOVE == pointerLocation || PointerLocation.SAME == pointerLocation) {
                final EffectedPermissions effectedPermissions = resourceNode.getPermissions();
                final Permissions grantedPermissions = effectedPermissions.getGrantedPermissions();
                final Permissions revokedPermissions = effectedPermissions.getRevokedPermissions();
                weightedPermissionsForSubjectId.addGranted(grantedPermissions, resourceNode.getLevel());
                weightedPermissionsForSubjectId.addRevoked(revokedPermissions, resourceNode.getLevel());
            }
        }

        private PointerLocation getLocationInRelationToTargetPointer(final ResourceNode resourceNode) {
            return pointerLocationEvaluator.apply(resourceNode.getAbsolutePointer());
        }

        private void evaluate() {

            final Map<String, WeightedPermission> revoked =
                    weightedPermissionsForSubjectId.getRevokedWithHighestWeight(expectedPermissions);
            final Map<String, WeightedPermission> granted =
                    weightedPermissionsForSubjectId.getGrantedWithHighestWeight(expectedPermissions);
            if (areExpectedPermissionsEffectivelyRevoked(revoked, granted)) {
                effectedSubjectIdsBuilder.withRevoked(subjectId);
            } else if (areExpectedPermissionsEffectivelyGranted(granted, revoked)) {
                effectedSubjectIdsBuilder.withGranted(subjectId);
            } // else the expected permissions are undefined
        }

        private boolean areExpectedPermissionsEffectivelyRevoked(final Map<String, WeightedPermission> revoked,
                final Map<String, WeightedPermission> granted) {

            if (revoked.size() != expectedPermissions.size()) {
                return false;
            }

            for (final String expectedPermission : expectedPermissions) {
                final WeightedPermission revokedPermission = revoked.get(expectedPermission);
                final WeightedPermission grantedPermission = granted.get(expectedPermission);
                if (null != grantedPermission) {
                    final int grantedPermissionWeight = grantedPermission.getWeight();
                    final int revokedPermissionWeight = revokedPermission.getWeight();
                    if (grantedPermissionWeight > revokedPermissionWeight) {
                        return false;
                    }
                }
            }

            return true;
        }

        private boolean areExpectedPermissionsEffectivelyGranted(final Map<String, WeightedPermission> granted,
                final Map<String, WeightedPermission> revoked) {

            if (granted.size() != expectedPermissions.size()) {
                return false;
            }

            for (final String expectedPermission : expectedPermissions) {
                final WeightedPermission grantedPermission = granted.get(expectedPermission);
                final WeightedPermission revokedPermission = revoked.get(expectedPermission);
                if (null != revokedPermission) {
                    final int revokedPermissionWeight = revokedPermission.getWeight();
                    final int grantedPermissionWeight = grantedPermission.getWeight();
                    if (revokedPermissionWeight >= grantedPermissionWeight) {
                        return false;
                    }
                }
            }

            return true;
        }

    }

}
