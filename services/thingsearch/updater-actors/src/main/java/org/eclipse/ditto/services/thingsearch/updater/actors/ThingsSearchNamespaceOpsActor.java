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

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.persistence.mongo.namespace.AbstractNamespaceOpsActor;
import org.eclipse.ditto.services.utils.persistence.mongo.namespace.NamespaceOps;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Cluster singleton to perform namespace operations on the search index.
 */
public final class ThingsSearchNamespaceOpsActor extends AbstractNamespaceOpsActor<String> {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "thingsSearchNamespaceOpsActor";

    private ThingsSearchNamespaceOpsActor(final ActorRef pubSubMediator, final NamespaceOps<String> namespaceOps) {
        super(pubSubMediator, namespaceOps);
    }

    /**
     * Create props for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param persistence the search updater persistence.
     * @return Props of this actor.
     */
    public static Props props(final ActorRef pubSubMediator, final ThingsSearchUpdaterPersistence persistence) {
        return Props.create(ThingsSearchNamespaceOpsActor.class,
                () -> new ThingsSearchNamespaceOpsActor(pubSubMediator, persistence));
    }

    @Override
    protected String getResourceType() {
        return ThingSearchCommand.RESOURCE_TYPE;
    }

    @Override
    protected Collection<String> selectNamespace(final String namespace) {
        return Collections.singletonList(namespace);
    }
}
