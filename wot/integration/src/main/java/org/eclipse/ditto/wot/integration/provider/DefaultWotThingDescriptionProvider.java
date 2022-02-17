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
package org.eclipse.ditto.wot.integration.provider;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URL;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.DefinitionIdentifier;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.wot.integration.config.DefaultWotConfig;
import org.eclipse.ditto.wot.integration.config.WotConfig;
import org.eclipse.ditto.wot.integration.generator.WotThingDescriptionGenerator;
import org.eclipse.ditto.wot.integration.generator.WotThingSkeletonGenerator;
import org.eclipse.ditto.wot.model.ThingDefinitionInvalidException;
import org.eclipse.ditto.wot.model.ThingDescription;
import org.eclipse.ditto.wot.model.WotInternalErrorException;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

/**
 * Default Ditto specific implementation of {@link WotThingDescriptionProvider}.
 */
@Immutable
final class DefaultWotThingDescriptionProvider implements WotThingDescriptionProvider {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DefaultWotThingDescriptionProvider.class);

    public static final String MODEL_PLACEHOLDERS_KEY = "model-placeholders";

    private final WotConfig wotConfig;
    private final WotThingModelFetcher thingModelFetcher;
    private final WotThingDescriptionGenerator thingDescriptionGenerator;
    private final WotThingSkeletonGenerator thingSkeletonGenerator;

    private DefaultWotThingDescriptionProvider(final ActorSystem actorSystem, final WotConfig wotConfig) {
        this.wotConfig = checkNotNull(wotConfig, "wotConfig");
        thingModelFetcher = new DefaultWotThingModelFetcher(actorSystem, wotConfig);
        thingDescriptionGenerator = WotThingDescriptionGenerator.of(actorSystem, wotConfig, thingModelFetcher);
        thingSkeletonGenerator = WotThingSkeletonGenerator.of(actorSystem, thingModelFetcher);
    }

    /**
     * Returns a new {@code DefaultWotThingDescriptionProvider} for the given parameters.
     *
     * @param actorSystem the actor system to use.
     * @param wotConfig the WoT config to use.
     * @return the DefaultWotThingDescriptionProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultWotThingDescriptionProvider of(final ActorSystem actorSystem, final WotConfig wotConfig) {
        return new DefaultWotThingDescriptionProvider(actorSystem, wotConfig);
    }

    @Override
    public ThingDescription provideThingTD(@Nullable final ThingDefinition thingDefinition,
            final ThingId thingId,
            @Nullable final Thing thing,
            final DittoHeaders dittoHeaders) {
        if (null != thingDefinition) {
            return getWotThingDescriptionForThing(thingDefinition, thingId, thing, dittoHeaders);
        } else {
            throw ThingDefinitionInvalidException.newBuilder(null)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public ThingDescription provideFeatureTD(final ThingId thingId,
            @Nullable final Thing thing,
            final Feature feature,
            final DittoHeaders dittoHeaders) {

        checkNotNull(feature, "feature");
        if (feature.getDefinition().isPresent()) {
            return getWotThingDescriptionForFeature(thingId, thing, feature, dittoHeaders);
        } else {
            throw ThingDefinitionInvalidException.newBuilder(null)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public Optional<Thing> provideThingSkeletonForCreation(final ThingId thingId,
            @Nullable final ThingDefinition thingDefinition,
            final DittoHeaders dittoHeaders) {

        final ThreadSafeDittoLogger logger = LOGGER.withCorrelationId(dittoHeaders);
        if (FeatureToggle.isWotIntegrationFeatureEnabled() &&
                wotConfig.getCreationConfig().isThingSkeletonCreationEnabled() &&
                null != thingDefinition) {
            final Optional<URL> urlOpt = thingDefinition.getUrl();
            if (urlOpt.isPresent()) {
                final URL url = urlOpt.get();
                try {
                    logger.debug("Fetching ThingModel from <{}> in order to create Thing skeleton for new Thing " +
                            "with id <{}>", url, thingId);
                    final Optional<Thing> thingSkeleton = thingModelFetcher.fetchThingModel(url, dittoHeaders)
                            .thenApply(thingModel -> thingSkeletonGenerator
                                    .generateThingSkeleton(thingId, thingModel, url, dittoHeaders))
                            .toCompletableFuture()
                            .join();
                    logger.debug("Created Thing skeleton for new Thing with id <{}>: <{}>", thingId, thingSkeleton);
                    return thingSkeleton;
                } catch (final DittoRuntimeException | CompletionException e) {
                    logger.info("Could not fetch ThingModel or generate Feature skeleton based on it due to: <{}: {}>",
                            e.getClass().getSimpleName(), e.getMessage(), e);
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Feature> provideFeatureSkeletonForCreation(final String featureId,
            @Nullable final FeatureDefinition featureDefinition, final DittoHeaders dittoHeaders) {

        final ThreadSafeDittoLogger logger = LOGGER.withCorrelationId(dittoHeaders);
        if (FeatureToggle.isWotIntegrationFeatureEnabled() &&
                wotConfig.getCreationConfig().isFeatureSkeletonCreationEnabled() &&
                null != featureDefinition) {
            final Optional<URL> urlOpt = featureDefinition.getFirstIdentifier().getUrl();
            if (urlOpt.isPresent()) {
                final URL url = urlOpt.get();
                try {
                    logger.debug("Fetching ThingModel from <{}> in order to create Feature skeleton for new Feature " +
                            "with id <{}>", url, featureId);
                    final Optional<Feature> featureSkeleton = thingModelFetcher.fetchThingModel(url, dittoHeaders)
                            .thenApply(thingModel -> thingSkeletonGenerator
                                    .generateFeatureSkeleton(featureId, thingModel, url, dittoHeaders))
                            .toCompletableFuture()
                            .join();
                    logger.debug("Created Feature skeleton for new Feature with id <{}>: <{}>", featureId,
                            featureSkeleton);
                    return featureSkeleton;
                } catch (final DittoRuntimeException | CompletionException e) {
                    logger.info("Could not fetch ThingModel or generate Feature skeleton based on it due to: <{}: {}>",
                            e.getClass().getSimpleName(), e.getMessage(), e);
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * Download TM, add it to local cache + build TD + return it
     */
    private ThingDescription getWotThingDescriptionForThing(final ThingDefinition definitionIdentifier,
            final ThingId thingId,
            @Nullable final Thing thing,
            final DittoHeaders dittoHeaders) {

        final Optional<URL> urlOpt = definitionIdentifier.getUrl();
        if (urlOpt.isPresent()) {
            final URL url = urlOpt.get();
            try {
                return thingModelFetcher.fetchThingModel(url, dittoHeaders)
                        .thenApply(thingModel -> thingDescriptionGenerator
                                .generateThingDescription(thingId,
                                        thing,
                                        Optional.ofNullable(thing)
                                                .flatMap(Thing::getAttributes)
                                                .flatMap(a -> a.getValue(MODEL_PLACEHOLDERS_KEY))
                                                .filter(JsonValue::isObject)
                                                .map(JsonValue::asObject)
                                                .orElse(null),
                                        null,
                                        thingModel,
                                        url,
                                        dittoHeaders)
                        )
                        .toCompletableFuture()
                        .join();
            } catch (final Exception e) {
                throw DittoRuntimeException.asDittoRuntimeException(e, throwable ->
                        WotInternalErrorException.newBuilder()
                                .dittoHeaders(dittoHeaders)
                                .cause(e)
                                .build()
                );
            }
        } else {
            throw ThingDefinitionInvalidException.newBuilder(definitionIdentifier)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Download TM, add it to local cache + build TD + return it
     */
    private ThingDescription getWotThingDescriptionForFeature(final ThingId thingId,
            @Nullable final Thing thing,
            final Feature feature,
            final DittoHeaders dittoHeaders) {

        final Optional<DefinitionIdentifier> definitionIdentifier = feature.getDefinition()
                .map(FeatureDefinition::getFirstIdentifier);
        final Optional<URL> urlOpt = definitionIdentifier.flatMap(DefinitionIdentifier::getUrl);
        if (urlOpt.isPresent()) {
            final URL url = urlOpt.get();
            try {
                return thingModelFetcher.fetchThingModel(url, dittoHeaders)
                        .thenApply(thingModel -> thingDescriptionGenerator
                                .generateThingDescription(thingId,
                                        thing,
                                        feature.getProperties()
                                                .flatMap(p -> p.getValue(MODEL_PLACEHOLDERS_KEY))
                                                .filter(JsonValue::isObject)
                                                .map(JsonValue::asObject)
                                                .orElse(null),
                                        feature.getId(),
                                        thingModel,
                                        url,
                                        dittoHeaders)
                        )
                        .toCompletableFuture()
                        .join();
            } catch (final Exception e) {
                throw DittoRuntimeException.asDittoRuntimeException(e, throwable ->
                        WotInternalErrorException.newBuilder()
                                .dittoHeaders(dittoHeaders)
                                .cause(e)
                                .build()
                );
            }
        } else {
            throw ThingDefinitionInvalidException.newBuilder(definitionIdentifier.orElse(null))
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    static final class ExtensionId extends AbstractExtensionId<WotThingDescriptionProvider> {

        private static final String WOT_PARENT_CONFIG_PATH = "things";

        static final ExtensionId INSTANCE = new ExtensionId();

        private ExtensionId() {}

        @Override
        public WotThingDescriptionProvider createExtension(final ExtendedActorSystem system) {
            final WotConfig wotConfig = DefaultWotConfig.of(
                    DefaultScopedConfig.dittoScoped(system.settings().config()).getConfig(WOT_PARENT_CONFIG_PATH)
            );
            return of(system, wotConfig);
        }
    }

}
