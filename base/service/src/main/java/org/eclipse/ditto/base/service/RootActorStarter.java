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

    String CONFIG_PATH = "ditto.root-actor-starter";

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
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH);
        return new ExtensionId<>(implementation, RootActorStarter.class).get(actorSystem);
    }
}
