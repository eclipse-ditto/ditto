/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsubthings;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.ConstantTopics;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.ReadSubjectExtractor;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorContext;

/**
 * Default implementation of {@link LiveSignalPub}.
 */
final class LiveSignalPubImpl implements LiveSignalPub {

    private final DistributedPub<ThingCommand<?>> liveCommandPub;
    private final DistributedPub<ThingEvent<?>> liveEventPub;
    private final DistributedPub<SignalWithEntityId<?>> messagePub;

    private LiveSignalPubImpl(
            final DistributedPub<ThingCommand<?>> liveCommandPub,
            final DistributedPub<ThingEvent<?>> liveEventPub,
            final DistributedPub<SignalWithEntityId<?>> messagePub) {
        this.liveCommandPub = liveCommandPub;
        this.liveEventPub = liveEventPub;
        this.messagePub = messagePub;
    }

    /**
     * Start a live signal pub in an actor system.
     *
     * @param context context of the actor under which the pub and sub supervisors are started.
     * @param distributedAcks the distributed acks interface.
     * @return the live signal pub.
     */
    static LiveSignalPubImpl of(final ActorContext context, final DistributedAcks distributedAcks) {
        final DistributedPub<?> distributedPub =
                LiveSignalPubSubFactory.of(context, distributedAcks).startDistributedPub();
        final DistributedPub<ThingCommand<?>> liveCommandPub =
                distributedPub.withTopicExtractor(getTopicExtractor(StreamingType.LIVE_COMMANDS));
        final DistributedPub<ThingEvent<?>> liveEventPub =
                distributedPub.withTopicExtractor(getTopicExtractor(StreamingType.LIVE_EVENTS));
        final DistributedPub<SignalWithEntityId<?>> messagePub =
                distributedPub.withTopicExtractor(getTopicExtractor(StreamingType.MESSAGES));
        return new LiveSignalPubImpl(liveCommandPub, liveEventPub, messagePub);
    }

    @Override
    public DistributedPub<ThingCommand<?>> command() {
        return liveCommandPub;
    }

    @Override
    public DistributedPub<ThingEvent<?>> event() {
        return liveEventPub;
    }

    @Override
    public DistributedPub<SignalWithEntityId<?>> message() {
        return messagePub;
    }

    private static <T extends WithDittoHeaders> PubSubTopicExtractor<T> getTopicExtractor(
            final StreamingType streamingType) {

        return ReadSubjectExtractor.<T>of().with(ConstantTopics.of(streamingType.getDistributedPubSubTopic()));
    }

}
