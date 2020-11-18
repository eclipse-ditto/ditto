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
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.services.utils.pubsub.api.Request;
import org.eclipse.ditto.services.utils.pubsub.api.SubAck;
import org.eclipse.ditto.services.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.services.utils.pubsub.api.Unsubscribe;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.ddata.Replicator;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import scala.collection.immutable.Set;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Manages local declared acknowledgement labels.
 * <ol>
 * <li>On Subscribe with known duplicate label, reject right away.</li>
 * <li>On Subscribe, add ack labels to local store.</li>
 * <li>On clock, flush DData change to replicator.</li>
 * <li>On DData change, fail local subscribers that lost a race against remote subscribers.</li>
 * </ol>
 */
public final class AcksUpdater extends AbstractUpdater<Address, LiteralUpdate, Map<Address, Set<String>>>
        implements ClusterMemberRemovedAware {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "acksUpdater";

    private final DData<Address, String, LiteralUpdate> acksDData;
    @Nullable private Map<Address, Set<String>> mmap;
    private final java.util.Set<ActorRef> ddataChangeRecipients;
    private final java.util.Set<ActorRef> localChangeRecipients;

    @SuppressWarnings("unused")
    private AcksUpdater(final PubSubConfig config,
            final Address ownAddress,
            final DData<Address, String, LiteralUpdate> acksDData) {
        super(ACTOR_NAME_PREFIX, config, ownAddress, acksDData.createSubscriptions(), acksDData.getWriter());
        this.acksDData = acksDData;
        ddataChangeRecipients = new HashSet<>();
        localChangeRecipients = new HashSet<>();
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
            final Address subscriber,
            final DData<Address, String, LiteralUpdate> acksDData) {

        return Props.create(AcksUpdater.class, config, subscriber, acksDData);
    }

    /**
     * Create a request to this actor that causes the given receiver to receive changes from the underlying distributed
     * data.
     *
     * @param receiver The receiver of distributed data changes.
     * @return The request.
     */
    public static Request receiveDDataChanges(final ActorRef receiver) {
        return new ReceiveDDataChanges(receiver);
    }

    /**
     * Create a request to this actor that causes the given receiver to receive changes of local subscriptions.
     *
     * @param receiver The receiver of local subscriptions.
     * @return The request.
     */
    public static Request receiveLocalChanges(final ActorRef receiver) {
        return new ReceiveLocalChanges(receiver);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Replicator.Changed.class, this::onChanged)
                .match(ReceiveDDataChanges.class, this::onReceiveDDataChanges)
                .match(ReceiveLocalChanges.class, this::onReceiveLocalChanges)
                .match(Terminated.class, this::terminated)
                .build()
                .orElse(receiveClusterMemberRemoved())
                .orElse(super.createReceive());
    }

    @Override
    public LoggingAdapter log() {
        return log;
    }

    @Override
    public DDataWriter<Address, ?> getDDataWriter() {
        return acksDData.getWriter();
    }

    @Override
    protected void subscribe(final Subscribe subscribe) {
        final ActorRef sender = getSender();
        if (areAckLabelsDeclaredHere(subscribe) || areTopicsClaimedRemotely(subscribe)) {
            failSubscribe(sender);
        } else {
            if (subscriptions.subscribe(subscribe.getSubscriber(), subscribe.getTopics())) {
                getContext().watch(subscribe.getSubscriber());
            }
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
        final SubscriptionsChanged subscriptionsChanged = new SubscriptionsChanged(subscriptions.snapshot());
        localChangeRecipients.forEach(recipient -> recipient.tell(subscriptionsChanged, getSelf()));
    }

    @Override
    protected void ddataOpSuccess(final DDataOpSuccess<Map<Address, Set<String>>> opSuccess) {
        log.warning("Unexpected DDataOpSuccess: sn=<{}> wc=<{}> payload=<{}>",
                opSuccess.seqNr, opSuccess.writeConsistency, opSuccess.payload);
    }

    private void onChanged(final Replicator.Changed<?> event) {
        mmap = CollectionConverters.asJava(event.get(acksDData.getReader().getKey()).entries());
        final java.util.Set<ActorRef> localLosers = getLocalLosers(mmap);
        localLosers.forEach(this::failSubscribe);
        localLosers.forEach(subscriptions::removeSubscriber);
        final DDataChanged ddataChanged = new DDataChanged(mmap);
        ddataChangeRecipients.forEach(recipient -> recipient.tell(ddataChanged, getSelf()));
    }

    private void onReceiveDDataChanges(final ReceiveDDataChanges request) {
        ddataChangeRecipients.add(request.receiver);
        getContext().watch(request.receiver);
    }

    private void onReceiveLocalChanges(final ReceiveLocalChanges request) {
        localChangeRecipients.add(request.receiver);
        getContext().watch(request.receiver);
    }

    private void terminated(final Terminated terminated) {
        final ActorRef terminatedActor = terminated.getActor();
        doRemoveSubscriber(terminatedActor);
        ddataChangeRecipients.remove(terminatedActor);
        localChangeRecipients.remove(terminatedActor);
    }

    private void writeLocalDData() {
        acksDData.getWriter()
                .put(subscriber, subscriptions.export(true), (Replicator.WriteConsistency) Replicator.writeLocal())
                .whenComplete((_void, error) -> {
                    if (error != null) {
                        log.error(error, "Failed to update local DData");
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
    private java.util.Set<ActorRef> getLocalLosers(final Map<Address, Set<String>> remoteAckLabels) {
        return remoteAckLabels.entrySet()
                .stream()
                .filter(entry -> isSmallerThanMySubscriber(entry.getKey()))
                .flatMap(entry -> CollectionConverters.asJava(entry.getValue())
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

    private boolean isSmallerThanMySubscriber(final Address otherSubscriber) {
        return Address.addressOrdering().compare(otherSubscriber, subscriber) < 0;
    }

    private abstract static class ReceiveChanges implements Request {

        protected final ActorRef receiver;

        private ReceiveChanges(final ActorRef receiver) {
            this.receiver = receiver;
        }

        @Override
        public java.util.Set<String> getTopics() {
            return java.util.Set.of();
        }

        @Override
        public Replicator.WriteConsistency getWriteConsistency() {
            return (Replicator.WriteConsistency) Replicator.writeLocal();
        }

        @Override
        public boolean shouldAcknowledge() {
            return false;
        }
    }

    private static final class ReceiveDDataChanges extends ReceiveChanges {

        private ReceiveDDataChanges(final ActorRef receiver) {
            super(receiver);
        }
    }

    private static final class ReceiveLocalChanges extends ReceiveChanges {

        private ReceiveLocalChanges(final ActorRef receiver) {
            super(receiver);
        }
    }

}
