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
package org.eclipse.ditto.services.utils.pubsub.actors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.LocalSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.LocalSubscriptionsReader;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.TopicBloomFiltersWriter;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubUpdaterConfig;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.cluster.ddata.Replicator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.util.ByteString;

/**
 * Manages local subscriptions. Request distributed data update at regular intervals at the highest write consistency
 * requested by a user since the previous update. Send acknowledgement to local subscription requesters after
 * acknowledgement from distributed data. There is no transaction---all subscriptions are eventually distributed in
 * the cluster once requested. Local subscribers should most likely not to get any published message before they
 * receive acknowledgement. Below is the state transition diagram.
 * <p>
 * <pre>
 * {@code
 *                Subscribe/Unsubscribe: Append to awaitUpdate
 *                +----------+
 *                |          |
 *                |          |
 *                |          |
 *                +------->  +
 *                             WAITING <-----------------------------------+
 *                +---------->   +                                         |
 *                |              |                                         |
 * Update failure:|              |                                         |Update success:
 * Do nothing.    |              | Clock tick:                             |Ack awaitAcknowledge
 * Wait for next  |              | Add awaitUpdate to awaitAcknowledge     |Clear awaitAcknowledge
 * tick.          |              | Request distributed data update         |Send written state to pubSubSubscriber
 *                |              |                                         |
 *                |              |                                         |
 *                |              |                                         |
 *                |              |                                         |
 *                |              |                                         |
 *                +-----------+  v                                         |
 *                             UPDATING +----------------------------------+
 *                +-----------+      +
 *                |                  | <-----+
 *                |           ^      |       |Clock tick: Do nothing
 *                |           |      +-------+
 *                +-----------+
 *                Subscribe/Unsubscribe: Append to awaitUpdate
 * }
 * </pre>
 */
public final class PubSubUpdater extends AbstractActorWithTimers {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final PubSubUpdaterConfig config;
    private final ActorRef pubSubSubscriber;
    private final LocalSubscriptions localSubscriptions;
    private final TopicBloomFiltersWriter topicBloomFiltersWriter;

    private final Gauge topicMetric = DittoMetrics.gauge("pubsub-topics");
    private final Gauge bloomFilterBytesMetric = DittoMetrics.gauge("pubsub-bloom-filter-bytes");
    private final Gauge awaitUpdateMetric = DittoMetrics.gauge("pubsub-await-update");
    private final Gauge awaitAcknowledgeMetric = DittoMetrics.gauge("pubsub-await-acknowledge");

    /**
     * Queue of actors demanding acknowledgement whose subscriptions are not sent to the distributed data replicator.
     */
    private final List<Acknowledgement> awaitUpdate = new ArrayList<>();

    /**
     * Queue of actors demanding acknowledgement whose subscriptions were sent to the replicator but not acknowledged.
     */
    private final List<Acknowledgement> awaitAcknowledge = new ArrayList<>();

    /**
     * Write consistency of the next message to the replicator.
     */
    private Replicator.WriteConsistency nextWriteConsistency = Replicator.writeLocal();

    /**
     * Whether local subscriptions changed.
     */
    private boolean localSubscriptionsChanged = false;

    /**
     * Current state of the actor.
     */
    private State state = State.WAITING;

    private PubSubUpdater(final PubSubUpdaterConfig config,
            final ActorRef pubSubSubscriber, final LocalSubscriptions localSubscriptions,
            final TopicBloomFiltersWriter topicBloomFiltersWriter) {
        this.config = config;
        this.pubSubSubscriber = pubSubSubscriber;
        this.localSubscriptions = localSubscriptions;
        this.topicBloomFiltersWriter = topicBloomFiltersWriter;

        getTimers().startPeriodicTimer(Clock.TICK, Clock.TICK, config.getUpdateInterval());
    }

    @Override
    public Receive createReceive() {
        // TODO: handle dead letters from publisher for removing dead remote entries with write consistency LOCAL
        // TODO: test dead letters handling in real cluster.
        return ReceiveBuilder.create()
                .match(Subscribe.class, this::subscribe)
                .match(Unsubscribe.class, this::unsubscribe)
                .match(Terminated.class, this::terminated)
                .matchEquals(Clock.TICK, this::tick)
                .match(LocalSubscriptionsReader.class, this::updateSuccess)
                .match(Status.Failure.class, this::updateFailure)
                .matchAny(this::logUnhandled)
                .build();
    }

    private void tick(final Clock tick) {
        if (state == State.UPDATING || !localSubscriptionsChanged) {
            log.debug("ignoring tick in state <{}> with changed=<{}>", state, localSubscriptionsChanged);
        } else {
            log.debug("updating");
            final LocalSubscriptionsReader snapshot = localSubscriptions.snapshot();
            final ByteString bloomFilter = localSubscriptions.toOptimalBloomFilter(config.getBufferFactor());
            topicBloomFiltersWriter.updateOwnTopics(pubSubSubscriber, bloomFilter, nextWriteConsistency)
                    .handle(handleDDataWriteResult(snapshot));

            bloomFilterBytesMetric.set((long) bloomFilter.size());
            topicMetric.set((long) localSubscriptions.getTopicCount());
            moveAwaitUpdateToAwaitAcknowledge();
            localSubscriptionsChanged = false;
            nextWriteConsistency = Replicator.writeLocal();
            state = State.UPDATING;
        }
    }

    private void updateSuccess(final LocalSubscriptionsReader snapshot) {
        log.debug("updateSuccess");
        for (final Acknowledgement ack : awaitAcknowledge) {
            ack.getSender().tell(ack, getSelf());
        }
        awaitAcknowledge.clear();
        awaitAcknowledgeMetric.set(0L);
        state = State.WAITING;

        // race condition possible -- some published messages may arrive before the acknowledgement
        // could solve it by having pubSubSubscriber forward acknowledgements. probably not worth it.
        pubSubSubscriber.tell(snapshot, getSelf());
    }

    private void updateFailure(final Status.Failure failure) {
        log.error(failure.cause(), "updateFailure");

        // try again next clock tick
        localSubscriptionsChanged = true;
        state = State.WAITING;
    }

    private void moveAwaitUpdateToAwaitAcknowledge() {
        awaitAcknowledge.addAll(awaitUpdate);
        awaitUpdate.clear();
        awaitAcknowledgeMetric.set((long) awaitAcknowledge.size());
        awaitUpdateMetric.set(0L);
    }

    private BiFunction<Void, Throwable, Void> handleDDataWriteResult(final LocalSubscriptionsReader snapshot) {
        // this function is called asynchronously. it must be thread-safe.
        return (_void, error) -> {
            if (error == null) {
                getSelf().tell(snapshot, ActorRef.noSender());
            } else {
                getSelf().tell(new Status.Failure(error), ActorRef.noSender());
            }
            return _void;
        };
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    private void subscribe(final Subscribe subscribe) {
        final boolean changed = localSubscriptions.subscribe(subscribe.getSubscriber(), subscribe.getTopics());
        enqueueRequest(subscribe, changed);
        if (changed) {
            getContext().watch(subscribe.getSubscriber());
        }
    }

    private void unsubscribe(final Unsubscribe unsubscribe) {
        final boolean changed = localSubscriptions.unsubscribe(unsubscribe.getSubscriber(), unsubscribe.getTopics());
        enqueueRequest(unsubscribe, changed);
        if (changed && !localSubscriptions.contains(unsubscribe.getSubscriber())) {
            getContext().unwatch(unsubscribe.getSubscriber());
        }
    }

    private void terminated(final Terminated terminated) {
        localSubscriptionsChanged |= localSubscriptions.removeSubscriber(terminated.actor());
    }

    private void enqueueRequest(final Request request, final boolean changed) {
        localSubscriptionsChanged |= changed;
        upgradeWriteConsistency(request.getWriteConsistency());
        if (request.shouldAcknowledge()) {
            final Acknowledgement acknowledgement = Acknowledgement.of(request, getSender());
            awaitUpdate.add(acknowledgement);
        }
    }

    private void upgradeWriteConsistency(final Replicator.WriteConsistency nextWriteConsistency) {
        if (isMoreConsistent(nextWriteConsistency, this.nextWriteConsistency)) {
            this.nextWriteConsistency = nextWriteConsistency;
        }
    }

    private static boolean isMoreConsistent(final Replicator.WriteConsistency a, final Replicator.WriteConsistency b) {
        return rank(a) > rank(b);
    }

    // roughly rank write consistency from the most local to the most global.
    private static int rank(final Replicator.WriteConsistency a) {
        if (Replicator.writeLocal().equals(a)) {
            return Integer.MIN_VALUE;
        } else if (a instanceof Replicator.WriteAll) {
            return Integer.MAX_VALUE;
        } else if (a instanceof Replicator.WriteMajority) {
            return ((Replicator.WriteMajority) a).minCap();
        } else if (a instanceof Replicator.WriteTo) {
            return ((Replicator.WriteTo) a).n();
        } else {
            return 0;
        }
    }

    private static abstract class Request {

        private final Set<String> topics;
        private final ActorRef subscriber;
        private final Replicator.WriteConsistency writeConsistency;
        private final boolean acknowledge;

        private Request(final Set<String> topics,
                final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency,
                final boolean acknowledge) {

            this.topics = topics;
            this.subscriber = subscriber;
            this.writeConsistency = writeConsistency;
            this.acknowledge = acknowledge;
        }

        /**
         * @return topics in the subscription.
         */
        public Set<String> getTopics() {
            return topics;
        }

        /**
         * @return subscriber of the subscription.
         */
        public ActorRef getSubscriber() {
            return subscriber;
        }

        /**
         * @return write consistency for the request.
         */
        public Replicator.WriteConsistency getWriteConsistency() {
            return writeConsistency;
        }

        /**
         * @return whether acknowledgement is expected.
         */
        public boolean shouldAcknowledge() {
            return acknowledge;
        }
    }

    /**
     * Request to subscribe to topics.
     */
    public static final class Subscribe extends Request {

        private Subscribe(final Set<String> topics, final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency, final boolean acknowledge) {
            super(topics, subscriber, writeConsistency, acknowledge);
        }
    }

    /**
     * Request to unsubscribe to topics.
     */
    public static final class Unsubscribe extends Request {

        private Unsubscribe(final Set<String> topics, final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency, final boolean acknowledge) {
            super(topics, subscriber, writeConsistency, acknowledge);
        }
    }

    /**
     * Acknowledgement for requests.
     */
    public static final class Acknowledgement {

        private final Request request;
        private final ActorRef sender;

        private Acknowledgement(final Request request, final ActorRef sender) {
            this.request = request;
            this.sender = sender;
        }

        private static Acknowledgement of(final Request request, final ActorRef sender) {
            return new Acknowledgement(request, sender);
        }

        /**
         * @return the request this object is acknowledging.
         */
        public Request getRequest() {
            return request;
        }

        /**
         * @return sender of the request.
         */
        public ActorRef getSender() {
            return sender;
        }
    }

    private enum Clock {

        /**
         * Clock tick to update distributed data.
         */
        TICK
    }

    private enum State {
        /**
         * Waiting for clock tick.
         */
        WAITING,

        /**
         * Waiting for acknowledgement from ddata.
         */
        UPDATING
    }
}
