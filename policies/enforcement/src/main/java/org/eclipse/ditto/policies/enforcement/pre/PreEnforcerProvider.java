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
package org.eclipse.ditto.policies.enforcement.pre;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint.ExtensionId.ExtensionIdConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Extension to provide the Pre-Enforcers for a service.
 */
public final class PreEnforcerProvider implements DittoExtensionPoint {

    private static final String PRE_ENFORCEMENT_TIMER = "pre_enforcement";
    private static final String ENFORCEMENT_TIMER_TAG_CHANNEL = "channel";
    private static final String ENFORCEMENT_TIMER_TAG_RESOURCE = "resource";
    private static final String ENFORCEMENT_TIMER_TAG_CATEGORY = "category";
    private static final String ENFORCEMENT_TIMER_TAG_OUTCOME = "outcome";
    private static final String ENFORCEMENT_TIMER_TAG_OUTCOME_FAIL = "fail";
    private static final String ENFORCEMENT_TIMER_TAG_OUTCOME_SUCCESS = "success";
    private static final String PRE_ENFORCERS = "pre-enforcers";
    private final List<PreEnforcer> preEnforcers;

    @SuppressWarnings("unused")
    private PreEnforcerProvider(final ActorSystem actorSystem, final Config config) {
        final DittoExtensionIds dittoExtensionIds = DittoExtensionIds.get(actorSystem);
        preEnforcers = config.getList(PRE_ENFORCERS)
                .stream()
                .map(configValue -> ExtensionIdConfig.of(PreEnforcer.class, configValue))
                .map(extensionIdConfig -> dittoExtensionIds.computeIfAbsent(extensionIdConfig,
                        PreEnforcerExtensionId::new))
                .map(extensionId -> extensionId.get(actorSystem))
                .toList();
    }

    /**
     * Applies the pre-enforcement to the signal.
     *
     * @param signal the signal the pre-enforcement is executed for.
     */
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        final StartedTimer timer = createTimer(signal);
        CompletionStage<Signal<?>> prior = CompletableFuture.completedStage(signal);
        for (final PreEnforcer preEnforcer : preEnforcers) {
            prior = prior.thenCompose(preEnforcer);
        }
        return prior.whenComplete((result, error) -> {
            if (result instanceof Command<?> command) {
                timer.tag(ENFORCEMENT_TIMER_TAG_CATEGORY, command.getCategory().name().toLowerCase());
            }
            stopTimer(timer).accept(result, error);
        });
    }

    /**
     * Loads the implementation of {@code PreEnforcerProvider} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code PreEnforcerProvider} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code PreEnforcerProvider} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    public static PreEnforcerProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    private StartedTimer createTimer(final WithDittoHeaders withDittoHeaders) {
        final PreparedTimer expiringTimer = DittoMetrics.timer(PRE_ENFORCEMENT_TIMER);

        withDittoHeaders.getDittoHeaders().getChannel().ifPresent(channel ->
                expiringTimer.tag(ENFORCEMENT_TIMER_TAG_CHANNEL, channel)
        );
        if (withDittoHeaders instanceof WithResource withResource) {
            expiringTimer.tag(ENFORCEMENT_TIMER_TAG_RESOURCE, withResource.getResourceType());
        }
        if (withDittoHeaders instanceof Command<?> command) {
            expiringTimer.tag(ENFORCEMENT_TIMER_TAG_CATEGORY, command.getCategory().name().toLowerCase());
        }
        return expiringTimer.start();
    }

    private BiConsumer<Object, Throwable> stopTimer(final StartedTimer timerToStop) {
        return (result, error) -> {
            final String outcome = error != null ?
                    ENFORCEMENT_TIMER_TAG_OUTCOME_FAIL :
                    ENFORCEMENT_TIMER_TAG_OUTCOME_SUCCESS;
            if (timerToStop.isRunning()) {
                timerToStop.tag(ENFORCEMENT_TIMER_TAG_OUTCOME, outcome)
                        .stop();
            }
        };
    }

    private static final class ExtensionId extends DittoExtensionPoint.ExtensionId<PreEnforcerProvider> {

        private static final String CONFIG_KEY = "pre-enforcer-provider";

        private ExtensionId(final ExtensionIdConfig<PreEnforcerProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<PreEnforcerProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(PreEnforcerProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

    private static final class PreEnforcerExtensionId extends DittoExtensionPoint.ExtensionId<PreEnforcer> {

        PreEnforcerExtensionId(final ExtensionIdConfig<PreEnforcer> extensionIdConfig) {
            super(extensionIdConfig);
        }

        @Override
        protected String getConfigKey() {
            throw new UnsupportedOperationException("PreEnforcers do not support an individual config key. " +
                    "They should be configured in the ditto.extensions.pre-enforcer-provider.extension-config " +
                    "pre-enforcers list.");
        }

    }

}
