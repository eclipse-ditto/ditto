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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.AllowedImportAddition;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
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
    public void forNamespaceWithNullPolicyReturnsSameInstance() {
        final PolicyEnforcer enforcer = PolicyEnforcer.embed(
                org.eclipse.ditto.internal.utils.cache.entry.Entry.of(0L,
                        org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers.defaultEvaluator(
                                Collections.emptyList())))
                .getValueOrThrow();
        assertThat(enforcer.forNamespace("any.ns")).isSameAs(enforcer);
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
                Collections.<AllowedImportAddition>emptySet());
    }
}
