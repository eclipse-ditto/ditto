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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.services.utils.pubsub.api.SubAck;
import org.eclipse.ditto.services.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.services.utils.pubsub.api.Unsubscribe;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriptionsReader;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.ddata.Replicator;

/**
 * Manages local subscriptions. Request distributed data update at regular intervals at the highest write consistency
 * requested by a user since the previous update. Send acknowledgement to local subscription requesters after
 * acknowledgement from distributed data. There is no transaction---all subscriptions are eventually distributed in
 * the cluster once requested. Local subscribers should most likely not to get any published message before they
 * receive acknowledgement.
 *
 * @param <T> type of representations of topics in the distributed data.
 */
public final class SubUpdater<T> extends AbstractUpdater<ActorRef, T, SubscriptionsReader> {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "subUpdater";

    @SuppressWarnings("unused")
    private SubUpdater(final PubSubConfig config,
            final ActorRef subscriber,
            final Subscriptions<T> subscriptions,
            final DDataWriter<ActorRef, T> topicsWriter) {
        super(ACTOR_NAME_PREFIX, config, subscriber, subscriptions, topicsWriter);
    }

    /**
     * Create Props object for this actor.
     *
     * @param config the pub-sub config.
     * @param subscriber the subscriber.
     * @param topicsDData access to the distributed data of topics.
     * @return the Props object.
     */
    public static <T> Props props(final PubSubConfig config, final ActorRef subscriber,
            final DData<ActorRef, ?, T> topicsDData) {

        return Props.create(SubUpdater.class, config, subscriber, topicsDData.createSubscriptions(),
                topicsDData.getWriter());
    }

    @Override
    protected void subscribe(final Subscribe subscribe) {
        final boolean changed =
                subscriptions.subscribe(subscribe.getSubscriber(), subscribe.getTopics(), subscribe.getFilter());
        enqueueRequest(subscribe, changed, getSender(), awaitUpdate, awaitUpdateMetric);
        if (changed) {
            getContext().watch(subscribe.getSubscriber());
        }
    }

    @Override
    protected void unsubscribe(final Unsubscribe unsubscribe) {
        final boolean changed = subscriptions.unsubscribe(unsubscribe.getSubscriber(), unsubscribe.getTopics());
        enqueueRequest(unsubscribe, changed, getSender(), awaitUpdate, awaitUpdateMetric);
        if (changed && !subscriptions.contains(unsubscribe.getSubscriber())) {
            getContext().unwatch(unsubscribe.getSubscriber());
        }
    }

    @Override
    protected void ddataOpSuccess(final DDataOpSuccess<SubscriptionsReader> opSuccess) {
        flushSubAcks(opSuccess.seqNr);
        // race condition possible -- some published messages may arrive before the acknowledgement
        // could solve it by having pubSubSubscriber forward acknowledgements. probably not worth it.
        subscriber.tell(opSuccess.payload, getSelf());

        // reset changed flags if there are no more pending changes
        if (awaitSubAck.isEmpty() && awaitUpdate.isEmpty()) {
            localSubscriptionsChanged = false;
            nextWriteConsistency = (Replicator.WriteConsistency) Replicator.writeLocal();
        }
    }

    @Override
    protected void tick(final Clock tick) {
        performDDataOp(forceUpdate(), localSubscriptionsChanged, nextWriteConsistency)
                .handle(handleDDataWriteResult(getSeqNr(), nextWriteConsistency));
        moveAwaitUpdateToAwaitAcknowledge();
    }

    private void flushSubAcks(final int seqNr) {
        for (final SubAck ack : exportAwaitSubAck(seqNr)) {
            ack.getSender().tell(ack, getSelf());
        }
    }

    private CompletionStage<SubscriptionsReader> performDDataOp(final boolean forceUpdate,
            final boolean localSubscriptionsChanged,
            final Replicator.WriteConsistency writeConsistency) {
        final SubscriptionsReader snapshot;
        final CompletionStage<Void> ddataOp;
        if (!localSubscriptionsChanged && !forceUpdate) {
            snapshot = subscriptions.snapshot();
            ddataOp = CompletableFuture.completedStage(null);
        } else if (subscriptions.isEmpty()) {
            snapshot = subscriptions.snapshot();
            ddataOp = topicsWriter.removeSubscriber(subscriber, writeConsistency);
            topicMetric.set(0L);
        } else {
            // export before taking snapshot so that implementations may output incremental update.
            final T ddata = subscriptions.export(forceUpdate);
            // take snapshot to give to the subscriber; clear accumulated incremental changes.
            snapshot = subscriptions.snapshot();
            ddataOp = topicsWriter.put(subscriber, ddata, writeConsistency);
            topicMetric.set((long) subscriptions.countTopics());
        }
        return ddataOp.thenApply(_void -> snapshot);
    }
}
