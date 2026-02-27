/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.enforcers.SubjectClassification;

/**
 * A single visitor that walks the policy tree once and classifies all subjects into three categories:
 * partial, unrestricted, and effected granted. This replaces the need for three separate tree walks
 * using {@code CollectPartialGrantedSubjectsVisitor}, {@code CollectUnrestrictedSubjectsVisitor},
 * and {@code CollectEffectedSubjectsVisitor}.
 *
 * <p>Per subject, maintains 3 {@link WeightedPermissions} instances tracking different aggregation strategies:</p>
 * <table>
 *   <tr><th>WeightedPermissions</th><th>ABOVE/SAME grants</th><th>ABOVE/SAME revokes</th>
 *       <th>BELOW grants</th><th>BELOW revokes</th></tr>
 *   <tr><td>partialWeights</td><td>yes</td><td>yes</td><td>yes</td><td>no</td></tr>
 *   <tr><td>unrestrictedWeights</td><td>yes</td><td>yes</td><td>no</td><td>yes</td></tr>
 *   <tr><td>effectedWeights</td><td>yes</td><td>yes</td><td>no</td><td>no</td></tr>
 * </table>
 *
 * @since 3.9.0
 */
@NotThreadSafe
final class ClassifySubjectsVisitor implements Visitor<SubjectClassification> {

    private final Permissions expectedPermissions;
    private final Function<JsonPointer, PointerLocation> pointerLocationEvaluator;
    private final Collection<ResourceNodeEvaluator> evaluators;
    @Nullable private ResourceNodeEvaluator currentEvaluator;

    /**
     * Constructs a new {@code ClassifySubjectsVisitor} object.
     *
     * @param resourcePointer the resource pointer to classify subjects for.
     * @param expectedPermissions the permissions to check.
     */
    ClassifySubjectsVisitor(final JsonPointer resourcePointer, final Permissions expectedPermissions) {
        this.expectedPermissions = expectedPermissions;
        this.pointerLocationEvaluator = new PointerLocationEvaluator(resourcePointer);
        this.evaluators = new HashSet<>();
        this.currentEvaluator = null;
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
        currentEvaluator = new ResourceNodeEvaluator(currentSubjectId);
        evaluators.add(currentEvaluator);
    }

    private void visitResourceNode(final ResourceNode resourceNode) {
        if (null != currentEvaluator) {
            currentEvaluator.aggregateWeightedPermissions(resourceNode);
        }
    }

    @Override
    public SubjectClassification get() {
        final Set<AuthorizationSubject> partialSubjects = new HashSet<>();
        final Set<AuthorizationSubject> unrestrictedSubjects = new HashSet<>();
        final EffectedSubjectsBuilder effectedSubjectsBuilder = new EffectedSubjectsBuilder();

        for (final ResourceNodeEvaluator evaluator : evaluators) {
            evaluator.evaluate(partialSubjects, unrestrictedSubjects, effectedSubjectsBuilder);
        }

        final Set<AuthorizationSubject> partialOnly = new HashSet<>(partialSubjects);
        partialOnly.removeAll(unrestrictedSubjects);

        return SubjectClassification.of(
                unrestrictedSubjects,
                partialOnly,
                effectedSubjectsBuilder.build().getGranted()
        );
    }

    /**
     * Aggregates and evaluates permissions for a single subject using three weight sets.
     */
    @NotThreadSafe
    private final class ResourceNodeEvaluator {

        private final AuthorizationSubject authorizationSubject;
        private final WeightedPermissions partialWeights;
        private final WeightedPermissions unrestrictedWeights;
        private final WeightedPermissions effectedWeights;

        private ResourceNodeEvaluator(final CharSequence subjectId) {
            this.authorizationSubject = AuthorizationModelFactory.newAuthSubject(subjectId);
            this.partialWeights = new WeightedPermissions();
            this.unrestrictedWeights = new WeightedPermissions();
            this.effectedWeights = new WeightedPermissions();
        }

        private void aggregateWeightedPermissions(final ResourceNode resourceNode) {
            final PointerLocation pointerLocation =
                    pointerLocationEvaluator.apply(resourceNode.getAbsolutePointer());
            final EffectedPermissions effectedPermissions = resourceNode.getPermissions();
            final Permissions grantedPermissions = effectedPermissions.getGrantedPermissions();
            final Permissions revokedPermissions = effectedPermissions.getRevokedPermissions();
            final int level = resourceNode.getLevel();

            if (PointerLocation.ABOVE == pointerLocation || PointerLocation.SAME == pointerLocation) {
                // All three weight sets get ABOVE/SAME grants and revokes
                partialWeights.addGranted(grantedPermissions, level);
                partialWeights.addRevoked(revokedPermissions, level);

                unrestrictedWeights.addGranted(grantedPermissions, level);
                unrestrictedWeights.addRevoked(revokedPermissions, level);

                effectedWeights.addGranted(grantedPermissions, level);
                effectedWeights.addRevoked(revokedPermissions, level);
            } else if (PointerLocation.BELOW == pointerLocation) {
                // Partial: BELOW grants only (no BELOW revokes)
                partialWeights.addGranted(grantedPermissions, level);

                // Unrestricted: BELOW revokes only (no BELOW grants)
                unrestrictedWeights.addRevoked(revokedPermissions, level);

                // Effected: nothing for BELOW
            }
        }

        private void evaluate(final Set<AuthorizationSubject> partialSubjects,
                final Set<AuthorizationSubject> unrestrictedSubjects,
                final EffectedSubjectsBuilder effectedSubjectsBuilder) {

            // Evaluate partial (same logic as CollectPartialGrantedSubjectsVisitor)
            evaluatePartial(partialSubjects);

            // Evaluate unrestricted (same logic as CollectUnrestrictedSubjectsVisitor)
            evaluateUnrestricted(unrestrictedSubjects);

            // Evaluate effected (same logic as CollectEffectedSubjectsVisitor)
            evaluateEffected(effectedSubjectsBuilder);
        }

        private void evaluatePartial(final Set<AuthorizationSubject> partialSubjects) {
            final Map<String, WeightedPermission> revoked =
                    partialWeights.getRevokedWithHighestWeight(expectedPermissions);
            final Map<String, WeightedPermission> granted =
                    partialWeights.getGrantedWithHighestWeight(expectedPermissions);
            if (areExpectedPermissionsEffectivelyRevoked(revoked, granted)) {
                partialSubjects.remove(authorizationSubject);
            } else if (areExpectedPermissionsEffectivelyGranted(granted, revoked)) {
                partialSubjects.add(authorizationSubject);
            }
        }

        private void evaluateUnrestricted(final Set<AuthorizationSubject> unrestrictedSubjects) {
            final Map<String, WeightedPermission> revoked =
                    unrestrictedWeights.getRevokedWithHighestWeight(expectedPermissions);
            final Map<String, WeightedPermission> granted =
                    unrestrictedWeights.getGrantedWithHighestWeight(expectedPermissions);
            if (areExpectedPermissionsEffectivelyRevoked(revoked, granted)) {
                unrestrictedSubjects.remove(authorizationSubject);
            } else if (areExpectedPermissionsEffectivelyGranted(granted, revoked)) {
                unrestrictedSubjects.add(authorizationSubject);
            }
        }

        private void evaluateEffected(final EffectedSubjectsBuilder effectedSubjectsBuilder) {
            final Map<String, WeightedPermission> revoked =
                    effectedWeights.getRevokedWithHighestWeight(expectedPermissions);
            final Map<String, WeightedPermission> granted =
                    effectedWeights.getGrantedWithHighestWeight(expectedPermissions);
            if (areExpectedPermissionsEffectivelyRevoked(revoked, granted)) {
                effectedSubjectsBuilder.withRevoked(authorizationSubject);
            } else if (areExpectedPermissionsEffectivelyGranted(granted, revoked)) {
                effectedSubjectsBuilder.withGranted(authorizationSubject);
            }
        }

        @SuppressWarnings("MethodWithMultipleReturnPoints")
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

        @SuppressWarnings("MethodWithMultipleReturnPoints")
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
