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
package org.eclipse.ditto.internal.utils.pubsubpolicies;

import org.eclipse.ditto.internal.utils.pubsub.AbstractPubSubFactory;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;

/**
 * Pub-sub factory for policy announcements.
 */
public final class PolicyAnnouncementPubSubFactory extends AbstractPubSubFactory<PolicyAnnouncement<?>> {

    private static final DDataProvider PROVIDER = DDataProvider.of("policy-announcement-aware");

    private static final AckExtractor<PolicyAnnouncement<?>> ACK_EXTRACTOR =
            AckExtractor.of(PolicyAnnouncement::getEntityId, PolicyAnnouncement::getDittoHeaders);

    @SuppressWarnings({"unchecked"})
    private PolicyAnnouncementPubSubFactory(final ActorRefFactory actorRefFactory, final ActorSystem actorSystem) {
        super(actorRefFactory, actorSystem, (Class<PolicyAnnouncement<?>>) (Object) PolicyAnnouncement.class,
                new PolicyAnnouncementTopicExtractor(), PROVIDER, ACK_EXTRACTOR, DistributedAcks.empty(actorSystem));
    }

    /**
     * Create a pubsub factory for thing events ignoring shard ID topics.
     *
     * @param actorRefFactory the factory with which to create the sub-supervisor actor.
     * @param system the actor system.
     * @return the thing event pub-sub factory.
     */
    public static PolicyAnnouncementPubSubFactory of(final ActorRefFactory actorRefFactory, final ActorSystem system) {
        return new PolicyAnnouncementPubSubFactory(actorRefFactory, system);
    }

}
