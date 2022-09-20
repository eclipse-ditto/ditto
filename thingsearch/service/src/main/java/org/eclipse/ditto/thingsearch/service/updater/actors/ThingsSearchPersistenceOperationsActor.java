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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import org.eclipse.ditto.internal.utils.persistence.operations.AbstractPersistenceOperationsActor;
import org.eclipse.ditto.internal.utils.persistence.operations.NamespacePersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.operations.PersistenceOperationsConfig;
import org.eclipse.ditto.thingsearch.model.ThingSearchConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.ThingsSearchUpdaterPersistence;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which performs ops on the search index.
 */
public final class ThingsSearchPersistenceOperationsActor extends AbstractPersistenceOperationsActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "thingsSearchOpsActor";

    private ThingsSearchPersistenceOperationsActor(final ActorRef pubSubMediator,
            final NamespacePersistenceOperations namespaceOps,
            final PersistenceOperationsConfig persistenceOperationsConfig) {

        super(pubSubMediator,
                ThingSearchConstants.ENTITY_TYPE,
                namespaceOps,
                null,
                persistenceOperationsConfig);
    }

    /**
     * Create props for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param persistence the search updater persistence.
     * @param persistenceOperationsConfig the Akka config.
     * @return Props of this actor.
     */
    public static Props props(final ActorRef pubSubMediator, final ThingsSearchUpdaterPersistence persistence,
            final PersistenceOperationsConfig persistenceOperationsConfig) {
        return Props.create(ThingsSearchPersistenceOperationsActor.class,
                () -> new ThingsSearchPersistenceOperationsActor(pubSubMediator, persistence,
                        persistenceOperationsConfig));
    }

    @Override
    public String getActorName() {
        return ACTOR_NAME;
    }

}
