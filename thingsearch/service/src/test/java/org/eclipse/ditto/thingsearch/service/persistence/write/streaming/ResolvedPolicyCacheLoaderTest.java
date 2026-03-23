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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.PolicyCacheLoader;
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
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

public final class ResolvedPolicyCacheLoaderTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final PolicyId CHILD_POLICY_ID = PolicyId.of("org.example.devices.alpha", "child-policy");
    private static final PolicyId ROOT_POLICY_ID = PolicyId.of("org.example", "tenant-root");
    private static final PolicyId GLOBAL_ROOT_POLICY_ID = PolicyId.of("global", "catch-all");
    private static final PolicyId IMPORTED_POLICY_ID = PolicyId.of("org.example", "imported-policy");
    private static final long CHILD_POLICY_REVISION = 1L;
    private static final long ROOT_POLICY_REVISION = 2L;
    private static final long GLOBAL_ROOT_POLICY_REVISION = 4L;
    private static final long IMPORTED_POLICY_REVISION = 3L;
    private static final Label LOCAL_LABEL = Label.of("LOCAL_OWNER");
    private static final Label ROOT_LABEL = Label.of("ROOT_READER");
    private static final Label GLOBAL_ROOT_LABEL = Label.of("GLOBAL_READER");
    private static final Label IMPORTED_LABEL = Label.of("IMPORTED_READER");

    private PolicyCacheLoader policyCacheLoader;
    private Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>> cache;

    @Before
    public void setUp() {
        policyCacheLoader = mock(PolicyCacheLoader.class);
        cache = mock(Cache.class);
    }

    @Test
    public void namespaceRootPoliciesResolveTheirOwnImportsAndTrackReferencedPolicies() {
        final NamespacePoliciesConfig config = DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies {\n" +
                "  \"org.example.devices.*\" = [\"org.example:tenant-root\"]\n" +
                "}"));

        final Policy childPolicy =
                policyWithEntry(CHILD_POLICY_ID, CHILD_POLICY_REVISION, LOCAL_LABEL, ImportableType.IMPLICIT);
        final Label resolvedImportedLabel = PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, IMPORTED_LABEL);
        final Policy resolvedRootPolicy = Policy.newBuilder(ROOT_POLICY_ID)
                .setRevision(ROOT_POLICY_REVISION)
                .set(policyEntry(ROOT_LABEL, ImportableType.IMPLICIT))
                .set(policyEntry(resolvedImportedLabel, ImportableType.IMPLICIT))
                .build();

        when(policyCacheLoader.asyncLoad(CHILD_POLICY_ID, DIRECT_EXECUTOR))
                .thenReturn(CompletableFuture.completedFuture(Entry.of(CHILD_POLICY_REVISION, childPolicy)));
        when(cache.get(new PolicyIdResolvingImports(ROOT_POLICY_ID, true)))
                .thenReturn(CompletableFuture.completedFuture(
                        Optional.of(Entry.of(ROOT_POLICY_REVISION, new Pair<>(resolvedRootPolicy,
                                Set.of(PolicyTag.of(IMPORTED_POLICY_ID, IMPORTED_POLICY_REVISION)))))
                ));

        final ResolvedPolicyCacheLoader underTest =
                new ResolvedPolicyCacheLoader(policyCacheLoader, CompletableFuture.completedFuture(cache), config);

        final Entry<Pair<Policy, Set<PolicyTag>>> entry = underTest
                .asyncLoad(new PolicyIdResolvingImports(CHILD_POLICY_ID, true), DIRECT_EXECUTOR)
                .join();

        final Policy resolvedPolicy = entry.getValueOrThrow().first();
        final Set<PolicyTag> referencedPolicies = entry.getValueOrThrow().second();

        assertThat(resolvedPolicy.contains(LOCAL_LABEL)).isTrue();
        assertThat(resolvedPolicy.contains(ROOT_LABEL)).isTrue();
        assertThat(resolvedPolicy.contains(
                PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, IMPORTED_LABEL))).isTrue();
        assertThat(referencedPolicies)
                .contains(PolicyTag.of(ROOT_POLICY_ID, ROOT_POLICY_REVISION),
                        PolicyTag.of(IMPORTED_POLICY_ID, IMPORTED_POLICY_REVISION));
        verify(cache).get(new PolicyIdResolvingImports(ROOT_POLICY_ID, true));
        verify(policyCacheLoader, never()).asyncLoad(ROOT_POLICY_ID, DIRECT_EXECUTOR);
    }

    /**
     * Verifies the inner resolution path: asyncLoad(rootPolicyId, true) must itself call
     * cache.get(importedPolicyId, false) to resolve the root policy's own imports, and the
     * resulting entry must contain the imported label and the imported policy's tag.
     * <p>
     * The previous test mocks cache.get(ROOT_POLICY_ID, true) directly so it never exercises
     * this code path. This test does not mock that key — it lets asyncLoad compute it from
     * policyCacheLoader + cache.get(IMPORTED_POLICY_ID, false).
     */
    @Test
    public void asyncLoadOfRootPolicyResolvesItsOwnImportsViaCache() {
        final NamespacePoliciesConfig config = DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies {\n" +
                "  \"org.example.devices.*\" = [\"org.example:tenant-root\"]\n" +
                "}"));

        // Root policy has a direct entry AND an import declaration for IMPORTED_POLICY_ID
        final Policy rawRootPolicy = Policy.newBuilder(ROOT_POLICY_ID)
                .setRevision(ROOT_POLICY_REVISION)
                .set(policyEntry(ROOT_LABEL, ImportableType.IMPLICIT))
                .setPolicyImport(PoliciesModelFactory.newPolicyImport(IMPORTED_POLICY_ID))
                .build();

        // Imported policy has IMPORTED_LABEL (IMPLICIT) — must be resolvable via cache
        final Policy rawImportedPolicy = policyWithEntry(IMPORTED_POLICY_ID, IMPORTED_POLICY_REVISION,
                IMPORTED_LABEL, ImportableType.IMPLICIT);

        when(policyCacheLoader.asyncLoad(ROOT_POLICY_ID, DIRECT_EXECUTOR))
                .thenReturn(CompletableFuture.completedFuture(Entry.of(ROOT_POLICY_REVISION, rawRootPolicy)));
        // The loader resolves imported-policy via cache.get(IMPORTED_POLICY_ID, false)
        when(cache.get(new PolicyIdResolvingImports(IMPORTED_POLICY_ID, false)))
                .thenReturn(CompletableFuture.completedFuture(
                        Optional.of(Entry.of(IMPORTED_POLICY_REVISION,
                                new Pair<>(rawImportedPolicy, Set.of())))));

        final ResolvedPolicyCacheLoader underTest =
                new ResolvedPolicyCacheLoader(policyCacheLoader, CompletableFuture.completedFuture(cache), config);

        final Entry<Pair<Policy, Set<PolicyTag>>> entry = underTest
                .asyncLoad(new PolicyIdResolvingImports(ROOT_POLICY_ID, true), DIRECT_EXECUTOR)
                .join();

        assertThat(entry.exists()).isTrue();
        final Policy resolvedRoot = entry.getValueOrThrow().first();
        final Set<PolicyTag> tags = entry.getValueOrThrow().second();

        // Direct root entry must be present
        assertThat(resolvedRoot.contains(ROOT_LABEL)).isTrue();
        // Imported entry must be present under its rewritten label
        assertThat(resolvedRoot.contains(PoliciesModelFactory.newImportedLabel(IMPORTED_POLICY_ID, IMPORTED_LABEL)))
                .isTrue();
        // Imported policy's tag must be tracked so that changes to it trigger re-indexing
        assertThat(tags).contains(PolicyTag.of(IMPORTED_POLICY_ID, IMPORTED_POLICY_REVISION));

        verify(policyCacheLoader).asyncLoad(ROOT_POLICY_ID, DIRECT_EXECUTOR);
        verify(cache).get(new PolicyIdResolvingImports(IMPORTED_POLICY_ID, false));
    }

    @Test
    public void namespaceRootPolicyDoesNotAttemptToLoadItselfAgain() {
        final NamespacePoliciesConfig config = DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies {\n" +
                "  \"org.example\" = [\"org.example:tenant-root\"]\n" +
                "}"));
        final Policy rootPolicy =
                policyWithEntry(ROOT_POLICY_ID, ROOT_POLICY_REVISION, ROOT_LABEL, ImportableType.IMPLICIT);

        when(policyCacheLoader.asyncLoad(ROOT_POLICY_ID, DIRECT_EXECUTOR))
                .thenReturn(CompletableFuture.completedFuture(Entry.of(ROOT_POLICY_REVISION, rootPolicy)));

        final ResolvedPolicyCacheLoader underTest =
                new ResolvedPolicyCacheLoader(policyCacheLoader, CompletableFuture.completedFuture(cache), config);

        final Entry<Pair<Policy, Set<PolicyTag>>> entry = underTest
                .asyncLoad(new PolicyIdResolvingImports(ROOT_POLICY_ID, true), DIRECT_EXECUTOR)
                .join();

        assertThat(entry.exists()).isTrue();
        assertThat(entry.getValueOrThrow().second()).isEmpty();
        verify(policyCacheLoader, times(1)).asyncLoad(ROOT_POLICY_ID, DIRECT_EXECUTOR);
        verifyNoInteractions(cache);
    }

    @Test
    public void rootPolicyStillMergesOtherMatchingNamespaceRoots() {
        final NamespacePoliciesConfig config = DefaultNamespacePoliciesConfig.of(ConfigFactory.parseString(
                "ditto.namespace-policies {\n" +
                "  \"org.example\" = [\"org.example:tenant-root\"]\n" +
                "  \"*\" = [\"global:catch-all\"]\n" +
                "}"));
        final Policy rootPolicy =
                policyWithEntry(ROOT_POLICY_ID, ROOT_POLICY_REVISION, ROOT_LABEL, ImportableType.IMPLICIT);
        final Policy globalRootPolicy = policyWithEntry(GLOBAL_ROOT_POLICY_ID, GLOBAL_ROOT_POLICY_REVISION,
                GLOBAL_ROOT_LABEL, ImportableType.IMPLICIT);

        when(policyCacheLoader.asyncLoad(ROOT_POLICY_ID, DIRECT_EXECUTOR))
                .thenReturn(CompletableFuture.completedFuture(Entry.of(ROOT_POLICY_REVISION, rootPolicy)));
        when(cache.get(new PolicyIdResolvingImports(GLOBAL_ROOT_POLICY_ID, true)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(Entry.of(GLOBAL_ROOT_POLICY_REVISION,
                        new Pair<>(globalRootPolicy, Set.of())))));

        final ResolvedPolicyCacheLoader underTest =
                new ResolvedPolicyCacheLoader(policyCacheLoader, CompletableFuture.completedFuture(cache), config);

        final Entry<Pair<Policy, Set<PolicyTag>>> entry = underTest
                .asyncLoad(new PolicyIdResolvingImports(ROOT_POLICY_ID, true), DIRECT_EXECUTOR)
                .join();

        assertThat(entry.exists()).isTrue();
        assertThat(entry.getValueOrThrow().first().contains(ROOT_LABEL)).isTrue();
        assertThat(entry.getValueOrThrow().first().contains(GLOBAL_ROOT_LABEL)).isTrue();
        verify(cache).get(new PolicyIdResolvingImports(GLOBAL_ROOT_POLICY_ID, true));
        verify(cache, never()).get(new PolicyIdResolvingImports(ROOT_POLICY_ID, true));
    }

    private static Policy policyWithEntry(final PolicyId policyId,
            final long revision,
            final Label label,
            final ImportableType importableType) {
        return Policy.newBuilder(policyId)
                .setRevision(revision)
                .set(policyEntry(label, importableType))
                .build();
    }

    private static org.eclipse.ditto.policies.model.PolicyEntry policyEntry(final Label label,
            final ImportableType importableType) {
        final Subjects subjects = Subjects.newInstance(
                Subject.newInstance(
                        SubjectId.newInstance(SubjectIssuer.newInstance(label.toString()), "user"),
                        SubjectType.newInstance("test")));
        final Resources resources = Resources.newInstance(
                Resource.newInstance("thing", JsonPointer.of("/"),
                        EffectedPermissions.newInstance(Permissions.newInstance("READ"), Permissions.none())));
        return PoliciesModelFactory.newPolicyEntry(label, subjects, resources, importableType);
    }
}
