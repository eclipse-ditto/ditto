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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.gateway.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.FI;
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
    private final Set<String> outstandingLiveSignals;
    private final Map<String, ActorRef> responseAwaitingLiveSignals;
    private final Set<StreamingType> outstandingSubscriptionAcks;

    private List<String> authorizationSubjects;

    private StreamingSessionActor(final String connectionCorrelationId, final String type,
            final ActorRef pubSubMediator, final ActorRef eventAndResponsePublisher) {
        this.connectionCorrelationId = connectionCorrelationId;
        this.type = type;
        this.pubSubMediator = pubSubMediator;
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        outstandingLiveSignals = new HashSet<>();
        responseAwaitingLiveSignals = new HashMap<>();
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

                /* Live Signals */
                // publish all those live signals which were issued by this session into the cluster
                .match(Signal.class, signal -> isLiveSignal(signal) && wasIssuedByThisSession(signal) &&
                                (signal instanceof MessageCommand || signal instanceof MessageCommandResponse),
                        publishLiveSignal(StreamingType.MESSAGES.getDistributedPubSubTopic())
                )
                .match(Signal.class, signal -> isLiveSignal(signal) && wasIssuedByThisSession(signal) &&
                                (signal instanceof Command || signal instanceof CommandResponse),
                        publishLiveSignal(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic())
                )
                .match(Signal.class, signal -> isLiveSignal(signal) && wasIssuedByThisSession(signal) &&
                                signal instanceof Event,
                        publishLiveSignal(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic())
                )

                // for live signals which were not issued by this session, handle them by forwarding towards WS:
                .match(Signal.class, this::isLiveSignal, liveSignal -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, liveSignal);

                    acknowledgeSubscriptionForLiveSignal(liveSignal);

                    final DittoHeaders dittoHeaders = liveSignal.getDittoHeaders();
                    final Optional<String> correlationId = dittoHeaders.getCorrelationId();
                    if (correlationId.map(cId -> cId.startsWith(connectionCorrelationId)).orElse(false)) {
                        logger.debug("Got 'Live' Signal <{}> in <{}> session, " +
                                "but this was issued by this connection itself, not telling "
                                + "eventAndResponsePublisher about it", liveSignal.getType(), type);
                    } else {
                        // check if this session is "allowed" to receive the LiveSignal
                        if (authorizationSubjects != null &&
                                !Collections.disjoint(dittoHeaders.getReadSubjects(), authorizationSubjects)) {
                            logger.debug("Got 'Live' Signal <{}> in <{}> session, " +
                                            "telling eventAndResponsePublisher about it: {}",
                                    liveSignal.getType(), type, liveSignal);

                            if (liveSignal instanceof Command) {
                                extractActualCorrelationId(liveSignal)
                                        .ifPresent(cId -> responseAwaitingLiveSignals.put(cId, getSender()));
                            }

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

                    if (outstandingSubscriptionAcks.contains(StreamingType.EVENTS)) {
                        acknowledgeSubscription(StreamingType.EVENTS, getSelf());
                    }

                    final DittoHeaders dittoHeaders = event.getDittoHeaders();
                    final Optional<String> correlationId = dittoHeaders.getCorrelationId();
                    if (correlationId.map(cId -> cId.startsWith(connectionCorrelationId)).orElse(false)) {
                        logger.debug(
                                "Got 'Event' message in <{}> session, but this was issued by this connection itself, not telling "
                                        + "eventAndResponsePublisher about it", type);
                    } else {
                        final Set<String> readSubjects = dittoHeaders.getReadSubjects();
                        // check if this session is "allowed" to receive the event
                        if (authorizationSubjects != null &&
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
                            .scheduleOnce(FiniteDuration.apply(MAX_SUBSCRIBE_TIMEOUT_MS, TimeUnit.MILLISECONDS), () ->
                                    acknowledgeSubscription(streamingType, self), getContext().getSystem().dispatcher());
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
                            .scheduleOnce(FiniteDuration.apply(MAX_SUBSCRIBE_TIMEOUT_MS, TimeUnit.MILLISECONDS), () ->
                                    acknowledgeUnsubscription(streamingType, self), getContext().getSystem().dispatcher());
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

    private void acknowledgeSubscriptionForLiveSignal(final Signal liveSignal) {
        if (liveSignal instanceof MessageCommand) {
            if (outstandingSubscriptionAcks.contains(StreamingType.MESSAGES)) {
                acknowledgeSubscription(StreamingType.MESSAGES, getSelf());
            }
        } else if (liveSignal instanceof Command) {
            if (outstandingSubscriptionAcks.contains(StreamingType.LIVE_COMMANDS)) {
                acknowledgeSubscription(StreamingType.LIVE_COMMANDS, getSelf());
            }
        } else if (liveSignal instanceof Event) {
            if (outstandingSubscriptionAcks.contains(StreamingType.LIVE_EVENTS)) {
                acknowledgeSubscription(StreamingType.LIVE_EVENTS, getSelf());
            }
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

    private FI.UnitApply<Signal> publishLiveSignal(final String topic) {
        return liveSignal -> {
            acknowledgeSubscriptionForLiveSignal(liveSignal);

            LogUtil.enhanceLogWithCorrelationId(logger, liveSignal);
            if (notYetPublished(liveSignal)) {
                logger.debug("Publishing 'Live' Signal <{}> on topic <{}> into cluster.", liveSignal.getType(), topic);
                pubSubMediator.forward(new DistributedPubSubMediator.Publish(
                        topic,
                        liveSignal,
                        true
                ), getContext());
                savePublished(liveSignal);
            }

            if (liveSignal instanceof CommandResponse && extractActualCorrelationId(liveSignal)
                    .filter(responseAwaitingLiveSignals::containsKey).isPresent()) {
                // we got a live signal waiting for a response
                extractActualCorrelationId(liveSignal).map(responseAwaitingLiveSignals::remove)
                        .ifPresent(sender -> {
                            logger.debug("Answering to a 'Live' Command with CommandResponse <{}> to sender: <{}>",
                                    liveSignal.getType(), sender);
                            sender.forward(liveSignal, getContext());
                        });
            }
        };
    }

    private boolean notYetPublished(final Signal<?> liveSignal) {
        return !extractActualCorrelationId(liveSignal)
                .filter(cId -> outstandingLiveSignals.contains(liveSignal.getType() + ":" + cId))
                .isPresent();
    }

    private boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    private boolean wasIssuedByThisSession(final Signal<?> signal) {
        return extractConnectionCorrelationId(signal).filter(connectionCorrelationId::equals).isPresent();
    }

    private void savePublished(final Signal<?> signal) {
        extractActualCorrelationId(signal).ifPresent(cId -> outstandingLiveSignals.add(signal.getType() + ":" + cId));
    }

    private static Optional<String> extractConnectionCorrelationId(final WithDittoHeaders withDittoHeaders) {
        return withDittoHeaders.getDittoHeaders().getCorrelationId()
                .map(cId -> cId.split(":", 2))
                .map(cIds -> cIds[0]);
    }

    private static Optional<String> extractActualCorrelationId(final WithDittoHeaders withDittoHeaders) {
        return withDittoHeaders.getDittoHeaders().getCorrelationId()
                .map(cId -> cId.split(":", 2))
                .filter(cIds -> cIds.length >= 1)
                .map(cIds -> cIds.length == 2 ? cIds[1] : cIds[0]);
    }


}
