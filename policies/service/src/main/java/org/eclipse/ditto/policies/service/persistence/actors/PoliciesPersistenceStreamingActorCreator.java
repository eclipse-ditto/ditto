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
package org.eclipse.ditto.policies.service.persistence.actors;

import java.util.regex.Pattern;

import org.eclipse.ditto.internal.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.internal.utils.persistence.mongo.DefaultPersistenceStreamingActor;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.PidWithSeqNr;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;

import akka.actor.Props;


/**
 * Creates an actor which streams information about persisted policies.
 */
public final class PoliciesPersistenceStreamingActorCreator {

    /**
     * The name of the created Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "persistenceStreamingActor";
    private static final Pattern PERSISTENCE_ID_PATTERN = Pattern.compile(PolicyPersistenceActor.PERSISTENCE_ID_PREFIX);

    private PoliciesPersistenceStreamingActorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for this PersistenceQueriesActor.
     *
     * @return the Akka configuration Props object.
     */
    public static Props props() {
        return DefaultPersistenceStreamingActor.props(PolicyTag.class,
                PoliciesPersistenceStreamingActorCreator::createElement,
                PoliciesPersistenceStreamingActorCreator::createPidWithSeqNr);
    }

    private static PolicyTag createElement(final PidWithSeqNr pidWithSeqNr) {
        final String id = PERSISTENCE_ID_PATTERN.matcher(pidWithSeqNr.getPersistenceId()).replaceFirst("");
        final PolicyId policyId = PolicyId.of(id);
        return PolicyTag.of(policyId, pidWithSeqNr.getSequenceNr());
    }

    private static PidWithSeqNr createPidWithSeqNr(final EntityIdWithRevision<?> connectionTag) {
        return new PidWithSeqNr(PolicyPersistenceActor.PERSISTENCE_ID_PREFIX + connectionTag.getEntityId(),
                connectionTag.getRevision());
    }

}
