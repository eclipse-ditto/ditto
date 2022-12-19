/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Metadata}.
 */
public final class MetadataTest extends AbstractWithActorSystemTest {

    @Test
    public void testHashCodeAndEquals() {
        system = ActorSystem.create();
        final TestProbe probe1 = TestProbe.apply(system);
        final TestProbe probe2 = TestProbe.apply(system);
        EqualsVerifier.forClass(Metadata.class)
                .usingGetClass()
                .withPrefabValues(ActorSelection.class, system.actorSelection(probe1.ref().path()),
                        system.actorSelection(probe2.ref().path()))
                .verify();
    }

    @Test
    public void appendsThingPolicyTagToAllReferencedPolicies() {
        final PolicyTag policyTag = PolicyTag.of(PolicyId.generateRandom(), 4711L);
        final PolicyTag referencedPolicyTag = PolicyTag.of(PolicyId.generateRandom(), 42L);

        final Set<PolicyTag> expectedReferencedPolicyTags = Set.of(policyTag, referencedPolicyTag);

        assertThat(Metadata.of(ThingId.generateRandom(), 1337L, policyTag, Set.of(referencedPolicyTag), null)
                .getAllReferencedPolicyTags())
                .containsExactlyInAnyOrderElementsOf(expectedReferencedPolicyTags);

        assertThat(Metadata.of(ThingId.generateRandom(), 1337L, policyTag, Set.of(referencedPolicyTag), List.of(), null,
                null).getAllReferencedPolicyTags())
                .containsExactlyInAnyOrderElementsOf(expectedReferencedPolicyTags);

        assertThat(Metadata.of(ThingId.generateRandom(), 1337L, policyTag, Set.of(referencedPolicyTag), null, null)
                .getAllReferencedPolicyTags())
                .containsExactlyInAnyOrderElementsOf(expectedReferencedPolicyTags);

        assertThat(Metadata.of(ThingId.generateRandom(), 1337L, policyTag, Set.of(referencedPolicyTag), null, List.of(),
                        List.of(), List.of(), List.of())
                .getAllReferencedPolicyTags())
                .containsExactlyInAnyOrderElementsOf(expectedReferencedPolicyTags);
    }

}
