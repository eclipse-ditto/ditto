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
package org.eclipse.ditto.services.things.starter;

import java.time.Duration;

import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.services.things.persistence.actors.ThingsActorsCreator;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.stream.ActorMaterializer;

/**
 * Creator of actors in Things service.
 */
public class DittoThingsActorsCreator implements ThingsActorsCreator {

    private final Config config;
    private final ActorSystem system;

    public DittoThingsActorsCreator(final Config config, final ActorSystem actorSystem) {
        this.config = config;
        this.system = actorSystem;
    }

    @Override
    public Props createRootActor() {
        final ActorRef pubSubMediator = DistributedPubSub.get(system).mediator();
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        return ThingsRootActor.props(config, pubSubMediator, materializer, this);
    }

    @Override
    public Props createSupervisorActor(final ActorRef pubSubMediator, final ActorRef thingCacheFacade) {
        final Duration minBackoff = config.getDuration(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_MIN);
        final Duration maxBackoff = config.getDuration(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_MAX);
        final double randomFactor = config.getDouble(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR);
        return ThingSupervisorActor.props(pubSubMediator, minBackoff, maxBackoff, randomFactor, thingCacheFacade, this);
    }

    @Override
    public Props createPersistentActor(final String thingId, final ActorRef pubSubMediator,
            final ActorRef thingCacheFacade) {
        return ThingPersistenceActor.props(thingId, pubSubMediator, thingCacheFacade);
    }
}
