/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriptionsReader;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.cluster.ddata.Replicator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract super class of SubUpdater and AcksUpdater.
 * Implement the logic to aggregate subscription changes and to replicate the changes each clock tick.
 */
public abstract class AbstractUpdater<T> extends AbstractActorWithTimers {

    // pseudo-random number generator for force updates. quality matters little.
    private final Random random = new Random();

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    protected final PubSubConfig config;
    protected final Subscriptions<T> subscriptions;
    protected final DDataWriter<T> topicsWriter;
    protected final ActorRef subscriber;

    protected final Gauge topicMetric;
    protected final Gauge awaitUpdateMetric;
    protected final Gauge awaitSubAckMetric;

    /**
     * Queue of actors demanding SubAck whose subscriptions are not sent to the distributed data replicator.
     */
    protected final List<SubAck> awaitUpdate = new ArrayList<>();

    /**
     * Queue of actors demanding SubAck whose subscriptions were sent to the replicator but not SubAck-ed.
     */
    protected final List<SubAck> awaitSubAck = new ArrayList<>();

    /**
     * Write consistency of the next message to the replicator.
     */
    protected Replicator.WriteConsistency nextWriteConsistency = Replicator.writeLocal();

    /**
     * Whether local subscriptions changed.
     */
    protected boolean localSubscriptionsChanged = false;

    protected AbstractUpdater(final String actorNamePrefix,
            final PubSubConfig config,
            final ActorRef subscriber,
            final Subscriptions<T> subscriptions,
            final DDataWriter<T> topicsWriter) {
        this.config = config;
        this.subscriber = subscriber;
        this.subscriptions = subscriptions;
        this.topicsWriter = topicsWriter;

        // tag metrics by parent name + this name prefix
        // so that the tag is finite and distinct between twin and live topics and declared ack labels.
        final String tagName = getContext().getParent().path().name() + "/" + actorNamePrefix;
        topicMetric = DittoMetrics.gauge("pubsub-topics").tag("name", tagName);
        awaitUpdateMetric = DittoMetrics.gauge("pubsub-await-update").tag("name", tagName);
        awaitSubAckMetric = DittoMetrics.gauge("pubsub-await-acknowledge").tag("name", tagName);

        getTimers().startPeriodicTimer(Clock.TICK, Clock.TICK, config.getUpdateInterval());
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Subscribe.class, this::subscribe)
                .match(Unsubscribe.class, this::unsubscribe)
                .match(Terminated.class, this::terminated)
                .match(RemoveSubscriber.class, this::removeSubscriber)
                .matchEquals(Clock.TICK, this::tick)
                .match(SubscriptionsReader.class, this::updateSuccess)
                .match(Status.Failure.class, this::updateFailure)
                .matchAny(this::logUnhandled)
                .build();
    }

    /**
     * Flush pending SubAcks to senders.
     */
    protected abstract void flushSubAcks();

    /**
     * What to do when update succeeded.
     *
     * @param snapshot the snapshot of the current subscription.
     */
    protected abstract void updateSuccess(final SubscriptionsReader snapshot);

    /**
     * Handle a subscribe request.
     *
     * @param subscribe the subscribe request.
     */
    protected abstract void subscribe(final Subscribe subscribe);

    /**
     * Handle an unsubscribe request.
     *
     * @param unsubscribe the unsubscribe request.
     */
    protected abstract void unsubscribe(final Unsubscribe unsubscribe);


    /**
     * What to do when DData update failed.
     *
     * @param failure the update failure.
     */
    protected void updateFailure(final Status.Failure failure) {
        log.error(failure.cause(), "updateFailure");
        // try again next clock tick
        localSubscriptionsChanged = true;
    }

    /**
     * @return whether the next update is a random force update.
     */
    private boolean forceUpdate() {
        return random.nextDouble() < config.getForceUpdateProbability();
    }

    /**
     * Add a request to the queue to be handled after cluster update.
     *
     * @param request the request.
     * @param changed whether the request changed ddata.
     * @param sender sender of the request.
     */
    protected void enqueueRequest(final Request request, final boolean changed, final ActorRef sender) {
        localSubscriptionsChanged |= changed;
        upgradeWriteConsistency(request.getWriteConsistency());
        if (request.shouldAcknowledge()) {
            final SubAck subAck = SubAck.of(request, sender);
            awaitUpdate.add(subAck);
            awaitUpdateMetric.increment();
        }
    }

    private void tick(final Clock tick) {
        final boolean forceUpdate = forceUpdate();
        if (!localSubscriptionsChanged && !forceUpdate) {
            moveAwaitUpdateToAwaitAcknowledge();
            flushSubAcks();
        } else {
            final SubscriptionsReader snapshot;
            final CompletionStage<Void> ddataOp;
            if (subscriptions.isEmpty()) {
                snapshot = subscriptions.snapshot();
                ddataOp = topicsWriter.removeSubscriber(subscriber, nextWriteConsistency);
                topicMetric.set(0L);
            } else {
                // export before taking snapshot so that implementations may output incremental update.
                final T ddata = subscriptions.export(forceUpdate);
                // take snapshot to give to the subscriber; clear accumulated incremental changes.
                snapshot = subscriptions.snapshot();
                ddataOp = topicsWriter.put(subscriber, ddata, nextWriteConsistency);
                topicMetric.set((long) subscriptions.countTopics());
            }
            ddataOp.handle(handleDDataWriteResult(snapshot));
            moveAwaitUpdateToAwaitAcknowledge();
            localSubscriptionsChanged = false;
            nextWriteConsistency = Replicator.writeLocal();
        }
    }

    private void moveAwaitUpdateToAwaitAcknowledge() {
        awaitSubAck.addAll(awaitUpdate);
        awaitUpdate.clear();
        awaitSubAckMetric.set((long) awaitSubAck.size());
        awaitUpdateMetric.set(0L);
    }

    private BiFunction<Void, Throwable, Void> handleDDataWriteResult(final SubscriptionsReader snapshot) {
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

    private void terminated(final Terminated terminated) {
        doRemoveSubscriber(terminated.actor());
    }

    private void removeSubscriber(final RemoveSubscriber request) {
        doRemoveSubscriber(request.getSubscriber());
    }

    private void doRemoveSubscriber(final ActorRef subscriber) {
        localSubscriptionsChanged |= subscriptions.removeSubscriber(subscriber);
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

    /**
     * Super class of subscription requests.
     */
    public abstract static class Request {

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

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "[topics=" + topics +
                    ", subscriber=" + subscriber +
                    ", writeConsistency=" + writeConsistency +
                    ", acknowledge=" + acknowledge +
                    "]";
        }
    }

    /**
     * Request to subscribe to topics.
     */
    public static final class Subscribe extends Request {

        private static final Predicate<Collection<String>> CONSTANT_TRUE = topics -> true;

        private final Predicate<Collection<String>> filter;

        private Subscribe(final Set<String> topics, final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency, final boolean acknowledge,
                final Predicate<Collection<String>> filter) {
            super(topics, subscriber, writeConsistency, acknowledge);
            this.filter = filter;
        }

        /**
         * Create a "subscribe" request.
         *
         * @param topics the set of topics to subscribe.
         * @param subscriber who is subscribing.
         * @param writeConsistency with which write consistency should this subscription be updated.
         * @param acknowledge whether acknowledgement is desired.
         * @return the request.
         */
        public static Subscribe of(final Set<String> topics, final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency, final boolean acknowledge) {
            return new Subscribe(topics, subscriber, writeConsistency, acknowledge, CONSTANT_TRUE);
        }

        /**
         * Create a "subscribe" request.
         *
         * @param topics the set of topics to subscribe.
         * @param subscriber who is subscribing.
         * @param writeConsistency with which write consistency should this subscription be updated.
         * @param acknowledge whether acknowledgement is desired.
         * @param filter local filter for incoming messages.
         * @return the request.
         */
        public static Subscribe of(final Set<String> topics, final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency, final boolean acknowledge,
                final Predicate<Collection<String>> filter) {
            return new Subscribe(topics, subscriber, writeConsistency, acknowledge, filter);
        }

        /**
         * @return Filter for incoming messages.
         */
        public Predicate<Collection<String>> getFilter() {
            return filter;
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

        /**
         * Create an "unsubscribe" request.
         *
         * @param topics the set of topics to subscribe.
         * @param subscriber who is subscribing.
         * @param writeConsistency with which write consistency should this subscription be updated.
         * @param acknowledge whether acknowledgement is desired.
         * @return the request.
         */
        public static Unsubscribe of(final Set<String> topics, final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency, final boolean acknowledge) {
            return new Unsubscribe(topics, subscriber, writeConsistency, acknowledge);
        }
    }

    /**
     * Request to remove a subscriber.
     */
    public static final class RemoveSubscriber extends Request {

        private RemoveSubscriber(final ActorRef subscriber, final Replicator.WriteConsistency writeConsistency,
                final boolean acknowledge) {
            super(Collections.emptySet(), subscriber, writeConsistency, acknowledge);
        }

        /**
         * Create an "unsubscribe" request.
         *
         * @param subscriber who is subscribing.
         * @param writeConsistency with which write consistency should this subscription be updated.
         * @param acknowledge whether acknowledgement is desired.
         * @return the request.
         */
        public static RemoveSubscriber of(final ActorRef subscriber,
                final Replicator.WriteConsistency writeConsistency, final boolean acknowledge) {
            return new RemoveSubscriber(subscriber, writeConsistency, acknowledge);
        }
    }

    /**
     * Acknowledgement for requests.
     */
    public static final class SubAck {

        private final Request request;
        private final ActorRef sender;

        private SubAck(final Request request, final ActorRef sender) {
            this.request = request;
            this.sender = sender;
        }

        private static SubAck of(final Request request, final ActorRef sender) {
            return new SubAck(request, sender);
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

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "[request=" + request +
                    ",sender=" + sender +
                    "]";
        }
    }

    private enum Clock {

        /**
         * Clock tick to update distributed data.
         */
        TICK
    }
}
