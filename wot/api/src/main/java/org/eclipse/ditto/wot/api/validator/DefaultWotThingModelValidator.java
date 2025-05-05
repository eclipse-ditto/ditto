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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
import org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException;
import org.eclipse.ditto.wot.validation.WotThingModelValidation;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Default Ditto specific implementation of {@link WotThingModelValidator}.
 */
@Immutable
final class DefaultWotThingModelValidator implements WotThingModelValidator {

    private static final Logger log = LoggerFactory.getLogger(DefaultWotThingModelValidator.class);
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
                        doValidateThing(Optional.ofNullable(thingDefinition).orElseThrow(),
                                thingModel, thing, context, validationConfig
                        ).handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateThing"),
                                executor
                        )
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
                        doValidateThing(thingDefinition, thingModel, thing, context, validationConfig)
                                .handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateThing"),
                                        executor
                                )
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
                        doValidateThing(thingDefinition, thingModel, thing, context, validationConfig)
                                .handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingDefinitionModification"),
                                        executor
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingDefinitionDeletion(final ThingDefinition thingDefinition,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .filter(validationConfig -> validationConfig.isEnabled() &&
                        validationConfig.getThingValidationConfig().isForbidThingDescriptionDeletion()
                )
                .filter(validationConfig -> Optional.ofNullable(context.featureDefinition())
                        .map(FeatureDefinition::getFirstIdentifier)
                        .filter(definitionIdentifier -> definitionIdentifier.getUrl().isPresent()) // only for URLs in the definition
                        .isPresent()
                )
                .map(validationConfig ->
                        CompletableFuture.<Void>failedStage(
                                WotThingModelPayloadValidationException
                                        .newBuilder("Deleting the Thing's <definition> is not allowed")
                                        .dittoHeaders(dittoHeaders)
                                        .build()
                        )
                )
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
                                .handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingAttributes"),
                                        executor
                                )
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
                                .handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingAttributes"),
                                        executor
                                )
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
                                ).handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingAttribute"),
                                        executor
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingScopedDeletion(@Nullable final ThingDefinition thingDefinition,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                                .thenComposeAsync(subModels ->
                                        selectValidation(validationConfig)
                                                .validateThingScopedDeletion(thingModel,
                                                        reduceSubmodelMapKeyToFeatureId(subModels),
                                                        resourcePath,
                                                        context
                                                ).handleAsync(
                                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingScopedDeletion"),
                                                        executor
                                                ),
                                        executor
                                )

                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingActionInput(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            final Supplier<JsonValue> inputPayloadSupplier,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                                CompletableFuture.supplyAsync(inputPayloadSupplier, executor)
                                        .thenComposeAsync(inputPayload ->
                                                selectValidation(validationConfig)
                                                        .validateThingActionInput(thingModel,
                                                                messageSubject, inputPayload, resourcePath, context
                                                        ),
                                                executor
                                )
                        )
                        .handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingActionInput"),
                                executor
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingActionOutput(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            final Supplier<JsonValue> outputPayloadSupplier,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                                CompletableFuture.supplyAsync(outputPayloadSupplier, executor)
                                        .thenComposeAsync(outputPayload ->
                                                selectValidation(validationConfig)
                                                        .validateThingActionOutput(thingModel,
                                                                messageSubject, outputPayload, resourcePath, context
                                                        ),
                                                executor
                                )
                        ).handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingActionOutput"),
                                executor
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateThingEventData(@Nullable final ThingDefinition thingDefinition,
            final String messageSubject,
            final Supplier<JsonValue> dataPayloadSupplier,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                                CompletableFuture.supplyAsync(dataPayloadSupplier, executor)
                                        .thenComposeAsync(dataPayload ->
                                                selectValidation(validationConfig)
                                                        .validateThingEventData(thingModel,
                                                                messageSubject, dataPayload, resourcePath, context
                                                        ),
                                                executor
                                )
                        ).handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateThingEventData"),
                                executor
                        )
                )
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
                                .handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatures"),
                                        executor
                                )
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
                                .handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatures"),
                                        executor
                                )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeature(@Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> {
                    final Optional<URL> urlOpt = Optional.ofNullable(thingDefinition).flatMap(ThingDefinition::getUrl);
                    return urlOpt.map(url -> fetchResolveAndValidateWith(url, dittoHeaders, thingModel ->
                                            doValidateFeature(thingModel, featureDefinition,
                                                    feature, resourcePath, context, validationConfig
                                            ).handleAsync(
                                                    applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeature"),
                                                    executor
                                            )
                                    )
                            )
                            .orElseGet(() ->
                                    doValidateFeature(null, featureDefinition,
                                            feature, resourcePath, context, validationConfig
                                    ).handleAsync(
                                            applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeature"),
                                            executor
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
                        doValidateFeature(thingModel, featureDefinition, featureThingModel,
                                feature, resourcePath, context, validationConfig
                        ).handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeature"),
                                executor
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureDefinitionModification(@Nullable final ThingDefinition thingDefinition,
            final FeatureDefinition featureDefinition,
            final Feature feature,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .filter(validationConfig -> validationConfig.isEnabled() &&
                        validationConfig.getFeatureValidationConfig().isEnforceFeatureDescriptionModification()
                )
                .map(validationConfig -> fetchResolveAndValidateWith(featureDefinition.getFirstIdentifier(),
                        dittoHeaders,
                        featureThingModel ->
                                selectValidation(validationConfig)
                                        .validateFeature(featureThingModel, feature, resourcePath, context)
                                        .handleAsync(
                                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureDefinitionModification"),
                                                executor
                                        )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureDefinitionDeletion(@Nullable final ThingDefinition thingDefinition,
            final FeatureDefinition featureDefinition,
            final String featureId,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return doValidateFeatureDefinitionDeletion(featureId, dittoHeaders, context);
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(@Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                        Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                        dittoHeaders,
                        featureThingModelWithExtensionsAndImports ->
                                validateFeatureProperties(thingDefinition,
                                        Optional.ofNullable(featureDefinition).orElseThrow(),
                                        featureThingModelWithExtensionsAndImports, featureId, featureProperties,
                                        desiredProperties, resourcePath, dittoHeaders
                                ).handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureProperties"),
                                        executor
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(@Nullable final ThingDefinition thingDefinition,
            final FeatureDefinition featureDefinition,
            final ThingModel featureThingModel,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig ->
                        selectValidation(validationConfig)
                                .validateFeatureProperties(featureThingModel, featureId, featureProperties,
                                        desiredProperties, resourcePath, context
                                ).handleAsync(
                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureProperties"),
                                        executor
                                )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureProperty(@Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final boolean desiredProperty,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                        Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                        dittoHeaders, featureThingModel ->
                                selectValidation(validationConfig)
                                        .validateFeatureProperty(featureThingModel,
                                                featureId, propertyPointer, propertyValue, desiredProperty,
                                                resourcePath, context
                                        ).handleAsync(
                                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureProperty"),
                                                executor
                                        )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureScopedDeletion(@Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                                .thenComposeAsync(subModels ->
                                        fetchResolveAndValidateWith(
                                                Optional.ofNullable(featureDefinition)
                                                        .map(FeatureDefinition::getFirstIdentifier).orElse(null),
                                                dittoHeaders,
                                                featureThingModel ->
                                                        selectValidation(validationConfig)
                                                                .validateFeatureScopedDeletion(
                                                                        reduceSubmodelMapKeyToFeatureId(subModels),
                                                                        featureThingModel, featureId, resourcePath,
                                                                        context
                                                                ).handleAsync(
                                                                        applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureScopedDeletion"),
                                                                        executor
                                                                )
                                        ),
                                        executor
                                )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureActionInput(@Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            final Supplier<JsonValue> inputPayloadSupplier,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                                Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                                dittoHeaders, featureThingModel ->
                                CompletableFuture.supplyAsync(inputPayloadSupplier, executor)
                                        .thenComposeAsync(inputPayload ->
                                                selectValidation(validationConfig)
                                                        .validateFeatureActionInput(featureThingModel,
                                                                featureId, messageSubject, inputPayload, resourcePath, context
                                                        ),
                                                executor
                                        )
                        ).handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureActionInput"),
                                executor
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureActionOutput(@Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            final Supplier<JsonValue> outputPayloadSupplier,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                                Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                                dittoHeaders, featureThingModel ->
                                CompletableFuture.supplyAsync(outputPayloadSupplier, executor)
                                        .thenComposeAsync(outputPayload ->
                                                selectValidation(validationConfig)
                                                        .validateFeatureActionOutput(featureThingModel, featureId,
                                                                messageSubject, outputPayload, resourcePath, context
                                                        ),
                                                executor
                                )
                        ).handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureActionOutput"),
                                executor
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateFeatureEventData(@Nullable final ThingDefinition thingDefinition,
            @Nullable final FeatureDefinition featureDefinition,
            final String featureId,
            final String messageSubject,
            final Supplier<JsonValue> dataPayloadSupplier,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition, featureDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(
                                Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier).orElse(null),
                                dittoHeaders, featureThingModel ->
                                CompletableFuture.supplyAsync(dataPayloadSupplier, executor)
                                        .thenComposeAsync(dataPayload ->
                                                selectValidation(validationConfig)
                                                        .validateFeatureEventData(featureThingModel, featureId,
                                                                messageSubject, dataPayload, resourcePath, context
                                                        ),
                                                executor
                                )
                        )
                        .handleAsync(
                                applyLogingErrorOnlyStrategy(validationConfig, context, "validateFeatureEventData"),
                                executor
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    private WotThingModelValidation selectValidation(final TmValidationConfig validationConfig) {
        return WotThingModelValidation.of(validationConfig, executor);
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

    private static <T> BiFunction<T, Throwable, T> applyLogingErrorOnlyStrategy(
            final TmValidationConfig validationConfig,
            final ValidationContext context,
            final String loggingHintSource
    ) {
        return (result, throwable) -> {
            if (throwable != null) {
                final Throwable cause =
                        (throwable instanceof CompletionException ce) ? ce.getCause() : throwable;
                if (validationConfig.logWarningInsteadOfFailingApiCalls()) {
                    // only log a warning, but do not fail the API call
                    logValidationWarning(true, context, loggingHintSource, cause);
                    return null;
                } else {
                    logValidationWarning(false, context, loggingHintSource, cause);
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    } else {
                        throw new IllegalStateException(cause);
                    }
                }
            } else {
                return result;
            }
        };
    }

    private static void logValidationWarning(final boolean logAsWarning,
            final ValidationContext context,
            final String loggingHintSource,
            final Throwable throwable
    ) {
        final DittoHeaders dittoHeaders = context.dittoHeaders();
        dittoHeaders.getCorrelationId().ifPresent(cId -> MDC.put("correlation-id", cId));
        // positions defined by https://www.w3.org/TR/trace-context/#traceparent-header-field-values to contain the "trace-id"
        dittoHeaders.getTraceParent().ifPresent(traceParent -> {
                    MDC.put("traceparent-trace-id", traceParent.substring(3, 35));
                    MDC.put("traceparent-span-id", traceParent.substring(36, 52));
                }
        );
        final LoggingEventBuilder logBuilder = logAsWarning ? log.atWarn() : log.atInfo();
        logBuilder.log("WoT based validation of Thing <{}> in <{}()> failed for <TD {}>/<FD {}> due to: <{}>",
                context.thingId(), loggingHintSource, context.thingDefinition(), context.featureDefinition(),
                throwable.toString()
        );
        dittoHeaders.getCorrelationId().ifPresent(cId -> MDC.remove("correlation-id"));
        dittoHeaders.getTraceParent().ifPresent(traceParent -> {
            MDC.remove("traceparent-trace-id");
            MDC.remove("traceparent-span-id");
        });
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

    private CompletionStage<Void> doValidateThing(final ThingDefinition thingDefinition,
            final ThingModel thingModel,
            final Thing thing,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final CompletionStage<Void> firstStage;
        if (validationConfig.getThingValidationConfig().isForbidThingDescriptionDeletion() &&
                thing.getDefinition().isEmpty()) {
            firstStage = validateThingDefinitionDeletion(thingDefinition, context.dittoHeaders());
        } else {
            firstStage = success();
        }
        return firstStage.thenComposeAsync(unused ->
                doValidateThingAttributes(thingModel,
                        thing.getAttributes().orElse(null),
                        Thing.JsonFields.ATTRIBUTES.getPointer(),
                        context,
                        validationConfig
                ), executor
        ).thenComposeAsync(aVoid ->
                thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                        .thenComposeAsync(subModels ->
                                doValidateFeatures(subModels,
                                        thing.getFeatures().orElse(null),
                                        JsonPointer.empty(),
                                        context,
                                        validationConfig
                                ), executor
                        ), executor
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
                .thenComposeAsync(subModels ->
                        doValidateFeatures(subModels, features, resourcePath, context, validationConfig), executor
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
                .thenComposeAsync(aVoid -> CompletableFuture.allOf(
                                featureThingModels.entrySet()
                                        .stream()
                                        .map(entry -> {
                                            final String featureId = entry.getKey();
                                            if (validationConfig.getFeatureValidationConfig()
                                                    .isForbidFeatureDescriptionDeletion() &&
                                                    entry.getValue() != null && Optional.ofNullable(features)
                                                    .flatMap(fs -> fs.getFeature(featureId))
                                                    .flatMap(Feature::getDefinition)
                                                    .isEmpty()
                                            ) {
                                                return doValidateFeatureDefinitionDeletion(featureId,
                                                        context.dittoHeaders(), context
                                                );
                                            } else {
                                                return success();
                                            }
                                        })
                                        .map(CompletionStage::toCompletableFuture)
                                        .toArray(CompletableFuture[]::new)
                        ),
                        executor
                ).thenComposeAsync(aVoid ->
                        selectValidation(validationConfig)
                                .validateFeaturesProperties(featureThingModels, features, resourcePath, context),
                        executor
                );
    }

    private CompletionStage<Void> doValidateFeature(@Nullable final ThingModel thingModel,
            @Nullable final FeatureDefinition previousFeatureDefinition,
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
                        doValidateFeature(thingModel, previousFeatureDefinition, featureThingModel,
                                feature, resourcePath, context, validationConfig
                        )
                ))
                .orElseGet(() ->
                        doValidateFeature(thingModel, previousFeatureDefinition, null,
                                feature, resourcePath, context, validationConfig
                        )
                );
    }

    private CompletionStage<Void> doValidateFeature(@Nullable final ThingModel thingModel,
            @Nullable final FeatureDefinition previousFeatureDefinition,
            @Nullable final ThingModel featureThingModel,
            final Feature feature,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final WotThingModelValidation selectedValidation = selectValidation(validationConfig);
        final CompletionStage<Void> firstStage;
        if (validationConfig.getFeatureValidationConfig().isForbidFeatureDescriptionDeletion() &&
                previousFeatureDefinition != null && feature.getDefinition().isEmpty()
        ) {
            firstStage = doValidateFeatureDefinitionDeletion(
                    feature.getId(),
                    context.dittoHeaders(),
                    context
            );
        } else {
            firstStage = CompletableFuture.completedStage(null);
        }

        final CompletionStage<Void> secondStage;
        if (thingModel != null && featureThingModel != null) {
            secondStage = thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                    .thenComposeAsync(subModels ->
                            selectedValidation.validateFeaturePresence(
                                    reduceSubmodelMapKeyToFeatureId(subModels), feature, context
                            ).thenComposeAsync(aVoid ->
                                    selectedValidation.validateFeature(featureThingModel, feature,
                                            resourcePath, context), executor
                            ), executor
                    );
        } else if (thingModel != null) {
            return thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                    .thenComposeAsync(subModels -> {
                        final Map<String, ThingModel> featureThingModels = reduceSubmodelMapKeyToFeatureId(subModels);
                        return selectedValidation.validateFeaturePresence(featureThingModels, feature, context)
                                .thenComposeAsync(aVoid ->
                                        selectedValidation.validateFeature(
                                                featureThingModels.get(feature.getId()),
                                                feature, resourcePath, context
                                        ), executor
                                );
                    }, executor);
        } else if (featureThingModel != null) {
            secondStage = selectedValidation.validateFeature(featureThingModel, feature, resourcePath, context);
        } else {
            secondStage = success();
        }
        return firstStage.thenComposeAsync(unused -> secondStage, executor);
    }

    private CompletionStage<Void> doValidateFeatureDefinitionDeletion(final String featureId,
            final DittoHeaders dittoHeaders,
            final ValidationContext context
    ) {
        return provideValidationConfigIfWotValidationEnabled(context)
                .filter(validationConfig -> validationConfig.isEnabled() &&
                        validationConfig.getFeatureValidationConfig().isForbidFeatureDescriptionDeletion()
                )
                .filter(validationConfig -> Optional.ofNullable(context.featureDefinition())
                        .map(FeatureDefinition::getFirstIdentifier)
                        .filter(definitionIdentifier -> definitionIdentifier.getUrl().isPresent()) // only for URLs in the definition
                        .isPresent()
                )
                .map(validationConfig ->
                        CompletableFuture.<Void>failedStage(
                                WotThingModelPayloadValidationException
                                        .newBuilder("Deleting the Feature <" + featureId + ">'s " +
                                                "<definition> is not allowed")
                                        .dittoHeaders(dittoHeaders)
                                        .build()
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
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
