/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Creates a connection priority provider based on the connection persistence actor and its logger.
 */
public interface ConnectionPriorityProviderFactory extends DittoExtensionPoint {

    /**
     * Creates a connection priority provider based on the connection persistence actor and its logger.
     *
     * @param connectionPersistenceActor the connection persistence actor.
     * @param log the logger of the connection persistence actor.
     * @return the new provider.
     */
    ConnectionPriorityProvider newProvider(ActorRef connectionPersistenceActor, DittoDiagnosticLoggingAdapter log);


    /**
     * Loads the implementation of {@code ConnectionPriorityProviderFactory} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code ConnectionPriorityProviderFactory} should be loaded.
     * @return the {@code ConnectionPriorityProviderFactory} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static ConnectionPriorityProviderFactory get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ConnectionPriorityProviderFactory> {

        private static final String CONFIG_PATH = "ditto.connectivity.connection.connection-priority-provider-factory";
        private static final ExtensionId INSTANCE = new ExtensionId(ConnectionPriorityProviderFactory.class);

        private ExtensionId(final Class<ConnectionPriorityProviderFactory> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
