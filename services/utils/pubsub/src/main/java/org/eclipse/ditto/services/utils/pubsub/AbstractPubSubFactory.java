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

import org.eclipse.ditto.services.utils.ddata.DistributedData;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.actors.PubSupervisor;
import org.eclipse.ditto.services.utils.pubsub.actors.SubSupervisor;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.compressed.CompressedDData;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Creator of pub-sub access. Should not be instantiated more than once per instance.
 *
 * @param <T> type of messages.
 */
public abstract class AbstractPubSubFactory<T> implements PubSubFactory<T> {

    protected final ActorSystem actorSystem;
    protected final Class<T> messageClass;
    protected final PubSubTopicExtractor<T> topicExtractor;

    protected final DistributedDataConfig ddataConfig;
    protected final DData<?, ?> ddata;

    /**
     * Create a pub-sub factory.
     *
     * @param actorSystem the actor system.
     * @param clusterRole the role of cluster members participating in the pub-sub.
     * @param messageClass the class of messages to publish and subscribe for.
     * @param topicExtractor a function extracting from each message the topics it was published at.
     */
    protected AbstractPubSubFactory(final ActorSystem actorSystem, final String clusterRole,
            final Class<T> messageClass, final PubSubTopicExtractor<T> topicExtractor) {

        this(actorSystem, clusterRole, messageClass, messageClass.getCanonicalName(), topicExtractor);
    }

    /**
     * Create a pub-sub factory with non-default distributed data key.
     *
     * @param actorSystem the actor system.
     * @param clusterRole the role of cluster members participating in the pub-sub.
     * @param messageClass the class of messages to publish and subscribe for.
     * @param ddataKey the key of the distributed topic Bloom filters.
     * @param topicExtractor a function extracting from each message the topics it was published at.
     */
    protected AbstractPubSubFactory(final ActorSystem actorSystem,
            final String clusterRole,
            final Class<T> messageClass,
            final String ddataKey,
            final PubSubTopicExtractor<T> topicExtractor) {

        this.actorSystem = actorSystem;
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;

        final String replicatorName = ddataKey + "-replicator";
        ddataConfig = DistributedData.createConfig(actorSystem, replicatorName, clusterRole);

        final PubSubConfig config = PubSubConfig.of(actorSystem);
        ddata = CompressedDData.of(actorSystem, ddataConfig, ddataKey, config);
    }

    @Override
    public DistributedPub<T> startDistributedPub() {
        final String pubSupervisorName = messageClass.getSimpleName() + "PubSupervisor";
        final Props pubSupervisorProps = PubSupervisor.props(ddata);
        final ActorRef pubSupervisor = actorSystem.actorOf(pubSupervisorProps, pubSupervisorName);
        return DistributedPub.of(pubSupervisor, topicExtractor);
    }

    @Override
    public DistributedSub startDistributedSub() {
        final String subSupervisorName = messageClass.getSimpleName() + "SubSupervisor";
        final Props subSupervisorProps = SubSupervisor.props(messageClass, topicExtractor, ddata);
        final ActorRef subSupervisor = actorSystem.actorOf(subSupervisorProps, subSupervisorName);
        return DistributedSub.of(ddataConfig, subSupervisor);
    }
}
