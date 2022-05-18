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

import akka.actor.ActorContext;
import akka.actor.ActorSystem;

/**
 * Extension to start custom child actors in root actor.
 *
 * @since 3.0.0
 */
public interface RootChildActorStarter extends DittoExtensionPoint {

    static final String CONFIG_PATH = "ditto.root-child-actor-starter";

    /**
     * Execute custom custom code.
     *
     * @param actorContext the context of the {@code RootActor}.
     */
    void execute(ActorContext actorContext);

    /**
     * Loads the implementation of {@code RootChildActorStarter} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code RootChildActorStarter} should be loaded.
     * @return the {@code RootChildActorStarter} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static RootChildActorStarter get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH);
        return new ExtensionId<>(implementation, RootChildActorStarter.class).get(actorSystem);
    }
}
