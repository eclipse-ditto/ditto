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
package org.eclipse.ditto.things.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.junit.Test;

/**
 * Verifies that the memoized read-subject computation in
 * {@link ThingCommandEnforcement#addEffectedReadSubjectsToThingSignal} produces exactly the same
 * {@code readGrantedSubjects} as the previous, un-memoized formula
 * ({@code getSubjectsWithUnrestrictedPermission(eventResource)} &cup;
 * {@code getSubjectsWithPartialPermission(root)}).
 */
public final class AddEffectedReadSubjectsTest {

    private static final PolicyId POLICY_ID = PolicyId.of("test:policy");
    private static final ThingId THING_ID = ThingId.of("test:thing");

    /** alice: unrestricted READ at root; bob: READ only on /attributes/x; carol: READ only on /attributes/y. */
    private static Policy policy() {
        return Policy.newBuilder(POLICY_ID)
                .setRevision(1L)
                .setSubjectFor("alice", subject("user:alice"))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), Permission.READ)
                .setSubjectFor("bob", subject("user:bob"))
                .setGrantedPermissionsFor("bob", ResourceKey.newInstance("thing", "/attributes/x"), Permission.READ)
                .setSubjectFor("carol", subject("user:carol"))
                .setGrantedPermissionsFor("carol", ResourceKey.newInstance("thing", "/attributes/y"), Permission.READ)
                .build();
    }

    @Test
    public void memoizedResultEqualsLegacyFormulaWithPartialSubjects() {
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(policy());
        final AttributeModified event = attributeModified("x");

        final Set<AuthorizationSubject> actual = readGrantedSubjects(
                ThingCommandEnforcement.addEffectedReadSubjectsToThingSignal(event, policyEnforcer, true));

        assertThat(actual).isEqualTo(legacyReadSubjects(policyEnforcer.getEnforcer(), event, true));
        // sanity: at /attributes/x the unrestricted readers are alice+bob; carol is only a partial (root) reader
        assertThat(actual).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance("user:alice"),
                AuthorizationSubject.newInstance("user:bob"),
                AuthorizationSubject.newInstance("user:carol"));
    }

    @Test
    public void memoizedResultEqualsLegacyFormulaWithoutPartialSubjects() {
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(policy());
        final AttributeModified event = attributeModified("x");

        final Set<AuthorizationSubject> actual = readGrantedSubjects(
                ThingCommandEnforcement.addEffectedReadSubjectsToThingSignal(event, policyEnforcer, false));

        assertThat(actual).isEqualTo(legacyReadSubjects(policyEnforcer.getEnforcer(), event, false));
        // without partial subjects only root-unrestricted readers (alice) are granted
        assertThat(actual).containsExactly(AuthorizationSubject.newInstance("user:alice"));
    }

    /** The exact computation the code performed before the PolicyEnforcer memo was introduced. */
    private static Set<AuthorizationSubject> legacyReadSubjects(final Enforcer enforcer,
            final AttributeModified event, final boolean includePartialReadSubjects) {
        final ResourceKey eventKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, event.getResourcePath());
        final ResourceKey rootKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, JsonPointer.empty());
        final Set<AuthorizationSubject> result = new HashSet<>(includePartialReadSubjects
                ? enforcer.getSubjectsWithUnrestrictedPermission(eventKey, Permission.READ)
                : enforcer.getSubjectsWithUnrestrictedPermission(rootKey, Permission.READ));
        if (includePartialReadSubjects) {
            result.addAll(enforcer.getSubjectsWithPartialPermission(rootKey,
                    org.eclipse.ditto.policies.model.Permissions.newInstance(Permission.READ)));
        }
        return result;
    }

    private static Set<AuthorizationSubject> readGrantedSubjects(final AttributeModified enriched) {
        return new HashSet<>(enriched.getDittoHeaders().getReadGrantedSubjects());
    }

    private static AttributeModified attributeModified(final String attribute) {
        return AttributeModified.of(THING_ID, JsonPointer.of(attribute), JsonValue.of(1), 2L,
                Instant.now(), DittoHeaders.empty(), null);
    }

    private static Subject subject(final String id) {
        return Subject.newInstance(SubjectId.newInstance(id), SubjectType.GENERATED);
    }
}
