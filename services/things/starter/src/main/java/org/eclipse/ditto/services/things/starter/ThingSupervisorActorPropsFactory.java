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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.function.Function;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.things.persistence.actors.ThingSupervisorActor;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Factory for creating Props of {@link ThingSupervisorActor}.
 */
@NotThreadSafe
final class ThingSupervisorActorPropsFactory implements Function<ActorRef, Props> {

    private final Config config;
    private final ActorRef pubSubMediator;
    private final ThingSnapshotter.Create thingSnapshotterCreate;

    private ThingSupervisorActorPropsFactory(final Config config, final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        this.config = checkNotNull(config, "config");
        this.pubSubMediator = checkNotNull(pubSubMediator, "distributed pub-sub mediator actor");
        this.thingSnapshotterCreate = checkNotNull(thingSnapshotterCreate, "creation function for ThingSnapshotter");
    }

    /**
     * Returns an instance of {@code ThingSupervisorActorPropsFactory}.
     *
     * @param config the configuration settings of the Things service.
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @param thingSnapshotterCreate functional interface for the constructor of snapshotter classes.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ThingSupervisorActorPropsFactory getInstance(final Config config, final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        return new ThingSupervisorActorPropsFactory(config, pubSubMediator, thingSnapshotterCreate);
    }

    /**
     * Returns the Props of the {@link ThingSupervisorActor}.
     *
     * @param thingCacheFacade ActorRef of the facade for accessing the thing cache in cluster.
     * @return the Props.
     * @throws NullPointerException if {@code thingCacheFacade} is {@code null}.
     */
    @Override
    public Props apply(final ActorRef thingCacheFacade) {
        final Duration minBackOff = config.getDuration(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_MIN);
        final Duration maxBackOff = config.getDuration(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_MAX);
        final double randomFactor = config.getDouble(ConfigKeys.Thing.SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR);

        return ThingSupervisorActor.props(minBackOff, maxBackOff, randomFactor,
                getThingPersistenceActorPropsFactory(thingCacheFacade));
    }

    private Function<String, Props> getThingPersistenceActorPropsFactory(final ActorRef thingCacheFacade) {
        return ThingPersistenceActorPropsFactory.getInstance(pubSubMediator, thingCacheFacade, thingSnapshotterCreate);
    }

}
