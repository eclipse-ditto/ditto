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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;
import org.eclipse.ditto.signals.acks.base.AcknowledgementLabelNotUniqueException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.ddata.Replicator;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import scala.collection.JavaConverters;
import scala.collection.immutable.Set;

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
public final class AcksUpdater extends AbstractUpdater<LiteralUpdate, Map<ActorRef, Set<String>>>
        implements ClusterMemberRemovedAware {

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
        subscribeForClusterMemberRemovedAware();
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
                .build()
                .orElse(receiveClusterMemberRemoved())
                .orElse(super.createReceive());
    }

    @Override
    public LoggingAdapter log() {
        return log;
    }

    @Override
    public DDataWriter<?> getDDataWriter() {
        return acksDData.getWriter();
    }

    @Override
    protected void subscribe(final Subscribe subscribe) {
        final ActorRef sender = getSender();
        // read local DData for remote subscribers
        areTopicsClaimedRemotely(subscribe).thenAccept(taken -> {
            if (taken) {
                failSubscribe(sender);
            } else {
                getSelf().tell(new DoSubscribe(subscribe, sender), ActorRef.noSender());
            }
        });
    }

    @Override
    protected void unsubscribe(final Unsubscribe unsubscribe) {
        log.warning("Got unexpected <{}>", unsubscribe);
    }

    @Override
    protected CompletionStage<Map<ActorRef, Set<String>>> performDDataOp(final boolean forceUpdate,
            final boolean localSubscriptionsChanged,
            final Replicator.WriteConsistency writeConsistency) {

        // always read from cluster to discover local subscribers that lost a race
        // TODO: add a separate config for acks-updater with slower clock ticks
        // TODO: OR, switch subscribe to ddata-updated events
        return writeLocalDData().thenCompose(_void -> acksDData.getReader().read(toReadConsistency(writeConsistency)));
    }

    @Override
    protected void ddataOpSuccess(final DDataOpSuccess<Map<ActorRef, Set<String>>> opSuccess) {
        if (!opSuccess.payload.isEmpty()) {
            final List<SubAck> subAcks = exportAwaitSubAck(opSuccess.seqNr);
            final java.util.Set<ActorRef> newlyFailedSubscribers = new HashSet<>();

            // Local losers are the actors whose declared ack labels are taken over concurrently by remote subscribers.
            final java.util.Set<ActorRef> localLosers = getLocalLosers(opSuccess.payload);
            // Remove local losers (replicated to cluster at the next Subscribe or the next clock tick at the latest)
            localLosers.forEach(subscriptions::removeSubscriber);
            for (final SubAck subAck : subAcks) {
                if (localLosers.contains(subAck.getRequest().getSubscriber())) {
                    newlyFailedSubscribers.add(subAck.getRequest().getSubscriber());
                    failSubscribe(subAck.getSender());
                } else {
                    subAck.getSender().tell(subAck, ActorRef.noSender());
                }
            }

            // There is a chance that a subscriber gets failure after SubAck due to unlucky timing.
            // Send a failure to the subscriber directly so that it terminates.
            localLosers.stream()
                    .filter(localSubscriber -> !newlyFailedSubscribers.contains(localSubscriber))
                    .forEach(this::failSubscribe);
        }
    }

    private Replicator.ReadConsistency toReadConsistency(final Replicator.WriteConsistency writeConsistency) {
        if (writeConsistency instanceof Replicator.WriteAll) {
            return new Replicator.ReadAll(writeConsistency.timeout());
        } else if (writeConsistency instanceof Replicator.WriteMajority) {
            return new Replicator.ReadMajority(writeConsistency.timeout());
        } else {
            return Replicator.readLocal();
        }
    }

    private void doSubscribe(final DoSubscribe doSubscribe) {
        if (areAckLabelsDeclaredHere(doSubscribe.subscribe)) {
            failSubscribe(doSubscribe.sender);
        } else {
            final boolean changed =
                    subscriptions.subscribe(doSubscribe.subscribe.getSubscriber(), doSubscribe.subscribe.getTopics());
            enqueueRequest(doSubscribe.subscribe, changed, doSubscribe.sender, awaitSubAck, awaitUpdateMetric);
            if (changed) {
                getContext().watch(doSubscribe.subscribe.getSubscriber());
            }
        }
    }

    private CompletionStage<Void> writeLocalDData() {
        final int seqNr = getSeqNr();
        return acksDData.getWriter()
                .put(subscriber, subscriptions.export(true), Replicator.writeLocal())
                .whenComplete((_void, error) -> {
                    if (error != null) {
                        log.error(error, "Failed to update local DData");
                    } else {
                        log.debug("Local update complete seqNr={}", seqNr);
                    }
                });
    }

    private void failSubscribe(final ActorRef sender) {
        final Throwable error = AcknowledgementLabelNotUniqueException.getInstance();
        sender.tell(error, getSelf());
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
     * @param remoteAckLabels ack labels claimed remotely.
     * @return a future set of local subscribers who lost a race.
     */
    private java.util.Set<ActorRef> getLocalLosers(final Map<ActorRef, Set<String>> remoteAckLabels) {
        return remoteAckLabels.entrySet()
                .stream()
                .filter(entry -> isSmallerThanMySubscriber(entry.getKey()))
                .flatMap(entry -> JavaConverters.asJavaCollection(entry.getValue())
                        .stream()
                        .flatMap(subscriptions::streamSubscribers)
                )
                .collect(Collectors.toSet());
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
