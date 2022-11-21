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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.RemoteAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.Grouped;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Publishes messages according to topic distributed data.
 */
public final class Publisher extends AbstractActor {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "publisher";

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final DDataReader<ActorRef, String> ddataReader;

    private final Counter messageCounter = DittoMetrics.counter("pubsub-published-messages");
    private final Counter topicCounter = DittoMetrics.counter("pubsub-published-topics");
    private final Counter sentMessagesCounter = DittoMetrics.counter("pubsub-sent-messages");
    private final Map<Key<?>, PublisherIndex<Long>> publisherIndexes = new HashMap<>();
    private final int subscriberPoolSize;

    private PublisherIndex<Long> publisherIndex = PublisherIndex.empty();
    private RemoteAcksChanged remoteAcks = RemoteAcksChanged.of(Map.of());

    @SuppressWarnings("unused")
    private Publisher(final DDataReader<ActorRef, String> ddataReader, final DistributedAcks distributedAcks) {
        this.ddataReader = ddataReader;
        subscriberPoolSize = distributedAcks.getConfig().getSubscriberPoolSize();
        ddataReader.receiveChanges(getSelf());
        distributedAcks.receiveDistributedDeclaredAcks(getSelf());
    }

    /**
     * Create Props for this actor.
     *
     * @param <T> representation of topics in the distributed data.
     * @param ddataReader reader of remote subscriptions.
     * @param distributedAcks access to the declared ack labels ddata.
     * @return a Props object.
     */
    public static <T> Props props(final DDataReader<ActorRef, T> ddataReader, final DistributedAcks distributedAcks) {

        return Props.create(Publisher.class, ddataReader, distributedAcks);
    }

    /**
     * Create a publish message for the publisher.
     *
     * @param topics the topics to publish at.
     * @param message the message to publish.
     * @param groupIndexKey the group index key.
     * @return a publish message.
     */
    public static Request publish(final Collection<String> topics, final Signal<?> message,
            final CharSequence groupIndexKey) {

        return new Publish(topics, message, groupIndexKey);
    }

    /**
     * Create a publish message with requested acknowledgements.
     *
     * @param topics the topics to publish at.
     * @param message the message to publish.
     * @param ackRequests acknowledgement requests of the message.
     * @param entityId entity ID of the message.
     * @param dittoHeaders the Ditto headers of any weak acknowledgements to send back.
     * @return the request.
     */
    public static Request publishWithAck(final Collection<String> topics,
            final Signal<?> message,
            final Set<AcknowledgementRequest> ackRequests,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {

        return new PublishWithAck(topics, message, ackRequests, entityId, dittoHeaders);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Publish.class, this::publish)
                .match(PublishWithAck.class, this::publishWithAck)
                .match(RemoteAcksChanged.class, this::declaredAcksChanged)
                .match(Replicator.Changed.class, this::topicSubscribersChanged)
                .matchAny(this::logUnhandled)
                .build();
    }

    private void publish(final Publish publish) {
        doPublish(publish.topics(), publish.message(), publish.groupIndexKey());
    }

    private void publishWithAck(final PublishWithAck publishWithAck) {
        final List<Pair<ActorRef, PublishSignal>> subscribers =
                doPublish(publishWithAck.topics, publishWithAck.message, publishWithAck.entityId);

        final Set<String> subscriberDeclaredAcks = subscribers.stream()
                .flatMap(pair -> {
                    final ActorRef subscriber = pair.first();
                    final Set<String> groups = pair.second().getGroups().keySet();
                    return remoteAcks.streamDeclaredAcksForGroup(subscriber.path().address(), groups);
                })
                .collect(Collectors.toSet());

        final Collection<AcknowledgementLabel> requestedCustomAcks =
                AckExtractor.getRequestedAndDeclaredCustomAcks(publishWithAck.ackRequests, remoteAcks::contains);

        final List<AcknowledgementLabel> labelsWithoutAuthorizedSubscribers = requestedCustomAcks.stream()
                .filter(label -> !subscriberDeclaredAcks.contains(label.toString()))
                .toList();

        if (!labelsWithoutAuthorizedSubscribers.isEmpty()) {
            final Acknowledgements weakAcks = publishWithAck.toWeakAcks(labelsWithoutAuthorizedSubscribers);
            final String ackregatorAddress = publishWithAck.getDittoHeaders()
                    .get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey());
            if (null != ackregatorAddress) {
                final ActorSelection acknowledgementRequester = getContext().actorSelection(ackregatorAddress);
                acknowledgementRequester.tell(weakAcks, ActorRef.noSender());
            } else {
                log.withCorrelationId(publishWithAck)
                        .error("Issuing weak Acknowledgements to acknowledgement aggregator failed because " +
                                "ackgregator address was missing from headers: {}", publishWithAck.getDittoHeaders());
            }
        }
    }

    private List<Pair<ActorRef, PublishSignal>> doPublish(final Collection<String> topics,
            final Signal<?> signal,
            final CharSequence groupIndexKey) {
        messageCounter.increment();
        topicCounter.increment(topics.size());
        final List<Long> hashes = topics.stream().map(ddataReader::approximate).toList();
        final ActorRef sender = getSender();

        final List<Pair<ActorRef, PublishSignal>> subscribers =
                publisherIndex.assignGroupsToSubscribers(signal, hashes, groupIndexKey);
        final ThreadSafeDittoLoggingAdapter l = log.withCorrelationId(signal);
        if (l.isDebugEnabled()) {
            l.debug("Calculated hashes for signal <{}>: <{}>", signal, hashes);
            l.debug("Publishing PublishSignal to subscribers: <{}>",
                    subscribers.stream().map(Pair::first).toList());
        }
        sentMessagesCounter.increment(subscribers.size());
        subscribers.forEach(pair -> publishSignal(pair.first(), pair.second(), sender));
        return subscribers;
    }

    private void publishSignal(final ActorRef subscriber, final PublishSignal signal, final ActorRef sender) {
        Subscriber.chooseSubscriber(subscriber, signal, subscriberPoolSize).tell(signal, sender);
    }

    private void declaredAcksChanged(final RemoteAcksChanged event) {
        remoteAcks = event;
    }

    private void topicSubscribersChanged(final Replicator.Changed<?> event) {
        log.debug("Topics changed <{}>", event.key());
        final Map<ActorRef, scala.collection.immutable.Set<String>> mmap =
                CollectionConverters.asJava(((ORMultiMap<ActorRef, String>) event.dataValue()).entries());
        final Map<ActorRef, List<Grouped<Long>>> deserializedMMap = mmap.entrySet()
                .stream()
                .map(entry -> Pair.create(entry.getKey(), deserializeGroupedHashes(entry.getValue())))
                .collect(Collectors.toMap(Pair::first, Pair::second));
        final PublisherIndex<Long> thePublisherIndex = PublisherIndex.fromDeserializedMMap(deserializedMMap);
        publisherIndexes.put(event.key(), thePublisherIndex);
        publisherIndex = PublisherIndex.fromMultipleIndexes(publisherIndexes.values());
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    private static List<Grouped<Long>> deserializeGroupedHashes(final scala.collection.immutable.Set<String> strings) {
        return CollectionConverters.asJava(strings).stream()
                .map(string -> Grouped.fromJson(JsonObject.of(string), JsonValue::asLong))
                .toList();
    }

    /**
     * Requests to a publisher actor.
     */
    public interface Request extends WithDittoHeaders {}

    /**
     * Request for the publisher to publish a message.
     * Only the message is sent across the cluster.
     */
    private record Publish(Collection<String> topics, Signal<?> message, CharSequence groupIndexKey)
            implements Request {

        @Override
        public DittoHeaders getDittoHeaders() {
            return message.getDittoHeaders();
        }
    }

    /**
     * Request for the publisher to publish a message with attention to acknowledgement requests.
     */
    private static final class PublishWithAck implements Request {

        private static final AckExtractor<PublishWithAck> ACK_EXTRACTOR =
                AckExtractor.of(p -> p.entityId, p -> p.dittoHeaders);

        private final Collection<String> topics;
        private final Signal<?> message;
        private final Set<AcknowledgementRequest> ackRequests;
        private final EntityId entityId;
        private final DittoHeaders dittoHeaders;

        private PublishWithAck(final Collection<String> topics,
                final Signal<?> message,
                final Set<AcknowledgementRequest> ackRequests,
                final EntityId entityId,
                final DittoHeaders dittoHeaders) {
            this.topics = topics;
            this.message = message;
            this.ackRequests = ackRequests;
            this.entityId = entityId;
            this.dittoHeaders = dittoHeaders;
        }

        private Acknowledgements toWeakAcks(final Collection<AcknowledgementLabel> ackLabels) {
            return ACK_EXTRACTOR.toWeakAcknowledgements(this, ackLabels);
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return dittoHeaders;
        }
    }
}
