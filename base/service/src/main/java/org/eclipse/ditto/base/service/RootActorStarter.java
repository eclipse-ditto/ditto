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
package org.eclipse.ditto.base.service;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import akka.actor.ActorSystem;

/**
 * Extension to start custom root actors in service.
 *
 * @since 3.0.0
 */
public interface RootActorStarter extends DittoExtensionPoint {

    /**
     * Execute custom custom code.
     */
    void execute();

    /**
     * Loads the implementation of {@code RootActorStarter} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code RootActorStarter} should be loaded.
     * @return the {@code RootActorStarter} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static RootActorStarter get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<RootActorStarter> {

        private static final String CONFIG_PATH = "ditto.root-actor-starter";
        private static final ExtensionId INSTANCE = new ExtensionId(RootActorStarter.class);

        private ExtensionId(final Class<RootActorStarter> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
