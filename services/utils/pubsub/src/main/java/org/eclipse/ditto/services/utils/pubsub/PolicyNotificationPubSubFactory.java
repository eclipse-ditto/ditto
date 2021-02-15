/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.services.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.signals.notifications.policies.PolicyNotification;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;

/**
 * Pub-sub factory for policy notifications.
 */
public final class PolicyNotificationPubSubFactory extends AbstractPubSubFactory<PolicyNotification<?>> {

    private static final DDataProvider PROVIDER = DDataProvider.of("policy-notification-aware");

    private static final AckExtractor<PolicyNotification<?>> ACK_EXTRACTOR =
            AckExtractor.of(PolicyNotification::getEntityId, PolicyNotification::getDittoHeaders);

    @SuppressWarnings({"unchecked"})
    private PolicyNotificationPubSubFactory(final ActorRefFactory actorRefFactory, final ActorSystem actorSystem) {
        super(actorRefFactory, actorSystem, (Class<PolicyNotification<?>>) (Object) PolicyNotification.class,
                new PolicyNotificationTopicExtractor(), PROVIDER, ACK_EXTRACTOR, DistributedAcks.empty());
    }

    /**
     * Create a pubsub factory for thing events ignoring shard ID topics.
     *
     * @param actorRefFactory the factory with which to create the sub-supervisor actor.l
     * @param system the actor system.
     * @return the thing event pub-sub factory.
     */
    public static PolicyNotificationPubSubFactory of(final ActorRefFactory actorRefFactory, final ActorSystem system) {
        return new PolicyNotificationPubSubFactory(actorRefFactory, system);
    }
}
