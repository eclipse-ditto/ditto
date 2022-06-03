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
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.LocalAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.SubscriptionsReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.GroupedSnapshot;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that distributes messages to local subscribers
 *
 * @param <T> type of messages.
 */
public final class Subscriber<T extends SignalWithEntityId<?>> extends AbstractActorWithTimers {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "subscriber";

    private final Class<T> messageClass;
    private final PubSubTopicExtractor<T> topicExtractor;
    private final AckExtractor<T> ackExtractor;
    private final DistributedAcks distributedAcks;

    private final Counter truePositiveCounter = DittoMetrics.counter("pubsub-true-positive");
    private final Counter falsePositiveCounter = DittoMetrics.counter("pubsub-false-positive");
    private final Counter receivedMessagesCounter = DittoMetrics.counter("pubsub-received-messages");
    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private PublisherIndex<String> publisherIndex = PublisherIndex.empty();
    private GroupedSnapshot<ActorRef, String> declaredAcks = GroupedSnapshot.empty();
    @Nullable private ActorRef ackUpdater = null;
    @Nullable private ActorRef subUpdater = null;

    @SuppressWarnings("unused")
    private Subscriber(final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final AckExtractor<T> ackExtractor,
            final DistributedAcks distributedAcks) {
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;
        this.ackExtractor = ackExtractor;
        this.distributedAcks = distributedAcks;
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

    private void broadcastToLocalSubscribers(final PublishSignal command) {
        receivedMessagesCounter.increment();
        final T message = messageClass.cast(command.getSignal());
        final Collection<String> topics = topicExtractor.getTopics(message);
        final Set<ActorRef> localSubscribers =
                publisherIndex.assignGroupsToSubscribers(message, topics, command.getGroups())
                        .stream()
                        .map(Pair::first)
                        .collect(Collectors.toSet());
        if (localSubscribers.isEmpty()) {
            falsePositiveCounter.increment();
        } else {
            truePositiveCounter.increment();
            for (final ActorRef localSubscriber : localSubscribers) {
                localSubscriber.tell(message, getSender());
            }
        }
        replyWeakAck(message, command, localSubscribers, getSender());
    }

    private void replyWeakAck(final T message, final PublishSignal command, final Set<ActorRef> localSubscribers,
            final ActorRef sender) {
        final Set<String> responsibleAcks = declaredAcks.getValues(command.getGroups().keySet());
        final Collection<AcknowledgementLabel> declaredCustomAcks =
                ackExtractor.getDeclaredCustomAcksRequestedBy(message, responsibleAcks::contains);
        final Collection<AcknowledgementLabel> declaredCustomAcksWithoutSubscribers = declaredCustomAcks.stream()
                .filter(label -> disjoint(localSubscribers, declaredAcks.getKeys(label.toString())))
                .toList();
        if (!declaredCustomAcksWithoutSubscribers.isEmpty()) {
            final Acknowledgements acknowledgements =
                    ackExtractor.toWeakAcknowledgements(message, declaredCustomAcksWithoutSubscribers);
            sender.tell(acknowledgements, ActorRef.noSender());
        }
    }

    private void updateLocalSubscriptions(final SubscriptionsReader subscriptionsReader) {
        this.publisherIndex = PublisherIndex.fromSubscriptionsReader(subscriptionsReader);

        // no need to watch the subUpdater -- the supervisor takes care of restarting on termination.
        subUpdater = getSender();
    }

    private void updateLocalAcks(final LocalAcksChanged localAcksChanged) {
        declaredAcks = localAcksChanged.getSnapshot();
        ackUpdater = getSender();
        getContext().watch(ackUpdater);
    }

    private Duration getRestartDelayWithBuffer() {
        final long bufferFactor = 4;
        final Duration configuredRestartDelay = PubSubConfig.of(getContext().getSystem()).getRestartDelay();
        return configuredRestartDelay.plus(configuredRestartDelay.dividedBy(bufferFactor));
    }

    private static <T> boolean disjoint(final Set<T> set1, final Set<T> set2) {
        final Set<T> smaller;
        final Set<T> bigger;
        if (set1.size() < set2.size()) {
            smaller = set1;
            bigger = set2;
        } else {
            smaller = set2;
            bigger = set1;
        }
        return smaller.stream().noneMatch(bigger::contains);
    }

    private enum Control {
        RECEIVE_LOCAL_DECLARED_ACKS
    }

}
