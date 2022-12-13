/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.PubSubFactory;
import org.eclipse.ditto.internal.utils.pubsub.api.LocalAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.SubscriptionsReader;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that distributes messages to local subscribers, triggers synchronization of declared acknowledgements,
 * and manages the subscriber pool.
 *
 * @param <T> type of messages.
 */
public final class Subscriber<T extends Signal<?>> extends AbstractSubscriber<T> {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "subscriber";

    private final List<ActorRef> subSubscribers;

    @SuppressWarnings("unused")
    private Subscriber(final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final AckExtractor<T> ackExtractor,
            final DistributedAcks distributedAcks) {
        super(messageClass, topicExtractor, ackExtractor, distributedAcks);
        subSubscribers = new ArrayList<>(distributedAcks.getConfig().getNumberOfShards());
        distributedAcks.receiveLocalDeclaredAcks(getSelf());
    }

    /**
     * Create Props object for this actor.
     *
     * @param <T> type of messages.
     * @param messageClass class of message distributed by the pub-sub.
     * @param topicExtractor extractor of topics from messages.
     * @param ackExtractor extractor of acknowledgement-related information from a message.
     * @param distributedAcks access to the distributed data of declared acknowledgement labels.
     * @return the Props object.
     */
    public static <T> Props props(final Class<T> messageClass, final PubSubTopicExtractor<T> topicExtractor,
            final AckExtractor<T> ackExtractor, final DistributedAcks distributedAcks) {
        return Props.create(Subscriber.class, messageClass, topicExtractor, ackExtractor, distributedAcks);
    }

    /**
     * Choose a subscriber from the subscriber pool based on the hash code of the entity ID of the published signal.
     *
     * @param parentSubscriber The parent subscriber from the distributed data.
     * @param signal The signal to be published.
     * @param poolSize The size of the subscriber pool.
     * @return An actor selection containing the subscriber in the pool responsible for the signal.
     */
    static ActorSelection chooseSubscriber(final ActorRef parentSubscriber, final PublishSignal signal,
            final int poolSize) {

        if (poolSize > 1) {
            final int index = PubSubFactory.hashForPubSub(signal.getGroupIndexKey()) % poolSize;
            if (index > 0) {
                return ActorSelection.apply(parentSubscriber, String.valueOf(index));
            }
        }
        return ActorSelection.apply(parentSubscriber, "");
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(PublishSignal.class, this::broadcastToLocalSubscribers)
                .match(SubscriptionsReader.class, this::updateLocalSubscriptions)
                .match(LocalAcksChanged.class, this::updateLocalAcks)
                .match(Terminated.class, this::terminated)
                .matchEquals(ActorEvent.ACK_UPDATER_NOT_AVAILABLE, this::scheduleReceiveLocalDeclaredAcks)
                .matchEquals(Control.RECEIVE_LOCAL_DECLARED_ACKS, this::receiveLocalDeclaredAcks)
                .build();
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(DeciderBuilder.matchAny(e -> {
            logger.error("Unknown error:'{}'! Escalating!", e);
            return (SupervisorStrategy.Directive) SupervisorStrategy.escalate();
        }).build());
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        final int subscriberPoolSize = distributedAcks.getConfig().getSubscriberPoolSize();
        if (subscriberPoolSize > 1) {
            final Props props = SubSubscriber.props(messageClass, topicExtractor, ackExtractor, distributedAcks);
            for (int i = 1; i < subscriberPoolSize; ++i) {
                subSubscribers.add(getContext().actorOf(props, String.valueOf(i)));
            }
        }
    }

    @Override
    void updateLocalSubscriptions(final SubscriptionsReader subscriptionsReader) {
        super.updateLocalSubscriptions(subscriptionsReader);
        for (final ActorRef subSubscriber : subSubscribers) {
            subSubscriber.forward(subscriptionsReader, getContext());
        }
    }

    @Override
    void updateLocalAcks(final LocalAcksChanged localAcksChanged) {
        super.updateLocalAcks(localAcksChanged);
        getContext().watch(ackUpdater);
        for (final ActorRef subSubscriber : subSubscribers) {
            subSubscriber.forward(localAcksChanged, getContext());
        }
    }

    private void scheduleReceiveLocalDeclaredAcks(final ActorEvent ackUpdaterNotAvailable) {
        if (!timers().isTimerActive(Control.RECEIVE_LOCAL_DECLARED_ACKS)) {
            timers().startSingleTimer(Control.RECEIVE_LOCAL_DECLARED_ACKS, Control.RECEIVE_LOCAL_DECLARED_ACKS,
                    getRestartDelayWithBuffer());
        }
    }

    private void receiveLocalDeclaredAcks(final Control receiveLocalDeclaredAcks) {
        distributedAcks.receiveLocalDeclaredAcks(getSelf());
    }

    private void terminated(final Terminated terminated) {
        if (Cluster.get(getContext().getSystem()).isTerminated()) {
            logger.info("This cluster instance was terminated - no action required ..");
        } else if (terminated.getActor().equals(ackUpdater)) {
            logger.error("Notifying SubUpdater <{}> of AckUpdater termination: <{}>", subUpdater, terminated);
            if (subUpdater != null) {
                subUpdater.tell(ActorEvent.PUBSUB_TERMINATED, getSelf());
            }
            scheduleReceiveLocalDeclaredAcks(ActorEvent.ACK_UPDATER_NOT_AVAILABLE);
        }
    }

    private Duration getRestartDelayWithBuffer() {
        final long bufferFactor = 4;
        final Duration configuredRestartDelay = PubSubConfig.of(getContext().getSystem()).getRestartDelay();
        return configuredRestartDelay.plus(configuredRestartDelay.dividedBy(bufferFactor));
    }

    private enum Control {
        RECEIVE_LOCAL_DECLARED_ACKS
    }

}
