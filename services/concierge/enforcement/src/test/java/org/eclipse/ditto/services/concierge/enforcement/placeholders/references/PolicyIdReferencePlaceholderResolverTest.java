/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.ditto.services.concierge.enforcement.placeholders.references;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderReferenceUnknownFieldException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.testkit.TestProbe;

/**
 * Tests {@link PolicyIdReferencePlaceholderResolver}.
 */
public class PolicyIdReferencePlaceholderResolverTest {

    private static final ActorSystem actorSystem = ActorSystem.create();
    private TestProbe conciergeForwarderActorProbe;
    private PolicyIdReferencePlaceholderResolver sut;

    @Before
    public void setup() {
        conciergeForwarderActorProbe = TestProbe.apply(actorSystem);
        sut = PolicyIdReferencePlaceholderResolver.of(conciergeForwarderActorProbe.testActor(),
                Duration.ofSeconds(5));
    }

    @Test
    public void resolvePolicyIdFromThingSendsRetrieveThing() {
        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/namespace:myThing/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat(retrieveThing.getThingId()).isEqualTo("namespace:myThing");
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));
    }

    @Test
    public void resolvePolicyIdFromThingReturnsPolicyId()
            throws InterruptedException, ExecutionException, TimeoutException {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/namespace:myThing/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat(retrieveThing.getThingId()).isEqualTo("namespace:myThing");
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of("namespace:myThing",
                Thing.newBuilder().setPolicyId("namespace:myPolicy").build(), DittoHeaders.empty()));

        assertThat(policyIdCS.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("namespace:myPolicy");
    }

    @Test
    public void resolvePolicyIdFromThingThrowsExceptionIfExpectedFieldDoesNotExist() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/namespace:myThing/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat(retrieveThing.getThingId()).isEqualTo("namespace:myThing");
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of("namespace:myThing",
                Thing.newBuilder().build(), DittoHeaders.empty()));

        policyIdCS.toCompletableFuture().whenComplete((response, error) -> assertThat(error).isInstanceOf(
                GatewayPlaceholderReferenceUnknownFieldException.class));
    }

    @Test
    public void resolvePolicyIdFromThingForwardsExceptionOfThingErrorResponse() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/namespace:myThing/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat(retrieveThing.getThingId()).isEqualTo("namespace:myThing");
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        final DittoRuntimeException dre = ThingNotAccessibleException.newBuilder("namespace:myThing").build();
        conciergeForwarderActorProbe.reply(ThingErrorResponse.of(dre));
        policyIdCS.toCompletableFuture().whenComplete((response, error) -> assertThat(error).isSameAs(dre));
    }

    @Test
    public void resolvePolicyIdFromThingThrowsExceptionIfResponseIsNotExpected() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/namespace:myThing/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat(retrieveThing.getThingId()).isEqualTo("namespace:myThing");
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply("someThingUnexpected");
        policyIdCS.toCompletableFuture()
                .whenComplete((response, error) -> assertThat(error).isInstanceOf(GatewayInternalErrorException.class));
    }

}