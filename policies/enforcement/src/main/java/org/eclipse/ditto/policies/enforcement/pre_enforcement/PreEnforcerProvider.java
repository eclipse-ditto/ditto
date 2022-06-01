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
package org.eclipse.ditto.policies.enforcement.pre_enforcement;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

/**
 * Extension to provide the pre-enforcer for a service.
 *
 * @since 3.0.0
 */
public final class PreEnforcerProvider implements DittoExtensionPoint {

    private static final String CONFIG_PATH = "ditto.pre-enforcers";
    private final List<PreEnforcer> preEnforcers;

    private PreEnforcerProvider(final ActorSystem actorSystem) {
        preEnforcers = actorSystem.settings().config().getStringList(CONFIG_PATH)
                .stream()
                .map(path -> PreEnforcer.ExtensionId.get(path, actorSystem))
                .map(extensionId -> extensionId.get(actorSystem))
                .toList();
    }

    /**
     * Applies the pre-enforcement to the signal.
     *
     * @param signal the signal the pre-enforcement is executed for.
     */
    public CompletionStage<DittoHeadersSettable<?>> apply(final DittoHeadersSettable<?> signal) {
        CompletionStage<DittoHeadersSettable<?>> prior = null;
        for (final PreEnforcer preEnforcer : preEnforcers) {
            if (preEnforcer.equals(preEnforcers.get(0))) {
                prior = preEnforcer.apply(signal);
            } else {
                prior = prior.thenCompose(preEnforcer);
            }
        }
        return prior;
    }

    /**
     * Loads the implementation of {@code PreEnforcerProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code PreEnforcerProvider} should be loaded.
     * @return the {@code PreEnforcerProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static PreEnforcerProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    private static final class ExtensionId extends AbstractExtensionId<PreEnforcerProvider> {

        private static final ExtensionId INSTANCE = new ExtensionId();

        @Override
        public PreEnforcerProvider createExtension(final ExtendedActorSystem system) {

            return AkkaClassLoader.instantiate(system, PreEnforcerProvider.class,
                    PreEnforcerProvider.class.getCanonicalName(),
                    List.of(ActorSystem.class),
                    List.of(system));
        }

    }
}
