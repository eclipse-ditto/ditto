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

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.pubsub.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriptionsReader;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ddata.Replicator;
import akka.japi.pf.ReceiveBuilder;
import scala.collection.JavaConverters;

/**
 * Manages local declared acknowledgement labels.
 * <ol>
 * <li>On Subscribe with known duplicate label, reject right away.</li>
 * <li>On Subscribe, add ack labels to local store.</li>
 * <li>On clock, flush DData change to replicator.</li>
 * <li>On update complete, check if each added label retains this node as the lowest subscriber.</li>
 * <li>If this node is the lowest subscriber, ask SubUpdater then forward SubAck.</li>
 * <li>If this node is not the lowest subscriber, rollback and send negative SubAck.</li>
 * </ol>
 */
public final class AcksUpdater extends AbstractUpdater<LiteralUpdate> {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "acksUpdater";

    private final DData<String, LiteralUpdate> acksDData;

    @SuppressWarnings("unused")
    private AcksUpdater(final PubSubConfig config,
            final ActorRef subscriber,
            final DData<String, LiteralUpdate> acksDData) {
        super(ACTOR_NAME_PREFIX, config, subscriber, acksDData.createSubscriptions(), acksDData.getWriter());
        this.acksDData = acksDData;
        Cluster.get(getContext().getSystem()).subscribe(getSelf(), ClusterEvent.MemberRemoved.class);
    }

    /**
     * Create Props object for this actor.
     *
     * @param config the pub-sub config.
     * @param subscriber the subscriber.
     * @param acksDData access to the distributed data of declared acknowledgement labels.
     * @return the Props object.
     */
    public static Props props(final PubSubConfig config,
            final ActorRef subscriber,
            final DData<String, LiteralUpdate> acksDData) {

        return Props.create(AcksUpdater.class, config, subscriber, acksDData);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(DoSubscribe.class, this::doSubscribe)
                .match(ClusterEvent.MemberRemoved.class, this::memberRemoved)
                .match(ClusterEvent.CurrentClusterState.class, this::logCurrentClusterState)
                .build()
                .orElse(super.createReceive());
    }

    @Override
    protected void subscribe(final Subscribe subscribe) {
        final ActorRef sender = getSender();
        if (areAckLabelsDeclaredHere(subscribe)) {
            failSubscribe(sender);
        } else {
            // read local DData for remote subscribers
            areTopicsClaimedRemotely(subscribe).thenAccept(taken -> {
                if (taken) {
                    failSubscribe(sender);
                } else {
                    getSelf().tell(new DoSubscribe(subscribe, sender), ActorRef.noSender());
                }
            });
        }
    }

    @Override
    protected void unsubscribe(final Unsubscribe unsubscribe) {
        final boolean changed = subscriptions.removeSubscriber(unsubscribe.getSubscriber());
        enqueueRequest(unsubscribe, changed, getSender());
        if (changed) {
            getContext().unwatch(unsubscribe.getSubscriber());
        }
    }

    @Override
    protected void updateSuccess(final SubscriptionsReader snapshot) {
        flushSubAcks();
    }

    @Override
    protected void flushSubAcks() {
        // There is a chance that a subscriber gets failure after SubAck due to unlucky timing.
        checkConsistencyAndReply(awaitSubAck);
        awaitSubAck.clear();
        awaitSubAckMetric.set(0L);
    }

    private void memberRemoved(final ClusterEvent.MemberRemoved memberRemoved) {
        // acksUpdater detected unreachable remote. remove it from local ORMultiMap.
        final Address address = memberRemoved.member().address();
        log.info("Removing declared acks on removed member <{}>", address);
        acksDData.getWriter().removeAddress(address, Replicator.writeLocal()).whenComplete((_void, error) -> {
            if (error != null) {
                log.error(error, "Failed to remove declared acks on removed cluster member <{}>", address);
            }
        });
    }

    private void doSubscribe(final DoSubscribe doSubscribe) {
        final boolean changed = subscriptions.subscribe(doSubscribe.sender, doSubscribe.subscribe.getTopics());
        enqueueRequest(doSubscribe.subscribe, changed, doSubscribe.sender);
        if (changed) {
            getContext().watch(doSubscribe.subscribe.getSubscriber());
        }
    }

    private void checkConsistencyAndReply(final List<SubAck> awaitSubAck) {
        final List<SubAck> subAcks = List.copyOf(awaitSubAck);
        final SubscriptionsReader snapshot = subscriptions.snapshot();

        // local losers are the actors whose declared ack labels are taken over concurrently by remote subscribers.
        getLocalLosers(snapshot).thenAccept(localLosers -> {
            // Under a rare condition, a local subscriber may lose a race _after_ a successful SubAck.
            localLosers.forEach(this::failSubscribe);
            for (final SubAck subAck : subAcks) {
                if (!localLosers.contains(subAck.getSender())) {
                    // the subscriber won the race
                    subAck.getSender().tell(subAck, ActorRef.noSender());
                } else {
                    // the subscriber lost the race; remove it. (failSubscribe already called)
                    final Unsubscribe unsubscribe =
                            Unsubscribe.of(Set.of(), subAck.getSender(), Replicator.writeLocal(), false);
                    getSelf().tell(unsubscribe, ActorRef.noSender());
                }
            }
        });
    }

    private void failSubscribe(final ActorRef sender) {
        final Throwable error = AcknowledgementLabelNotUniqueException.getInstance();
        sender.tell(error, getSelf());
    }

    private void logCurrentClusterState(final ClusterEvent.CurrentClusterState currentClusterState) {
        log.info("Got <{}>" + currentClusterState);
    }

    private boolean areAckLabelsDeclaredHere(final Subscribe subscribe) {
        return subscribe.getTopics()
                .stream()
                .flatMap(subscriptions::streamSubscribers)
                .anyMatch(previousSubscriber -> !previousSubscriber.equals(subscribe.getSubscriber()));
    }

    /**
     * Get local subscribers who lost a race of ack label declaration.
     *
     * @param snapshot a snapshot of local subscribers for asynchronous read.
     * @return a future set of local subscribers who lost a race.
     */
    private CompletionStage<Set<ActorRef>> getLocalLosers(final SubscriptionsReader snapshot) {
        final CompletionStage<Set<String>> conflictingAckLabels = acksDData.getReader()
                .read()
                .thenApply(map -> map.entrySet()
                        .stream()
                        .filter(entry -> isSmallerThanMySubscriber(entry.getKey()))
                        .flatMap(entry -> JavaConverters.asJavaCollection(entry.getValue())
                                .stream()
                                .filter(snapshot::containsTopic)
                        )
                        .collect(Collectors.toSet())
                );
        return conflictingAckLabels.thenApply(snapshot::getSubscribers);
    }

    private CompletionStage<Boolean> areTopicsClaimedRemotely(final Subscribe subscribe) {
        return acksDData.getReader()
                .getSubscribers(subscribe.getTopics())
                .thenApply(allSubscribers -> allSubscribers.stream()
                        .anyMatch(otherSubscriber -> !subscriber.equals(otherSubscriber))
                );
    }

    private boolean isSmallerThanMySubscriber(final ActorRef otherSubscriber) {
        return otherSubscriber.compareTo(subscriber) < 0;
    }

    private static final class DoSubscribe {

        private final Subscribe subscribe;
        private final ActorRef sender;

        private DoSubscribe(final Subscribe subscribe, final ActorRef sender) {
            this.subscribe = subscribe;
            this.sender = sender;
        }
    }

}
