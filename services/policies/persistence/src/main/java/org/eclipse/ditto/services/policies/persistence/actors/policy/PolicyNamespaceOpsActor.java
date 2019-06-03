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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import org.eclipse.ditto.services.utils.persistence.mongo.namespace.AbstractEventSourceNamespaceOpsActor;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Namespace operations for the event-sourcing persistence of policies.
 */
public final class PolicyNamespaceOpsActor extends AbstractEventSourceNamespaceOpsActor {

    public static final String ACTOR_NAME = "policyNamespaceOps";

    @SuppressWarnings("unused")
    private PolicyNamespaceOpsActor(final ActorRef pubSubMediator) {

        super(pubSubMediator);
    }

    /**
     * Create Props of this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @return a Props object.
     */
    public static Props props(final ActorRef pubSubMediator) {
        return Props.create(PolicyNamespaceOpsActor.class, pubSubMediator);
    }

    @Override
    protected String getPersistenceIdPrefix() {
        return PolicyPersistenceActor.PERSISTENCE_ID_PREFIX;
    }

    @Override
    protected String getJournalPluginId() {
        return PolicyPersistenceActor.JOURNAL_PLUGIN_ID;
    }

    @Override
    protected String getSnapshotPluginId() {
        return PolicyPersistenceActor.SNAPSHOT_PLUGIN_ID;
    }

    @Override
    protected String getResourceType() {
        return PolicyCommand.RESOURCE_TYPE;
    }

}
