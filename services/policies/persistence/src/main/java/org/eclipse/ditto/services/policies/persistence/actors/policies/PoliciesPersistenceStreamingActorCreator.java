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
package org.eclipse.ditto.services.policies.persistence.actors.policies;

import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicyPersistenceActor;
import org.eclipse.ditto.services.utils.persistence.mongo.DefaultPersistenceStreamingActor;
import org.eclipse.ditto.services.utils.persistence.mongo.streaming.PidWithSeqNr;

import com.typesafe.config.Config;

import akka.actor.Props;


/**
 * Creates an actor which streams information about persisted policies.
 */
public final class PoliciesPersistenceStreamingActorCreator {

    /**
     * The name of the created Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "persistenceStreamingActor";

    private PoliciesPersistenceStreamingActorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for this PersistenceQueriesActor.
     *
     * @param config the actor system configuration.
     * @param streamingCacheSize the size of the streaming cache.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Config config, final int streamingCacheSize) {
        return DefaultPersistenceStreamingActor.props(PolicyTag.class, config, streamingCacheSize,
                PoliciesPersistenceStreamingActorCreator::createElement);
    }

    private static PolicyTag createElement(final PidWithSeqNr pidWithSeqNr) {
        final String id = pidWithSeqNr.getPersistenceId()
                .replaceFirst(PolicyPersistenceActor.PERSISTENCE_ID_PREFIX, "");
        return PolicyTag.of(id, pidWithSeqNr.getSequenceNr());
    }
}
