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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.base.actors.ShutdownBehaviour;
import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.services.things.common.config.DittoThingsConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Supervisor for {@link ThingPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link ThingUnavailableException} as fail fast strategy.
 * </p>
 */
public final class ThingSupervisorActor extends AbstractPersistenceSupervisor<ThingId> {

    private final ActorRef pubSubMediator;
    private final DistributedPub<ThingEvent> distributedPub;
    private final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory;

    @SuppressWarnings("unused")
    private ThingSupervisorActor(final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent> distributedPub,
            final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory) {

        this.pubSubMediator = pubSubMediator;
        this.distributedPub = distributedPub;
        this.thingPersistenceActorPropsFactory = thingPersistenceActorPropsFactory;
    }

    /**
     * Props for creating a {@code ThingSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that stops the child
     * for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param distributedPub distributed-pub access for publishing thing events.
     * @param propsFactory factory for creating Props to be used for creating
     * {@link ThingPersistenceActor}s.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(
            final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent> distributedPub,
            final ThingPersistenceActorPropsFactory propsFactory) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, distributedPub, propsFactory);
    }

    @Override
    protected ThingId getEntityId() throws Exception {
        return ThingId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name()));
    }

    @Override
    @Nonnull
    protected Props getPersistenceActorProps(@Nonnull final ThingId entityId) {
        return thingPersistenceActorPropsFactory.props(entityId, distributedPub);
    }

    @Override
    @Nonnull
    protected ShutdownBehaviour getShutdownBehaviour(@Nonnull final ThingId entityId) {
        return ShutdownBehaviour.fromId(entityId, pubSubMediator, getSelf());
    }

    @Override
    @Nonnull
    protected DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable final ThingId entityId) {
        if (entityId != null) {
            return ThingUnavailableException.newBuilder(entityId);
        } else {
            return ThingUnavailableException.newBuilder(ThingId.of(DefaultNamespacedEntityId.dummy()));
        }
    }

    @Override
    @Nonnull
    protected ExponentialBackOffConfig getExponentialBackOffConfig() {
        return DittoThingsConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                .getThingConfig()
                .getSupervisorConfig()
                .getExponentialBackOffConfig();
    }

}
