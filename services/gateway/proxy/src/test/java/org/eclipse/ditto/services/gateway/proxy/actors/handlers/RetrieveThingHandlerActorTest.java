/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.proxy.actors.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.After;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link RetrieveThingHandlerActor}.
 */
public final class RetrieveThingHandlerActorTest extends AbstractThingHandlerActorTestBase {

    private ActorRef underTest;

    @After
    public void cleanupUnderTest() {
        if (actorSystem != null && underTest != null) {
            actorSystem.stop(underTest);
        }
    }

    @Test
    public void aggregationNotNeededForV1() {
        final DittoHeaders v1Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_1).build();
        final RetrieveThing retrieveThing = RetrieveThing.of("any:id", v1Headers);

        assertThat(RetrieveThingHandlerActor.checkIfAggregationIsNeeded(retrieveThing))
                .as("Should not need aggregation for v1 JsonSchemaVersion.")
                .isFalse();
    }

    @Test
    public void aggregationNotNeededWithoutPolicy() {
        final DittoHeaders v2Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build();
        final RetrieveThing retrieveThing = RetrieveThing.of("any:id", v2Headers);

        assertThat(RetrieveThingHandlerActor.checkIfAggregationIsNeeded(retrieveThing))
                .as("Should not need aggregation if _policy is missing in field request.")
                .isFalse();
    }

    @Test
    public void aggregationNeeded() {
        final DittoHeaders v2Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build();
        final RetrieveThing retrieveThing = RetrieveThing.getBuilder("any:id", v2Headers)
                .withSelectedFields(JsonFieldSelector.newInstance("_policy"))
                .build();

        assertThat(RetrieveThingHandlerActor.checkIfAggregationIsNeeded(retrieveThing))
                .as("Should need aggregation if _policy is part of request.")
                .isTrue();
    }

    @Test
    public void retrieveErrorWhenEnforcerIsMissing() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives RetrieveThing
            final String thingId = "thing:" + UUID.randomUUID();
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // forwards RetrieveThing to the given enforcer shard region
            final ThingNotAccessibleException thingNotAccessibleException = ThingNotAccessibleException.newBuilder
                    (thingId)
                    .dittoHeaders(DEFAULT_HEADERS)
                    .build();

            // initial requester receives the exception
            enforcerShard.expectNoMsg();
            expectMsgEquals(thingNotAccessibleException);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void retrieveThingWithPolicyAndThingId() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives RetrieveThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policyId:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final Policy policy = Policy.newBuilder(policyId).build();
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // forwards RetrieveThing to the given enforcer shard region
            final RetrievePolicy forwardedRetrievePolicy = RetrievePolicy.of(DEFAULT_ENFORCER_ID, DEFAULT_HEADERS);
            final ShardedMessageEnvelope forwardedRetrieveThing = ShardedMessageEnvelope.of(DEFAULT_ENFORCER_ID,
                    retrieveThing.getType(),
                    retrieveThing.toJson(retrieveThing.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                    retrieveThing.getDittoHeaders());
            enforcerShard.expectMsg(forwardedRetrievePolicy);
            enforcerShard.expectMsg(forwardedRetrieveThing);
            enforcerShard.reply(RetrieveThingResponse.of(thingId, thing, DEFAULT_HEADERS));
            enforcerShard.reply(RetrievePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            // initial requester receives the retrieve thing response with the policy
            final RetrieveThingResponse expectedResult = RetrieveThingResponse.of(thingId, JsonObject.newBuilder()
                            .setAll(thing.toJson(DEFAULT_HEADERS.getImplementedSchemaVersion(), selectedFields))
                            .setAll(policy.toInlinedJson(DEFAULT_HEADERS.getImplementedSchemaVersion(),
                                    FieldType.notHidden()))
                            .build(),
                    DEFAULT_HEADERS);

            expectMsgEquals(expectedResult);
            expectUnderTestTerminated(this);

        }};
    }

    @Test
    public void retrieveThingWithoutPolicyIfPermissionIsMissing() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives RetrieveThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policyId:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // forwards RetrieveThing to the given enforcer shard region
            final RetrievePolicy forwardedRetrievePolicy = RetrievePolicy.of(DEFAULT_ENFORCER_ID, DEFAULT_HEADERS);
            final ShardedMessageEnvelope forwardedRetrieveThing = ShardedMessageEnvelope.of(DEFAULT_ENFORCER_ID,
                    retrieveThing.getType(),
                    retrieveThing.toJson(retrieveThing.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                    retrieveThing.getDittoHeaders());
            final PolicyNotAccessibleException policyNotAccessibleException = PolicyNotAccessibleException
                    .newBuilder(policyId)
                    .dittoHeaders(DEFAULT_HEADERS)
                    .build();
            final RetrieveThingResponse retrieveThingResponse = RetrieveThingResponse.of(thingId, thing,
                    DEFAULT_HEADERS);
            enforcerShard.expectMsg(forwardedRetrievePolicy);
            enforcerShard.expectMsg(forwardedRetrieveThing);
            enforcerShard.reply(policyNotAccessibleException);
            enforcerShard.reply(retrieveThingResponse);

            // initial requester receives the retrieve thing response without the policy
            final RetrieveThingResponse expectedResult = RetrieveThingResponse.of(thingId, JsonObject.newBuilder()
                            .setAll(thing.toJson(DEFAULT_HEADERS.getImplementedSchemaVersion(), selectedFields))
                            .build(),
                    DEFAULT_HEADERS);

            expectMsgEquals(expectedResult);
        }};
    }

    @Test
    public void retrieveErrorWhenThingIsNotAccessible() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives RetrieveThing
            final String thingId = "thing:" + UUID.randomUUID();
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // forwards RetrieveThing to the given enforcer shard region
            final RetrievePolicy forwardedRetrievePolicy = RetrievePolicy.of(DEFAULT_ENFORCER_ID, DEFAULT_HEADERS);
            final ShardedMessageEnvelope forwardedRetrieveThing = ShardedMessageEnvelope.of(DEFAULT_ENFORCER_ID,
                    retrieveThing.getType(),
                    retrieveThing.toJson(retrieveThing.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                    retrieveThing.getDittoHeaders());
            final ThingNotAccessibleException thingNotAccessibleException = ThingNotAccessibleException
                    .newBuilder(thingId)
                    .build();
            enforcerShard.expectMsg(forwardedRetrievePolicy);
            enforcerShard.expectMsg(forwardedRetrieveThing);
            enforcerShard.reply(thingNotAccessibleException);

            // initial requester receives the exception
            expectMsgEquals(thingNotAccessibleException);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void retrieveErrorOnTimeout() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives RetrieveThing
            final String thingId = "thing:" + UUID.randomUUID();
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // forwards RetrieveThing to the given enforcer shard region
            final RetrievePolicy forwardedRetrievePolicy = RetrievePolicy.of(DEFAULT_ENFORCER_ID, DEFAULT_HEADERS);
            final ShardedMessageEnvelope forwardedRetrieveThing = ShardedMessageEnvelope.of(DEFAULT_ENFORCER_ID,
                    retrieveThing.getType(),
                    retrieveThing.toJson(retrieveThing.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                    retrieveThing.getDittoHeaders());
            final AskTimeoutException timeoutException = new AskTimeoutException("a timeout");
            enforcerShard.expectMsg(forwardedRetrievePolicy);
            enforcerShard.expectMsg(forwardedRetrieveThing);
            enforcerShard.reply(timeoutException);

            expectUnderTestTerminated(this);
            // initial requester receives nothing else
            expectNoMsg();
        }};
    }

    @Test
    public void retrieveErrorOnDittoRuntimeException() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives RetrieveThing
            final String thingId = "thing:" + UUID.randomUUID();
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // forwards RetrieveThing to the given enforcer shard region
            final DittoRuntimeException dittoException = DittoRuntimeException
                    .newBuilder("matrix.glitch", HttpStatusCode.SERVICE_UNAVAILABLE)
                    .build();
            final RetrievePolicy forwardedRetrievePolicy = RetrievePolicy.of(DEFAULT_ENFORCER_ID, DEFAULT_HEADERS);
            final ShardedMessageEnvelope forwardedRetrieveThing = ShardedMessageEnvelope.of(DEFAULT_ENFORCER_ID,
                    retrieveThing.getType(),
                    retrieveThing.toJson(retrieveThing.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                    retrieveThing.getDittoHeaders());
            enforcerShard.expectMsg(forwardedRetrievePolicy);
            enforcerShard.expectMsg(forwardedRetrieveThing);
            enforcerShard.reply(dittoException);

            // initial requester receives the retrieve thing response with the policy
            expectMsgEquals(dittoException);
            expectUnderTestTerminated(this);
        }};
    }

    private void startHandlerWithEnforcer(final TestKit testkit) {
        underTest = actorSystem.actorOf(
                RetrieveThingHandlerActor.props(enforcerShard.ref(), DEFAULT_ENFORCER_ID, aclEnforcerShard.ref(),
                        policyEnforcerShard.ref()));
        testkit.watch(underTest);
    }

    private void startHandlerWithoutEnforcer(final TestKit testkit) {
        underTest = actorSystem.actorOf(
                RetrieveThingHandlerActor.props(null, null, aclEnforcerShard.ref(), policyEnforcerShard.ref()));
        testkit.watch(underTest);
    }

    private void expectUnderTestTerminated(final TestKit testkit) {
        testkit.expectTerminated(underTest);
        underTest = null;
    }

}