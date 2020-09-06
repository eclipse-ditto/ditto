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

import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.SubscriptionsReader;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.Props;

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

    private final ActorRef subUpdater;
    private final DData<String, LiteralUpdate> acksDData;

    @SuppressWarnings("unused")
    private AcksUpdater(final PubSubConfig config,
            final ActorRef subscriber,
            final ActorRef subUpdater,
            final DData<String, LiteralUpdate> acksDData) {
        super(ACTOR_NAME_PREFIX, config, subscriber, acksDData.createSubscriptions(), acksDData.getWriter());
        this.subUpdater = subUpdater;
        this.acksDData = acksDData;
    }

    /**
     * Create Props object for this actor.
     *
     * @param config the pub-sub config.
     * @param subscriber the subscriber.
     * @param subUpdater the subUpdater.
     * @param acksDData access to the distributed data of declared acknowledgement labels.
     * @return the Props object.
     */
    public static Props props(final PubSubConfig config,
            final ActorRef subscriber,
            final ActorRef subUpdater,
            final DData<String, LiteralUpdate> acksDData) {

        return Props.create(AcksUpdater.class, config, subscriber, subUpdater, acksDData);
    }

    @Override
    protected void updateSuccess(final SubscriptionsReader snapshot) {
        // TODO: fix this
        flushSubAcks(true);
        // race condition possible -- some published messages may arrive before the acknowledgement
        // could solve it by having pubSubSubscriber forward acknowledgements. probably not worth it.
        subscriber.tell(snapshot, getSelf());
    }

    @Override
    protected void flushSubAcks(final boolean ddataChanged) {
        // TODO: fix this
        for (final SubAck ack : awaitSubAck) {
            ack.getSender().tell(ack, getSelf());
        }
        awaitSubAck.clear();
        awaitSubAckMetric.set(0L);
    }

}
