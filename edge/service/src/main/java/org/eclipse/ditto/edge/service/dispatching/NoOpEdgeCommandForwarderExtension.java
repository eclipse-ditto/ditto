/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.dispatching;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;

import akka.actor.AbstractActor.Receive;
import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.japi.pf.ReceiveBuilder;

/**
 * No-operation implementation of {@link EdgeCommandForwarderExtension}.
 */
@Immutable
public final class NoOpEdgeCommandForwarderExtension implements EdgeCommandForwarderExtension {

    /**
     * Constructs a new instance of NoOpEdgeCommandForwarderExtension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the config the extension is configured.
     */
    @SuppressWarnings("unused")
    public NoOpEdgeCommandForwarderExtension(final ActorSystem actorSystem, final Config config) {
        // no-op
    }

    @Override
    public Receive getReceiveExtension(final ActorContext actorContext) {
        return ReceiveBuilder.create().build();
    }

}
