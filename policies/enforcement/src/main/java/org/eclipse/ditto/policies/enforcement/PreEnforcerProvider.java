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
package org.eclipse.ditto.policies.enforcement;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;
import java.util.function.BinaryOperator;

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.DittoExtensionPoint;

import akka.actor.ActorSystem;

/**
 * Extension to provide the pre-enforcer for a service.
 *
 * @since 3.0.0
 */
public interface PreEnforcerProvider extends DittoExtensionPoint{

    /**
     * Applies the pre-enforcement to the signal.
     * @param signal the signal the pre-enforcement is executed for.
     */
    <T extends DittoHeadersSettable<?>> CompletionStage<T> apply(T signal);

    /**
     * Loads the implementation of {@code PreEnforcerProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code PreEnforcerProvider} should be loaded.
     * @return the {@code PreEnforcerProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static PreEnforcerProvider get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<PreEnforcerProvider> {

        private static final String CONFIG_PATH = "ditto.pre-enforcer-provider";
        private static final ExtensionId INSTANCE = new ExtensionId(PreEnforcerProvider.class);

        private ExtensionId(final Class<PreEnforcerProvider> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }
}
