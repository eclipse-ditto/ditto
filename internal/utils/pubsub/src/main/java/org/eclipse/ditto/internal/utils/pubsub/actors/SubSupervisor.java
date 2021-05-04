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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.Request;
import org.eclipse.ditto.internal.utils.pubsub.ddata.compressed.CompressedDData;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of actors dealing with subscriptions.
 * <pre>
 * {@code
 * SubSupervisor
 *          +
 *          |
 *          |supervises one-for-many
 *          +---------------------------------+
 *          |                                 |
 *          |                                 |
 *          |                                 |
 *          |                                 |
 *          |                                 |
 *          v                                 v
 *       SubUpdater +-----------------> Subscriber
 *          +        update             (forwards
 *          |        local               published
 *          |        subscriptions       signals)
 *          |
 *          |
 *          |
 *          |
 *          |
 *          |
 *          |
 *          |write with highest requested consistency
 *          |
 *          +----------------->DDataReplicator
 * }
 * </pre>
 *
 * @param <T> type of messages subscribed for.
 */
public final class SubSupervisor<T> extends AbstractPubSubSupervisor {

    private final Class<T> messageClass;
    private final PubSubTopicExtractor<T> topicExtractor;
    private final CompressedDData topicsDData;
    private final AckExtractor<T> ackExtractor;
    private final DistributedAcks distributedAcks;

    @Nullable private ActorRef subscriber;
    @Nullable private ActorRef updater;

    @SuppressWarnings("unused")
    private SubSupervisor(final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final CompressedDData topicsDData,
            final AckExtractor<T> ackExtractor,
            final DistributedAcks distributedAcks) {
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;
        this.topicsDData = topicsDData;
        this.ackExtractor = ackExtractor;
        this.distributedAcks = distributedAcks;
    }

    /**
     * Create Props object for this actor.
     *
     * @param <T> type of messages.
     * @param messageClass class of messages.
     * @param topicExtractor extractor of topics from messages.
     * @param topicsDData access to the distributed data of topics.
     * @param ackExtractor extractor of acknowledgement-related information from a message.
     * @param distributedAcks access to the distributed data of declared acknowledgement labels.
     * @return the Props object.
     */
    public static <T> Props props(final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final CompressedDData topicsDData,
            final AckExtractor<T> ackExtractor,
            final DistributedAcks distributedAcks) {

        return Props.create(SubSupervisor.class, messageClass, topicExtractor, topicsDData, ackExtractor,
                distributedAcks);
    }

    @Override
    protected Receive createPubSubBehavior() {
        return ReceiveBuilder.create()
                .match(Request.class, this::isUpdaterAvailable, this::request)
                .match(Request.class, this::updaterUnavailable)
                .match(Terminated.class, this::childTerminated)
                .matchEquals(ActorEvent.DEBUG_KILL_CHILDREN, this::debugKillChildren)
                .build();
    }

    @Override
    protected void onChildFailure(final ActorRef failingChild) {
        if (updater != null && !failingChild.equals(updater)) {
            // Updater survived. Ask it to inform known subscribers of local data loss.
            updater.tell(ActorEvent.PUBSUB_TERMINATED, getSelf());
        }
        updater = null;
        subscriber = null;
        log.error("All local subscriptions lost.");
    }

    @Override
    protected void startChildren() {
        subscriber = startChild(Subscriber.props(messageClass, topicExtractor, ackExtractor, distributedAcks),
                Subscriber.ACTOR_NAME_PREFIX);
        updater = startChild(SubUpdater.props(config, subscriber, topicsDData), SubUpdater.ACTOR_NAME_PREFIX);
    }

    private void debugKillChildren(final ActorEvent debugKillChildren) {
        log.warning("Killing children on request. DO NOT do this in production!");
        getContext().getChildren().forEach(getContext()::stop);
    }

    private void childTerminated(final Terminated terminated) {
        if (terminated.getActor().equals(subscriber) || terminated.getActor().equals(updater)) {
            log.error("Child actor terminated. Removing subscriber from DData: <{}>", terminated.getActor());
            topicsDData.getWriter().removeSubscriber(terminated.getActor(), ClusterMemberRemovedAware.writeLocal());
            getContext().getChildren().forEach(getContext()::stop);
            subscriber = null;
            updater = null;
            scheduleRestartChildren();
        }
    }

    private boolean isUpdaterAvailable() {
        return updater != null;
    }

    @SuppressWarnings("ConstantConditions")
    private void request(final Request request) {
        updater.tell(request, getSender());
    }

    private void updaterUnavailable(final Request request) {
        log.error("SubUpdater unavailable. Dropping <{}>", request);
        getSender().tell(new IllegalStateException("AcksUpdater not available"), getSelf());
    }

}
