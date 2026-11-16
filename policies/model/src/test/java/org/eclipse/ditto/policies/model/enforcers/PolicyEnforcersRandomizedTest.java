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
package org.eclipse.ditto.policies.model.enforcers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.junit.Test;

/**
 * Randomized test of the throughput optimized (trie based) and the memory optimized (tree based) policy enforcers.
 * For random policies with several subjects and mixed grants and revokes on nested resources, both enforcers have to
 * return the same results as a straightforward reference evaluation of the policy semantics for checks of single and
 * multiple permissions:
 * <ul>
 * <li>A permission is effectively granted on a resource if the deepest grant or revoke of that permission to any
 * subject of the authorization context on the resource or one of its super-resources is a grant. A revoke wins over
 * a grant on the same level.</li>
 * <li>{@link Enforcer#hasUnrestrictedPermissions}: each permission is effectively granted on the resource and not
 * revoked from any subject of the authorization context on any of its sub-resources.</li>
 * <li>{@link Enforcer#hasPartialPermissions}: each permission is effectively granted on the resource or on any of its
 * sub-resources.</li>
 * <li>{@link Enforcer#getSubjectsWithPartialPermission}, {@link Enforcer#getSubjectsWithUnrestrictedPermission} and
 * {@link Enforcer#classifySubjects}: the subjects for which the partial respectively unrestricted check succeeds on
 * their own.</li>
 * </ul>
 */
public final class PolicyEnforcersRandomizedTest {

    private static final long SEED = 20261116L;
    private static final int POLICY_COUNT = 1_000;
    private static final int MAX_REPORTED_MISMATCHES = 20;

    private static final List<String> SUBJECT_IDS = Arrays.asList("test:user", "test:group-a", "test:group-b");

    private static final List<String> POLICY_PATHS = Arrays.asList(
            "/",
            "/attributes",
            "/attributes/a",
            "/attributes/a/b",
            "/attributes/c",
            "/features",
            "/features/f",
            "/features/f/properties/p"
    );

    private static final List<String> CHECKED_PATHS;

    static {
        final List<String> checkedPaths = new ArrayList<>(POLICY_PATHS);
        checkedPaths.addAll(Arrays.asList(
                "/attributes/a/b/c",
                "/attributes/x",
                "/features/f/properties",
                "/features/g",
                "/policyId"
        ));
        CHECKED_PATHS = Collections.unmodifiableList(checkedPaths);
    }

    private static final List<String> PERMISSIONS = Arrays.asList("READ", "WRITE");

    private static final List<Permissions> CHECKED_PERMISSIONS = Arrays.asList(
            Permissions.newInstance("READ"),
            Permissions.newInstance("WRITE"),
            Permissions.newInstance("READ", "WRITE")
    );

    private static final List<List<String>> AUTHORIZATION_SUBJECT_COMBINATIONS = allNonEmptySubjectCombinations();

    @Test
    public void enforcersMatchReferenceSemanticsForRandomPolicies() {
        final Random random = new Random(SEED);
        final List<String> mismatches = new ArrayList<>();
        int checkCount = 0;

        for (int i = 0; i < POLICY_COUNT; i++) {
            final List<Rule> rules = randomRules(random);
            final List<PolicyEntry> policyEntries = toPolicyEntries(rules);
            final Enforcer trie = PolicyEnforcers.throughputOptimizedEvaluator(policyEntries);
            final Enforcer tree = PolicyEnforcers.memoryOptimizedEvaluator(policyEntries);

            for (final String path : CHECKED_PATHS) {
                final ResourceKey resourceKey = PoliciesResourceType.thingResource(path);
                for (final Permissions permissions : CHECKED_PERMISSIONS) {
                    for (final List<String> subjectIds : AUTHORIZATION_SUBJECT_COMBINATIONS) {
                        final AuthorizationContext authorizationContext = toAuthorizationContext(subjectIds);
                        checkCount += 4;

                        final boolean expectedUnrestricted =
                                isUnrestrictedGranted(rules, subjectIds, path, permissions);
                        verify("hasUnrestrictedPermissions", "trie", expectedUnrestricted,
                                trie.hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions),
                                rules, path, subjectIds, permissions, mismatches);
                        verify("hasUnrestrictedPermissions", "tree", expectedUnrestricted,
                                tree.hasUnrestrictedPermissions(resourceKey, authorizationContext, permissions),
                                rules, path, subjectIds, permissions, mismatches);

                        final boolean expectedPartial = isPartiallyGranted(rules, subjectIds, path, permissions);
                        verify("hasPartialPermissions", "trie", expectedPartial,
                                trie.hasPartialPermissions(resourceKey, authorizationContext, permissions),
                                rules, path, subjectIds, permissions, mismatches);
                        verify("hasPartialPermissions", "tree", expectedPartial,
                                tree.hasPartialPermissions(resourceKey, authorizationContext, permissions),
                                rules, path, subjectIds, permissions, mismatches);
                    }

                    final Set<AuthorizationSubject> expectedPartialSubjects =
                            getPartiallyGrantedSubjects(rules, path, permissions);
                    final Set<AuthorizationSubject> expectedUnrestrictedSubjects =
                            getUnrestrictedGrantedSubjects(rules, path, permissions);
                    final Set<AuthorizationSubject> expectedPartialOnlySubjects =
                            new HashSet<>(expectedPartialSubjects);
                    expectedPartialOnlySubjects.removeAll(expectedUnrestrictedSubjects);
                    for (final Enforcer enforcer : Arrays.asList(trie, tree)) {
                        final String name = enforcer == trie ? "trie" : "tree";
                        checkCount += 4;
                        verify("getSubjectsWithPartialPermission", name, expectedPartialSubjects,
                                enforcer.getSubjectsWithPartialPermission(resourceKey, permissions),
                                rules, path, permissions, mismatches);
                        verify("getSubjectsWithUnrestrictedPermission", name, expectedUnrestrictedSubjects,
                                enforcer.getSubjectsWithUnrestrictedPermission(resourceKey, permissions),
                                rules, path, permissions, mismatches);
                        final SubjectClassification classification =
                                enforcer.classifySubjects(resourceKey, permissions);
                        verify("classifySubjects.partialOnly", name, expectedPartialOnlySubjects,
                                classification.getPartialOnly(), rules, path, permissions, mismatches);
                        verify("classifySubjects.unrestricted", name, expectedUnrestrictedSubjects,
                                classification.getUnrestricted(), rules, path, permissions, mismatches);
                    }
                }
            }
        }

        assertThat(mismatches)
                .as("%d of %d checks did not match the reference semantics, first %d:%n%s",
                        mismatches.size(), checkCount, Math.min(mismatches.size(), MAX_REPORTED_MISMATCHES),
                        String.join("\n", mismatches.subList(0,
                                Math.min(mismatches.size(), MAX_REPORTED_MISMATCHES))))
                .isEmpty();
    }

    private static boolean isUnrestrictedGranted(final List<Rule> rules, final Collection<String> subjectIds,
            final String path, final Collection<String> permissions) {

        for (final String permission : permissions) {
            if (!isEffectivelyGranted(rules, subjectIds, path, permission)) {
                return false;
            }
            for (final Rule rule : rules) {
                if (subjectIds.contains(rule.subjectId) && isStrictlyBelow(rule.path, path) &&
                        rule.revoked.contains(permission)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isPartiallyGranted(final List<Rule> rules, final Collection<String> subjectIds,
            final String path, final Collection<String> permissions) {

        for (final String permission : permissions) {
            if (!isEffectivelyGrantedOnSelfOrAnySubResource(rules, subjectIds, path, permission)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEffectivelyGrantedOnSelfOrAnySubResource(final List<Rule> rules,
            final Collection<String> subjectIds, final String path, final String permission) {

        if (isEffectivelyGranted(rules, subjectIds, path, permission)) {
            return true;
        }
        // sub-resources which are not mentioned in the policy inherit the permissions of a mentioned resource
        for (final Rule rule : rules) {
            if (isStrictlyBelow(rule.path, path) && isEffectivelyGranted(rules, subjectIds, rule.path, permission)) {
                return true;
            }
        }
        return false;
    }

    private static Set<AuthorizationSubject> getPartiallyGrantedSubjects(final List<Rule> rules, final String path,
            final Collection<String> permissions) {

        final Set<AuthorizationSubject> result = new HashSet<>();
        for (final String subjectId : SUBJECT_IDS) {
            if (isPartiallyGranted(rules, Collections.singleton(subjectId), path, permissions)) {
                result.add(AuthorizationSubject.newInstance(subjectId));
            }
        }
        return result;
    }

    private static Set<AuthorizationSubject> getUnrestrictedGrantedSubjects(final List<Rule> rules,
            final String path, final Collection<String> permissions) {

        final Set<AuthorizationSubject> result = new HashSet<>();
        for (final String subjectId : SUBJECT_IDS) {
            if (isUnrestrictedGranted(rules, Collections.singleton(subjectId), path, permissions)) {
                result.add(AuthorizationSubject.newInstance(subjectId));
            }
        }
        return result;
    }

    private static boolean isEffectivelyGranted(final List<Rule> rules, final Collection<String> subjectIds,
            final String path, final String permission) {

        int deepestLevel = -1;
        boolean granted = false;
        boolean revoked = false;
        for (final Rule rule : rules) {
            final boolean grants = rule.granted.contains(permission);
            final boolean revokes = rule.revoked.contains(permission);
            if (subjectIds.contains(rule.subjectId) && (grants || revokes) && isSelfOrAbove(rule.path, path)) {
                final int level = getLevel(rule.path);
                if (level > deepestLevel) {
                    deepestLevel = level;
                    granted = false;
                    revoked = false;
                }
                if (level == deepestLevel) {
                    granted |= grants;
                    revoked |= revokes;
                }
            }
        }
        return granted && !revoked;
    }

    private static boolean isSelfOrAbove(final String candidate, final String path) {
        return "/".equals(candidate) || candidate.equals(path) || path.startsWith(candidate + "/");
    }

    private static boolean isStrictlyBelow(final String candidate, final String path) {
        return !candidate.equals(path) && isSelfOrAbove(path, candidate);
    }

    private static int getLevel(final String path) {
        return "/".equals(path) ? 0 : path.split("/").length - 1;
    }

    private static void verify(final String method, final String enforcer, final Object expected,
            final Object actual, final List<Rule> rules, final String path, final Collection<String> subjectIds,
            final Collection<String> permissions, final List<String> mismatches) {

        if (!expected.equals(actual)) {
            mismatches.add(String.format("\t%s %s(%s, %s, %s): expected=%s, actual=%s, policy:%s", enforcer,
                    method, path, subjectIds, permissions, expected, actual, describe(rules)));
        }
    }

    private static void verify(final String method, final String enforcer, final Object expected,
            final Object actual, final List<Rule> rules, final String path, final Collection<String> permissions,
            final List<String> mismatches) {

        if (!expected.equals(actual)) {
            mismatches.add(String.format("\t%s %s(%s, %s): expected=%s, actual=%s, policy:%s", enforcer, method,
                    path, permissions, expected, actual, describe(rules)));
        }
    }

    private static String describe(final List<Rule> rules) {
        final StringBuilder result = new StringBuilder();
        for (final Rule rule : rules) {
            result.append("\n\t\t").append(rule);
        }
        return result.toString();
    }

    /*
     * Each rule grants and/or revokes permissions of a single subject on a single resource. A rule may grant and
     * revoke the same permission, which is an effective revoke.
     */
    private static List<Rule> randomRules(final Random random) {
        final int ruleCount = 1 + random.nextInt(6);
        final List<Rule> rules = new ArrayList<>(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            final List<String> granted = randomSubset(random, PERMISSIONS);
            final List<String> revoked = randomSubset(random, PERMISSIONS);
            if (granted.isEmpty() && revoked.isEmpty()) {
                granted.add(PERMISSIONS.get(random.nextInt(PERMISSIONS.size())));
            }
            rules.add(new Rule(SUBJECT_IDS.get(random.nextInt(SUBJECT_IDS.size())),
                    POLICY_PATHS.get(random.nextInt(POLICY_PATHS.size())), granted, revoked));
        }
        return rules;
    }

    private static List<String> randomSubset(final Random random, final List<String> values) {
        final List<String> result = new ArrayList<>(values.size());
        for (final String value : values) {
            if (random.nextBoolean()) {
                result.add(value);
            }
        }
        return result;
    }

    private static List<PolicyEntry> toPolicyEntries(final List<Rule> rules) {
        final List<PolicyEntry> policyEntries = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            final Rule rule = rules.get(i);
            final Subject subject =
                    PoliciesModelFactory.newSubject(SubjectId.newInstance(rule.subjectId), SubjectType.GENERATED);
            final Resource resource = PoliciesModelFactory.newResource(PoliciesResourceType.thingResource(rule.path),
                    PoliciesModelFactory.newEffectedPermissions(rule.granted, rule.revoked));
            policyEntries.add(PoliciesModelFactory.newPolicyEntry("entry" + i, Collections.singletonList(subject),
                    Collections.singletonList(resource)));
        }
        return policyEntries;
    }

    private static AuthorizationContext toAuthorizationContext(final List<String> subjectIds) {
        final List<AuthorizationSubject> authorizationSubjects = new ArrayList<>(subjectIds.size());
        for (final String subjectId : subjectIds) {
            authorizationSubjects.add(AuthorizationSubject.newInstance(subjectId));
        }
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, authorizationSubjects);
    }

    private static List<List<String>> allNonEmptySubjectCombinations() {
        final List<List<String>> result = new ArrayList<>();
        for (int mask = 1; mask < 1 << SUBJECT_IDS.size(); mask++) {
            final List<String> subjectIds = new ArrayList<>();
            for (int i = 0; i < SUBJECT_IDS.size(); i++) {
                if ((mask & 1 << i) != 0) {
                    subjectIds.add(SUBJECT_IDS.get(i));
                }
            }
            result.add(Collections.unmodifiableList(subjectIds));
        }
        return Collections.unmodifiableList(result);
    }

    private static final class Rule {

        private final String subjectId;
        private final String path;
        private final Set<String> granted;
        private final Set<String> revoked;

        private Rule(final String subjectId, final String path, final Collection<String> granted,
                final Collection<String> revoked) {

            this.subjectId = subjectId;
            this.path = path;
            this.granted = Collections.unmodifiableSet(new HashSet<>(granted));
            this.revoked = Collections.unmodifiableSet(new HashSet<>(revoked));
        }

        @Override
        public String toString() {
            return subjectId + " " + path + " grant=" + granted + " revoke=" + revoked;
        }

    }

}
