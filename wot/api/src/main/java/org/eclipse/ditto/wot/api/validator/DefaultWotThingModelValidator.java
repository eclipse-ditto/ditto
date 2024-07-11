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

import static org.eclipse.ditto.wot.validation.ValidationContext.buildValidationContext;

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
import org.eclipse.ditto.wot.validation.ValidationContext;
import org.eclipse.ditto.wot.validation.WotThingModelValidation;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

/**
 * Default Ditto specific implementation of {@link WotThingModelValidator}.
 */
@Immutable
final class DefaultWotThingModelValidator implements WotThingModelValidator {

    private final WotConfig wotConfig;
    private final WotThingModelResolver thingModelResolver;
    private final Executor executor;

    DefaultWotThingModelValidator(final WotConfig wotConfig,
            final WotThingModelResolver thingModelResolver,
            final Executor executor) {
        this.wotConfig = wotConfig;
        this.thingModelResolver = thingModelResolver;
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
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        doValidateThing(thingModel, thing, resourcePath, context, validationConfig)
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThing(final ThingDefinition thingDefinition,
            final ThingModel thingModel,
            final Thing thing,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig ->
                        doValidateThing(thingModel, thing, resourcePath, context, validationConfig)
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingDefinitionModification(final ThingDefinition thingDefinition,
            final Thing thing,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .filter(validationConfig -> validationConfig.isEnabled() &&
                        validationConfig.getThingValidationConfig().isEnforceThingDescriptionModification()
                )
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        doValidateThing(thingModel, thing, Thing.JsonFields.DEFINITION.getPointer(), context,
                                validationConfig)
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingAttributes(@Nullable final ThingDefinition thingDefinition,
            @Nullable final Attributes attributes,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        doValidateThingAttributes(thingModel, attributes, resourcePath, context, validationConfig)
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingAttributes(final ThingDefinition thingDefinition,
            final ThingModel thingModel,
            @Nullable final Attributes attributes,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig ->
                        doValidateThingAttributes(thingModel, attributes, resourcePath, context, validationConfig)
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingAttribute(@Nullable final ThingDefinition thingDefinition,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        selectValidation(validationConfig)
                                .validateThingAttribute(thingModel,
                                        attributePointer, attributeValue, resourcePath, context
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingActionInput(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        selectValidation(validationConfig)
                                .validateThingActionInput(thingModel,
                                        messageSubject, inputPayload, resourcePath, context
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingActionOutput(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            @Nullable final JsonValue outputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        selectValidation(validationConfig)
                                .validateThingActionOutput(thingModel,
                                        messageSubject, outputPayload, resourcePath, context
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingEventData(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            @Nullable final JsonValue dataPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        selectValidation(validationConfig)
                                .validateThingEventData(thingModel,
                                        messageSubject, dataPayload, resourcePath, context
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatures(@Nullable final ThingDefinition thingDefinition,
            final Features features,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        doValidateFeatures(thingModel, features, resourcePath, context, validationConfig)
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatures(final ThingDefinition thingDefinition,
            final ThingModel thingModel,
            final Features features,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig ->
                        doValidateFeatures(thingModel, features, resourcePath, context, validationConfig)
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeature(@Nullable final ThingDefinition thingDefinition,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> {
                    final Optional<URL> urlOpt = Optional.ofNullable(thingDefinition).flatMap(ThingDefinition::getUrl);
                    return urlOpt.map(url -> fetchResolveAndValidateWith(url, dittoHeaders, thingModel ->
                                            doValidateFeature(thingModel,
                                                    feature, resourcePath, context, validationConfig
                                            )
                                    )
                            )
                            .orElseGet(() ->
                                    doValidateFeature(null,
                                            feature, resourcePath, context, validationConfig
                                    )
                            );
                })
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeature(@Nullable final ThingDefinition thingDefinition,
            @Nullable final ThingModel thingModel,
            @Nullable final FeatureDefinition featureDefinition,
            @Nullable final ThingModel featureThingModel,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig ->
                        doValidateFeature(thingModel, featureThingModel,
                                feature, resourcePath, context, validationConfig
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureDefinitionModification(final FeatureDefinition featureDefinition,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(featureDefinition.getFirstIdentifier(),
                        dittoHeaders,
                        featureThingModel ->
                                selectValidation(validationConfig)
                                        .validateFeature(featureThingModel, feature, resourcePath, context)
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                        Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                        dittoHeaders,
                        featureThingModelWithExtensionsAndImports ->
                                validateFeatureProperties(Optional.ofNullable(featureDefinition).orElseThrow(),
                                        featureThingModelWithExtensionsAndImports, featureId, featureProperties,
                                        desiredProperties, resourcePath, dittoHeaders)
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(final FeatureDefinition featureDefinition,
            final ThingModel featureThingModel,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig ->
                        selectValidation(validationConfig)
                                .validateFeatureProperties(featureThingModel, featureId, featureProperties,
                                        desiredProperties, resourcePath, context
                                ))
                .orElseGet(DefaultWotThingModelValidator::success);
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
        final ValidationContext context = buildValidationContext(dittoHeaders, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                        Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                        dittoHeaders, featureThingModel ->
                                selectValidation(validationConfig)
                                        .validateFeatureProperty(featureThingModel,
                                                featureId, propertyPointer, propertyValue, desiredProperty,
                                                resourcePath, context
                                        )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureActionInput(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                        Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                        dittoHeaders, featureThingModel ->
                                selectValidation(validationConfig)
                                        .validateFeatureActionInput(featureThingModel,
                                                featureId, messageSubject, inputPayload, resourcePath, context
                                        )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureActionOutput(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                        Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                        dittoHeaders, featureThingModel ->
                                selectValidation(validationConfig)
                                        .validateFeatureActionOutput(featureThingModel,
                                                featureId, messageSubject, inputPayload, resourcePath, context
                                        )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureEventData(@Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue dataPayload,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                        Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                        dittoHeaders, featureThingModel ->
                                selectValidation(validationConfig)
                                        .validateFeatureEventData(featureThingModel,
                                                featureId, messageSubject, dataPayload, resourcePath, context
                                        )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    private WotThingModelValidation selectValidation(final TmValidationConfig validationConfig) {
        return WotThingModelValidation.of(validationConfig);
    }

    private Optional<TmValidationConfig> provideValidationConfigIfWotValidationEnabled(
            final ValidationContext context
    ) {
        final TmValidationConfig validationConfig = wotConfig.getValidationConfig(context);
        if (FeatureToggle.isWotIntegrationFeatureEnabled() && validationConfig.isEnabled()) {
            return Optional.of(validationConfig);
        } else {
            return Optional.empty();
        }
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

    private CompletionStage<Void> doValidateThing(final ThingModel thingModel,
            final Thing thing,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        return doValidateThingAttributes(thingModel, thing.getAttributes().orElse(null), resourcePath, context,
                validationConfig
        ).thenCompose(aVoid ->
                thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                        .thenCompose(subModels ->
                                doValidateFeatures(subModels, thing.getFeatures().orElse(null), resourcePath,
                                        context, validationConfig
                                )
                        )
        );
    }

    private CompletionStage<Void> doValidateThingAttributes(final ThingModel thingModel,
            @Nullable final Attributes attributes,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        return selectValidation(validationConfig)
                .validateThingAttributes(thingModel, attributes, resourcePath, context);
    }

    private CompletionStage<Void> doValidateFeatures(final ThingModel thingModel,
            final Features features,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        return thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                .thenCompose(subModels ->
                        doValidateFeatures(subModels, features, resourcePath, context, validationConfig)
                );
    }

    private CompletionStage<Void> doValidateFeatures(final Map<ThingSubmodel, ThingModel> subModels,
            @Nullable final Features features,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final Map<String, ThingModel> featureThingModels = reduceSubmodelMapKeyToFeatureId(subModels);
        return selectValidation(validationConfig)
                .validateFeaturesPresence(featureThingModels, features, context)
                .thenCompose(aVoid ->
                        selectValidation(validationConfig)
                                .validateFeaturesProperties(featureThingModels, features, resourcePath, context)
                );
    }

    private CompletionStage<Void> doValidateFeature(@Nullable final ThingModel thingModel,
            final Feature feature,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final Optional<FeatureDefinition> featureDefinition = feature.getDefinition();
        final Optional<DefinitionIdentifier> definitionIdentifier = featureDefinition
                .map(FeatureDefinition::getFirstIdentifier);
        final Optional<URL> urlOpt = definitionIdentifier.flatMap(DefinitionIdentifier::getUrl);
        return urlOpt.map(url -> fetchResolveAndValidateWith(url, context.dittoHeaders(), featureThingModel ->
                        doValidateFeature(thingModel, featureThingModel,
                                feature, resourcePath, context, validationConfig
                        )
                ))
                .orElseGet(() ->
                        doValidateFeature(thingModel, null,
                                feature, resourcePath, context, validationConfig
                        )
                );
    }

    private CompletionStage<Void> doValidateFeature(@Nullable final ThingModel thingModel,
            @Nullable final ThingModel featureThingModel,
            final Feature feature,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
            ) {
        final WotThingModelValidation selectedValidation = selectValidation(validationConfig);
        if (thingModel != null && featureThingModel != null) {
            return thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                    .thenCompose(subModels ->
                            selectedValidation.validateFeaturePresence(
                                    reduceSubmodelMapKeyToFeatureId(subModels), feature, context
                            ).thenCompose(aVoid ->
                                    selectedValidation.validateFeature(featureThingModel, feature,
                                            resourcePath, context)
                            )
                    );
        } else if (thingModel != null) {
            return thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                    .thenCompose(subModels ->
                            selectedValidation.validateFeaturePresence(
                                    reduceSubmodelMapKeyToFeatureId(subModels), feature, context)
                    );
        } else if (featureThingModel != null) {
            return selectedValidation.validateFeature(featureThingModel, feature, resourcePath, context);
        }
        return success();
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
