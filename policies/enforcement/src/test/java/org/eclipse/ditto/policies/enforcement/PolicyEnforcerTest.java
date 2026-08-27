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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.enforcers.SubjectClassification;
import org.junit.Test;

public final class PolicyEnforcerTest {


    @Test
    public void forNamespaceFiltersRestrictedEntriesButKeepsGlobalEntries() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("restricted", "google:tenant-user",
                        Arrays.asList("com.acme", "com.acme.*")))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();

        final org.eclipse.ditto.policies.model.enforcers.Enforcer filteredEnforcer = PolicyEnforcer.of(policy)
                .forNamespace("org.example")
                .getEnforcer();

        final Set<AuthorizationSubject> subjects = filteredEnforcer.getSubjectsWithUnrestrictedPermission(
                PoliciesResourceType.thingResource("/"), Permission.READ);
        assertThat(subjects).contains(AuthorizationSubject.newInstance("google:global-user"));
        assertThat(subjects).doesNotContain(AuthorizationSubject.newInstance("google:tenant-user"));
    }

    @Test
    public void forNamespaceKeepsMatchingRestrictedEntries() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("restricted", "google:tenant-user",
                        Arrays.asList("com.acme", "com.acme.*")))
                .build();

        final org.eclipse.ditto.policies.model.enforcers.Enforcer filteredEnforcer = PolicyEnforcer.of(policy)
                .forNamespace("com.acme.vehicles")
                .getEnforcer();

        final Set<AuthorizationSubject> subjects = filteredEnforcer.getSubjectsWithUnrestrictedPermission(
                PoliciesResourceType.thingResource("/"), Permission.READ);
        assertThat(subjects).containsExactly(AuthorizationSubject.newInstance("google:tenant-user"));
    }

    @Test
    public void forNamespaceReturnsSameInstanceWhenNoEntriesAreRestricted() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        final PolicyEnforcer original = PolicyEnforcer.of(policy);

        final PolicyEnforcer filtered = original.forNamespace("com.acme");

        assertThat(filtered).isSameAs(original);
    }

    @Test
    public void forNamespaceMemoizesResultForRepeatedCalls() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("restricted", "google:tenant-user",
                        Arrays.asList("com.acme", "com.acme.*")))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        final PolicyEnforcer original = PolicyEnforcer.of(policy);

        // "org.example" filters out the restricted entry, so a new (filtered) enforcer is built the first
        // time. A second call for the same namespace must return the memoized instance, not rebuild the tree.
        final PolicyEnforcer first = original.forNamespace("org.example");
        final PolicyEnforcer second = original.forNamespace("org.example");

        assertThat(first).isNotSameAs(original);
        assertThat(second).isSameAs(first);
    }

    @Test
    public void forNamespaceReturnsDistinctEnforcersForDistinctNamespaces() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("restricted", "google:tenant-user",
                        Arrays.asList("com.acme", "com.acme.*")))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        final PolicyEnforcer original = PolicyEnforcer.of(policy);

        // "org.example" excludes the restricted entry; "com.acme.vehicles" keeps it.
        final PolicyEnforcer excluding = original.forNamespace("org.example");
        final PolicyEnforcer including = original.forNamespace("com.acme.vehicles");

        assertThat(excluding).isNotSameAs(including);
        assertThat(excluding.getEnforcer().getSubjectsWithUnrestrictedPermission(
                PoliciesResourceType.thingResource("/"), Permission.READ))
                .doesNotContain(AuthorizationSubject.newInstance("google:tenant-user"));
        assertThat(including.getEnforcer().getSubjectsWithUnrestrictedPermission(
                PoliciesResourceType.thingResource("/"), Permission.READ))
                .contains(AuthorizationSubject.newInstance("google:tenant-user"));
    }

    @Test
    public void forNamespaceWithNullPolicyReturnsSameInstance() {
        final PolicyEnforcer enforcer = PolicyEnforcer.embed(
                org.eclipse.ditto.internal.utils.cache.entry.Entry.of(0L,
                        org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers.defaultEvaluator(
                                Collections.emptyList())))
                .getValueOrThrow();
        assertThat(enforcer.forNamespace("any.ns")).isSameAs(enforcer);
    }

    @Test
    public void classifyReadSubjectsMatchesDirectEnforcerClassification() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(policy);
        final ResourceKey root = PoliciesResourceType.thingResource("/");

        final SubjectClassification memoized = policyEnforcer.classifyReadSubjects(root);
        final SubjectClassification direct = policyEnforcer.getEnforcer()
                .classifySubjects(root, PoliciesModelFactory.newPermissions(Permission.READ));

        assertThat(memoized.getUnrestricted()).isEqualTo(direct.getUnrestricted());
        assertThat(memoized.getPartialOnly()).isEqualTo(direct.getPartialOnly());
        assertThat(memoized.getEffectedGranted()).isEqualTo(direct.getEffectedGranted());
    }

    @Test
    public void classifyReadSubjectsMemoizesRepeatedCallsForSameResource() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(policy);
        final ResourceKey root = PoliciesResourceType.thingResource("/");

        // A second call for the same resource must return the memoized instance, not re-walk the policy tree.
        final SubjectClassification first = policyEnforcer.classifyReadSubjects(root);
        final SubjectClassification second = policyEnforcer.classifyReadSubjects(root);

        assertThat(second).isSameAs(first);
    }

    @Test
    public void getRootResourceReadClassificationDelegatesToClassifyReadSubjects() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(policy);

        assertThat(policyEnforcer.getRootResourceReadClassification())
                .isSameAs(policyEnforcer.classifyReadSubjects(PoliciesResourceType.thingResource("/")));
    }

    @Test
    public void classifyReadSubjectsStaysCorrectUnderBoundedCache() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        // Tiny bound: cache correctness must not depend on capacity — a miss simply recomputes an equal result.
        final PolicyEnforcer bounded = PolicyEnforcer.of(policy, 100, 1);
        final ResourceKey a = PoliciesResourceType.thingResource("/features/a");
        final ResourceKey b = PoliciesResourceType.thingResource("/features/b");

        final SubjectClassification classificationA = bounded.classifyReadSubjects(a);
        bounded.classifyReadSubjects(b);
        final SubjectClassification classificationAgain = bounded.classifyReadSubjects(a);

        assertThat(classificationAgain.getUnrestricted()).isEqualTo(classificationA.getUnrestricted());
        assertThat(classificationAgain.getPartialOnly()).isEqualTo(classificationA.getPartialOnly());
    }

    @Test
    public void getReadGrantedSubjectsHeaderValueMemoizesRepeatedCalls() {
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(partialGrantsPolicy());
        final ResourceKey attributeX = PoliciesResourceType.thingResource("/attributes/x");

        assertThat(policyEnforcer.getReadGrantedSubjectsHeaderValue(attributeX, true))
                .isSameAs(policyEnforcer.getReadGrantedSubjectsHeaderValue(attributeX, true));
        assertThat(policyEnforcer.getReadGrantedSubjectsHeaderValue(attributeX, false))
                .isSameAs(policyEnforcer.getReadGrantedSubjectsHeaderValue(attributeX, false));
    }

    @Test
    public void getReadGrantedSubjectsHeaderValueRendersClassificationUnion() {
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(partialGrantsPolicy());
        final ResourceKey attributeX = PoliciesResourceType.thingResource("/attributes/x");

        // includePartial: unrestricted(/attributes/x) = alice + bob, plus root partial readers = carol
        assertThat(policyEnforcer.getReadGrantedSubjectsHeaderValue(attributeX, true))
                .isEqualTo(JsonArray.of("[\"user:alice\",\"user:bob\",\"user:carol\"]"));
        // without partial: only root-unrestricted readers
        assertThat(policyEnforcer.getReadGrantedSubjectsHeaderValue(attributeX, false))
                .isEqualTo(JsonArray.of("[\"user:alice\"]"));
    }

    @Test
    public void getReadGrantedSubjectsHeaderValueIsSortedAndDeduplicated() {
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(partialGrantsPolicy());

        // alice is both root-unrestricted and unrestricted on the resource, so she must appear exactly once;
        // sorting makes the rendered value stable across pods and restarts.
        final JsonArray headerValue = policyEnforcer.getReadGrantedSubjectsHeaderValue(
                PoliciesResourceType.thingResource("/attributes/x"), true);

        assertThat(headerValue.stream().map(JsonValue::asString))
                .containsExactly("user:alice", "user:bob", "user:carol");
    }

    @Test
    public void getReadGrantedSubjectsHeaderValueIsEmptyWithoutReadGrants() {
        final Policy writeOnlyPolicy = Policy.newBuilder(PolicyId.of("test:policy"))
                .setRevision(1L)
                .setSubjectFor("alice", Subject.newInstance(SubjectId.newInstance("user:alice")))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), Permission.WRITE)
                .build();
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(writeOnlyPolicy);

        assertThat(policyEnforcer.getReadGrantedSubjectsHeaderValue(
                PoliciesResourceType.thingResource("/"), false)).isEmpty();
        assertThat(policyEnforcer.getReadGrantedSubjectsHeaderValue(
                PoliciesResourceType.thingResource("/"), true)).isEmpty();
    }

    @Test
    public void getReadGrantedSubjectsHeaderValueStaysCorrectUnderBoundedCache() {
        // Tiny bound: correctness must not depend on capacity — an evicted entry simply re-renders an equal value.
        final PolicyEnforcer bounded = PolicyEnforcer.of(partialGrantsPolicy(), 100, 1);
        final ResourceKey a = PoliciesResourceType.thingResource("/attributes/x");
        final ResourceKey b = PoliciesResourceType.thingResource("/attributes/y");

        final JsonArray valueA = bounded.getReadGrantedSubjectsHeaderValue(a, true);
        bounded.getReadGrantedSubjectsHeaderValue(b, true);

        assertThat(bounded.getReadGrantedSubjectsHeaderValue(a, true)).isEqualTo(valueA);
    }

    @Test
    public void getReadGrantedSubjectsHeaderValueOfNamespaceChildIsComputedIndependently() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("restricted", "google:tenant-user", Arrays.asList("com.acme", "com.acme.*")))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
        final PolicyEnforcer parent = PolicyEnforcer.of(policy, 100, 100);
        final ResourceKey root = PoliciesResourceType.thingResource("/");

        final JsonArray unfiltered = parent.getReadGrantedSubjectsHeaderValue(root, false);
        final JsonArray filtered = parent.forNamespace("org.example").getReadGrantedSubjectsHeaderValue(root, false);

        assertThat(unfiltered.stream().map(JsonValue::asString))
                .containsExactly("google:global-user", "google:tenant-user");
        assertThat(filtered.stream().map(JsonValue::asString)).containsExactly("google:global-user");
    }

    /** alice: unrestricted READ at root; bob: READ only on /attributes/x; carol: READ only on /attributes/y. */
    private static Policy partialGrantsPolicy() {
        return Policy.newBuilder(PolicyId.of("test:policy"))
                .setRevision(1L)
                .setSubjectFor("alice", Subject.newInstance(SubjectId.newInstance("user:alice")))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), Permission.READ)
                .setSubjectFor("bob", Subject.newInstance(SubjectId.newInstance("user:bob")))
                .setGrantedPermissionsFor("bob", ResourceKey.newInstance("thing", "/attributes/x"), Permission.READ)
                .setSubjectFor("carol", Subject.newInstance(SubjectId.newInstance("user:carol")))
                .setGrantedPermissionsFor("carol", ResourceKey.newInstance("thing", "/attributes/y"), Permission.READ)
                .build();
    }

    @Test
    public void forNamespaceChildInheritsConfiguredAuthorizationMemoBound() {
        final PolicyEnforcer parent = providerStyleEnforcer(10_000L);

        // The namespace-filtered child - the instance the things-service hot path enforces against - is built
        // by a separate defaultEvaluator call, so it has to be handed the configured bound explicitly.
        assertThat(parent.getAuthorizationMemoMaxSize()).isEqualTo(10_000);
        assertThat(namespaceFilteredChild(parent).getAuthorizationMemoMaxSize()).isEqualTo(10_000);
    }

    @Test
    public void forNamespaceChildInheritsDisabledAuthorizationMemo() {
        final PolicyEnforcer parent = providerStyleEnforcer(0L);

        // 0 is the documented escape hatch for disabling the memo entirely; it must reach the child too,
        // otherwise an operator disabling it still gets a memoizing enforcer on the hot path.
        assertThat(namespaceFilteredChild(parent).getAuthorizationMemoMaxSize()).isEqualTo(0);
    }

    @Test
    public void configuredAuthorizationMemoBoundAboveIntMaxSaturatesInsteadOfWrapping() {
        final PolicyEnforcer parent = providerStyleEnforcer(3_000_000_000L);

        // A plain (int) cast wraps this to a negative number, which reads as "disabled" - the opposite of what
        // an operator configuring a very large bound asked for. Saturate at Integer.MAX_VALUE instead.
        assertThat(parent.getAuthorizationMemoMaxSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(namespaceFilteredChild(parent).getAuthorizationMemoMaxSize()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void transientEnforcerHasNoConfiguredAuthorizationMemoBound() {
        // of/embed instances carry no operator configuration, so children fall back to the library default.
        assertThat(PolicyEnforcer.of(namespaceScopedPolicy()).getAuthorizationMemoMaxSize()).isNull();
    }

    /**
     * Builds a {@code PolicyEnforcer} the way {@code PolicyEnforcerCacheLoader} does, i.e. through the
     * config-carrying factory, with the given authorization-memo bound.
     */
    private static PolicyEnforcer providerStyleEnforcer(final long authorizationMemoMaxSize) {
        final NamespacePoliciesConfig emptyNamespacePolicies = mock(NamespacePoliciesConfig.class);
        when(emptyNamespacePolicies.isEmpty()).thenReturn(true);

        return PolicyEnforcer.withResolvedImportsAndNamespacePolicies(namespaceScopedPolicy(),
                        policyId -> CompletableFuture.completedFuture(Optional.empty()), emptyNamespacePolicies,
                        100L, 100L, authorizationMemoMaxSize)
                .toCompletableFuture()
                .join();
    }

    /**
     * Returns the namespace-filtered child for a namespace that excludes the scoped entry, so
     * {@code forNamespace} genuinely builds a new child enforcer rather than returning the parent.
     */
    private static PolicyEnforcer namespaceFilteredChild(final PolicyEnforcer parent) {
        final PolicyEnforcer child = parent.forNamespace("org.example");
        assertThat(child).isNotSameAs(parent);
        return child;
    }

    private static Policy namespaceScopedPolicy() {
        return PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test:policy"))
                .set(newScopedEntry("restricted", "google:tenant-user",
                        Arrays.asList("com.acme", "com.acme.*")))
                .set(newScopedEntry("global", "google:global-user", Collections.emptyList()))
                .build();
    }

    private static PolicyEntry newScopedEntry(final String label, final String subjectId, final List<String> namespaces) {
        return PoliciesModelFactory.newPolicyEntry(label,
                Collections.singletonList(Subject.newInstance(SubjectId.newInstance(subjectId))),
                Collections.singletonList(PoliciesModelFactory.newResource("thing", "/",
                        EffectedPermissions.newInstance(
                                PoliciesModelFactory.newPermissions(Permission.READ),
                                PoliciesModelFactory.noPermissions()))),
                namespaces,
                ImportableType.IMPLICIT,
                Collections.<AllowedAddition>emptySet());
    }
}
