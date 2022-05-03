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

package org.eclipse.ditto.policies.enforcement.placeholders.references;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.exceptions.PlaceholderReferenceUnknownFieldException;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.testkit.TestProbe;

/**
 * Tests {@link org.eclipse.ditto.policies.enforcement.placeholders.references.PolicyIdReferencePlaceholderResolver}.
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
                DefaultAskWithRetryConfig.of(ConfigFactory.empty(), "test"), actorSystem.scheduler(),
                actorSystem.dispatcher());
    }

    @Test
    public void resolvePolicyIdFromThingReturnsPolicyId() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of(THING_ID,
                Thing.newBuilder().setPolicyId(PolicyId.of("namespace:myPolicy")).build(), null, null,
                DittoHeaders.empty()));

        Awaitility.await()
                .atMost(Duration.ofSeconds(1))
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
        assertThat((CharSequence) retrieveThing.getEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("/attributes/policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of(THING_ID,
                Thing.newBuilder().setAttribute(JsonPointer.of("policyId"), JsonValue.of("namespace:myPolicy")).build(),
                null, null, DittoHeaders.empty()));

        Awaitility.await()
                .atMost(Duration.ofSeconds(1))
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
        assertThat((CharSequence) retrieveThing.getEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("/attributes/policyId"));

        conciergeForwarderActorProbe.reply(RetrieveThingResponse.of(THING_ID,
                Thing.newBuilder().build(),
                null, null, DittoHeaders.empty()));

        Awaitility.await()
                .atMost(Duration.ofSeconds(1))
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .hasFailedWithThrowableThat()
                .isInstanceOf(PlaceholderReferenceUnknownFieldException.class);
    }

    @Test
    public void resolvePolicyIdFromThingThrowsExceptionIfExpectedFieldDoesNotExist() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply(
                RetrieveThingResponse.of(THING_ID, Thing.newBuilder().build(), null, null, DittoHeaders.empty()));

        Awaitility.await()
                .atMost(Duration.ofSeconds(1))
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .hasFailedWithThrowableThat()
                .isInstanceOf(PlaceholderReferenceUnknownFieldException.class);
    }

    @Test
    public void resolvePolicyIdFromThingForwardsExceptionOfThingErrorResponse() {

        final ReferencePlaceholder referencePlaceholder =
                ReferencePlaceholder.fromCharSequence("{{ ref:things/" + THING_ID + "/policyId }}")
                        .orElseThrow(IllegalStateException::new);

        final CompletionStage<String> policyIdCS = sut.resolve(referencePlaceholder, DittoHeaders.empty());

        final RetrieveThing retrieveThing = conciergeForwarderActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat((CharSequence) retrieveThing.getEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        final DittoRuntimeException dre = ThingNotAccessibleException.newBuilder(THING_ID).build();
        conciergeForwarderActorProbe.reply(ThingErrorResponse.of(dre));

        Awaitility.await()
                .atMost(Duration.ofSeconds(1))
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
        assertThat((CharSequence) retrieveThing.getEntityId()).isEqualTo(THING_ID);
        assertThat(retrieveThing.getSelectedFields()).contains(JsonFieldSelector.newInstance("policyId"));

        conciergeForwarderActorProbe.reply("someThingUnexpected");

        Awaitility.await()
                .atMost(Duration.ofSeconds(1))
                .until(() -> policyIdCS.toCompletableFuture().isDone());
        assertThat(policyIdCS.toCompletableFuture())
                .hasFailedWithThrowableThat()
                .isInstanceOf(DittoInternalErrorException.class);
    }

}
