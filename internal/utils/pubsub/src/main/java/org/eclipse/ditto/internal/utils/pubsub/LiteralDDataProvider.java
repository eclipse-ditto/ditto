/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub;

import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.AbstractConfigAwareDDataProvider;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralDDataHandler;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

/**
 * Literal DData provider.
 */
public final class LiteralDDataProvider extends AbstractConfigAwareDDataProvider {

    private final String clusterRole;
    private final String messageType;

    private LiteralDDataProvider(final String clusterRole, final String messageType) {
        this.clusterRole = clusterRole;
        this.messageType = messageType;
    }

    /**
     * Create a distributed data provider.
     *
     * @param clusterRole Cluster role where this provider start.
     * @param messageType Message type that uniquely identifies this provider.
     * @return the ddata provider.
     */
    public static LiteralDDataProvider of(final String clusterRole, final String messageType) {
        return new LiteralDDataProvider(clusterRole, messageType);
    }

    @Override
    public LiteralDDataHandler createExtension(final ExtendedActorSystem system) {
        return LiteralDDataHandler.create(system, getConfig(system), messageType);
    }

    @Override
    public DistributedDataConfig getConfig(final ActorSystem actorSystem) {
        return DistributedData.createConfig(actorSystem, messageType + "-replicator", clusterRole);
    }
}
