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

import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

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
 * <li>On DData change, fail local subscribers that lost a race against remote subscribers.</li>
 * </ol>
 */
public final class AcksUpdater extends AbstractUpdater<LiteralUpdate, Map<ActorRef, Set<String>>>
        implements ClusterMemberRemovedAware {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "acksUpdater";

    private final DData<String, LiteralUpdate> acksDData;
    @Nullable private Map<ActorRef, Set<String>> mmap;

    @SuppressWarnings("unused")
    private AcksUpdater(final PubSubConfig config,
            final ActorRef subscriber,
            final DData<String, LiteralUpdate> acksDData) {
        super(ACTOR_NAME_PREFIX, config, subscriber, acksDData.createSubscriptions(), acksDData.getWriter());
        this.acksDData = acksDData;
        subscribeForClusterMemberRemovedAware();
        acksDData.getReader().receiveChanges(getSelf());
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
                .match(Replicator.Changed.class, this::onChanged)
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
        if (areAckLabelsDeclaredHere(subscribe) || areTopicsClaimedRemotely(subscribe)) {
            failSubscribe(sender);
        } else {
            subscriptions.subscribe(subscribe.getSubscriber(), subscribe.getTopics());
            getSender().tell(SubAck.of(subscribe, sender, 0), getSelf());
        }
    }

    @Override
    protected void unsubscribe(final Unsubscribe unsubscribe) {
        log.warning("Got unexpected <{}>", unsubscribe);
    }

    @Override
    protected void tick(final Clock tick) {
        writeLocalDData();
    }

    @Override
    protected void ddataOpSuccess(final DDataOpSuccess<Map<ActorRef, Set<String>>> opSuccess) {
        log.warning("Unexpected DDataOpSuccess: sn=<{}> wc=<{}> payload=<{}>",
                opSuccess.seqNr, opSuccess.writeConsistency, opSuccess.payload);
    }

    private void onChanged(final Replicator.Changed<?> event) {
        mmap = JavaConverters.mapAsJavaMap(event.get(acksDData.getReader().getKey()).entries());
        final java.util.Set<ActorRef> localLosers = getLocalLosers(mmap);
        localLosers.forEach(this::failSubscribe);
        localLosers.forEach(subscriptions::removeSubscriber);
    }

    private void writeLocalDData() {
        acksDData.getWriter()
                .put(subscriber, subscriptions.export(true), Replicator.writeLocal())
                .whenComplete((_void, error) -> {
                    if (error != null) {
                        log.error(error, "Failed to update local DData");
                    } else {
                        log.debug("Local update complete");
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

    private boolean areTopicsClaimedRemotely(final Subscribe subscribe) {
        if (mmap != null) {
            return mmap.entrySet().stream().anyMatch(entry ->
                    !subscriber.equals(entry.getKey()) &&
                            subscribe.getTopics().stream().anyMatch(entry.getValue()::contains)
            );
        } else {
            // no info. be optimistic
            return false;
        }
    }

    private boolean isSmallerThanMySubscriber(final ActorRef otherSubscriber) {
        return otherSubscriber.compareTo(subscriber) < 0;
    }

}
