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
package org.eclipse.ditto.connectivity.service;

import java.util.List;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

/**
 * Executor for custom code in connectivity root. Can be used i.e. to start custom actors.
 *
 * @since 3.0.0
 */
public abstract class CustomConnectivityRootExecutor implements DittoExtensionPoint {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected CustomConnectivityRootExecutor(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Execute custom custom code.
     * @param actorContext the context of the {@code ConnectivityRootActor}.
     */
    public abstract void execute(ActorContext actorContext);

    /**
     * Loads the implementation of {@code CustomConnectivityRootExecutor} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code CustomConnectivityRootExecutor} should be loaded.
     * @return the {@code CustomConnectivityRootExecutor} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static CustomConnectivityRootExecutor get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    private static final class ExtensionId extends AbstractExtensionId<CustomConnectivityRootExecutor> {

        @Override
        public CustomConnectivityRootExecutor createExtension(final ExtendedActorSystem system) {
            final var implementation = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(
                    system.settings().config())).getCustomRootExecutor();

            return AkkaClassLoader.instantiate(system, CustomConnectivityRootExecutor.class,
                    implementation,
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }
}
