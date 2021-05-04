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
package org.eclipse.ditto.internal.utils.pubsub.ddata.literal;

import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.ddata.ORMultiMap;

/**
 * Abstract class of distributed data extension provider to be instantiated at user site.
 */
public abstract class AbstractConfigAwareDDataProvider
        extends DistributedData.AbstractDDataProvider<ORMultiMap<Address, String>, LiteralDDataHandler> {

    /**
     * Get the ddata extension's config from an actor system.
     *
     * @param actorSystem The actor system.
     * @return The ddata extension's config.
     */
    public abstract DistributedDataConfig getConfig(ActorSystem actorSystem);
}
