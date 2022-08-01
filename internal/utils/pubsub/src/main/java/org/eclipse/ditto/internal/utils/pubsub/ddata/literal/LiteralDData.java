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

import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataWriter;

import akka.actor.ActorSystem;
import akka.actor.Address;

/**
 * Access to distributed data of literal topics.
 */
public final class LiteralDData implements DData<Address, String, LiteralUpdate> {

    private final LiteralDDataHandler handler;

    private LiteralDData(final LiteralDDataHandler handler) {
        this.handler = handler;
    }

    /**
     * Start distributed-data replicator for compressed topics under an actor system's user guardian using the default
     * dispatcher.
     *
     * @param system the actor system.
     * @param provider the ddata extension provider.
     * @return access to the distributed data.
     */
    public static LiteralDData of(final ActorSystem system, final AbstractConfigAwareDDataProvider provider) {
        return new LiteralDData(provider.get(system));
    }

    /**
     * Package an existing distributed data extension.
     *
     * @param extension the ddata extension.
     * @return access to the distributed data.
     */
    public static LiteralDData of(final LiteralDDataHandler extension) {
        return new LiteralDData(extension);
    }

    @Override
    public DDataReader<Address, String> getReader() {
        return handler;
    }

    @Override
    public DDataWriter<Address, LiteralUpdate> getWriter() {
        return handler;
    }

    @Override
    public DistributedDataConfig getConfig() {
        return handler.getConfig();
    }

}
