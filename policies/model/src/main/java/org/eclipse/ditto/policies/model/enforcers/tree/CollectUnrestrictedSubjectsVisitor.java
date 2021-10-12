/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Permissions;

/**
 * @since 2.2.0
 */
@NotThreadSafe
@ParametersAreNonnullByDefault
final class CollectUnrestrictedSubjectsVisitor implements Visitor<Set<AuthorizationSubject>> {

    private final Permissions expectedPermissions;

    private final Function<JsonPointer, PointerLocation> pointerLocationEvaluator;
    private final Collection<ResourceNodeEvaluator> evaluators;
    private final Set<AuthorizationSubject> unrestrictedSubjects;
    @Nullable private ResourceNodeEvaluator currentEvaluator;

    /**
     * Constructs a new {@code CollectUnrestrictedSubjectsVisitor} object.
     *
     * @throws NullPointerException if any argument is {@code null}.
     */
    CollectUnrestrictedSubjectsVisitor(final JsonPointer resourcePointer, final Permissions expectedPermissions) {
        this.expectedPermissions = expectedPermissions;

        pointerLocationEvaluator = new PointerLocationEvaluator(resourcePointer);
        evaluators = new HashSet<>();
        unrestrictedSubjects = new HashSet<>();
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
        if (null != currentEvaluator) {
            currentEvaluator.aggregateWeightedPermissions(resourceNode);
        }
    }

    @Override
    public Set<AuthorizationSubject> get() {

        // populate effectedSubjectIdsBuilder via side effect
        evaluators.forEach(ResourceNodeEvaluator::evaluate);
        return new HashSet<>(unrestrictedSubjects);
    }

    /**
     * The permissions have to be evaluated for each subject ID separately as this visitor's task is to collect
     * subject IDs. This class aggregates and evaluates the permissions for a particular subject ID. If the subject
     * ID is granted or revoked an expected permission it is added to the effectedSubjectIdsBuilder via side effect.
     */
    @NotThreadSafe
    private final class ResourceNodeEvaluator {

        private final AuthorizationSubject subject;
        private final WeightedPermissions weightedPermissions;

        private ResourceNodeEvaluator(final CharSequence subjectId) {
            subject = AuthorizationModelFactory.newAuthSubject(subjectId);
            weightedPermissions = new WeightedPermissions();
        }

        private void aggregateWeightedPermissions(final ResourceNode resourceNode) {
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

        private PointerLocation getLocationInRelationToTargetPointer(final ResourceNode resourceNode) {
            return pointerLocationEvaluator.apply(resourceNode.getAbsolutePointer());
        }

        private void evaluate() {
            final Map<String, WeightedPermission> revoked =
                    weightedPermissions.getRevokedWithHighestWeight(expectedPermissions);
            final Map<String, WeightedPermission> granted =
                    weightedPermissions.getGrantedWithHighestWeight(expectedPermissions);
            if (areExpectedPermissionsEffectivelyRevoked(revoked, granted)) {
                unrestrictedSubjects.remove(subject);
            } else if (areExpectedPermissionsEffectivelyGranted(granted, revoked)) {
                unrestrictedSubjects.add(subject);
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
