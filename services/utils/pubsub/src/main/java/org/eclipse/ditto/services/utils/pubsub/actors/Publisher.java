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
package org.eclipse.ditto.services.utils.pubsub.actors;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.services.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.services.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.ddata.Replicator;
import akka.japi.pf.ReceiveBuilder;
import scala.collection.immutable.Set;
import scala.collection.immutable.Set$;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Publishes messages according to topic Bloom filters.
 *
 * @param <T> representation of topics in the distributed data.
 */
public final class Publisher<T> extends AbstractActor {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "publisher";

    private static final Set<String> EMPTY_SET = Set$.MODULE$.empty();

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final DDataReader<ActorRef, T> ddataReader;

    private final Counter messageCounter = DittoMetrics.counter("pubsub-published-messages");
    private final Counter topicCounter = DittoMetrics.counter("pubsub-published-topics");

    private Map<ActorRef, Set<T>> topicSubscribers = Map.of();
    private Map<Address, Set<String>> declaredAcks = Map.of();
    private java.util.Set<String> allDeclaredAcks = java.util.Set.of();

    @SuppressWarnings("unused")
    private Publisher(final DDataReader<ActorRef, T> ddataReader, final DistributedAcks distributedAcks) {
        this.ddataReader = ddataReader;
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
     * @return a publish message.
     */
    public static Request publish(final Collection<String> topics, final Object message) {
        return new Publish(topics, message);
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
            final Object message,
            final java.util.Set<AcknowledgementRequest> ackRequests,
            final EntityIdWithType entityId,
            final DittoHeaders dittoHeaders) {

        return new PublishWithAck(topics, message, ackRequests, entityId, dittoHeaders);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Publish.class, this::publish)
                .match(PublishWithAck.class, this::publishWithAck)
                .match(AcksUpdater.DDataChanged.class, this::declaredAcksChanged)
                .match(Replicator.Changed.class, this::topicSubscribersChanged)
                .matchAny(this::logUnhandled)
                .build();
    }

    private void publish(final Publish publish) {
        doPublish(publish.topics, publish.message);
    }

    private void publishWithAck(final PublishWithAck publishWithAck) {
        final Collection<ActorRef> subscribers = doPublish(publishWithAck.topics, publishWithAck.message);
        final java.util.Set<String> subscriberDeclaredAcks = subscribers.stream()
                .map(subscriber -> declaredAcks.getOrDefault(subscriber.path().address(), EMPTY_SET))
                .flatMap(set -> CollectionConverters.asJava(set).stream())
                .collect(Collectors.toSet());

        final Collection<AcknowledgementLabel> requestedCustomAcks =
                AckExtractor.getRequestedAndDeclaredCustomAcks(publishWithAck.ackRequests, allDeclaredAcks::contains);

        final List<AcknowledgementLabel> labelsWithoutAuthorizedSubscribers = requestedCustomAcks.stream()
                .filter(label -> !subscriberDeclaredAcks.contains(label.toString()))
                .collect(Collectors.toList());

        if (!labelsWithoutAuthorizedSubscribers.isEmpty()) {
            getSender().tell(publishWithAck.toWeakAcks(labelsWithoutAuthorizedSubscribers), ActorRef.noSender());
        }
    }

    private Collection<ActorRef> doPublish(final Collection<String> topics, final Object message) {
        messageCounter.increment();
        topicCounter.increment(topics.size());
        final List<T> hashes = topics.stream().map(ddataReader::approximate).collect(Collectors.toList());
        final ActorRef sender = getSender();
        final Collection<ActorRef> subscribers = ddataReader.getSubscribers(topicSubscribers, hashes);
        subscribers.forEach(subscriber -> subscriber.tell(message, sender));
        return subscribers;
    }

    private void declaredAcksChanged(final AcksUpdater.DDataChanged ddataChanged) {
        declaredAcks = ddataChanged.getMultiMap();
        allDeclaredAcks = declaredAcks.values().stream()
                .flatMap(set -> CollectionConverters.asJava(set).stream())
                .collect(Collectors.toSet());
    }

    private void topicSubscribersChanged(final Replicator.Changed<?> event) {
        topicSubscribers = CollectionConverters.asJava(event.get(ddataReader.getKey()).entries());
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    /**
     * Requests to a publisher actor.
     */
    public interface Request {}

    /**
     * Request for the publisher to publish a message.
     * Only the message is sent across the cluster.
     */
    private static final class Publish implements Request {

        private final Collection<String> topics;
        private final Object message;

        private Publish(final Collection<String> topics, final Object message) {
            this.topics = topics;
            this.message = message;
        }
    }

    /**
     * Request for the publisher to publish a message with attention to acknowledgement requests.
     */
    private static final class PublishWithAck implements Request {

        private static final AckExtractor<PublishWithAck> ACK_EXTRACTOR =
                AckExtractor.of(p -> p.entityId, p -> p.dittoHeaders);

        private final Collection<String> topics;
        private final Object message;
        private final java.util.Set<AcknowledgementRequest> ackRequests;
        private final EntityIdWithType entityId;
        private final DittoHeaders dittoHeaders;

        private PublishWithAck(final Collection<String> topics, final Object message,
                final java.util.Set<AcknowledgementRequest> ackRequests,
                final EntityIdWithType entityId,
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
    }
}
