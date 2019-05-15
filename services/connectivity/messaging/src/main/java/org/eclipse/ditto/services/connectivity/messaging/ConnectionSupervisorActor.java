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
package org.eclipse.ditto.services.connectivity.messaging;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.naming.NamingException;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;

import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Supervisor for {@link ConnectionActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * The child has to send {@link ManualReset} after it started successfully.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link ConnectionUnavailableException} as fail fast strategy.
 * </p>
 */
public final class ConnectionSupervisorActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String connectionId;
    private final ExponentialBackOffConfig exponentialBackOffConfig;
    private final SupervisorStrategy supervisorStrategy;
    private final Props persistenceActorProps;

    @Nullable private ActorRef child;
    private long restartCount;

    private ConnectionSupervisorActor(final SupervisorStrategy supervisorStrategy,
            final ConnectionConfig connectionConfig,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final ClientActorPropsFactory propsFactory,
            @Nullable final ConnectivityCommandInterceptor commandValidator) {

        try {
            connectionId = URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding", e);
        }
        this.supervisorStrategy = supervisorStrategy;

        exponentialBackOffConfig = connectionConfig.getSupervisorConfig().getExponentialBackOffConfig();

        persistenceActorProps =
                ConnectionActor.props(connectionId, pubSubMediator, conciergeForwarder, propsFactory, commandValidator,
                        connectionConfig);
    }

    /**
     * Props for creating a {@code ConnectionSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that restarts the child on NullPointerExceptions,
     * stops it for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param connectionConfig the connection config.
     * @param pubSubMediator the PubSub mediator actor.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param propsFactory the {@link ClientActorPropsFactory}
     * @param commandValidator a custom command validator for connectivity commands
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ConnectionConfig connectionConfig,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final ClientActorPropsFactory propsFactory,
            @Nullable final ConnectivityCommandInterceptor commandValidator) {

        return Props.create(ConnectionSupervisorActor.class, new Creator<ConnectionSupervisorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ConnectionSupervisorActor create() {
                return new ConnectionSupervisorActor(new OneForOneStrategy(true, DeciderBuilder
                        .match(JMSRuntimeException.class, e -> SupervisorStrategy.resume())
                        .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                        .match(JMSException.class, e -> SupervisorStrategy.stop())
                        .match(NamingException.class, e -> SupervisorStrategy.stop())
                        .match(ActorKilledException.class, e -> SupervisorStrategy.stop())
                        .matchAny(e -> SupervisorStrategy.escalate())
                        .build()),
                        connectionConfig, pubSubMediator, conciergeForwarder, propsFactory, commandValidator);
            }
        });
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        startChild();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(StartChild.class, startChild -> startChild())
                .match(ManualReset.class, manualReset -> restartCount = 0)
                .match(Terminated.class, terminated -> {
                    LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
                    log.info("Persistence actor for Connection with ID '{}' terminated abnormally", connectionId);
                    child = null;
                    final Duration restartDelay = calculateRestartDelay();
                    getContext().system().scheduler()
                            .scheduleOnce(new FiniteDuration(restartDelay.toNanos(), TimeUnit.NANOSECONDS), getSelf(),
                                    StartChild.getInstance(),
                                    getContext().dispatcher(), null);
                    restartCount += 1;
                })
                .match(ShardRegion.Passivate.class, passivate -> getContext().getParent().tell(passivate, getSelf()))
                .matchAny(message -> {
                    LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
                    if (child != null) {
                        if (child.equals(getSender())) {
                            log.warning("Received unhandled message from child actor <{}>: {}", connectionId, message);
                            unhandled(message);
                        } else {
                            log.debug("Forwarding <{}> message to child <{}>.", message.getClass().getSimpleName(),
                                    child.path());
                            child.forward(message, getContext());
                        }
                    } else {
                        log.warning("Received message <{}> during downtime of child actor for Connection with ID <{}>.",
                                message.getClass().getSimpleName(), connectionId);
                        final ConnectionUnavailableException.Builder builder =
                                ConnectionUnavailableException.newBuilder(connectionId);
                        if (message instanceof WithDittoHeaders) {
                            builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
                        }
                        getSender().tell(builder.build(), getSelf());
                    }
                })
                .build();
    }

    private void startChild() {
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
        if (child == null) {
            log.debug("Starting persistence actor for Connection with ID <{}>.", connectionId);
            final ActorRef childRef = getContext().actorOf(persistenceActorProps, "pa");
            child = getContext().watch(childRef);
        }
    }

    /*
     * - minBackOff: minimum (initial) duration until the child actor will started again, if it is terminated.
     * - maxBackOff: the exponential back-off is capped to this duration.
     * - randomFactor: after calculation of the exponential back-off an additional random delay based on this factor
     *   is added, e. g. `0.2` adds up to `20 %` delay. In order to skip this additional delay pass in `0`.
     */
    private Duration calculateRestartDelay() {
        final double rnd = 1.0 + ThreadLocalRandom.current().nextDouble() * exponentialBackOffConfig.getRandomFactor();
        final Duration maxBackOff = exponentialBackOffConfig.getMax();
        if (restartCount >= 30) // Duration overflow protection (> 100 years)
        {
            return maxBackOff;
        }
        final Duration minBackOff = exponentialBackOffConfig.getMin();
        final double backOff = minBackOff.toNanos() * Math.pow(2, restartCount) * rnd;
        return Duration.ofNanos(Math.min(maxBackOff.toNanos(), (long) backOff));
    }

    /**
     * Message that should be sent to this actor to indicate a working child and reset the exponential backoff
     * mechanism.
     */
    static final class ManualReset {

        private ManualReset() {
            // no-op
        }

        static ManualReset getInstance() {
            return new ManualReset();
        }

    }

    /**
     * Message that is sent to the actor by itself to restart the child.
     */
    private static final class StartChild {

        private StartChild() {
            // no-op
        }

        static StartChild getInstance() {
            return new StartChild();
        }

    }

}
