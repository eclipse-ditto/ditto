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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.AbstractOpsActor;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.NamespaceOps;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which performs ops on the search index.
 */
public final class ThingsSearchOpsActor extends AbstractOpsActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "thingsSearchOpsActor";

    private ThingsSearchOpsActor(final ActorRef pubSubMediator, final NamespaceOps namespaceOps) {
        super(pubSubMediator, ThingSearchCommand.RESOURCE_TYPE, namespaceOps, null);
    }

    /**
     * Create props for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param persistence the search updater persistence.
     * @return Props of this actor.
     */
    public static Props props(final ActorRef pubSubMediator, final ThingsSearchUpdaterPersistence persistence) {
        return Props.create(ThingsSearchOpsActor.class,
                () -> new ThingsSearchOpsActor(pubSubMediator, persistence));
    }

}
