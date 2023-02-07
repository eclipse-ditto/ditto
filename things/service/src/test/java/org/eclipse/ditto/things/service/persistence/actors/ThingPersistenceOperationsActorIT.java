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
package org.eclipse.ditto.things.service.persistence.actors;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource.MongoEventSourceITAssertions;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.enforcement.TestSetup;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link ThingPersistenceOperationsActor} against a local MongoDB.
 */
@AllValuesAreNonnullByDefault
public final class ThingPersistenceOperationsActorIT extends MongoEventSourceITAssertions<ThingId> {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private PolicyEnforcerProvider policyEnforcerProvider;

    @Before
    public void setup() {
        policyEnforcerProvider = Mockito.mock(PolicyEnforcerProvider.class);
    }

    @Test
    public void purgeNamespace() {
        assertPurgeNamespace();
    }

    @Override
    protected String getServiceName() {
        // this loads the things.conf from module "ditto-internal-things-config" as ActorSystem conf
        return "things";
    }

    @Override
    protected String getResourceType() {
        return ThingCommand.RESOURCE_TYPE;
    }

    @Override
    protected ThingId toEntityId(final CharSequence entityId) {
        return ThingId.of(entityId);
    }

    @Override
    protected Object getCreateEntityCommand(final ThingId id) {
        return CreateThing.of(Thing.newBuilder()
                .setId(id)
                .setPolicyId(PolicyId.of(id))
                .build(), null, DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(),
                        "true") // required for a stable test - which does not try to load policies from the policiesShardRegion for enforcement
                .build());
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreateThingResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final ThingId id) {
        return RetrieveThing.of(id, DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(),
                        "true") // required for a stable test - which does not try to load policies from the policiesShardRegion for enforcement
                .build());
    }

    @Override
    protected Class<?> getRetrieveEntityResponseClass() {
        return RetrieveThingResponse.class;
    }

    @Override
    protected Class<?> getEntityNotAccessibleClass() {
        return ThingNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {
        final Props opsActorProps = ThingPersistenceOperationsActor.props(pubSubMediator, mongoDbConfig, config,
                persistenceOperationsConfig);

        return actorSystem.actorOf(opsActorProps, ThingPersistenceOperationsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final ThingId id) {

        final LiveSignalPub liveSignalPub = new TestSetup.DummyLiveSignalPub(pubSubMediator);

        final Props props = ThingSupervisorActor.props(pubSubMediator,
                new DistributedPub<>() {

                    @Override
                    public ActorRef getPublisher() {
                        return pubSubMediator;
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
                liveSignalPub,
                (thingId, mongoReadJournal, distributedPub, searchShardRegionProxy) -> ThingPersistenceActor.props(
                        thingId,
                        mongoReadJournal,
                        distributedPub,
                        null
                ),
                null,
                policyEnforcerProvider,
                Mockito.mock(MongoReadJournal.class));

        return system.actorOf(props, id.toString());
    }

}
