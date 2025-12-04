/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.annotation.Nullable;

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Terminated;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformers;
import org.eclipse.ditto.internal.utils.cluster.StopShardedActor;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.WotValidationConfigCommand;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;

/**
 * Supervisor for {@link WotValidationConfigSupervisorActor} which means it will create, start and watch it as child actor.
 */
public final class WotValidationConfigSupervisorActor extends AbstractActorWithTimers {

    private final ActorRef pubSubMediator;
    private final MongoReadJournal mongoReadJournal;
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    @Nullable
    private ActorRef persistenceActorChild;
    private final SignalTransformer signalTransformer;

    @SuppressWarnings("unused")
    private WotValidationConfigSupervisorActor(final ActorRef pubSubMediator,
            final MongoReadJournal mongoReadJournal) {
        this.pubSubMediator = pubSubMediator;
        this.mongoReadJournal = mongoReadJournal;

        final var system = getContext().getSystem();
        // This is the only way that works for your transformer setup!
        this.signalTransformer = SignalTransformers.get(system, ScopedConfig.dittoExtension(system.settings().config()));
    }

    public static Props props(final ActorRef pubSubMediator,
            final MongoReadJournal mongoReadJournal) {
        return Props.create(WotValidationConfigSupervisorActor.class, pubSubMediator, mongoReadJournal);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(WotValidationConfigCommand.class, this::handleWotValidationConfigCommand)
                .match(StopShardedActor.class, this::stopShardedActor)
                .matchEquals(Control.SHUTDOWN_TIMEOUT, this::shutdownActor)
                .match(Terminated.class, this::handleTerminated)
                .matchAny(this::unhandled)
                .build();
    }

    private void handleWotValidationConfigCommand(final WotValidationConfigCommand<?> command) {
        if (null != persistenceActorChild) {
            signalTransformer.apply(command, persistenceActorChild).thenAccept(transformed ->
                    persistenceActorChild.tell(transformed, getSender())
            );
        } else {
            final WotValidationConfigNotAccessibleException exception = WotValidationConfigNotAccessibleException
                    .newBuilder(command.getEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
            getSender().tell(ThingErrorResponse.of(exception), getSelf());
        }
    }

    @Override
    public void preStart() {
        try {
            final WotValidationConfigId entityId = getEntityId();
            persistenceActorChild = getContext().actorOf(getPersistenceActorProps(entityId), "persistence");
            getContext().watch(persistenceActorChild);
        } catch (final Exception e) {
            log.error(e, "Failed to start persistence actor");
            getContext().stop(getSelf());
        }
    }

    @Override
    public void postStop() {
        if (persistenceActorChild != null) {
            getContext().unwatch(persistenceActorChild);
            getContext().stop(persistenceActorChild);
        }
    }

    private WotValidationConfigId getEntityId() {
        return WotValidationConfigId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8));
    }

    private Props getPersistenceActorProps(final WotValidationConfigId entityId) {
        return WotValidationConfigPersistenceActor.props(entityId, mongoReadJournal, pubSubMediator);
    }

    private ExponentialBackOffConfig getExponentialBackOffConfig() {
        final var system = getContext().getSystem();
        final var scopedConfig = DefaultScopedConfig.dittoScoped(system.settings().config());
        final var thingsConfig = DittoThingsConfig.of(scopedConfig);
        return thingsConfig.getThingConfig().getSupervisorConfig().getExponentialBackOffConfig();
    }

    private void stopShardedActor(final StopShardedActor trigger) {
        getTimers().startSingleTimer(Control.SHUTDOWN_TIMEOUT, Control.SHUTDOWN_TIMEOUT, Duration.ZERO);
    }

    private void shutdownActor(final Control shutdown) {
        log.warning("Stopping myself");
        getContext().stop(getSelf());
    }

    private void handleTerminated(final Terminated terminated) {
        if (terminated.getActor().equals(persistenceActorChild)) {
            log.warning("Persistence actor terminated, will restart after backoff");
            persistenceActorChild = null;
            // Schedule restart after backoff
            final ExponentialBackOffConfig backOffConfig = getExponentialBackOffConfig();
            final Duration backOff = Duration.ofMillis(backOffConfig.getMin().toMillis());
            getTimers().startSingleTimer("restart-persistence", Control.RESTART_PERSISTENCE, backOff);
        }
    }

    private enum Control {
        SHUTDOWN_TIMEOUT,
        RESTART_PERSISTENCE
    }
}