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
package org.eclipse.ditto.services.utils.pubsub;

import org.eclipse.ditto.services.utils.ddata.DistributedDataConfigReader;
import org.eclipse.ditto.services.utils.pubsub.actors.PubSupervisor;
import org.eclipse.ditto.services.utils.pubsub.actors.SubSupervisor;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.TopicBloomFilters;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Creator of pub-sub access. Should not be instantiated more than once per instance.
 *
 * @param <T> type of messages.
 */
public abstract class AbstractPubSubFactory<T> {

    private final ActorSystem actorSystem;
    private final Class<T> messageClass;
    private final PubSubTopicExtractor<T> topicExtractor;

    private final DistributedDataConfigReader ddataConfig;
    private final TopicBloomFilters topicBloomFilters;
    private final PubSubConfig config;

    /**
     * Create a pub-sub factory.
     *
     * @param actorSystem the actor system.
     * @param clusterRole the role of cluster members participating in the pub-sub.
     * @param messageClass the class of messages to publish and subscribe for.
     * @param topicExtractor a function extracting from each message the topics it was published at.
     * @param config the pub-sub configuration.
     */
    protected AbstractPubSubFactory(final ActorSystem actorSystem, final String clusterRole,
            final Class<T> messageClass, final PubSubTopicExtractor<T> topicExtractor, final PubSubConfig config) {

        this.actorSystem = actorSystem;
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;
        this.config = config;

        final String replicatorName = messageClass.getSimpleName() + "PubSubReplicator";
        ddataConfig = DistributedDataConfigReader.of(actorSystem, replicatorName, clusterRole);
        topicBloomFilters = TopicBloomFilters.of(actorSystem, ddataConfig, messageClass.getCanonicalName());
    }

    /**
     * Start a pub-supervisor under the user guardian. Will fail when called a second time in an actor system.
     *
     * @return access to distributed publication.
     */
    public DistributedPub<T> startPubAccess() {
        final String pubSupervisorName = messageClass.getSimpleName() + "PubSupervisor";
        final Props pubSupervisorProps = PubSupervisor.props(config, topicBloomFilters);
        final ActorRef pubSupervisor = actorSystem.actorOf(pubSupervisorProps, pubSupervisorName);
        return DistributedPub.of(pubSupervisor, topicExtractor);
    }

    /**
     * Start a sub-supervisor under the user guardian. Will fail when called a second time in an actor system.
     *
     * @return access to distributed subscription.
     */
    public DistributedSub startSubAccess() {
        final String subSupervisorName = messageClass.getSimpleName() + "SubSupervisor";
        final Props subSupervisorProps = SubSupervisor.props(config, messageClass, topicExtractor, topicBloomFilters);
        final ActorRef subSupervisor = actorSystem.actorOf(subSupervisorProps, subSupervisorName);
        return DistributedSub.of(ddataConfig, subSupervisor);
    }
}
