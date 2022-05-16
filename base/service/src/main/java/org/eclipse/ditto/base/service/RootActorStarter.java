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

import java.util.List;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;

/**
 * Extension to start custom child actors in root actor.
 *
 * @since 3.0.0
 */
public interface RootActorStarter extends DittoExtensionPoint {

    static final String CONFIG_PATH = "root-actor-starter";

    /**
     * Execute custom custom code.
     *
     * @param actorContext the context of the {@code GatewayRootActor}.
     */
    void execute(ActorContext actorContext);

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
        final DefaultScopedConfig dittoScoped = DefaultScopedConfig.dittoScoped(actorSystem.settings().config());
        final var implementation = dittoScoped.getString(CONFIG_PATH);

        return AkkaClassLoader.instantiate(actorSystem, RootActorStarter.class,
                implementation,
                List.of(ActorSystem.class),
                List.of(actorSystem));
    }
}
