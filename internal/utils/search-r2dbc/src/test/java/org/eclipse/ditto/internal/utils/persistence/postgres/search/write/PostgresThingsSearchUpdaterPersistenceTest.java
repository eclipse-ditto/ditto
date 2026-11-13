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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.write;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;
import org.junit.Test;

/**
 * Unit tests for {@link PostgresThingsSearchUpdaterPersistence#policyReferenceTags} — the pure fan-out {@code mapConcat}
 * logic (row → {@link PolicyReferenceTag}s against a revisions map), transcribing
 * {@code MongoThingsSearchUpdaterPersistence#getPolicyReferenceTags}. No database.
 */
public final class PostgresThingsSearchUpdaterPersistenceTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "thing-1");
    private static final PolicyId OWN_POLICY = PolicyId.of("org.eclipse.ditto", "own-policy");
    private static final PolicyId IMPORTED_POLICY = PolicyId.of("org.eclipse.ditto", "imported-policy");
    private static final PolicyId UNRELATED_POLICY = PolicyId.of("org.eclipse.ditto", "unrelated-policy");

    /** referenced_policies as stored: an array of FULL PolicyTag.toJson ({@code {"type","id","revision"}}). */
    private static String referencedPoliciesJson(final PolicyTag... tags) {
        final JsonArrayBuilder builder = JsonArray.newBuilder();
        for (final PolicyTag tag : tags) {
            builder.add(tag.toJson());
        }
        return builder.build().toString();
    }

    @Test
    public void ownPolicyPresentInMapEmitsTagWithMapRevision() {
        // stored own-policy revision is 3; the incoming map carries the NEW revision 9 — the emitted tag carries 9.
        final String refs = referencedPoliciesJson(PolicyTag.of(OWN_POLICY, 3L));
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), OWN_POLICY.toString(), refs, Map.of(OWN_POLICY, 9L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(THING_ID, PolicyTag.of(OWN_POLICY, 9L)));
    }

    @Test
    public void importedPolicyReferencedViaReferencedPoliciesEmitsTag() {
        final String refs = referencedPoliciesJson(PolicyTag.of(OWN_POLICY, 3L), PolicyTag.of(IMPORTED_POLICY, 2L));
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), OWN_POLICY.toString(), refs, Map.of(IMPORTED_POLICY, 5L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(THING_ID, PolicyTag.of(IMPORTED_POLICY, 5L)));
    }

    @Test
    public void bothOwnAndImportedPresentInMapEmitBothTags() {
        final String refs = referencedPoliciesJson(PolicyTag.of(OWN_POLICY, 3L), PolicyTag.of(IMPORTED_POLICY, 2L));
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), OWN_POLICY.toString(), refs,
                Map.of(OWN_POLICY, 9L, IMPORTED_POLICY, 5L));

        assertThat(tags).containsExactlyInAnyOrder(
                PolicyReferenceTag.of(THING_ID, PolicyTag.of(OWN_POLICY, 9L)),
                PolicyReferenceTag.of(THING_ID, PolicyTag.of(IMPORTED_POLICY, 5L)));
    }

    @Test
    public void policiesNotInMapAreDropped() {
        // the map hits only the imported policy; the own policy is NOT in the map -> only the imported tag is emitted.
        final String refs = referencedPoliciesJson(PolicyTag.of(OWN_POLICY, 3L), PolicyTag.of(IMPORTED_POLICY, 2L));
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), OWN_POLICY.toString(), refs, Map.of(IMPORTED_POLICY, 5L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(THING_ID, PolicyTag.of(IMPORTED_POLICY, 5L)));
    }

    @Test
    public void unrelatedPolicyInMapEmitsNothing() {
        final String refs = referencedPoliciesJson(PolicyTag.of(OWN_POLICY, 3L));
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), OWN_POLICY.toString(), refs, Map.of(UNRELATED_POLICY, 5L));

        assertThat(tags).isEmpty();
    }

    @Test
    public void nullReferencedPoliciesFallsBackToOwnPolicyOnly() {
        // emptied-out/legacy rows carry a NULL referenced_policies; the own policy_id is still considered.
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), OWN_POLICY.toString(), null, Map.of(OWN_POLICY, 9L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(THING_ID, PolicyTag.of(OWN_POLICY, 9L)));
    }

    @Test
    public void nullPolicyIdIsToleratedAndMatchesViaReferencedPoliciesOnly() {
        final String refs = referencedPoliciesJson(PolicyTag.of(IMPORTED_POLICY, 2L));
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), null, refs, Map.of(IMPORTED_POLICY, 5L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(THING_ID, PolicyTag.of(IMPORTED_POLICY, 5L)));
    }

    @Test
    public void ownPolicyAlsoAppearingInReferencedPoliciesIsNotDuplicated() {
        // the own policy is stored both as policy_id and inside referenced_policies (the C2 write engine mirrors Mongo);
        // it must be emitted exactly once.
        final String refs = referencedPoliciesJson(PolicyTag.of(OWN_POLICY, 3L));
        final List<PolicyReferenceTag> tags = PostgresThingsSearchUpdaterPersistence.policyReferenceTags(
                THING_ID.toString(), OWN_POLICY.toString(), refs, Map.of(OWN_POLICY, 9L));

        assertThat(tags).containsExactly(PolicyReferenceTag.of(THING_ID, PolicyTag.of(OWN_POLICY, 9L)));
    }

}
