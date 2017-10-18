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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor handling a single streaming connection / session.
 */
final class StreamingSessionActor extends AbstractActor {

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final String connectionCorrelationId;
    private final String type;
    private final ActorRef pubSubMediator;
    private final ActorRef eventAndResponsePublisher;

    private List<String> authorizationSubjects;

    private StreamingSessionActor(final String connectionCorrelationId, final String type,
            final ActorRef pubSubMediator, final ActorRef eventAndResponsePublisher) {
        this.connectionCorrelationId = connectionCorrelationId;
        this.type = type;
        this.pubSubMediator = pubSubMediator;
        this.eventAndResponsePublisher = eventAndResponsePublisher;

        getContext().watch(eventAndResponsePublisher);
    }

    /**
     * Creates Akka configuration object Props for this StreamingSessionActor.
     *
     * @param pubSubMediator the PubSub mediator actor
     * @param eventAndResponsePublisher the {@link EventAndResponsePublisher} actor.
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionCorrelationId, final String type,
            final ActorRef pubSubMediator, final ActorRef eventAndResponsePublisher) {
        return Props.create(StreamingSessionActor.class, new Creator<StreamingSessionActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public StreamingSessionActor create() throws Exception {
                return new StreamingSessionActor(connectionCorrelationId, type, pubSubMediator,
                        eventAndResponsePublisher);
            }
        });
    }

    @Override
    public void postStop() throws Exception {
        LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
        logger.info("Closing '{}' streaming session: {}", type, connectionCorrelationId);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                /* Live Signals */
                .match(Signal.class, StreamingSessionActor::isLiveSignal, liveSignal -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, liveSignal);

                    final DittoHeaders dittoHeaders = liveSignal.getDittoHeaders();
                    final Optional<String> correlationId = dittoHeaders.getCorrelationId();
                    if (correlationId.map(cId -> cId.startsWith(connectionCorrelationId)).orElse(false)) {
                        logger.debug(
                                "Got 'LiveSignal' message in <{}> session, but this was issued by this connection itself, not telling "
                                        + "eventAndResponsePublisher about it", type);
                    } else {
                        final Set<String> readSubjects = dittoHeaders.getReadSubjects();
                        // check if this session is "allowed" to receive the LiveSignal
                        if (readSubjects != null && authorizationSubjects != null &&
                                !Collections.disjoint(readSubjects, authorizationSubjects)) {
                            logger.debug(
                                    "Got 'LiveSignal' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                                    type, liveSignal);
                            eventAndResponsePublisher.tell(liveSignal, getSelf());
                        }
                    }
                })

                .match(Command.class, command -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, command);
                    logger.debug(
                            "Got 'Command' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                            type, command);
                    eventAndResponsePublisher.forward(command, getContext());
                })
                .match(CommandResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, response);
                    logger.debug(
                            "Got 'CommandResponse' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                            type, response);
                    eventAndResponsePublisher.forward(response, getContext());
                })
                .match(DittoRuntimeException.class, cre -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, cre);
                    logger.info(
                            "Got 'DittoRuntimeException' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                            type, cre);
                    eventAndResponsePublisher.forward(cre, getContext());
                })
                .match(Event.class, event -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, event);

                    final DittoHeaders dittoHeaders = event.getDittoHeaders();
                    final Optional<String> correlationId = dittoHeaders.getCorrelationId();
                    if (correlationId.map(cId -> cId.startsWith(connectionCorrelationId)).orElse(false)) {
                        logger.debug(
                                "Got 'Event' message in <{}> session, but this was issued by this connection itself, not telling "
                                        + "eventAndResponsePublisher about it", type);
                    } else {
                        final Set<String> readSubjects = dittoHeaders.getReadSubjects();
                        // check if this session is "allowed" to receive the event
                        if (readSubjects != null && authorizationSubjects != null &&
                                !Collections.disjoint(readSubjects, authorizationSubjects)) {
                            logger.debug(
                                    "Got 'Event' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                                    type, event);
                            eventAndResponsePublisher.tell(event, getSelf());
                        }
                    }
                })

                .match(StartStreaming.class, startStreaming -> {
                    authorizationSubjects = startStreaming.getAuthorizationContext().getAuthorizationSubjectIds();
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.info("Got 'StartStreaming' message for WS session, subscribing for <{}> in Cluster..",
                            startStreaming.getStreamingType().name());

                    // In Cluster: Subscribe
                    pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(
                            startStreaming.getStreamingType().getDistributedPubSubTopic(),
                            connectionCorrelationId,
                            getSelf()), getSelf());
                })
                .match(StopStreaming.class, stopStreaming -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.info("Got 'StopStreaming' message for WS session, unsubscribing from <{}> in Cluster..",
                            stopStreaming.getStreamingType().name());

                    // In Cluster: Unsubscribe
                    pubSubMediator.tell(new DistributedPubSubMediator.Unsubscribe(
                            stopStreaming.getStreamingType().getDistributedPubSubTopic(),
                            connectionCorrelationId, getSelf()), getSelf());
                })
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("Subscribed to Cluster <{}> in <{}> session: <{}>",
                            StreamingType.fromTopic(subscribeAck.subscribe().topic()), type, subscribeAck);
                })
                .match(DistributedPubSubMediator.UnsubscribeAck.class, unsubscribeAck -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("Unsubscribed from Cluster <{}> in <{}> session: <{}>", type, unsubscribeAck);
                })
                .match(Terminated.class, terminated -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("eventAndResponsePublisher was terminated");
                    // In Cluster: Unsubscribe from ThingEvents:
                    logger.info("<{}> connection was closed, unsubscribing from Streams in Cluster..", type);

                    Arrays.stream(StreamingType.values())
                            .map(StreamingType::getDistributedPubSubTopic)
                            .forEach(topic ->
                                    pubSubMediator.tell(new DistributedPubSubMediator.Unsubscribe(topic,
                                            connectionCorrelationId, getSelf()), getSelf()));

                    getContext().getSystem()
                            .scheduler()
                            .scheduleOnce(FiniteDuration.apply(1, TimeUnit.SECONDS), getSelf(),
                                    PoisonPill.getInstance(), getContext().dispatcher(), getSelf());
                })
                .matchAny(any -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.warning("Got unknown message in '{}' session: '{}'", type, any);
                })
                .build();
    }

    private static boolean isLiveSignal(final WithDittoHeaders<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }


}
