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
package org.eclipse.ditto.services.things.persistence.actors;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEventSourceITAssertions;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link ThingPersistenceOperationsActor} against a local MongoDB.
 */
@AllValuesAreNonnullByDefault
public final class ThingPersistenceOperationsActorIT extends MongoEventSourceITAssertions {

    @Test
    public void purgeNamespaceWithoutSuffix() {
        assertPurgeNamespaceWithoutSuffix();
    }

    @Test
    public void purgeNamespaceWithSuffix() {
        assertPurgeNamespaceWithSuffix();
    }

    @Override
    protected String getServiceName() {
        return "things";
    }

    @Override
    protected Config getExtraConfig() {
        return ConfigFactory.load("thing-test");
    }

    @Override
    protected String getResourceType() {
        return ThingCommand.RESOURCE_TYPE;
    }

    @Override
    protected Object getCreateEntityCommand(final String id) {
        return CreateThing.of(Thing.newBuilder().setId(id).setPolicyId(id).build(), null, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreateThingResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final String id) {
        return RetrieveThing.of(id, DittoHeaders.empty());
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

        final Props opsActorProps = ThingPersistenceOperationsActor.props(pubSubMediator);
        return actorSystem.actorOf(opsActorProps, ThingPersistenceOperationsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final String id) {
        final Props props = ThingSupervisorActor.props(pubSubMediator,
                theId -> ThingPersistenceActor.props(theId, pubSubMediator));

        return system.actorOf(props, id);
    }

}
