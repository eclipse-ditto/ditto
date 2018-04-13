/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.updater.actors;

import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.streaming.DefaultStreamSupervisor;
import org.eclipse.ditto.services.utils.akka.streaming.StreamConsumerSettings;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;

/**
 * Creates an actor which is responsible for triggering a cyclic synchronization of all policies which changed within a
 * specified time period.
 */
public final class PoliciesStreamSupervisorCreator {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "policiesStreamSupervisor";
    @SuppressWarnings("squid:S1075")
    private static final String POLICIES_STREAM_PROVIDER_ACTOR_PATH = "/user/policiesRoot/persistenceStreamingActor";

    private PoliciesStreamSupervisorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates the props for {@link PoliciesStreamSupervisorCreator}.
     *
     * @param thingsUpdater the things updater actor
     * @param pubSubMediator the PubSub mediator Actor.
     * @param streamMetadataPersistence the {@link StreamMetadataPersistence} used to read and write stream metadata (is
     * used to remember the end time of the last stream after a re-start).
     * @param materializer the materializer for the akka actor system.
     * @param streamConsumerSettings The settings for stream consumption.
     * @return the props
     */
    public static Props props(final ActorRef thingsUpdater, final ActorRef pubSubMediator,
            final StreamMetadataPersistence streamMetadataPersistence, final Materializer materializer,
            final StreamConsumerSettings streamConsumerSettings,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence) {

        return DefaultStreamSupervisor.props(thingsUpdater, pubSubMediator,
                PolicyTag.class,
                policyTag -> toPolicyReferenceTags(policyTag, searchUpdaterPersistence),
                PoliciesStreamSupervisorCreator::mapStreamTriggerCommand,
                streamMetadataPersistence, materializer, streamConsumerSettings);
    }

    private static DistributedPubSubMediator.Send mapStreamTriggerCommand(
            final SudoStreamModifiedEntities sudoStreamModifiedEntities) {

        return new DistributedPubSubMediator.Send(POLICIES_STREAM_PROVIDER_ACTOR_PATH, sudoStreamModifiedEntities,
                true);
    }

    private static Source<Object, NotUsed> toPolicyReferenceTags(final PolicyTag policyTag,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence) {

        return searchUpdaterPersistence.getOutdatedThingIds(policyTag)
                .map(thingId -> PolicyReferenceTag.of(thingId, policyTag));
    }
}
