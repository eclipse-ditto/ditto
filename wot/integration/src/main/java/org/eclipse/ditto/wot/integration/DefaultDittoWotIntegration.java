/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.AbstractExtensionId;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.wot.api.config.DefaultWotConfig;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.generator.WotThingDescriptionGenerator;
import org.eclipse.ditto.wot.api.generator.WotThingModelExtensionResolver;
import org.eclipse.ditto.wot.api.generator.WotThingSkeletonGenerator;
import org.eclipse.ditto.wot.api.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.api.validator.WotThingModelValidator;

/**
 * Default Ditto specific implementation of {@link DittoWotIntegration} and Pekko extension.
 */
@Immutable
final class DefaultDittoWotIntegration implements DittoWotIntegration {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DefaultDittoWotIntegration.class);

    private final WotConfig wotConfig;
    private final WotThingModelFetcher thingModelFetcher;
    private final WotThingModelExtensionResolver thingModelExtensionResolver;
    private final WotThingModelResolver thingModelResolver;
    private final WotThingDescriptionGenerator thingDescriptionGenerator;
    private final WotThingSkeletonGenerator thingSkeletonGenerator;
    private final WotThingModelValidator thingModelValidator;

    private DefaultDittoWotIntegration(final ActorSystem actorSystem, final WotConfig wotConfig) {
        this.wotConfig = checkNotNull(wotConfig, "wotConfig");
        LOGGER.info("Initializing DefaultDittoWotIntegration with config: {}", wotConfig);

        final Executor executor = actorSystem.dispatchers().lookup("wot-dispatcher");
        final Executor cacheLoaderExecutor = actorSystem.dispatchers().lookup("wot-dispatcher-cache-loader");
        final PekkoHttpJsonDownloader httpThingModelDownloader =
                new PekkoHttpJsonDownloader(actorSystem, wotConfig);
        thingModelFetcher = WotThingModelFetcher.of(wotConfig, httpThingModelDownloader, cacheLoaderExecutor);
        thingModelExtensionResolver = WotThingModelExtensionResolver.of(thingModelFetcher, executor);
        thingModelResolver =
                WotThingModelResolver.of(wotConfig, thingModelFetcher, thingModelExtensionResolver, cacheLoaderExecutor);
        thingDescriptionGenerator = WotThingDescriptionGenerator.of(wotConfig, thingModelResolver, executor);
        thingSkeletonGenerator = WotThingSkeletonGenerator.of(wotConfig, thingModelResolver, executor);
        thingModelValidator = WotThingModelValidator.of(wotConfig, thingModelResolver, executor);
    }

    /**
     * Returns a new {@code DefaultWotThingDescriptionProvider} for the given parameters.
     *
     * @param actorSystem the actor system to use.
     * @param wotConfig the WoT config to use.
     * @return the DefaultWotThingDescriptionProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultDittoWotIntegration of(final ActorSystem actorSystem, final WotConfig wotConfig) {
        return new DefaultDittoWotIntegration(actorSystem, wotConfig);
    }

    @Override
    public WotConfig getWotConfig() {
        return wotConfig;
    }

    @Override
    public WotThingModelFetcher getWotThingModelFetcher() {
        return thingModelFetcher;
    }

    @Override
    public WotThingModelResolver getWotThingModelResolver() {
        return thingModelResolver;
    }

    @Override
    public WotThingDescriptionGenerator getWotThingDescriptionGenerator() {
        return thingDescriptionGenerator;
    }

    @Override
    public WotThingModelExtensionResolver getWotThingModelExtensionResolver() {
        return thingModelExtensionResolver;
    }

    @Override
    public WotThingSkeletonGenerator getWotThingSkeletonGenerator() {
        return thingSkeletonGenerator;
    }

    @Override
    public WotThingModelValidator getWotThingModelValidator() {
        return thingModelValidator;
    }


    static final class ExtensionId extends AbstractExtensionId<DittoWotIntegration> {

        static final ExtensionId INSTANCE = new ExtensionId();

        private ExtensionId() {}

        @Override
        public DittoWotIntegration createExtension(final ExtendedActorSystem system) {
            final WotConfig wotConfig = DefaultWotConfig.of(
                    DefaultScopedConfig.dittoScoped(system.settings().config())
                            .getConfig(DefaultWotConfig.WOT_PARENT_CONFIG_PATH)
            );
            return of(system, wotConfig);
        }
    }

}
