/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.LocalAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.ddata.SubscriptionsReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.GroupedSnapshot;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;

/**
 * Super class of actors that distributes messages to local subscribers
 *
 * @param <T> type of messages.
 */
abstract class AbstractSubscriber<T extends Signal<?>> extends AbstractActorWithTimers {

    final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    final Class<T> messageClass;
    final PubSubTopicExtractor<T> topicExtractor;
    final AckExtractor<T> ackExtractor;
    final DistributedAcks distributedAcks;
    @Nullable ActorRef ackUpdater = null;
    @Nullable ActorRef subUpdater = null;

    private final Counter truePositiveCounter = DittoMetrics.counter("pubsub-true-positive");
    private final Counter falsePositiveCounter = DittoMetrics.counter("pubsub-false-positive");
    private final Counter receivedMessagesCounter = DittoMetrics.counter("pubsub-received-messages");
    private PublisherIndex<String> publisherIndex = PublisherIndex.empty();
    private GroupedSnapshot<ActorRef, String> declaredAcks = GroupedSnapshot.empty();

    AbstractSubscriber(final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final AckExtractor<T> ackExtractor,
            final DistributedAcks distributedAcks) {
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;
        this.ackExtractor = ackExtractor;
        this.distributedAcks = distributedAcks;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(PublishSignal.class, this::broadcastToLocalSubscribers)
                .match(SubscriptionsReader.class, this::updateLocalSubscriptions)
                .match(LocalAcksChanged.class, this::updateLocalAcks)
                .build();
    }

    void broadcastToLocalSubscribers(final PublishSignal command) {
        receivedMessagesCounter.increment();
        final T message = messageClass.cast(command.getSignal());
        final Collection<String> topics = topicExtractor.getTopics(message);
        final Set<ActorRef> localSubscribers =
                publisherIndex.assignGroupsToSubscribers(message, topics, command.getGroups(),
                                command.getGroupIndexKey())
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
        replyWeakAck(message, command, localSubscribers);
    }

    private void replyWeakAck(final T message, final PublishSignal command, final Set<ActorRef> localSubscribers) {
        final Set<String> responsibleAcks = declaredAcks.getValues(command.getGroups().keySet());
        final Collection<AcknowledgementLabel> declaredCustomAcks =
                ackExtractor.getDeclaredCustomAcksRequestedBy(message, responsibleAcks::contains);
        final Collection<AcknowledgementLabel> declaredCustomAcksWithoutSubscribers = declaredCustomAcks.stream()
                .filter(label -> disjoint(localSubscribers, declaredAcks.getKeys(label.toString())))
                .toList();
        if (!declaredCustomAcksWithoutSubscribers.isEmpty()) {
            final Acknowledgements acknowledgements =
                    ackExtractor.toWeakAcknowledgements(message, declaredCustomAcksWithoutSubscribers);

            final String ackregatorAddress = acknowledgements.getDittoHeaders()
                    .get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey());
            if (null != ackregatorAddress) {
                final ActorSelection acknowledgementRequester = getContext().actorSelection(ackregatorAddress);
                acknowledgementRequester.tell(acknowledgements, ActorRef.noSender());
            } else {
                logger.withCorrelationId(acknowledgements)
                        .error("Issuing weak Acknowledgements to acknowledgement aggregator failed because " +
                                        "ackgregator address was missing from headers: {}",
                                acknowledgements.getDittoHeaders());
            }
        }
    }

    void updateLocalSubscriptions(final SubscriptionsReader subscriptionsReader) {
        this.publisherIndex = PublisherIndex.fromSubscriptionsReader(subscriptionsReader);

        // no need to watch the subUpdater -- the supervisor takes care of restarting on termination.
        subUpdater = getSender();
    }

    void updateLocalAcks(final LocalAcksChanged localAcksChanged) {
        declaredAcks = localAcksChanged.getSnapshot();
        ackUpdater = getSender();
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
}
