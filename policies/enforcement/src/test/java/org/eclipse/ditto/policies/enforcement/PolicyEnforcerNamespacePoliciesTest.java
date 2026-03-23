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
package org.eclipse.ditto.policies.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.config.DefaultNamespacePoliciesConfig;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for the namespace-root-policy merge logic in
 * {@link PolicyEnforcer#withResolvedImportsAndNamespacePolicies}.
 */
public final class PolicyEnforcerNamespacePoliciesTest {

    private static final String NAMESPACE = "org.example.devices";
    private static final PolicyId CHILD_POLICY_ID = PolicyId.of(NAMESPACE, "child-policy");
    private static final PolicyId ROOT_POLICY_ID = PolicyId.of("org.example", "tenant-root");

    private static final Label LOCAL_LABEL = Label.of("LOCAL_OWNER");
    private static final Label ROOT_IMPLICIT_LABEL = Label.of("ROOT_READER");
    private static final Label ROOT_NEVER_LABEL = Label.of("ROOT_NEVER");
    private static final Label ROOT_EXPLICIT_LABEL = Label.of("ROOT_EXPLICIT");

    @Test
    public void implicitRootEntryIsMergedIntoChildEnforcer() throws Exception {
        final Policy rootPolicy = policyWithEntry(ROOT_POLICY_ID, ROOT_IMPLICIT_LABEL, ImportableType.IMPLICIT);
        final Policy childPolicy = policyWithEntry(CHILD_POLICY_ID, LOCAL_LABEL, ImportableType.IMPLICIT);

        final var enforcer = buildEnforcer(childPolicy, ROOT_POLICY_ID, rootPolicy);
        final Policy merged = enforcer.getPolicy().orElseThrow();

        assertThat(merged.contains(LOCAL_LABEL)).isTrue();
        assertThat(merged.contains(ROOT_IMPLICIT_LABEL)).isTrue();
    }

    @Test
    public void neverImportableRootEntryIsNotMerged() throws Exception {
        final Policy rootPolicy = policyWithEntry(ROOT_POLICY_ID, ROOT_NEVER_LABEL, ImportableType.NEVER);
        final Policy childPolicy = policyWithEntry(CHILD_POLICY_ID, LOCAL_LABEL, ImportableType.IMPLICIT);

        final var enforcer = buildEnforcer(childPolicy, ROOT_POLICY_ID, rootPolicy);
        final Policy merged = enforcer.getPolicy().orElseThrow();

        assertThat(merged.contains(LOCAL_LABEL)).isTrue();
        assertThat(merged.contains(ROOT_NEVER_LABEL)).isFalse();
    }

    @Test
    public void explicitImportableRootEntryIsNotMerged() throws Exception {
        final Policy rootPolicy = policyWithEntry(ROOT_POLICY_ID, ROOT_EXPLICIT_LABEL, ImportableType.EXPLICIT);
        final Policy childPolicy = policyWithEntry(CHILD_POLICY_ID, LOCAL_LABEL, ImportableType.IMPLICIT);

        final var enforcer = buildEnforcer(childPolicy, ROOT_POLICY_ID, rootPolicy);
        final Policy merged = enforcer.getPolicy().orElseThrow();

        assertThat(merged.contains(LOCAL_LABEL)).isTrue();
        assertThat(merged.contains(ROOT_EXPLICIT_LABEL)).isFalse();
    }

    @Test
    public void localLabelWinsOverNamespaceRootLabelOnConflict() throws Exception {
        // Both child and root have the same label — local must win (root entry must NOT override it)
        final var localSubjectId = SubjectId.newInstance(SubjectIssuer.newInstance("local-issuer"), "user");
        final var rootSubjectId = SubjectId.newInstance(SubjectIssuer.newInstance("root-issuer"), "user");

        final Policy rootPolicy = policyWithEntryAndSubject(ROOT_POLICY_ID, LOCAL_LABEL,
                ImportableType.IMPLICIT, rootSubjectId);
        final Policy childPolicy = policyWithEntryAndSubject(CHILD_POLICY_ID, LOCAL_LABEL,
                ImportableType.IMPLICIT, localSubjectId);

        final var enforcer = buildEnforcer(childPolicy, ROOT_POLICY_ID, rootPolicy);
        final Policy merged = enforcer.getPolicy().orElseThrow();

        assertThat(merged.contains(LOCAL_LABEL)).isTrue();
        final var entry = merged.getEntryFor(LOCAL_LABEL).orElseThrow();
        // local subject must be present; root subject must not have replaced it
        assertThat(entry.getSubjects().stream()
                .anyMatch(s -> s.getId().equals(localSubjectId))).isTrue();
        assertThat(entry.getSubjects().stream()
                .anyMatch(s -> s.getId().equals(rootSubjectId))).isFalse();
    }

    @Test
    public void missingRootPolicyIsHandledGracefully() throws Exception {
        // Root policy resolver returns empty — should not throw, child policy enforcer built normally
        final Policy childPolicy = policyWithEntry(CHILD_POLICY_ID, LOCAL_LABEL, ImportableType.IMPLICIT);

        final NamespacePoliciesConfig config = namespaceConfig(ROOT_POLICY_ID);
        final Function<PolicyId, CompletableFuture<Optional<Policy>>> resolver =
                id -> CompletableFuture.completedFuture(Optional.empty());

        final var enforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(childPolicy, resolver::apply, config)
                .toCompletableFuture()
                .join();

        assertThat(enforcer.getPolicy().orElseThrow().contains(LOCAL_LABEL)).isTrue();
    }

    @Test
    public void emptyNamespacePoliciesConfigLeavesChildUnchanged() throws Exception {
        final Policy childPolicy = policyWithEntry(CHILD_POLICY_ID, LOCAL_LABEL, ImportableType.IMPLICIT);

        final NamespacePoliciesConfig emptyConfig = mock(NamespacePoliciesConfig.class);
        when(emptyConfig.isEmpty()).thenReturn(true);
        when(emptyConfig.getRootPoliciesForNamespace(NAMESPACE)).thenReturn(List.of());

        final Function<PolicyId, CompletableFuture<Optional<Policy>>> resolver =
                id -> CompletableFuture.completedFuture(Optional.empty());

        final var enforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(childPolicy, resolver::apply, emptyConfig)
                .toCompletableFuture()
                .join();

        final Policy merged = enforcer.getPolicy().orElseThrow();
        assertThat(merged.contains(LOCAL_LABEL)).isTrue();
        // only the local label should be present
        assertThat(merged.stream().count()).isEqualTo(1);
    }

    @Test
    public void moreSpecificNamespaceWildcardWinsOnLabelConflict() {
        final PolicyId generalRootPolicyId = PolicyId.of("org.example", "tenant-root-general");
        final PolicyId specificRootPolicyId = PolicyId.of("org.example", "tenant-root-devices");
        final PolicyId childPolicyId = PolicyId.of("org.example.devices.alpha", "child-policy");

        final var generalSubjectId = SubjectId.newInstance(SubjectIssuer.newInstance("general-issuer"), "user");
        final var specificSubjectId = SubjectId.newInstance(SubjectIssuer.newInstance("specific-issuer"), "user");

        final Policy generalRootPolicy = policyWithEntryAndSubject(generalRootPolicyId, ROOT_IMPLICIT_LABEL,
                ImportableType.IMPLICIT, generalSubjectId);
        final Policy specificRootPolicy = policyWithEntryAndSubject(specificRootPolicyId, ROOT_IMPLICIT_LABEL,
                ImportableType.IMPLICIT, specificSubjectId);
        final Policy childPolicy = policyWithEntry(childPolicyId, LOCAL_LABEL, ImportableType.IMPLICIT);

        final NamespacePoliciesConfig config = DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies {\n" +
                "  \"org.example.*\"         = [\"org.example:tenant-root-general\"]\n" +
                "  \"org.example.devices.*\" = [\"org.example:tenant-root-devices\"]\n" +
                "}"));
        final Function<PolicyId, CompletableFuture<Optional<Policy>>> resolver =
                id -> CompletableFuture.completedFuture(Optional.ofNullable(
                        generalRootPolicyId.equals(id) ? generalRootPolicy :
                                specificRootPolicyId.equals(id) ? specificRootPolicy : null));

        final var enforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(childPolicy, resolver::apply, config)
                .toCompletableFuture()
                .join();

        final Policy merged = enforcer.getPolicy().orElseThrow();
        final var entry = merged.getEntryFor(ROOT_IMPLICIT_LABEL).orElseThrow();
        assertThat(entry.getSubjects().stream()
                .anyMatch(s -> s.getId().equals(specificSubjectId))).isTrue();
        assertThat(entry.getSubjects().stream()
                .anyMatch(s -> s.getId().equals(generalSubjectId))).isFalse();
    }

    @Test
    public void rootPolicyWithOwnImportsHasImportedEntriesMergedIntoChild() throws Exception {
        final PolicyId importedPolicyId = PolicyId.of("org.example", "imported-policy");
        final Label importedLabel = Label.of("IMPORTED_READER");

        final Policy importedPolicy = policyWithEntry(importedPolicyId, importedLabel, ImportableType.IMPLICIT);

        final Policy rootPolicyWithImport = Policy.newBuilder(ROOT_POLICY_ID)
                .set(PoliciesModelFactory.newPolicyEntry(ROOT_IMPLICIT_LABEL,
                        Subjects.newInstance(Subject.newInstance(
                                SubjectId.newInstance(SubjectIssuer.newInstance("root-issuer"), "user"),
                                SubjectType.newInstance("test"))),
                        Resources.newInstance(Resource.newInstance("thing", JsonPointer.of("/"),
                                EffectedPermissions.newInstance(Permissions.newInstance("READ"), Permissions.none()))),
                        ImportableType.IMPLICIT))
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(importedPolicyId))
                .build();

        final NamespacePoliciesConfig config = namespaceConfig(ROOT_POLICY_ID);
        final Function<PolicyId, CompletableFuture<Optional<Policy>>> resolver = id -> {
            if (ROOT_POLICY_ID.equals(id)) return CompletableFuture.completedFuture(Optional.of(rootPolicyWithImport));
            if (importedPolicyId.equals(id)) return CompletableFuture.completedFuture(Optional.of(importedPolicy));
            return CompletableFuture.completedFuture(Optional.empty());
        };

        final Policy childPolicy = policyWithEntry(CHILD_POLICY_ID, LOCAL_LABEL, ImportableType.IMPLICIT);
        final var enforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(childPolicy, resolver::apply, config)
                .toCompletableFuture()
                .join();

        final Policy merged = enforcer.getPolicy().orElseThrow();
        final Label importedMergedLabel = PoliciesModelFactory.newImportedLabel(importedPolicyId, importedLabel);
        assertThat(merged.contains(LOCAL_LABEL)).isTrue();
        assertThat(merged.contains(ROOT_IMPLICIT_LABEL)).isTrue();
        assertThat(merged.contains(importedMergedLabel)).isTrue();
        assertThat(merged.contains(importedLabel)).isFalse();
    }

    @Test
    public void rootPolicyStillReceivesOtherMatchingNamespaceRoots() {
        final PolicyId globalRootPolicyId = PolicyId.of("global", "catch-all");
        final Label globalLabel = Label.of("GLOBAL_READER");

        final Policy rootPolicy = policyWithEntry(ROOT_POLICY_ID, ROOT_IMPLICIT_LABEL, ImportableType.IMPLICIT);
        final Policy globalRootPolicy = policyWithEntry(globalRootPolicyId, globalLabel, ImportableType.IMPLICIT);

        final NamespacePoliciesConfig config = DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies {\n" +
                "  \"org.example.devices\" = [\"org.example:tenant-root\"]\n" +
                "  \"*\" = [\"global:catch-all\"]\n" +
                "}"));
        final Function<PolicyId, CompletableFuture<Optional<Policy>>> resolver = id -> CompletableFuture.completedFuture(
                Optional.ofNullable(ROOT_POLICY_ID.equals(id) ? rootPolicy :
                        globalRootPolicyId.equals(id) ? globalRootPolicy : null));

        final var enforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(rootPolicy, resolver::apply, config)
                .toCompletableFuture()
                .join();

        final Policy merged = enforcer.getPolicy().orElseThrow();
        assertThat(merged.contains(ROOT_IMPLICIT_LABEL)).isTrue();
        assertThat(merged.contains(globalLabel)).isTrue();
    }

    // ---- helpers ----

    private static Policy policyWithEntryAndSubject(final PolicyId policyId, final Label label,
            final ImportableType importableType, final SubjectId subjectId) {
        final var subjects = Subjects.newInstance(
                Subject.newInstance(subjectId, SubjectType.newInstance("test")));
        final var resources = Resources.newInstance(
                Resource.newInstance("thing", JsonPointer.of("/"),
                        EffectedPermissions.newInstance(Permissions.newInstance("READ"), Permissions.none())));
        final var entry = PoliciesModelFactory.newPolicyEntry(label, subjects, resources, importableType);
        return Policy.newBuilder(policyId).set(entry).build();
    }

    private static Policy policyWithEntry(final PolicyId policyId, final Label label,
            final ImportableType importableType) {
        final var subjects = Subjects.newInstance(
                Subject.newInstance(
                        SubjectId.newInstance(SubjectIssuer.newInstance(label.toString()), "user"),
                        SubjectType.newInstance("test")));
        final var resources = Resources.newInstance(
                Resource.newInstance("thing", JsonPointer.of("/"),
                        EffectedPermissions.newInstance(
                                Permissions.newInstance("READ"),
                                Permissions.none())));
        final var entry = PoliciesModelFactory.newPolicyEntry(label, subjects, resources, importableType);
        return Policy.newBuilder(policyId).set(entry).build();
    }

    private static PolicyEnforcer buildEnforcer(final Policy childPolicy,
            final PolicyId rootPolicyId, final Policy rootPolicy) {
        final NamespacePoliciesConfig config = namespaceConfig(rootPolicyId);
        final Function<PolicyId, CompletableFuture<Optional<Policy>>> resolver =
                id -> rootPolicyId.equals(id)
                        ? CompletableFuture.completedFuture(Optional.of(rootPolicy))
                        : CompletableFuture.completedFuture(Optional.empty());

        return PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(childPolicy, resolver::apply, config)
                .toCompletableFuture()
                .join();
    }

    private static NamespacePoliciesConfig namespaceConfig(final PolicyId rootPolicyId) {
        final NamespacePoliciesConfig config = mock(NamespacePoliciesConfig.class);
        when(config.isEmpty()).thenReturn(false);
        when(config.getRootPoliciesForNamespace(NAMESPACE)).thenReturn(Collections.singletonList(rootPolicyId));
        when(config.getAllNamespaceRootPolicyIds()).thenReturn(Set.of(rootPolicyId));
        when(config.getNamespacesForRootPolicy(rootPolicyId)).thenReturn(Set.of(NAMESPACE));
        return config;
    }

}
