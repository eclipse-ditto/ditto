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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.junit.Test;

/**
 * Verifies the per-instance authorization-verdict memoization on {@link TreeBasedPolicyEnforcer}: repeat
 * calls return results identical to a fresh (cold) enforcer, and distinct
 * (resource, subjects, permissions, check-kind) inputs are never conflated by the shared caches.
 */
public final class TreeBasedPolicyEnforcerMemoTest {

    private static final Permissions READ = Permissions.newInstance("READ");
    private static final Permissions WRITE = Permissions.newInstance("WRITE");
    private static final ResourceKey THING_ROOT = ResourceKey.newInstance("thing", "/");

    // fullSubject: unrestricted READ+WRITE on thing:/ .
    private static final AuthorizationSubject FULL = AuthorizationSubject.newInstance("test:full");
    // readOnlySubject: unrestricted READ only on thing:/ (used to prove READ vs WRITE keys don't conflate).
    private static final AuthorizationSubject READ_ONLY = AuthorizationSubject.newInstance("test:read-only");
    // partialSubject: granted READ on thing:/ but READ revoked on a child -> partial, not unrestricted.
    private static final AuthorizationSubject PARTIAL = AuthorizationSubject.newInstance("test:partial");
    // strangerSubject: has no grants at all.
    private static final AuthorizationSubject STRANGER = AuthorizationSubject.newInstance("test:stranger");

    private static Policy policy() {
        return Policy.newBuilder(PolicyId.of("namespace", "id"))
                .forLabel("full")
                .setSubject(FULL.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), Permissions.newInstance("READ", "WRITE"))
                .forLabel("read-only")
                .setSubject(READ_ONLY.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .forLabel("partial")
                .setSubject(PARTIAL.getId(), SubjectType.GENERATED)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), READ)
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/secret"), READ)
                .build();
    }

    private static AuthorizationContext ctx(final AuthorizationSubject subject) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, subject);
    }

    @Test
    public void hasUnrestrictedPermissionsIsConsistentAcrossRepeatedCalls() {
        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy());
        final TreeBasedPolicyEnforcer cold = TreeBasedPolicyEnforcer.createInstance(policy());

        // first call populates the memo, second call hits it: both must equal the cold oracle.
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ)).isTrue();
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ))
                .isEqualTo(cold.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ));
    }

    @Test
    public void distinctSubjectSetsAreNotConflated() {
        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy());

        // Same resource + permission, different subject -> different verdict; the subject set is part of the key.
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ)).isTrue();
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(STRANGER), READ)).isFalse();
        // re-query the first one to ensure the second did not overwrite it.
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ)).isTrue();
    }

    @Test
    public void distinctPermissionsAreNotConflated() {
        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy());

        // read-only subject: READ granted (unrestricted) but WRITE not -> permissions must be part of the key.
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(READ_ONLY), READ)).isTrue();
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(READ_ONLY), WRITE)).isFalse();
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(READ_ONLY), READ)).isTrue();
    }

    @Test
    public void unrestrictedAndPartialChecksShareNoKey() {
        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy());
        final TreeBasedPolicyEnforcer cold = TreeBasedPolicyEnforcer.createInstance(policy());

        // The partial subject has a revoke below root: NOT unrestricted on root, but partially permitted.
        // If the two checks shared a cache key (missing the 'partial' discriminator), one would poison the other.
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(PARTIAL), READ))
                .isEqualTo(cold.hasUnrestrictedPermissions(THING_ROOT, ctx(PARTIAL), READ))
                .isFalse();
        assertThat(underTest.hasPartialPermissions(THING_ROOT, ctx(PARTIAL), READ))
                .isEqualTo(cold.hasPartialPermissions(THING_ROOT, ctx(PARTIAL), READ))
                .isTrue();
        // and again in the reverse order after both are cached:
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, ctx(PARTIAL), READ)).isFalse();
        assertThat(underTest.hasPartialPermissions(THING_ROOT, ctx(PARTIAL), READ)).isTrue();
    }

    @Test
    public void getSubjectsWithPermissionReturnsCachedImmutableInstanceEqualToColdEnforcer() {
        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy());
        final TreeBasedPolicyEnforcer cold = TreeBasedPolicyEnforcer.createInstance(policy());

        final EffectedSubjects first = underTest.getSubjectsWithPermission(THING_ROOT, READ);
        final EffectedSubjects second = underTest.getSubjectsWithPermission(THING_ROOT, READ);

        assertThat(first).isEqualTo(cold.getSubjectsWithPermission(THING_ROOT, READ));
        // EffectedSubjects is immutable, so a cache hit returns the very same instance (proves memoization is live).
        assertThat(second).isSameAs(first);
        assertThat(first.getGranted()).contains(FULL, READ_ONLY, PARTIAL);
    }

    @Test
    public void getSubjectsWithPartialPermissionReturnsFreshMutableCopyOnEachCall() {
        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy());
        final TreeBasedPolicyEnforcer cold = TreeBasedPolicyEnforcer.createInstance(policy());

        final Set<AuthorizationSubject> first = underTest.getSubjectsWithPartialPermission(THING_ROOT, READ);
        assertThat(first).isEqualTo(cold.getSubjectsWithPartialPermission(THING_ROOT, READ));

        // The contract returns a private, mutable set; mutating it must NOT corrupt the memoized snapshot.
        final Set<AuthorizationSubject> snapshotBeforeMutation = new HashSet<>(first);
        first.clear();
        first.add(AuthorizationSubject.newInstance("test:injected"));

        final Set<AuthorizationSubject> second = underTest.getSubjectsWithPartialPermission(THING_ROOT, READ);
        assertThat(second).isEqualTo(snapshotBeforeMutation);
        assertThat(second).isNotSameAs(first);
    }

    @Test
    public void memoizationDisabledAllocatesNoMemosAndStaysCorrect() throws Exception {
        final TreeBasedPolicyEnforcer disabled = TreeBasedPolicyEnforcer.createInstance(policy(), 0);
        final TreeBasedPolicyEnforcer cold = TreeBasedPolicyEnforcer.createInstance(policy());

        assertThat(disabled.isMemoizationEnabled()).isFalse();
        // No ConcurrentHashMaps are allocated when disabled: the three memo fields must be null.
        assertThat(memoField(disabled, "permissionCheckMemo")).isNull();
        assertThat(memoField(disabled, "effectedSubjectsMemo")).isNull();
        assertThat(memoField(disabled, "partialSubjectsMemo")).isNull();

        // All four memoized methods must still return results identical to a cold (memoizing) enforcer.
        assertThat(disabled.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ))
                .isEqualTo(cold.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ)).isTrue();
        assertThat(disabled.hasUnrestrictedPermissions(THING_ROOT, ctx(STRANGER), READ))
                .isEqualTo(cold.hasUnrestrictedPermissions(THING_ROOT, ctx(STRANGER), READ)).isFalse();
        assertThat(disabled.hasPartialPermissions(THING_ROOT, ctx(PARTIAL), READ))
                .isEqualTo(cold.hasPartialPermissions(THING_ROOT, ctx(PARTIAL), READ)).isTrue();
        assertThat(disabled.getSubjectsWithPermission(THING_ROOT, READ))
                .isEqualTo(cold.getSubjectsWithPermission(THING_ROOT, READ));
        assertThat(disabled.getSubjectsWithPartialPermission(THING_ROOT, READ))
                .isEqualTo(cold.getSubjectsWithPartialPermission(THING_ROOT, READ));

        // With no cache, repeated getSubjectsWithPermission recomputes -> not the same instance (proves no memo).
        assertThat(disabled.getSubjectsWithPermission(THING_ROOT, READ))
                .isNotSameAs(disabled.getSubjectsWithPermission(THING_ROOT, READ));
    }

    @Test
    public void smallCapStaysCorrectEvenWhenExceeded() {
        final TreeBasedPolicyEnforcer bounded = TreeBasedPolicyEnforcer.createInstance(policy(), 4);
        final TreeBasedPolicyEnforcer cold = TreeBasedPolicyEnforcer.createInstance(policy());

        assertThat(bounded.isMemoizationEnabled()).isTrue();
        // Push well past the cap of 4 with distinct subject-sets: above the cap, verdicts are computed uncached,
        // but must remain correct for every distinct key (cached or not).
        for (int i = 0; i < 20; i++) {
            final AuthorizationContext stranger =
                    ctx(AuthorizationSubject.newInstance("test:stranger-" + i));
            assertThat(bounded.hasUnrestrictedPermissions(THING_ROOT, stranger, READ))
                    .isEqualTo(cold.hasUnrestrictedPermissions(THING_ROOT, stranger, READ)).isFalse();
        }
        // The genuinely-permitted subjects still resolve correctly after the cap was hit.
        assertThat(bounded.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ)).isTrue();
        assertThat(bounded.hasUnrestrictedPermissions(THING_ROOT, ctx(FULL), READ)).isTrue();
    }

    @Test
    public void differentSubjectOrderingsStayCorrectDespiteOrderDependentKeys() throws Exception {
        final TreeBasedPolicyEnforcer underTest = TreeBasedPolicyEnforcer.createInstance(policy());
        final TreeBasedPolicyEnforcer cold = TreeBasedPolicyEnforcer.createInstance(policy());

        // The memo keys on the AuthorizationContext's own memoized ID *list*, so key equality is
        // order-dependent and these two contexts occupy separate entries. Each entry's verdict is computed
        // from its own IDs, so both orderings must still agree with a cold enforcer.
        final AuthorizationContext strangerFirst = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, STRANGER, FULL);
        final AuthorizationContext fullFirst = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, FULL, STRANGER);

        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, strangerFirst, READ))
                .isEqualTo(cold.hasUnrestrictedPermissions(THING_ROOT, strangerFirst, READ)).isTrue();
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, fullFirst, READ))
                .isEqualTo(cold.hasUnrestrictedPermissions(THING_ROOT, fullFirst, READ)).isTrue();

        // Repeat calls are memo hits now; neither ordering may have been contaminated by the other.
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, strangerFirst, READ)).isTrue();
        assertThat(underTest.hasUnrestrictedPermissions(THING_ROOT, fullFirst, READ)).isTrue();

        // Documented consequence of the list-based key: one entry per ordering, not one shared entry.
        assertThat((Map<?, ?>) memoField(underTest, "permissionCheckMemo")).hasSize(2);
    }

    private static Object memoField(final TreeBasedPolicyEnforcer enforcer, final String fieldName)
            throws Exception {
        final Field field = TreeBasedPolicyEnforcer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(enforcer);
    }
}
