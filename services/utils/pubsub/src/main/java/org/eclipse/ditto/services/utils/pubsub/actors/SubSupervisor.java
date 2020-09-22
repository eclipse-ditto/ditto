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
package org.eclipse.ditto.services.utils.pubsub.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.ddata.Replicator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of actors dealing with subscriptions.
 * <pre>
 * {@code
 *     SubSupervisor
 *          +
 *          |
 *          |supervises one-for-many
 *          +---------------------------------+-----------------------+
 *          |                                 |                       |
 *          |                                 |                       |
 *          |                                 |                       |
 *          |                                 |                       |
 *          |                                 |                       |
 *          v                                 v                       v
 *       SubUpdater +-----------------> Subscriber               AcksUpdater
 *          +        update             (forwards                     +
 *          |        local               published                    |
 *          |        subscriptions       signals)                     |
 *          |                                                         |
 *          |                                                         |
 *          |                                                         |
 *          |                                              write-local|
 *          |                                            on clock tick|
 *          |                                                         |
 *          |                                                         |
 *          |write with highest requested consistency                 |
 *          |                                                         |
 *          +----------------->DDataReplicator<-----------------------+
 * </pre>
 *
 * @param <T> type of messages subscribed for.
 * @param <U> type of compressed topic in the cluster.
 */
public final class SubSupervisor<T, U> extends AbstractPubSubSupervisor {

    private final Class<T> messageClass;
    private final PubSubTopicExtractor<T> topicExtractor;

    private final DData<?, U> topicsDData;
    private final DData<String, LiteralUpdate> acksDData;

    @Nullable private ActorRef updater;
    @Nullable private ActorRef acksUpdater;

    @SuppressWarnings("unused")
    private SubSupervisor(final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final DData<?, U> topicsDData,
            final DData<String, LiteralUpdate> acksDData) {
        super();
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;
        this.topicsDData = topicsDData;
        this.acksDData = acksDData;
    }

    /**
     * Create Props object for this actor.
     *
     * @param messageClass class of messages.
     * @param topicExtractor extractor of topics from messages.
     * @param topicsDData access to the distributed data of topics.
     * @param acksDData access to the distributed data of acknowledgement labels.
     * @param <T> type of messages.
     * @param <U> type of ddata updates.
     * @return the Props object.
     */
    public static <T, U> Props props(final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final DData<?, U> topicsDData,
            final DData<String, LiteralUpdate> acksDData) {

        return Props.create(SubSupervisor.class, messageClass, topicExtractor, topicsDData, acksDData);
    }

    @Override
    protected Receive createPubSubBehavior() {
        return ReceiveBuilder.create()
                .match(AbstractUpdater.DeclareAckLabels.class, this::isAcksUpdaterAvailable, this::declareAckLabels)
                .match(AbstractUpdater.DeclareAckLabels.class, this::acksUpdaterUnavailable)
                .match(AbstractUpdater.Request.class, this::isUpdaterAvailable, this::request)
                .match(AbstractUpdater.Request.class, this::updaterUnavailable)
                .match(Terminated.class, this::subscriberTerminated)
                .build();
    }

    @Override
    protected void onChildFailure() {
        // if this ever happens, consider adding a recovery mechanism in SubUpdater.postStop.
        updater = null;
        log.error("All local subscriptions lost.");
    }

    @Override
    protected void startChildren() {
        final ActorRef subscriber =
                startChild(Subscriber.props(messageClass, topicExtractor), Subscriber.ACTOR_NAME_PREFIX);
        getContext().watch(subscriber);

        final Props updaterProps = SubUpdater.props(config, subscriber, topicsDData);
        updater = startChild(updaterProps, SubUpdater.ACTOR_NAME_PREFIX);

        final Props acksUpdaterProps = AcksUpdater.props(config, subscriber, acksDData);
        acksUpdater = startChild(acksUpdaterProps, AcksUpdater.ACTOR_NAME_PREFIX);
    }

    private void subscriberTerminated(final Terminated terminated) {
        log.error("Subscriber terminated. Removing subscriber from DData: <{}>", terminated.getActor());
        topicsDData.getWriter().removeSubscriber(terminated.getActor(), Replicator.writeLocal());
        acksDData.getWriter().removeSubscriber(terminated.getActor(), Replicator.writeLocal());
    }

    private boolean isUpdaterAvailable() {
        return updater != null;
    }

    @SuppressWarnings("ConstantConditions")
    private void request(final AbstractUpdater.Request request) {
        updater.tell(request, getSender());
    }

    private boolean isAcksUpdaterAvailable() {
        return acksUpdater != null;
    }

    @SuppressWarnings("ConstantConditions")
    private void declareAckLabels(final AbstractUpdater.DeclareAckLabels request) {
        acksUpdater.tell(request.toSubscribe(), getSender());
    }

    private void updaterUnavailable(final SubUpdater.Request request) {
        log.error("SubUpdater unavailable. Dropping <{}>", request);
    }

    private void acksUpdaterUnavailable(final AbstractUpdater.DeclareAckLabels request) {
        log.error("AcksUpdater unavailable. Dropping <{}>", request);
    }

}
