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

import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriptionsReader;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Manages local subscriptions. Request distributed data update at regular intervals at the highest write consistency
 * requested by a user since the previous update. Send acknowledgement to local subscription requesters after
 * acknowledgement from distributed data. There is no transaction---all subscriptions are eventually distributed in
 * the cluster once requested. Local subscribers should most likely not to get any published message before they
 * receive acknowledgement.
 *
 * @param <T> type of representations of topics in the distributed data.
 */
public final class SubUpdater<T> extends AbstractUpdater<T> {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "subUpdater";

    @SuppressWarnings("unused")
    private SubUpdater(final PubSubConfig config,
            final ActorRef subscriber,
            final Subscriptions<T> subscriptions,
            final DDataWriter<T> topicsWriter) {
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
    public static <T> Props props(final PubSubConfig config, final ActorRef subscriber, final DData<?, T> topicsDData) {

        return Props.create(SubUpdater.class, config, subscriber, topicsDData.createSubscriptions(),
                topicsDData.getWriter());
    }

    @Override
    protected void updateSuccess(final SubscriptionsReader snapshot) {
        flushSubAcks(true);
        // race condition possible -- some published messages may arrive before the acknowledgement
        // could solve it by having pubSubSubscriber forward acknowledgements. probably not worth it.
        subscriber.tell(snapshot, getSelf());
    }

    @Override
    protected void flushSubAcks(final boolean ddataChanged) {
        for (final SubAck ack : awaitSubAck) {
            ack.getSender().tell(ack, getSelf());
        }
        awaitSubAck.clear();
        awaitSubAckMetric.set(0L);
    }
}
