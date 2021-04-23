/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultStreamConfig;
import org.eclipse.ditto.thingsearch.service.common.config.StreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit tests for {@link EnforcementFlow}.
 */
public final class EnforcementFlowTest {

    private static ActorSystem system;

    private TestPublisher.Probe<Map<ThingId, Metadata>> sourceProbe;
    private TestSubscriber.Probe<AbstractWriteModel> sinkProbe;

    @BeforeClass
    public static void init() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void cleanup() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void updateThingAndPolicyRevisions() {
        new TestKit(system) {{
            // GIVEN: enqueued metadata is out of date
            final long thingRev1 = 1L;
            final long thingRev2 = 99L;
            final long policyRev1 = 2L;
            final long policyRev2 = 98L;
            final ThingId thingId = ThingId.of("thing:id");
            final PolicyId policyId = PolicyId.of("policy:id");
            final Metadata metadata = Metadata.of(thingId, thingRev1, policyId, policyRev1, null);
            final Map<ThingId, Metadata> inputMap = Map.of(thingId, metadata);

            final TestProbe thingsProbe = TestProbe.apply(system);
            final TestProbe policiesProbe = TestProbe.apply(system);

            final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
            final EnforcementFlow underTest =
                    EnforcementFlow.of(streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                            system.dispatchers().defaultGlobalDispatcher());

            materializeTestProbes(underTest.create(false, 1));

            sinkProbe.ensureSubscription();
            sourceProbe.ensureSubscription();
            sinkProbe.request(1);
            assertThat(sourceProbe.expectRequest()).isEqualTo(1);
            sourceProbe.sendNext(inputMap);
            sourceProbe.sendComplete();

            // WHEN: thing and policy are retrieved with up-to-date revisions
            thingsProbe.expectMsgClass(SudoRetrieveThing.class);
            final var thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).setRevision(thingRev2).build();
            final var thingJson = thing.toJson(FieldType.regularOrSpecial());
            assertThat(thingJson.getValue(Thing.JsonFields.REVISION)).contains(thingRev2);
            thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

            policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
            final var policy = Policy.newBuilder(policyId).setRevision(policyRev2).build();
            policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty()));

            // THEN: the write model contains up-to-date revisions
            final AbstractWriteModel writeModel = sinkProbe.expectNext();
            sinkProbe.expectComplete();
            assertThat(writeModel).isInstanceOf(ThingWriteModel.class);
            final var document = JsonObject.of(((ThingWriteModel) writeModel).getThingDocument().toJson());
            assertThat(document.getValue("_id")).contains(JsonValue.of(thingId));
            assertThat(document.getValue("policyId")).contains(JsonValue.of(policyId));
            assertThat(document.getValue("_revision")).contains(JsonValue.of(thingRev2));
            assertThat(document.getValue("__policyRev")).contains(JsonValue.of(policyRev2));
        }};
    }

    @Test
    public void ignoreCacheWhenRequestedToUpdate() {
        // GIVEN: the enqueued metadata is out-of-date
        final long thingRev1 = 1L;
        final long thingRev2 = 99L;
        final long policyRev1 = 2L;
        final long policyRev2 = 98L;
        final ThingId thingId = ThingId.of("thing:id");
        final PolicyId policyId = PolicyId.of("policy:id");
        final Metadata metadata1 = Metadata.of(thingId, thingRev1, policyId, policyRev1, null);

        final TestProbe thingsProbe = TestProbe.apply(system);
        final TestProbe policiesProbe = TestProbe.apply(system);

        final StreamConfig streamConfig = DefaultStreamConfig.of(ConfigFactory.empty());
        final EnforcementFlow underTest =
                EnforcementFlow.of(streamConfig, thingsProbe.ref(), policiesProbe.ref(),
                        system.dispatchers().defaultGlobalDispatcher());

        materializeTestProbes(underTest.create(false, 1));

        sinkProbe.ensureSubscription();
        sourceProbe.ensureSubscription();
        sinkProbe.request(2);
        sourceProbe.sendNext(Map.of(thingId, metadata1));

        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        final var thing = Thing.newBuilder().setId(thingId).setPolicyId(policyId).setRevision(thingRev2).build();
        final var thingJson = thing.toJson(FieldType.regularOrSpecial());
        thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

        // GIVEN: enforcer cache is loaded with out-of-date policy
        policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        final var policy1 = Policy.newBuilder(policyId).setRevision(policyRev1).build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy1, DittoHeaders.empty()));

        final AbstractWriteModel writeModel1 = sinkProbe.expectNext();
        assertThat(writeModel1).isInstanceOf(ThingWriteModel.class);
        final var document1 = JsonObject.of(((ThingWriteModel) writeModel1).getThingDocument().toJson());
        assertThat(document1.getValue("_revision")).contains(JsonValue.of(thingRev2));
        assertThat(document1.getValue("__policyRev")).contains(JsonValue.of(policyRev1));

        // WHEN: a metadata with 'invalidateCache' flag is enqueued
        final Metadata metadata2 = metadata1.invalidateCache();
        sourceProbe.sendNext(Map.of(thingId, metadata2));
        sourceProbe.sendComplete();
        thingsProbe.expectMsgClass(SudoRetrieveThing.class);
        thingsProbe.reply(SudoRetrieveThingResponse.of(thingJson, DittoHeaders.empty()));

        // THEN: policy cache is reloaded
        policiesProbe.expectMsgClass(SudoRetrievePolicy.class);
        final var policy2 = Policy.newBuilder(policyId).setRevision(policyRev2).build();
        policiesProbe.reply(SudoRetrievePolicyResponse.of(policyId, policy2, DittoHeaders.empty()));

        // THEN: write model contains up-to-date policy revisions.
        final AbstractWriteModel writeModel2 = sinkProbe.expectNext();
        sinkProbe.expectComplete();
        assertThat(writeModel2).isInstanceOf(ThingWriteModel.class);
        final var document2 = JsonObject.of(((ThingWriteModel) writeModel2).getThingDocument().toJson());
        assertThat(document2.getValue("_revision")).contains(JsonValue.of(thingRev2));
        assertThat(document2.getValue("__policyRev")).contains(JsonValue.of(policyRev2));
    }

    private void materializeTestProbes(
            final Flow<Map<ThingId, Metadata>, Source<AbstractWriteModel, NotUsed>, NotUsed> enforcementFlow) {
        final var source = TestSource.<Map<ThingId, Metadata>>probe(system);
        final var sink = TestSink.<AbstractWriteModel>probe(system);
        final var runnableGraph =
                source.viaMat(enforcementFlow, Keep.left()).flatMapConcat(x -> x).toMat(sink, Keep.both());
        final var materializedValue = runnableGraph.run(() -> system);
        sourceProbe = materializedValue.first();
        sinkProbe = materializedValue.second();
    }

}
