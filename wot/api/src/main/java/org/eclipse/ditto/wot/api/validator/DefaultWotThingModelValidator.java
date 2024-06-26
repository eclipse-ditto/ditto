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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.DefinitionIdentifier;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.resolver.ThingSubmodel;
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
        thingModelValidation = WotThingModelValidation.of(wotConfig.getValidationConfig());
        this.executor = executor;
    }

    @Override
    public CompletionStage<Void> validateThing(final Thing thing,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        return thing.getDefinition()
                .map(thingDefinition -> validateThing(thingDefinition, thing, resourcePath, dittoHeaders))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThing(@Nullable final ThingDefinition thingDefinition,
            final Thing thing,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModelWithExtensionsAndImports ->
                    validateThing(thingModelWithExtensionsAndImports, thing, resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThing(final ThingModel thingModel,
            final Thing thing,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return validateThingAttributes(thingModel, thing.getAttributes().orElse(null), resourcePath,
                    dittoHeaders
            ).thenCompose(aVoid ->
                    thingModelResolver.resolveThingModelSubmodels(thingModel, dittoHeaders)
                            .thenCompose(subModels ->
                                    doValidateFeatures(subModels, thing.getFeatures().orElse(null), resourcePath,
                                            dittoHeaders)
                            )
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingDefinitionModification(final ThingDefinition thingDefinition,
            final Thing thing,
            final DittoHeaders dittoHeaders
    ) {
        if (FeatureToggle.isWotIntegrationFeatureEnabled() && wotConfig.getValidationConfig().isEnabled() &&
                wotConfig.getValidationConfig().getThingValidationConfig().isEnforceThingDescriptionModification()) {
            return validateThing(thingDefinition, thing, Thing.JsonFields.DEFINITION.getPointer(), dittoHeaders);
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingAttributes(@Nullable final ThingDefinition thingDefinition,
            @Nullable final Attributes attributes,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModelWithExtensionsAndImports ->
                    validateThingAttributes(thingModelWithExtensionsAndImports, attributes, resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingAttributes(final ThingModel thingModel,
            @Nullable final Attributes attributes,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return thingModelValidation.validateThingAttributes(thingModel, attributes, resourcePath, dittoHeaders);
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingAttribute(@Nullable final ThingDefinition thingDefinition,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModelWithExtensionsAndImports ->
                    thingModelValidation.validateThingAttribute(thingModelWithExtensionsAndImports,
                            attributePointer, attributeValue, resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingMessageInput(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModelWithExtensionsAndImports ->
                    thingModelValidation.validateThingMessageInput(thingModelWithExtensionsAndImports,
                            messageSubject, inputPayload, resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingMessageOutput(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            @Nullable final JsonValue outputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModelWithExtensionsAndImports ->
                    thingModelValidation.validateThingMessageOutput(thingModelWithExtensionsAndImports,
                            messageSubject, outputPayload, resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatures(@Nullable final ThingDefinition thingDefinition,
            final Features features,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModelWithExtensionsAndImports ->
                    validateFeatures(thingModelWithExtensionsAndImports, features, resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatures(final ThingModel thingModel,
            final Features features,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return thingModelResolver.resolveThingModelSubmodels(thingModel, dittoHeaders)
                    .thenCompose(subModels ->
                            doValidateFeatures(subModels, features, resourcePath, dittoHeaders)
                    );
        } else {
            return success();
        }
    }

    @Override
    public CompletionStage<Void> validateFeature(@Nullable final ThingDefinition thingDefinition,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            final Optional<URL> urlOpt = Optional.ofNullable(thingDefinition).flatMap(ThingDefinition::getUrl);
            return urlOpt
                    .map(url ->
                            fetchResolveAndValidateWith(url, dittoHeaders, thingModelWithExtensionsAndImports ->
                                    doValidateFeature(thingModelWithExtensionsAndImports, feature, resourcePath,
                                            dittoHeaders)
                            )
                    ).orElseGet(() -> doValidateFeature(null, feature, resourcePath, dittoHeaders));
        } else {
            return success();
        }
    }

    @Override
    public CompletionStage<Void> validateFeature(@Nullable final ThingModel thingModel,
            @Nullable final ThingModel featureThingModel,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            if (thingModel != null && featureThingModel != null) {
                return thingModelResolver.resolveThingModelSubmodels(thingModel, dittoHeaders)
                        .thenCompose(subModels ->
                                thingModelValidation.validateFeaturePresence(
                                        reduceSubmodelMapKeyToFeatureId(subModels),
                                        feature,
                                        dittoHeaders
                                ).thenCompose(aVoid ->
                                        thingModelValidation.validateFeature(featureThingModel, feature,
                                                resourcePath, dittoHeaders)
                                )
                        );
            } else if (thingModel != null) {
                return thingModelResolver.resolveThingModelSubmodels(thingModel, dittoHeaders)
                        .thenCompose(subModels ->
                                thingModelValidation.validateFeaturePresence(
                                        reduceSubmodelMapKeyToFeatureId(subModels),
                                        feature,
                                        dittoHeaders
                                )
                        );
            } else if (featureThingModel != null) {
                return thingModelValidation.validateFeature(featureThingModel, feature, resourcePath,
                        dittoHeaders);
            }
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureDefinitionModification(final FeatureDefinition featureDefinition,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (FeatureToggle.isWotIntegrationFeatureEnabled() && wotConfig.getValidationConfig().isEnabled() &&
                wotConfig.getValidationConfig()
                        .getFeatureValidationConfig()
                        .isEnforceFeatureDescriptionModification()
        ) {
            return fetchResolveAndValidateWith(featureDefinition.getFirstIdentifier(), dittoHeaders,
                    featureThingModelWithExtensionsAndImports ->
                            thingModelValidation.validateFeature(featureThingModelWithExtensionsAndImports, feature,
                                    resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(
                    Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                    dittoHeaders,
                    featureThingModelWithExtensionsAndImports ->
                            validateFeatureProperties(featureThingModelWithExtensionsAndImports, featureId,
                                    featureProperties, desiredProperties, resourcePath, dittoHeaders
                            )
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(final ThingModel featureThingModel,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return thingModelValidation.validateFeatureProperties(featureThingModel, featureId, featureProperties,
                    desiredProperties, resourcePath, dittoHeaders
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureProperty(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final boolean desiredProperty,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(
                    Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                    dittoHeaders,
                    featureThingModelWithExtensionsAndImports ->
                            thingModelValidation.validateFeatureProperty(featureThingModelWithExtensionsAndImports,
                                    featureId, propertyPointer, propertyValue, desiredProperty, resourcePath,
                                    dittoHeaders
                            )
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureMessageInput(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(
                    Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                    dittoHeaders,
                    featureThingModelWithExtensionsAndImports ->
                            thingModelValidation.validateFeatureMessageInput(featureThingModelWithExtensionsAndImports,
                                    featureId,
                                    messageSubject, inputPayload,
                                    resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureMessageOutput(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        if (isWotValidationEnabled()) {
            return fetchResolveAndValidateWith(
                    Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                    dittoHeaders,
                    featureThingModelWithExtensionsAndImports ->
                            thingModelValidation.validateFeatureMessageOutput(featureThingModelWithExtensionsAndImports,
                                    featureId,
                                    messageSubject, inputPayload,
                                    resourcePath, dittoHeaders)
            );
        }
        return success();
    }

    private boolean isWotValidationEnabled() {
        return FeatureToggle.isWotIntegrationFeatureEnabled() && wotConfig.getValidationConfig().isEnabled();
    }

    private static <T> CompletionStage<T> success() {
        return CompletableFuture.completedStage(null);
    }

    private CompletionStage<Void> fetchResolveAndValidateWith(@Nullable final DefinitionIdentifier definitionIdentifier,
            final DittoHeaders dittoHeaders,
            final Function<ThingModel, CompletionStage<Void>> validationFunction
    ) {
        final Optional<URL> urlOpt = Optional.ofNullable(definitionIdentifier).flatMap(DefinitionIdentifier::getUrl);
        return urlOpt
                .map(url -> fetchResolveAndValidateWith(url, dittoHeaders, validationFunction))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    private CompletionStage<Void> fetchResolveAndValidateWith(final URL url,
            final DittoHeaders dittoHeaders,
            final Function<ThingModel, CompletionStage<Void>> validationFunction
    ) {
        return thingModelResolver.resolveThingModel(url, dittoHeaders)
                .thenComposeAsync(validationFunction, executor);
    }

    private CompletionStage<Void> doValidateFeatures(final Map<ThingSubmodel, ThingModel> subModels,
            @Nullable final Features features,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final Map<String, ThingModel> featureThingModels = reduceSubmodelMapKeyToFeatureId(subModels);
        return thingModelValidation.validateFeaturesPresence(featureThingModels, features, dittoHeaders)
                .thenCompose(aVoid2 ->
                        thingModelValidation
                                .validateFeaturesProperties(featureThingModels, features, resourcePath, dittoHeaders)
                );
    }

    private CompletionStage<Void> doValidateFeature(@Nullable final ThingModel thingModel,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final Optional<DefinitionIdentifier> definitionIdentifier = feature.getDefinition()
                .map(FeatureDefinition::getFirstIdentifier);
        final Optional<URL> urlOpt = definitionIdentifier.flatMap(DefinitionIdentifier::getUrl);
        return urlOpt
                .map(url -> fetchResolveAndValidateWith(url, dittoHeaders,
                        featureThingModelWithExtensionsAndImports ->
                                validateFeature(thingModel, featureThingModelWithExtensionsAndImports, feature,
                                        resourcePath, dittoHeaders)
                )).orElseGet(() -> validateFeature(thingModel, null, feature, resourcePath, dittoHeaders));
    }

    private static LinkedHashMap<String, ThingModel> reduceSubmodelMapKeyToFeatureId(
            final Map<ThingSubmodel, ThingModel> subModels
    ) {
        return subModels.entrySet().stream().collect(
                Collectors.toMap(
                        e -> e.getKey().instanceName(),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                )
        );
    }
}
