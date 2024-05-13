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
package org.eclipse.ditto.wot.api.validator;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.things.model.DefinitionIdentifier;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.validation.WotThingModelValidation;

/**
 * Default Ditto specific implementation of {@link WotThingModelValidator}.
 */
@Immutable
final class DefaultWotThingModelValidator implements WotThingModelValidator {

    private final WotConfig wotConfig;
    private final WotThingModelResolver thingModelResolver;
    private final WotThingModelValidation thingModelValidation;
    private final Executor executor;

    DefaultWotThingModelValidator(final WotConfig wotConfig,
            final WotThingModelResolver thingModelResolver,
            final Executor executor) {
        this.wotConfig = wotConfig;
        this.thingModelResolver = thingModelResolver;
        thingModelValidation = WotThingModelValidation.createInstance(wotConfig.getValidationConfig());
        this.executor = executor;
    }

    @Override
    public CompletionStage<Void> validateThing(final Thing thing, final DittoHeaders dittoHeaders) {

        if (FeatureToggle.isWotIntegrationFeatureEnabled() && wotConfig.getValidationConfig().isEnabled()) {
            final Optional<URL> urlOpt = thing.getDefinition().flatMap(DefinitionIdentifier::getUrl);
            if (urlOpt.isPresent()) {
                final URL url = urlOpt.get();
                final Function<ThingModel, CompletionStage<Void>> validationFunction =
                        thingModelWithExtensionsAndImports ->
                                validateThing(thingModelWithExtensionsAndImports, thing, dittoHeaders);
                return fetchResolveAndValidateWith(url, dittoHeaders, validationFunction);
            } else {
                return CompletableFuture.completedStage(null);
            }
        } else {
            return CompletableFuture.completedStage(null);
        }
    }

    @Override
    public CompletionStage<Void> validateThing(final ThingModel thingModel,
            final Thing thing,
            final DittoHeaders dittoHeaders) {

        if (FeatureToggle.isWotIntegrationFeatureEnabled() && wotConfig.getValidationConfig().isEnabled()) {
            return thingModelValidation.validateThingAttributes(thingModel, thing, dittoHeaders)
                    .thenCompose(aVoid ->
                            thing.getFeatures().map(features ->
                                    thingModelResolver.resolveThingModelSubmodels(thingModel, dittoHeaders)
                                            .thenAccept(subModels -> // TODO TJ do this async?
                                                    thingModelValidation.validateFeatures(
                                                            subModels.entrySet().stream().collect(
                                                                    Collectors.toMap(
                                                                            e -> e.getKey().instanceName(),
                                                                            Map.Entry::getValue,
                                                                            (a, b) -> a,
                                                                            LinkedHashMap::new
                                                                    )
                                                            ), features, dittoHeaders
                                                    )
                                            )
                            ).orElse(CompletableFuture.completedStage(null))
                    );
        } else {
            return CompletableFuture.completedStage(null);
        }
    }

    @Override
    public CompletionStage<Void> validateFeature(final Feature feature, final DittoHeaders dittoHeaders) {

        if (FeatureToggle.isWotIntegrationFeatureEnabled() && wotConfig.getValidationConfig().isEnabled()) {
            final Optional<DefinitionIdentifier> definitionIdentifier = feature.getDefinition()
                    .map(FeatureDefinition::getFirstIdentifier);
            final Optional<URL> urlOpt = definitionIdentifier.flatMap(DefinitionIdentifier::getUrl);
            if (urlOpt.isPresent()) {
                final URL url = urlOpt.get();
                final Function<ThingModel, CompletionStage<Void>> validationFunction =
                        thingModelWithExtensionsAndImports ->
                                validateFeature(thingModelWithExtensionsAndImports, feature, dittoHeaders);
                return fetchResolveAndValidateWith(url, dittoHeaders, validationFunction);
            } else {
                return CompletableFuture.completedStage(null);
            }
        } else {
            return CompletableFuture.completedStage(null);
        }
    }

    @Override
    public CompletionStage<Void> validateFeature(final ThingModel thingModel, final Feature feature,
            final DittoHeaders dittoHeaders) {

        if (FeatureToggle.isWotIntegrationFeatureEnabled() && wotConfig.getValidationConfig().isEnabled()) {
            return thingModelValidation.validateFeature(thingModel, feature, dittoHeaders);
        } else {
            return CompletableFuture.completedStage(null);
        }
    }

    private CompletionStage<Void> fetchResolveAndValidateWith(final URL url,
            final DittoHeaders dittoHeaders,
            final Function<ThingModel, CompletionStage<Void>> validationFunction) {

        return thingModelResolver.resolveThingModel(url, dittoHeaders)
                .thenComposeAsync(validationFunction, executor);
    }
}
