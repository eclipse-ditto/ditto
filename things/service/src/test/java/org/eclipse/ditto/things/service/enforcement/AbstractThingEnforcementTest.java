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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.enforcement.DefaultPreEnforcerProvider;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
import org.junit.After;
import org.junit.Before;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract base class for all {@link org.eclipse.ditto.things.service.enforcement.ThingEnforcement} related unit tests.
 */
abstract class AbstractThingEnforcementTest {

    protected ActorSystem system;
    protected TestProbe pubSubMediatorProbe;
    protected TestProbe thingPersistenceActorProbe;
    protected TestProbe policiesShardRegionProbe;
    protected ActorRef supervisor;
    protected ThingSupervisorActor mockThingPersistenceSupervisor;

    @Before
    public void init() {
        system = ActorSystem.create("test",
                ConfigFactory.parseMap(Map.of("ditto.pre-enforcer-provider",
                        DefaultPreEnforcerProvider.class.getCanonicalName(), "akka.actor.provider",
                        "akka.cluster.ClusterActorRefProvider")).withFallback(ConfigFactory.load(
                        "test")));
        pubSubMediatorProbe = createPubSubMediatorProbe();
        thingPersistenceActorProbe = createThingPersistenceActorProbe();
        policiesShardRegionProbe = getTestProbe(createUniqueName("policiesShardRegionProbe-"));
        final TestActorRef<ThingSupervisorActor> thingPersistenceSupervisorTestActorRef =
                createThingPersistenceSupervisor();
        supervisor = thingPersistenceSupervisorTestActorRef;
        mockThingPersistenceSupervisor = thingPersistenceSupervisorTestActorRef.underlyingActor();
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
                new TestSetup.DummyLiveSignalPub(pubSubMediatorProbe.ref()),
                thingPersistenceActorProbe.ref(),
                null
        ), system.guardian(), URLEncoder.encode(THING_ID.toString(), Charset.defaultCharset()));
    }

    protected void expectAndAnswerSudoRetrieveThing(final Object sudoRetrieveThingResponse) {
        final SudoRetrieveThing sudoRetrieveThing =
                thingPersistenceActorProbe.expectMsgClass(SudoRetrieveThing.class);
        assertThat((CharSequence) sudoRetrieveThing.getEntityId()).isEqualTo(THING_ID);
        thingPersistenceActorProbe.reply(sudoRetrieveThingResponse);
    }

    protected void expectAndAnswerSudoRetrievePolicy(final PolicyId policyId, final Object sudoRetrievePolicyResponse) {
        final SudoRetrievePolicy sudoRetrievePolicy =
                policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(policyId);
        policiesShardRegionProbe.reply(sudoRetrievePolicyResponse);
    }

    @SuppressWarnings("unchecked")
    protected <C extends ThingCommand<?>> C addReadSubjectHeader(final C command, final AuthorizationSubject subject) {
        return (C) command.setDittoHeaders(command.getDittoHeaders().toBuilder()
                .readGrantedSubjects(List.of(subject))
                .build()
        );
    }
}
