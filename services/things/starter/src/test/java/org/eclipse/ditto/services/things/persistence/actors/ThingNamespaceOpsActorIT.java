/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.things.persistence.actors;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.persistence.mongo.namespace.EventSourceNamespaceOpsActorTestCases;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link org.eclipse.ditto.services.things.persistence.actors.ThingNamespaceOpsActor} against a local MongoDB.
 */
@AllValuesAreNonnullByDefault
public final class ThingNamespaceOpsActorIT extends EventSourceNamespaceOpsActorTestCases {

    @Override
    protected String serviceName() {
        return "things";
    }

    @Override
    protected String resourceType() {
        return ThingCommand.RESOURCE_TYPE;
    }

    @Override
    protected List<String> supportedPrefixes() {
        return Collections.singletonList(ThingCommand.RESOURCE_TYPE);
    }

    @Override
    protected Object createEntity(final String id) {
        final DittoHeaders headers = DittoHeaders.empty();
        return CreateThing.of(Thing.newBuilder().setId(id).setPolicyId(id).build(), null, headers);
    }

    @Override
    protected Class<?> createEntityResponseClass() {
        return CreateThingResponse.class;
    }

    @Override
    protected Object retrieveEntity(final String id) {
        return RetrieveThing.of(id, DittoHeaders.empty());
    }

    @Override
    protected Class<?> retrieveEntityResponseClass() {
        return RetrieveThingResponse.class;
    }

    @Override
    protected Class<?> entityNotAccessibleClass() {
        return ThingNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {

        final Props nanmespaceOpsActorProps = ThingNamespaceOpsActor.props(pubSubMediator, config);
        return actorSystem.actorOf(nanmespaceOpsActorProps, ThingNamespaceOpsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final String id) {
        // essentially never restart
        final Duration minBackOff = Duration.ofSeconds(36000);
        final Duration maxBackOff = Duration.ofSeconds(36000);
        final double randomFactor = 0.2;

        final Props props = ThingSupervisorActor.props(pubSubMediator, minBackOff, maxBackOff, randomFactor,
                theId -> ThingPersistenceActor.props(theId, pubSubMediator));

        return system.actorOf(props, id);
    }
}
