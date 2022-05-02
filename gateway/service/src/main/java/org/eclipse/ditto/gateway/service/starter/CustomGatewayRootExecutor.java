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
package org.eclipse.ditto.gateway.service.starter;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;

/**
 * Executor for custom code in gateway root. Can be used i.e. to start custom actors.
 *
 * @since 3.0.0
 */
public abstract class CustomGatewayRootExecutor extends DittoExtensionPoint {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected CustomGatewayRootExecutor(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    /**
     * Execute custom custom code.
     * @param actorContext the context of the {@code GatewayRootActor}.
     */
    public abstract void execute(ActorContext actorContext);

    /**
     * Loads the implementation of {@code CustomGatewayRootExecutor} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code CustomGatewayRootExecutor} should be loaded.
     * @return the {@code CustomGatewayRootExecutor} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static CustomGatewayRootExecutor get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                actorSystem.settings().config())).getCustomRootExecutor();

        return new ExtensionId<>(implementation, CustomGatewayRootExecutor.class).get(actorSystem);
    }
}
