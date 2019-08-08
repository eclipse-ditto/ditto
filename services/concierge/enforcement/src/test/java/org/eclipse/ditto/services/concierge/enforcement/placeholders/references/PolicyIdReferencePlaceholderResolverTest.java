/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.concierge.enforcement.placeholders.references;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.awaitility.Awaitility;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderReferenceUnknownFieldException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.testkit.TestProbe;

/**
 * Tests {@link PolicyIdReferencePlaceholderResolver}.
 */
public class PolicyIdReferencePlaceholderResolverTest {

    private static final ActorSystem actorSystem = ActorSystem.create("test", ConfigFactory.load("test"));
    private TestProbe conciergeForwarderActorProbe;
    private PolicyIdReferencePlaceholderResolver sut;
    private static final ThingId THING_ID = ThingId.of("namespace:myThing");

    @AfterClass
    public static void stopActorSystem() {
        if (null != actorSystem) {
            actorSystem.terminate();
        }
    }

    @Before
    public void setup() {
        conciergeForwarderActorProbe = TestProbe.apply(actorSystem);
        sut = PolicyIdReferencePlaceholderResolver.of(conciergeForwarderActorProbe.testActor(),
                Duration.ofSeconds(5));
    }

    @Test
    public void resolvePolicyIdFromThingReturnsPolicyId() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getThingEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of(THING_ID,
                Thing.newBuilder().setPolicyId("namespace:myPolicy").build(), DittoHeaders.empty()));

        Awaitility.await()
                .atMost(org.awaitility.Duration.ONE_SECOND)
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .isCompletedWithValue("namespace:myPolicy");
    }

    @Test
    public void resolvePolicyIdFromThingAttributeReturnsPolicyId() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/attributes/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getThingEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("/attributes/policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of(THING_ID,
                Thing.newBuilder().setAttribute(JsonPointer.of("policyId"), JsonValue.of("namespace:myPolicy")).build(),
                DittoHeaders.empty()));

        Awaitility.await()
                .atMost(org.awaitility.Duration.ONE_SECOND)
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .isCompletedWithValue("namespace:myPolicy");
    }


    @Test
    public void resolvePolicyIdFromThingAttributeFailsIfFieldIsNotFound() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/attributes/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getThingEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("/attributes/policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of(THING_ID,
                Thing.newBuilder().build(),
                DittoHeaders.empty()));

        Awaitility.await()
                .atMost(org.awaitility.Duration.ONE_SECOND)
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .hasFailedWithThrowableThat()
                .isInstanceOf(GatewayPlaceholderReferenceUnknownFieldException.class);
    }

    @Test
    public void resolvePolicyIdFromThingThrowsExceptionIfExpectedFieldDoesNotExist() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getThingEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply(
                RetrieveThingResponse.of(THING_ID, Thing.newBuilder().build(), DittoHeaders.empty()));

        Awaitility.await()
                .atMost(org.awaitility.Duration.ONE_SECOND)
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .hasFailedWithThrowableThat()
                .isInstanceOf(GatewayPlaceholderReferenceUnknownFieldException.class);
    }

    @Test
    public void resolvePolicyIdFromThingForwardsExceptionOfThingErrorResponse() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getThingEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        final DittoRuntimeException dre = ThingNotAccessibleException.newBuilder(THING_ID).build();
        conciergeForwarderActorProbe.reply(ThingErrorResponse.of(dre));

        Awaitility.await()
                .atMost(org.awaitility.Duration.ONE_SECOND)
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .hasFailedWithThrowableThat()
                .isEqualTo(dre);
    }

    @Test
    public void resolvePolicyIdFromThingThrowsExceptionIfResponseIsNotExpected() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getThingEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply("someThingUnexpected");

        Awaitility.await()
                .atMost(org.awaitility.Duration.ONE_SECOND)
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .hasFailedWithThrowableThat()
                .isInstanceOf(GatewayInternalErrorException.class);
    }

}
