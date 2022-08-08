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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.acks.PubSubTerminatedException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.pubsub.api.RemoveSubscriber;
import org.eclipse.ditto.internal.utils.pubsub.api.Request;
import org.eclipse.ditto.internal.utils.pubsub.api.SubAck;
import org.eclipse.ditto.internal.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.internal.utils.pubsub.api.Unsubscribe;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.Subscriptions;
import org.eclipse.ditto.internal.utils.pubsub.ddata.SubscriptionsReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.compressed.CompressedDData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.compressed.CompressedSubscriptions;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.cluster.ddata.Replicator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Manages local subscriptions. Request distributed data update at regular intervals at the highest write consistency
 * requested by a user since the previous update. Send acknowledgement to local subscription requesters after
 * acknowledgement from distributed data. There is no transaction---all subscriptions are eventually distributed in
 * the cluster once requested. Local subscribers should most likely not to get any published message before they
 * receive acknowledgement.
 */
public final class SubUpdater extends akka.actor.AbstractActorWithTimers
        implements ClusterStateSyncBehavior<ActorRef> {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "subUpdater";

    public static final int MAX_ERROR_COUNTER = 3;

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
    private final Subscriptions<LiteralUpdate> subscriptions;
    private final ActorRef subscriber;
    private final Gauge topicSizeMetric;
    private final Gauge awaitUpdateMetric;
    private final Gauge awaitSubAckMetric;
    private final DData<ActorRef, ?, LiteralUpdate> ddata;
    private final Cluster cluster;
    private final double resetProbability;

    /**
     * Queue of actors demanding SubAck whose subscriptions are not sent to the distributed data replicator.
     */
    private final List<SubAck> awaitUpdate = new ArrayList<>();

    /**
     * Queue of actors demanding SubAck whose subscriptions were sent to the replicator but not SubAck-ed.
     */
    private final Queue<SubAck> awaitSubAck = new ArrayDeque<>();

    /**
     * Write consistency of the next message to the replicator.
     */
    private final Replicator.WriteConsistency writeConsistency;

    private int seqNr = 0;
    private LiteralUpdate previousUpdate = LiteralUpdate.empty();
    private int errorCounter = 0;

    @SuppressWarnings("unused")
    private SubUpdater(final PubSubConfig config,
            final ActorRef subscriber,
            final Subscriptions<LiteralUpdate> subscriptions,
            final DData<ActorRef, ?, LiteralUpdate> ddata) {
        this.subscriber = subscriber;
        this.subscriptions = subscriptions;
        this.ddata = ddata;
        cluster = Cluster.get(getContext().getSystem());
        resetProbability = config.getResetProbability();
        writeConsistency = ddata.getConfig().getSubscriptionWriteConsistency();

        // tag metrics by parent name + this name prefix
        // so that the tag is finite and distinct between twin and live topics and declared ack labels.
        final String tagName = getContext().getParent().path().name() + "/" + ACTOR_NAME_PREFIX;
        this.topicSizeMetric = DittoMetrics.gauge("pubsub-topics-size-bytes").tag("name", tagName);
        this.awaitUpdateMetric = DittoMetrics.gauge("pubsub-await-update").tag("name", tagName);
        this.awaitSubAckMetric = DittoMetrics.gauge("pubsub-await-acknowledge").tag("name", tagName);

        getTimers().startTimerAtFixedRate(Clock.TICK, Clock.TICK, config.getUpdateInterval());
        scheduleClusterStateSync(config);
    }

    /**
     * Create Props object for this actor.
     *
     * @param config the pub-sub config.
     * @param subscriber the subscriber.
     * @param topicsDData access to the distributed data of topics.
     * @return the Props object.
     */
    public static Props props(final PubSubConfig config, final ActorRef subscriber, final CompressedDData topicsDData) {
        return Props.create(SubUpdater.class, config, subscriber, CompressedSubscriptions.of(topicsDData.getSeeds()),
                topicsDData);
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
                .matchEquals(ActorEvent.PUBSUB_TERMINATED, this::pubSubTerminated)
                .build()
                .orElse(getClusterStateSyncBehavior())
                .orElse(ReceiveBuilder.create().matchAny(this::logUnhandled).build());
    }

    private void subscribe(final Subscribe subscribe) {
        final boolean changed =
                subscriptions.subscribe(subscribe.getSubscriber(), subscribe.getTopics(), subscribe.getFilter(),
                        subscribe.getGroup().orElse(null));
        final var consistent = checkForLostSubscriber(subscribe, changed);
        enqueueRequest(subscribe, getSender(), awaitUpdate, awaitUpdateMetric, consistent);
        if (changed) {
            getContext().watch(subscribe.getSubscriber());
        }
    }

    private void unsubscribe(final Unsubscribe unsubscribe) {
        final boolean changed = subscriptions.unsubscribe(unsubscribe.getSubscriber(), unsubscribe.getTopics());
        enqueueRequest(unsubscribe, getSender(), awaitUpdate, awaitUpdateMetric, true);
        if (changed && !subscriptions.contains(unsubscribe.getSubscriber())) {
            getContext().unwatch(unsubscribe.getSubscriber());
        }
    }

    private boolean checkForLostSubscriber(final Subscribe subscribe, final boolean changed) {
        if (subscribe.isResubscribe() && changed) {
            log().error("[RESUB] Subscriber was missing: <{}>", subscribe.getSubscriber());
            return false;
        } else if (subscribe.isResubscribe()) {
            log().debug("[RESUB] Refreshed subscriber <{}>", subscribe.getSubscriber());
        }
        return true;
    }

    private void ddataOpSuccess(final DDataOpSuccess<SubscriptionsReader> opSuccess) {
        log().debug("DDataOp success seqNr=<{}>", opSuccess.seqNr);
        errorCounter = 0;
        // race condition possible -- some published messages may arrive before the acknowledgement
        // could solve it by having pubSubSubscriber forward acknowledgements. probably not worth it.
        subscriber.tell(opSuccess.payload, getSelf());
        flushSubAcks(opSuccess.seqNr);
    }

    private void tick(final Clock tick) {
        performDDataOp(writeConsistency).handle(handleDDataWriteResult(getSeqNr()));
        moveAwaitUpdateToAwaitAcknowledge();
    }

    private void flushSubAcks(final int seqNr) {
        for (final SubAck ack : exportAwaitSubAck(seqNr)) {
            ack.getSender().tell(ack, getSelf());
        }
    }

    private CompletionStage<SubscriptionsReader> performDDataOp(final Replicator.WriteConsistency writeConsistency) {
        final SubscriptionsReader snapshot;
        final CompletionStage<Void> ddataOp;
        log().debug("Tick seq=<{}> empty=<{}> writeConsistency=<{}>", seqNr, subscriptions.isEmpty(), writeConsistency);
        final boolean isReset = previousUpdate.isEmpty() && !subscriptions.isEmpty();
        if (isReset || resetProbability > 0 && Math.random() < resetProbability) {
            log().debug("Resetting ddata topics: <{}>", getSelf());
            final var nextUpdate = subscriptions.export();
            ddataOp = ddata.getWriter().reset(subscriber, nextUpdate, writeConsistency);
            snapshot = subscriptions.snapshot();
            previousUpdate = nextUpdate;
            topicSizeMetric.set(subscriptions.estimateSize());
        } else if (subscriptions.isEmpty()) {
            ddataOp = ddata.getWriter().removeSubscriber(subscriber, writeConsistency);
            snapshot = subscriptions.snapshot();
            previousUpdate = LiteralUpdate.empty();
            topicSizeMetric.set(0L);
        } else {
            // export before taking snapshot so that implementations may output incremental update.
            final LiteralUpdate nextUpdate = subscriptions.export();
            // take snapshot to give to the subscriber; clear accumulated incremental changes.
            snapshot = subscriptions.snapshot();
            final var diff = nextUpdate.diff(previousUpdate);
            log().debug("diff.isEmpty=<{}>", diff.isEmpty());
            if (!diff.isEmpty()) {
                ddataOp = ddata.getWriter().put(subscriber, nextUpdate.diff(previousUpdate), writeConsistency);
            } else {
                ddataOp = CompletableFuture.completedStage(null);
            }
            previousUpdate = nextUpdate;
            topicSizeMetric.set(subscriptions.estimateSize());
        }
        return ddataOp.thenApply(unused -> snapshot);
    }

    /**
     * What to do when DData update failed.
     *
     * @param failure the update failure.
     */
    private void updateFailure(final Status.Failure failure) {
        ++errorCounter;
        if (errorCounter > MAX_ERROR_COUNTER) {
            log.error(failure.cause(), "Failure updating Ditto pub/sub subscription - trying again next clock tick");
        } else {
            log.warning("Failure updating Ditto pub/sub subscription - trying again next clock tick");
        }
        previousUpdate = LiteralUpdate.empty();
    }

    /**
     * Add a request to the queue to be handled after cluster update.
     *
     * @param request the request.
     * @param sender sender of the request.
     * @param queue the queue to enqueue the request.
     * @param queueSizeMetric the metrics for the queue size.
     * @param consistent whether the consistency check of a resubscription succeeded.
     */
    private void enqueueRequest(final Request request, final ActorRef sender,
            final Collection<SubAck> queue, final Gauge queueSizeMetric, final boolean consistent) {
        if (request.shouldAcknowledge()) {
            final SubAck subAck = SubAck.of(request, sender, ++seqNr, consistent);
            queue.add(subAck);
            queueSizeMetric.increment();
        }
    }

    /**
     * @return the sequence number of the last SubAck created by this#enqueueRequest.
     */
    private int getSeqNr() {
        return seqNr;
    }

    /**
     * Export the list of pending SubAck messages up to a sequence number.
     * SubAck messages that are not exported stay in the queue.
     *
     * @param seqNr the final sequence number.
     * @return the list of SubAck up to the sequence number.
     */
    private List<SubAck> exportAwaitSubAck(final int seqNr) {
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
    private void moveAwaitUpdateToAwaitAcknowledge() {
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
     * @return the function to handle the distributed data write result.
     */
    private BiFunction<SubscriptionsReader, Throwable, Void> handleDDataWriteResult(final int lastSeqNr) {
        // this function is called asynchronously. it must be thread-safe.
        return (payload, error) -> {
            if (error != null) {
                getSelf().tell(new Status.Failure(error), ActorRef.noSender());
            } else if (payload == null) {
                // do nothing - no ddata op was performed
                return null;
            } else {
                getSelf().tell(new DDataOpSuccess<>(payload, lastSeqNr), ActorRef.noSender());
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

    private void doRemoveSubscriber(final ActorRef subscriber) {
        subscriptions.removeSubscriber(subscriber);
        getContext().unwatch(subscriber);
    }

    private void pubSubTerminated(final ActorEvent terminated) {
        final Set<ActorRef> informedSubscribers = new HashSet<>();
        final Stream<ActorRef> awaitingAck =
                Stream.concat(awaitUpdate.stream(), awaitSubAck.stream()).map(SubAck::getSender);
        Stream.concat(awaitingAck, subscriptions.getSubscribers().stream())
                .forEach(theSubscriber -> {
                    if (informedSubscribers.add(theSubscriber)) {
                        theSubscriber.tell(PubSubTerminatedException.getInstance(), getSelf());
                    }
                });

        // delete local data
        subscriptions.clear();
        awaitUpdate.clear();
        awaitSubAck.clear();

        // subscriber will recover from this error on its own.
    }

    @Override
    public Cluster getCluster() {
        return cluster;
    }

    @Override
    public Address toAddress(final ActorRef ddataKey) {
        return ddataKey.path().address();
    }

    @Override
    public ThreadSafeDittoLoggingAdapter log() {
        return log;
    }

    @Override
    public DData<ActorRef, ?, ?> getDData() {
        return ddata;
    }

    @Override
    public void verifyNoDDataForCurrentMember() {
        if (!subscriptions.isEmpty()) {
            previousUpdate = LiteralUpdate.empty();
        }
        // Do nothing for empty subscriptions: No data is expected for the current member.
    }

    private enum Clock {

        /**
         * Clock tick to update distributed data.
         */
        TICK
    }

    /**
     * Self-message to indicate success of a distributed data operation.
     *
     * @param <P> the payload type.
     */
    private static final class DDataOpSuccess<P> {

        private final P payload;
        private final int seqNr;

        private DDataOpSuccess(final P payload, final int seqNr) {
            this.payload = payload;
            this.seqNr = seqNr;
        }
    }
}
