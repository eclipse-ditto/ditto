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
package org.eclipse.ditto.edge.api.dispatching;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;

import akka.actor.ActorSystem;

/**
 * Determines the default namespace based on the given signal.
 */
public interface DefaultNamespaceProvider extends DittoExtensionPoint {

    /**
     * @param createThing The command that requires a default namespace.
     * @return The default namespace.
     */
    String getDefaultNamespace(CreateThing createThing);

    /**
     * @param createPolicy The command that requires a default namespace.
     * @return The default namespace.
     */
    String getDefaultNamespace(CreatePolicy createPolicy);

    static DefaultNamespaceProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<DefaultNamespaceProvider> {

        private static final String CONFIG_PATH = "ditto.entity-creation.default-namespace-provider";
        private static final ExtensionId INSTANCE = new ExtensionId(DefaultNamespaceProvider.class);

        private ExtensionId(final Class<DefaultNamespaceProvider> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }
}
