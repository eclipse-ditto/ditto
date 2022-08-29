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
package org.eclipse.ditto.base.service.signaltransformer;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint.ExtensionId.ExtensionIdConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import akka.actor.ActorSystem;

public final class SignalTransformers implements DittoExtensionPoint, SignalTransformer {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(SignalTransformers.class);
    private static final String SIGNAL_TRANSFORMERS = "signal-transformers";
    private final List<SignalTransformer> transformers;

    @SuppressWarnings("unused")
    private SignalTransformers(final ActorSystem actorSystem, final Config config) {
        final DittoExtensionIds dittoExtensionIds = DittoExtensionIds.get(actorSystem);
        final List<ConfigValue> configuredTransformers =
                config.hasPath(SIGNAL_TRANSFORMERS) ? config.getList(SIGNAL_TRANSFORMERS) : List.of();
        transformers = configuredTransformers
                .stream()
                .map(configValue -> ExtensionIdConfig.of(SignalTransformer.class, configValue))
                .map(extensionIdConfig -> dittoExtensionIds.computeIfAbsent(extensionIdConfig,
                        SignalTransformerExtensionId::new))
                .map(extensionId -> extensionId.get(actorSystem))
                .toList();
        final String loadedSignalTransformersAsCommaSeparatedList =
                transformers.stream().map(Object::getClass).map(Class::getName).collect(Collectors.joining(","));
        LOGGER.info("Instantiated the following signal transformers: {}.",
                loadedSignalTransformersAsCommaSeparatedList);
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        CompletionStage<Signal<?>> prior = CompletableFuture.completedStage(signal);
        for (final SignalTransformer signalTransformer : transformers) {
            prior = prior.thenCompose(signalTransformer);
        }
        return prior.whenComplete((result, error) -> {
            if (error != null) {
                LOGGER.withCorrelationId(signal)
                        .debug("Error happened during signal transforming.", error);
            } else {
                LOGGER.withCorrelationId(signal)
                        .debug("Signal transforming of <{}> resulted in <{}>.", signal, result);
            }
        });
    }

    /**
     * Loads all implementation of {@code SignalTransformer} which is configured for the {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SignalTransformers} should be loaded.
     * @return the {@code SignalTransformers}.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static SignalTransformers get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    private static final class ExtensionId extends DittoExtensionPoint.ExtensionId<SignalTransformers> {

        private static final String CONFIG_KEY = "signal-transformers-provider";

        private ExtensionId(final ExtensionIdConfig<SignalTransformers> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SignalTransformers> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SignalTransformers.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

    private static final class SignalTransformerExtensionId extends DittoExtensionPoint.ExtensionId<SignalTransformer> {

        SignalTransformerExtensionId(final ExtensionIdConfig<SignalTransformer> extensionIdConfig) {
            super(extensionIdConfig);
        }

        @Override
        protected String getConfigKey() {
            throw new UnsupportedOperationException("SignalTransformers do not support an individual config key. " +
                    "They should be configured in the " +
                    "ditto.extensions.signal-transformers-provider.extension-config.signal-transformers list.");
        }

    }

}
