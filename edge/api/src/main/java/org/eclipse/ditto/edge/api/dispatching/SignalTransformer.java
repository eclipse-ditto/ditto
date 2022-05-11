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

import java.util.List;
import java.util.function.UnaryOperator;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

public abstract class SignalTransformer implements DittoExtensionPoint, UnaryOperator<Signal<?>> {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    private static final String CONFIG_PATH = "signal-transformer";

    protected final ActorSystem actorSystem;

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected SignalTransformer(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Loads the implementation of {@code SignalTransformer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SignalTransformer} should be loaded.
     * @return the {@code SignalTransformer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static SignalTransformer get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    private static final class ExtensionId extends AbstractExtensionId<SignalTransformer> {

        @Override
        public SignalTransformer createExtension(final ExtendedActorSystem system) {
            final var implementation = system.settings().config().getString(CONFIG_PATH);

            return AkkaClassLoader.instantiate(system, SignalTransformer.class,
                    implementation,
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }
}
