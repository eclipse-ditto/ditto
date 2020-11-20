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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.function.BiFunction;

import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.pubsub.api.RemoveSubscriber;
import org.eclipse.ditto.services.utils.pubsub.api.Request;
import org.eclipse.ditto.services.utils.pubsub.api.SubAck;
import org.eclipse.ditto.services.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.services.utils.pubsub.api.Unsubscribe;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.cluster.ddata.Replicator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Abstract super class of SubUpdater and AcksUpdater.
 * Implement the logic to aggregate subscription changes and to replicate the changes each clock tick.
 *
 * @param <K> type of keys of the distributed multimap.
 * @param <T> type of topics in the distributed data.
 * @param <P> type of payload of DDataOpSuccess messages.
 */
abstract class AbstractUpdater<K, T, P> extends AbstractActorWithTimers {

    protected final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    protected final PubSubConfig config;
    protected final Subscriptions<T> subscriptions;
    protected final DDataWriter<K, T> topicsWriter;
    protected final K subscriber;

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
    protected final Queue<SubAck> awaitSubAck = new ArrayDeque<>();

    /**
     * Write consistency of the next message to the replicator.
     */
    protected Replicator.WriteConsistency nextWriteConsistency = (Replicator.WriteConsistency) Replicator.writeLocal();

    /**
     * Whether local subscriptions changed.
     */
    protected boolean localSubscriptionsChanged = false;

    private int seqNr = 0;

    protected AbstractUpdater(final String actorNamePrefix,
            final PubSubConfig config,
            final K subscriber,
            final Subscriptions<T> subscriptions,
            final DDataWriter<K, T> topicsWriter) {
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

        getTimers().startTimerAtFixedRate(Clock.TICK, Clock.TICK, config.getUpdateInterval());
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Subscribe.class, this::subscribe)
                .match(Unsubscribe.class, this::unsubscribe)
                .match(Terminated.class, this::terminated)
                .match(RemoveSubscriber.class, this::removeSubscriber)
                .matchEquals(Clock.TICK, this::tick)
                .match(DDataOpSuccess.class, this::ddataOpSuccess)
                .match(Status.Failure.class, this::updateFailure)
                .matchAny(this::logUnhandled)
                .build();
    }

    /**
     * What to do when update succeeded.
     *
     * @param opSuccess success message of a distributed data op.
     */
    protected abstract void ddataOpSuccess(DDataOpSuccess<P> opSuccess);

    /**
     * Handle a subscribe request.
     *
     * @param subscribe the subscribe request.
     */
    protected abstract void subscribe(Subscribe subscribe);

    /**
     * Handle an unsubscribe request.
     *
     * @param unsubscribe the unsubscribe request.
     */
    protected abstract void unsubscribe(Unsubscribe unsubscribe);

    /**
     * Handle a clock tick.
     *
     * @param tick the clock tick.
     */
    protected abstract void tick(Clock tick);

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
     * Add a request to the queue to be handled after cluster update.
     *
     * @param request the request.
     * @param changed whether the request changed ddata.
     * @param queue the queue to enqueue the request.
     * @param queueSizeMetric the metrics for the queue size.
     * @param sender sender of the request.
     */
    protected void enqueueRequest(final Request request, final boolean changed, final ActorRef sender,
            final Collection<SubAck> queue, final Gauge queueSizeMetric) {
        localSubscriptionsChanged |= changed;
        upgradeWriteConsistency(request.getWriteConsistency());
        if (request.shouldAcknowledge()) {
            final SubAck subAck = SubAck.of(request, sender, ++seqNr);
            queue.add(subAck);
            queueSizeMetric.increment();
        }
    }

    /**
     * @return the sequence number of the last SubAck created by this#enqueueRequest.
     */
    protected int getSeqNr() {
        return seqNr;
    }

    /**
     * Export the list of pending SubAck messages up to a sequence number.
     * SubAck messages that are not exported stay in the queue.
     *
     * @param seqNr the final sequence number.
     * @return the list of SubAck up to the sequence number.
     */
    protected List<SubAck> exportAwaitSubAck(final int seqNr) {
        final List<SubAck> subAcks = new ArrayList<>(awaitSubAck.size());
        while (!awaitSubAck.isEmpty()) {
            final SubAck ack = awaitSubAck.poll();
            subAcks.add(ack);
            // Stop exporting after seqNr equal to the last added SubAck. Testing equality to tolerate overflow.
            if (ack.getSeqNr() == seqNr) {
                break;
            }
        }
        awaitSubAckMetric.set((long) awaitSubAck.size());
        return Collections.unmodifiableList(subAcks);
    }

    /**
     * Flush the "awaitUpdate" queue after a distributed data write started.
     */
    protected void moveAwaitUpdateToAwaitAcknowledge() {
        if (!awaitUpdate.isEmpty()) {
            awaitSubAck.addAll(awaitUpdate);
            awaitUpdate.clear();
            awaitSubAckMetric.set((long) awaitSubAck.size());
            awaitUpdateMetric.set(0L);
        }
    }

    /**
     * Handle the result of a distributed data write by sending a report to self.
     *
     * @param lastSeqNr the final sequence number of this update.
     * @param writeConsistency the write consistency of this update.
     * @return the function to handle the distributed data write result.
     */
    protected BiFunction<P, Throwable, Void> handleDDataWriteResult(final int lastSeqNr,
            final Replicator.WriteConsistency writeConsistency) {
        // this function is called asynchronously. it must be thread-safe.
        return (payload, error) -> {
            if (error != null) {
                getSelf().tell(new Status.Failure(error), ActorRef.noSender());
            } else if (payload == null) {
                // do nothing - no ddata op was performed
                return null;
            } else {
                getSelf().tell(new DDataOpSuccess<>(payload, lastSeqNr, writeConsistency), ActorRef.noSender());
            }
            return null;
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

    protected void doRemoveSubscriber(final ActorRef subscriber) {
        localSubscriptionsChanged |= subscriptions.removeSubscriber(subscriber);
        getContext().unwatch(subscriber);
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

    protected enum Clock {

        /**
         * Clock tick to update distributed data.
         */
        TICK
    }

    /**
     * Package-private message to indicate success of a distributed data operation.
     *
     * @param <P> the payload type.
     */
    static final class DDataOpSuccess<P> {

        final P payload;
        final int seqNr;
        final Replicator.WriteConsistency writeConsistency;

        DDataOpSuccess(final P payload, final int seqNr, final Replicator.WriteConsistency writeConsistency) {
            this.payload = payload;
            this.seqNr = seqNr;
            this.writeConsistency = writeConsistency;
        }
    }
}
