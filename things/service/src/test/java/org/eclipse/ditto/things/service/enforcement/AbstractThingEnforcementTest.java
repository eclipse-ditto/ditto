/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import static org.eclipse.ditto.things.service.enforcement.TestSetup.THING_ID;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Abstract base class for all {@link org.eclipse.ditto.things.service.enforcement.ThingEnforcement} related unit tests.
 */
abstract class AbstractThingEnforcementTest {

    protected ActorSystem system;
    protected TestProbe pubSubMediatorProbe;
    protected TestProbe thingPersistenceActorProbe;
    protected TestProbe policiesShardRegionProbe;
    protected ActorRef supervisor;

    protected PolicyEnforcerProvider policyEnforcerProvider;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.parseMap(Map.of("akka.actor.provider",
                "akka.cluster.ClusterActorRefProvider")).withFallback(ConfigFactory.load(
                "test")));
        policyEnforcerProvider = Mockito.mock(PolicyEnforcerProvider.class);
        pubSubMediatorProbe = createPubSubMediatorProbe();
        thingPersistenceActorProbe = createThingPersistenceActorProbe();
        policiesShardRegionProbe = getTestProbe(createUniqueName("policiesShardRegionProbe-"));
        supervisor = createThingPersistenceSupervisor();

        new TestKit(system) {{
            // wait for supervisor actor to be ready
            supervisor.tell(new Identify(getClass().getName()), getRef());
            expectMsgClass(Duration.ofSeconds(15), ActorIdentity.class);
        }};
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    private TestProbe createPubSubMediatorProbe() {
        return getTestProbe(createUniqueName("pubSubMediatorProbe-"));
    }

    private TestProbe createThingPersistenceActorProbe() {
        return getTestProbe(createUniqueName("thingPersistenceActorProbe-"));
    }

    private TestProbe getTestProbe(final String uniqueName) {
        return new TestProbe(system, uniqueName);
    }

    private static String createUniqueName(final String prefix) {
        return prefix + UUID.randomUUID();
    }

    private TestActorRef<ThingSupervisorActor> createThingPersistenceSupervisor() {
        return new TestActorRef<>(system, ThingSupervisorActor.props(
                pubSubMediatorProbe.ref(),
                policiesShardRegionProbe.ref(),
                new DistributedPub<>() {

                    @Override
                    public ActorRef getPublisher() {
                        return pubSubMediatorProbe.ref();
                    }

                    @Override
                    public Object wrapForPublication(final ThingEvent<?> message, final CharSequence groupIndexKey) {
                        return message;
                    }

                    @Override
                    public <S extends ThingEvent<?>> Object wrapForPublicationWithAcks(final S message,
                            final CharSequence groupIndexKey, final AckExtractor<S> ackExtractor) {
                        return wrapForPublication(message, groupIndexKey);
                    }
                },
                new TestSetup.DummyLiveSignalPub(pubSubMediatorProbe.ref()),
                thingPersistenceActorProbe.ref(),
                null,
                policyEnforcerProvider,
                Mockito.mock(MongoReadJournal.class)
        ).withDispatcher("akka.actor.default-dispatcher"), system.guardian(),
                URLEncoder.encode(THING_ID.toString(), Charset.defaultCharset()));
        // Actors using "stash()" require the above dispatcher to be configured, otherwise stash() and unstashAll() won't
        // work like in the "normal" actor!
    }

    protected void expectAndAnswerSudoRetrieveThing(final Object sudoRetrieveThingResponse) {
        final SudoRetrieveThing sudoRetrieveThing =
                thingPersistenceActorProbe.expectMsgClass(FiniteDuration.apply(15, TimeUnit.SECONDS),
                        SudoRetrieveThing.class);
        assertThat((CharSequence) sudoRetrieveThing.getEntityId()).isEqualTo(THING_ID);
        thingPersistenceActorProbe.reply(sudoRetrieveThingResponse);
    }

    protected void expectAndAnswerSudoRetrieveThingWithSpecificTimeout(final Object sudoRetrieveThingResponse,
            final FiniteDuration timeout) {

        final SudoRetrieveThing sudoRetrieveThing =
                thingPersistenceActorProbe.expectMsgClass(timeout, SudoRetrieveThing.class);
        assertThat((CharSequence) sudoRetrieveThing.getEntityId()).isEqualTo(THING_ID);
        thingPersistenceActorProbe.reply(sudoRetrieveThingResponse);
    }


    protected void expectAndAnswerRetrievePolicy(final PolicyId policyId, final Object retrievePolicyResponse) {
        final var retrievePolicy = policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
        assertThat((CharSequence) retrievePolicy.getEntityId()).isEqualTo(policyId);
        policiesShardRegionProbe.reply(retrievePolicyResponse);
    }

    @SuppressWarnings("unchecked")
    protected <C extends ThingCommand<?>> C addReadSubjectHeader(final C command, final AuthorizationSubject subject) {
        return (C) command.setDittoHeaders(command.getDittoHeaders().toBuilder()
                .readGrantedSubjects(List.of(subject))
                .build()
        );
    }
}
