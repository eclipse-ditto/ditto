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

import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.LocalSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.TopicBloomFiltersWriter;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of actors dealing with subscriptions.
 * <pre>
 * {@code
 *    SubSupervisor
 *          +
 *          |
 *          |supervises one-for-many
 *          +---------------------------+
 *          |                           |
 *          v                           v
 *       SubUpdater +-----------> PubSubSubscriber
 *       +           update
 *       |           local
 *       |           subscriptions
 *       |
 *       |
 *       |
 *       |write with highest requested consistency
 *       v
 *    DDataReplicator
 * }
 * </pre>
 */
public final class SubSupervisor<T> extends AbstractPubSubSupervisor {

    private final Class<T> messageClass;
    private final PubSubTopicExtractor<T> topicExtractor;
    private final TopicBloomFiltersWriter topicBloomFiltersWriter;

    @Nullable private ActorRef updater;

    private SubSupervisor(final PubSubConfig pubSubConfig, final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor,
            final TopicBloomFiltersWriter topicBloomFiltersWriter) {
        super(pubSubConfig);
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;
        this.topicBloomFiltersWriter = topicBloomFiltersWriter;
    }

    /**
     * Create Props object for this actor.
     *
     * @param pubSubConfig the pub-sub config.
     * @param messageClass class of messages.
     * @param topicExtractor extractor of topics from messages.
     * @param topicBloomFiltersWriter write access to distributed topic Bloom filters.
     * @param <T> type of messages.
     * @return the Props object.
     */
    public static <T> Props props(final PubSubConfig pubSubConfig, final Class<T> messageClass,
            final PubSubTopicExtractor<T> topicExtractor, final TopicBloomFiltersWriter topicBloomFiltersWriter) {

        return Props.create(SubSupervisor.class, pubSubConfig, messageClass, topicExtractor, topicBloomFiltersWriter);
    }

    @Override
    protected Receive createPubSubBehavior() {
        return ReceiveBuilder.create()
                .match(SubUpdater.Request.class, this::isUpdaterAvailable, this::request)
                .match(SubUpdater.Request.class, this::updaterUnavailable)
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
        final LocalSubscriptions localSubscriptions = LocalSubscriptions.of(getSeeds());
        final Props updaterProps =
                SubUpdater.props(config, subscriber, localSubscriptions, topicBloomFiltersWriter);
        updater = startChild(updaterProps, SubUpdater.ACTOR_NAME_PREFIX);
    }

    private boolean isUpdaterAvailable() {
        return updater != null;
    }

    @SuppressWarnings("ConstantConditions")
    private void request(final SubUpdater.Request request) {
        updater.tell(request, getSender());
    }

    private void updaterUnavailable(final SubUpdater.Request request) {
        log.error("SubUpdater unavailable. Dropping <{}>", request);
    }
}
