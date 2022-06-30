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
package org.eclipse.ditto.connectivity.service.enforcement;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.connectivity.model.ConnectionId;

import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Responsible to provide the props of the actor that should be used to enforce connection commands.
 */
public interface ConnectionEnforcerActorPropsFactory extends DittoExtensionPoint {

    /**
     * @param connectionId the ID of the connection for which the actor should enforce the commands.
     * @return the enforcer actor props.
     */
    Props get(ConnectionId connectionId);

    static ConnectionEnforcerActorPropsFactory get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<ConnectionEnforcerActorPropsFactory> {

        private static final String CONFIG_PATH = "ditto.connectivity.connection.enforcer-actor-props-factory";
        private static final ExtensionId INSTANCE = new ExtensionId(ConnectionEnforcerActorPropsFactory.class);

        private ExtensionId(final Class<ConnectionEnforcerActorPropsFactory> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
