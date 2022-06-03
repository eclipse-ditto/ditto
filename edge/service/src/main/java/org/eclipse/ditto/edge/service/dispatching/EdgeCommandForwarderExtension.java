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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;

import akka.actor.AbstractActor.Receive;
import akka.actor.ActorContext;
import akka.actor.ActorSystem;

/**
 * This extension allows to extend commands that are forwarded from the edges of Ditto to other microservices by
 * handling them in the {@link Receive} provided by {@link #getReceiveExtension(ActorContext)}.
 */
public interface EdgeCommandForwarderExtension extends DittoExtensionPoint {

    static EdgeCommandForwarderExtension get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * Builds the receive extension to allow custom handling of messages.
     * This receive will be applied BEFORE the default receives of {@link EdgeCommandForwarderActor}, so it's possible
     * to overwrite the default handling.
     *
     * @param actorContext can be used for example to determine the original sender of a message.
     * @return The desired receive extension.
     */
    Receive getReceiveExtension(ActorContext actorContext);

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<EdgeCommandForwarderExtension> {

        private static final String CONFIG_PATH = "ditto.edge-command-forwarder-extension";
        private static final ExtensionId INSTANCE = new ExtensionId(EdgeCommandForwarderExtension.class);

        private ExtensionId(final Class<EdgeCommandForwarderExtension> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
