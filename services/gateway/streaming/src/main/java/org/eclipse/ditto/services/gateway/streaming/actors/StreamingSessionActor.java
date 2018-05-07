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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
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

    /**
     * The max. timeout in milliseconds how long to wait until sending an "acknowledge" message back to the client.
     * If too small, we might miss some events which the client expects once the "ack" message is received as the
     * messages via distributed pub/sub are not yet received.
     */
    private static final int MAX_SUBSCRIBE_TIMEOUT_MS = 2500;

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final String connectionCorrelationId;
    private final String type;
    private final ActorRef pubSubMediator;
    private final ActorRef eventAndResponsePublisher;
    private final Set<StreamingType> outstandingSubscriptionAcks;

    private List<String> authorizationSubjects;

    private StreamingSessionActor(final String connectionCorrelationId, final String type,
            final ActorRef pubSubMediator, final ActorRef eventAndResponsePublisher) {
        this.connectionCorrelationId = connectionCorrelationId;
        this.type = type;
        this.pubSubMediator = pubSubMediator;
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        outstandingSubscriptionAcks = new HashSet<>();

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
                .match(CommandResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, response);
                    logger.debug(
                            "Got 'CommandResponse' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                            type, response);
                    eventAndResponsePublisher.forward(response, getContext());
                })
                .match(Signal.class, this::handleSignal)
                .match(DittoRuntimeException.class, cre -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, cre);
                    logger.info(
                            "Got 'DittoRuntimeException' message in <{}> session, telling eventAndResponsePublisher about it: {}",
                            type, cre);
                    eventAndResponsePublisher.forward(cre, getContext());
                })
                .match(StartStreaming.class, startStreaming -> {
                    authorizationSubjects = startStreaming.getAuthorizationContext().getAuthorizationSubjectIds();
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("Got 'StartStreaming' message in <{}> session, subscribing for <{}> in Cluster..",
                            type, startStreaming.getStreamingType().name());

                    outstandingSubscriptionAcks.add(startStreaming.getStreamingType());
                    // In Cluster: Subscribe
                    pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(
                            startStreaming.getStreamingType().getDistributedPubSubTopic(),
                            connectionCorrelationId,
                            getSelf()), getSelf());
                })
                .match(StopStreaming.class, stopStreaming -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    logger.debug("Got 'StopStreaming' message in <{}> session, unsubscribing from <{}> in Cluster..",
                            type, stopStreaming.getStreamingType().name());

                    // In Cluster: Unsubscribe
                    pubSubMediator.tell(new DistributedPubSubMediator.Unsubscribe(
                            stopStreaming.getStreamingType().getDistributedPubSubTopic(),
                            connectionCorrelationId, getSelf()), getSelf());
                })
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    final String topic = subscribeAck.subscribe().topic();
                    final StreamingType streamingType = StreamingType.fromTopic(topic);
                    final ActorRef self = getSelf();
                    /* send the StreamingAck with a little delay, as the akka doc states:
                     * The acknowledgment means that the subscription is registered, but it can still take some time
                     * until it is replicated to other nodes.
                     */
                    getContext().getSystem().scheduler()
                            .scheduleOnce(FiniteDuration.apply(MAX_SUBSCRIBE_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                                    () ->
                                            acknowledgeSubscription(streamingType, self),
                                    getContext().getSystem().dispatcher());
                })
                .match(DistributedPubSubMediator.UnsubscribeAck.class, unsubscribeAck -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, connectionCorrelationId);
                    final String topic = unsubscribeAck.unsubscribe().topic();
                    final StreamingType streamingType = StreamingType.fromTopic(topic);

                    final ActorRef self = getSelf();
                    /* send the StreamingAck with a little delay, as the akka doc states:
                     * The acknowledgment means that the subscription is registered, but it can still take some time
                     * until it is replicated to other nodes.
                     */
                    getContext().getSystem().scheduler()
                            .scheduleOnce(FiniteDuration.apply(MAX_SUBSCRIBE_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                                    () ->
                                            acknowledgeUnsubscription(streamingType, self),
                                    getContext().getSystem().dispatcher());
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

    private void handleSignal(final Signal<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(logger, signal);
        acknowledgeSubscriptionForSignal(signal);

        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        if (connectionCorrelationId.equals(dittoHeaders.getOrigin().orElse(null))) {
            logger.debug("Got Signal <{}> in <{}> session, " +
                    "but this was issued by this connection itself, not telling "
                    + "eventAndResponsePublisher about it", signal.getType(), type);
        } else {
            // check if this session is "allowed" to receive the LiveSignal
            if (authorizationSubjects != null &&
                    !Collections.disjoint(dittoHeaders.getReadSubjects(), authorizationSubjects)) {
                logger.debug("Got 'Live' Signal <{}> in <{}> session, " +
                                "telling eventAndResponsePublisher about it: {}",
                        signal.getType(), type, signal);

                eventAndResponsePublisher.tell(signal, getSelf());
            }
        }
    }

    private void acknowledgeSubscriptionForSignal(final Signal liveSignal) {
        if (liveSignal instanceof MessageCommand && outstandingSubscriptionAcks.contains(StreamingType.MESSAGES)) {
            acknowledgeSubscription(StreamingType.MESSAGES, getSelf());
        } else if (liveSignal instanceof Command && outstandingSubscriptionAcks.contains(StreamingType.LIVE_COMMANDS)) {
            acknowledgeSubscription(StreamingType.LIVE_COMMANDS, getSelf());
        } else if (liveSignal instanceof Event && outstandingSubscriptionAcks.contains(StreamingType.LIVE_EVENTS)) {
            acknowledgeSubscription(StreamingType.LIVE_EVENTS, getSelf());
        }
    }

    private void acknowledgeSubscription(final StreamingType streamingType, final ActorRef self) {
        outstandingSubscriptionAcks.remove(streamingType);
        eventAndResponsePublisher.tell(new StreamingAck(streamingType, true), self);
        logger.debug("Subscribed to Cluster <{}> in <{}> session", streamingType, type);
    }

    private void acknowledgeUnsubscription(final StreamingType streamingType, final ActorRef self) {
        eventAndResponsePublisher.tell(new StreamingAck(streamingType, false), self);
        logger.debug("Unsubscribed from Cluster <{}> in <{}> session", streamingType, type);
    }
}
