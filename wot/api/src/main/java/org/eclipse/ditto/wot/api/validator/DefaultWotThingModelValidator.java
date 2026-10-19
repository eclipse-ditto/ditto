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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.json.JsonKey;
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
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.wot.api.resolver.ThingSubmodel;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;
import org.eclipse.ditto.wot.validation.JsonSchemaCacheKey;
import org.eclipse.ditto.wot.validation.ValidationContext;
import org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException;
import org.eclipse.ditto.wot.validation.WotThingModelValidation;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

import com.networknt.schema.Schema;

/**
 * Default Ditto specific implementation of {@link WotThingModelValidator}.
 */
@Immutable
public final class DefaultWotThingModelValidator implements WotThingModelValidator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWotThingModelValidator.class);
    private static final AtomicReference<DefaultWotThingModelValidator> INSTANCE = new AtomicReference<>();

    private static final String JSON_SCHEMA_CACHE = "json_schema_cache";

    private static final String ALTERNATIVE_ACCEPTED_METRIC = "wot_validation_accepted_by_alternative";
    private static final String TAG_THING_DEFINITION = "thing_definition";
    private static final String TAG_ALTERNATIVE_THING_DEFINITION = "alternative_thing_definition";

    private final WotThingModelResolver thingModelResolver;
    private final Executor executor;
    @Nullable private final Cache<JsonSchemaCacheKey, Schema> jsonSchemaCache;

    private TmValidationConfig dynamicConfig;

    private DefaultWotThingModelValidator(final WotThingModelResolver thingModelResolver,
            final Executor executor,
            final TmValidationConfig initialConfig) {
        this.thingModelResolver = thingModelResolver;
        this.executor = executor;
        LOG.debug("Initial TmValidationConfig: {}", initialConfig);
        this.dynamicConfig = initialConfig;
        if (initialConfig.isJsonSchemaCacheEnabled()) {
            jsonSchemaCache =
                    CacheFactory.createCache(initialConfig.getJsonSchemaCacheConfig(), JSON_SCHEMA_CACHE, executor);
        } else {
            jsonSchemaCache = null;
        }
    }

    /**
     * Gets the singleton instance of the validator, creating it if it doesn't exist.
     *
     * @param thingModelResolver the ThingModel resolver to use
     * @param executor the executor to use
     * @param initialConfig the initial validation config
     * @return the singleton validator instance
     */
    public static DefaultWotThingModelValidator getInstance(final WotThingModelResolver thingModelResolver,
            final Executor executor,
            final TmValidationConfig initialConfig) {
        return INSTANCE.updateAndGet(existing -> {
            if (existing == null) {
                return new DefaultWotThingModelValidator(thingModelResolver, executor, initialConfig);
            }
            return existing;
        });
    }

    @Override
    public void updateConfig(final TmValidationConfig newConfig) {
        final TmValidationConfig oldConfig = dynamicConfig;
        LOG.debug("Updating config from Old config JSON={} to New config JSON={}", oldConfig, newConfig);
        dynamicConfig = newConfig;
    }

    /**
     * Delegates config accessors to the current dynamic config.
     *
     * @return whether the ThingModel validation of Things/Features should be enabled or not.
     */
    public boolean isEnabled() {
        return dynamicConfig.isEnabled();
    }

    /**
     * Delegates config accessors to the current dynamic config.
     *
     * @return whether to instead of to reject/fail API calls (when enabled=true), log a WARNING log instead
     */
    public boolean logWarningInsteadOfFailingApiCalls() {
        return dynamicConfig.logWarningInsteadOfFailingApiCalls();
    }

    @Override
    public CompletionStage<Void> validateThing(final Thing thing,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thing.getDefinition().orElse(null));
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> thing.getDefinition()
                        .map(thingDefinition -> validateThing(thingDefinition, thing, resourcePath, dittoHeaders))
                        .orElseGet(DefaultWotThingModelValidator::success))
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
                        validateWithAlternatives(
                                doValidateThing(Optional.ofNullable(thingDefinition).orElseThrow(),
                                        thingModel, thing, context, validationConfig
                                ),
                                validationConfig, context, "validateThing",
                                alternativeThingModel -> doValidateThing(thingDefinition,
                                        alternativeThingModel, thing, context, validationConfig
                                )
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
                        validateWithAlternatives(
                                doValidateThing(thingDefinition, thingModel, thing, context, validationConfig),
                                validationConfig, context, "validateThing",
                                alternativeThingModel -> doValidateThing(thingDefinition,
                                        alternativeThingModel, thing, context, validationConfig
                                )
                        )
                )
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateMergeThing(@Nullable final ThingDefinition thingDefinition,
            final MergeThing mergeThing,
            final Thing thing,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig -> fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                        validateWithAlternatives(
                                doValidateMergeThing(Optional.ofNullable(thingDefinition).orElseThrow(),
                                        thingModel, mergeThing, thing, context, validationConfig
                                ),
                                validationConfig, context, "validateMergeThing",
                                alternativeThingModel -> doValidateMergeThingChanges(thingDefinition,
                                        alternativeThingModel, mergeThing, thing, context, validationConfig
                                )
                        )
                ))
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    @Override
    public CompletionStage<Void> validateMergeThing(final ThingDefinition thingDefinition,
            final ThingModel thingModel,
            final MergeThing mergeThing,
            final Thing thing,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final ValidationContext context = buildValidationContext(dittoHeaders, thingDefinition);
        return provideValidationConfigIfWotValidationEnabled(context)
                .map(validationConfig ->
                        validateWithAlternatives(
                                doValidateMergeThing(thingDefinition, thingModel, mergeThing, thing, context,
                                        validationConfig
                                ),
                                validationConfig, context, "validateMergeThing",
                                alternativeThingModel -> doValidateMergeThingChanges(thingDefinition,
                                        alternativeThingModel, mergeThing, thing, context, validationConfig
                                )
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
                        validateWithAlternatives(
                                doValidateThing(thingDefinition, thingModel, thing, context, validationConfig),
                                validationConfig, context, "validateThingDefinitionModification",
                                alternativeThingModel -> doValidateThing(thingDefinition,
                                        alternativeThingModel, thing, context, validationConfig
                                )
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
                        .filter(definitionIdentifier -> definitionIdentifier.getUrl()
                                .isPresent()) // only for URLs in the definition
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
                        validateWithAlternatives(
                                doValidateThingAttributes(thingModel, attributes, resourcePath, context,
                                        validationConfig
                                ),
                                validationConfig, context, "validateThingAttributes",
                                alternativeThingModel -> doValidateThingAttributes(alternativeThingModel,
                                        attributes, resourcePath, context, validationConfig
                                )
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
                        validateWithAlternatives(
                                doValidateThingAttributes(thingModel, attributes, resourcePath, context,
                                        validationConfig
                                ),
                                validationConfig, context, "validateThingAttributes",
                                alternativeThingModel -> doValidateThingAttributes(alternativeThingModel,
                                        attributes, resourcePath, context, validationConfig
                                )
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = thingModel ->
                            selectValidation(validationConfig)
                                    .validateThingAttribute(thingModel,
                                            attributePointer, attributeValue, resourcePath, context
                                    );
                    return fetchResolveAndValidateWith(thingDefinition, dittoHeaders, thingModel ->
                            validateWithAlternatives(validation.apply(thingModel),
                                    validationConfig, context, "validateThingAttribute", validation
                            )
                    );
                })
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
                                                validateWithAlternatives(
                                                        selectValidation(validationConfig)
                                                                .validateThingScopedDeletion(thingModel,
                                                                        reduceSubmodelMapKeyToFeatureId(subModels),
                                                                        resourcePath,
                                                                        context
                                                                ),
                                                        validationConfig, context, "validateThingScopedDeletion",
                                                        alternativeThingModel -> thingModelResolver
                                                                .resolveThingModelSubmodels(alternativeThingModel,
                                                                        context.dittoHeaders())
                                                                .thenComposeAsync(alternativeSubModels ->
                                                                                selectValidation(validationConfig)
                                                                                        .validateThingScopedDeletion(
                                                                                                alternativeThingModel,
                                                                                                reduceSubmodelMapKeyToFeatureId(
                                                                                                        alternativeSubModels),
                                                                                                resourcePath,
                                                                                                context
                                                                                        ),
                                                                        executor
                                                                )
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = thingModel ->
                            CompletableFuture.supplyAsync(inputPayloadSupplier, executor)
                                    .thenComposeAsync(inputPayload ->
                                                    selectValidation(validationConfig)
                                                            .validateThingActionInput(thingModel,
                                                                    messageSubject, inputPayload, resourcePath, context
                                                            ),
                                            executor
                                    );
                    return validateWithAlternatives(
                            fetchResolveAndValidateWith(thingDefinition, dittoHeaders, validation),
                            validationConfig, context, "validateThingActionInput", validation
                    );
                })
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = thingModel ->
                            CompletableFuture.supplyAsync(outputPayloadSupplier, executor)
                                    .thenComposeAsync(outputPayload ->
                                                    selectValidation(validationConfig)
                                                            .validateThingActionOutput(thingModel,
                                                                    messageSubject, outputPayload, resourcePath, context
                                                            ),
                                            executor
                                    );
                    return validateWithAlternatives(
                            fetchResolveAndValidateWith(thingDefinition, dittoHeaders, validation),
                            validationConfig, context, "validateThingActionOutput", validation
                    );
                })
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = thingModel ->
                            CompletableFuture.supplyAsync(dataPayloadSupplier, executor)
                                    .thenComposeAsync(dataPayload ->
                                                    selectValidation(validationConfig)
                                                            .validateThingEventData(thingModel,
                                                                    messageSubject, dataPayload, resourcePath, context
                                                            ),
                                            executor
                                    );
                    return validateWithAlternatives(
                            fetchResolveAndValidateWith(thingDefinition, dittoHeaders, validation),
                            validationConfig, context, "validateThingEventData", validation
                    );
                })
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
                        validateWithAlternatives(
                                doValidateFeatures(thingModel, features, resourcePath, context, validationConfig),
                                validationConfig, context, "validateFeatures",
                                alternativeThingModel -> doValidateFeatures(alternativeThingModel,
                                        features, resourcePath, context, validationConfig
                                )
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
                        validateWithAlternatives(
                                doValidateFeatures(thingModel, features, resourcePath, context, validationConfig),
                                validationConfig, context, "validateFeatures",
                                alternativeThingModel -> doValidateFeatures(alternativeThingModel,
                                        features, resourcePath, context, validationConfig
                                )
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
                    final Function<ThingModel, CompletionStage<Void>> alternativeValidation =
                            againstAlternativeFeatureSubmodel(feature.getId(), context,
                                    (alternativeThingModel, featureThingModels, featureThingModel) ->
                                            doValidateFeature(alternativeThingModel, featureDefinition,
                                                    featureThingModel, feature, resourcePath, context,
                                                    validationConfig
                                            )
                            );
                    final Optional<URL> urlOpt = Optional.ofNullable(thingDefinition).flatMap(ThingDefinition::getUrl);
                    return urlOpt.map(url -> fetchResolveAndValidateWith(url, dittoHeaders, thingModel ->
                                            validateWithAlternatives(
                                                    doValidateFeature(thingModel, featureDefinition,
                                                            feature, resourcePath, context, validationConfig
                                                    ),
                                                    validationConfig, context, "validateFeature",
                                                    alternativeValidation
                                            )
                                    )
                            )
                            .orElseGet(() ->
                                    validateWithAlternatives(
                                            doValidateFeature(null, featureDefinition,
                                                    feature, resourcePath, context, validationConfig
                                            ),
                                            validationConfig, context, "validateFeature",
                                            alternativeValidation
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
                        validateWithAlternatives(
                                doValidateFeature(thingModel, featureDefinition, featureThingModel,
                                        feature, resourcePath, context, validationConfig
                                ),
                                validationConfig, context, "validateFeature",
                                againstAlternativeFeatureSubmodel(feature.getId(), context,
                                        (alternativeThingModel, featureThingModels, alternativeFeatureThingModel) ->
                                                doValidateFeature(alternativeThingModel, featureDefinition,
                                                        alternativeFeatureThingModel, feature, resourcePath, context,
                                                        validationConfig
                                                )
                                )
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
                                validateWithAlternatives(
                                        selectValidation(validationConfig)
                                                .validateFeature(featureThingModel, feature, resourcePath, context),
                                        validationConfig, context, "validateFeatureDefinitionModification",
                                        againstAlternativeFeatureSubmodel(feature.getId(), context,
                                                (alternativeThingModel, featureThingModels,
                                                        alternativeFeatureThingModel) ->
                                                        selectValidation(validationConfig)
                                                                .validateFeature(alternativeFeatureThingModel,
                                                                        feature, resourcePath, context)
                                        )
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
                                doValidateFeatureProperties(featureThingModelWithExtensionsAndImports, featureId,
                                        featureProperties, desiredProperties, resourcePath, context, validationConfig
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
                        doValidateFeatureProperties(featureThingModel, featureId, featureProperties,
                                desiredProperties, resourcePath, context, validationConfig
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = featureThingModel ->
                            selectValidation(validationConfig)
                                    .validateFeatureProperty(featureThingModel,
                                            featureId, propertyPointer, propertyValue, desiredProperty,
                                            resourcePath, context
                                    );
                    return fetchResolveAndValidateWith(
                            Optional.ofNullable(featureDefinition).map(FeatureDefinition::getFirstIdentifier)
                                    .orElse(null),
                            dittoHeaders, featureThingModel ->
                                    validateWithAlternatives(validation.apply(featureThingModel),
                                            validationConfig, context, "validateFeatureProperty",
                                            againstAlternativeFeatureSubmodel(featureId, context,
                                                    (alternativeThingModel, featureThingModels,
                                                            alternativeFeatureThingModel) ->
                                                            validation.apply(alternativeFeatureThingModel)
                                            )
                                    )
                    );
                })
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
                                                                validateWithAlternatives(
                                                                        selectValidation(validationConfig)
                                                                                .validateFeatureScopedDeletion(
                                                                                        reduceSubmodelMapKeyToFeatureId(
                                                                                                subModels),
                                                                                        featureThingModel, featureId,
                                                                                        resourcePath, context
                                                                                ),
                                                                        validationConfig, context,
                                                                        "validateFeatureScopedDeletion",
                                                                        againstAlternativeFeatureSubmodel(featureId,
                                                                                context,
                                                                                (alternativeThingModel,
                                                                                        featureThingModels,
                                                                                        alternativeFeatureThingModel) ->
                                                                                        selectValidation(validationConfig)
                                                                                                .validateFeatureScopedDeletion(
                                                                                                        featureThingModels,
                                                                                                        alternativeFeatureThingModel,
                                                                                                        featureId,
                                                                                                        resourcePath,
                                                                                                        context
                                                                                                )
                                                                        )
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = featureThingModel ->
                            CompletableFuture.supplyAsync(inputPayloadSupplier, executor)
                                    .thenComposeAsync(inputPayload ->
                                                    selectValidation(validationConfig)
                                                            .validateFeatureActionInput(featureThingModel,
                                                                    featureId, messageSubject, inputPayload,
                                                                    resourcePath, context
                                                            ),
                                            executor
                                    );
                    return validateWithAlternatives(
                            fetchResolveAndValidateWith(
                                    Optional.ofNullable(featureDefinition)
                                            .map(FeatureDefinition::getFirstIdentifier).orElse(null),
                                    dittoHeaders, validation
                            ),
                            validationConfig, context, "validateFeatureActionInput",
                            againstAlternativeFeatureSubmodel(featureId, context,
                                    (alternativeThingModel, featureThingModels, alternativeFeatureThingModel) ->
                                            validation.apply(alternativeFeatureThingModel)
                            )
                    );
                })
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = featureThingModel ->
                            CompletableFuture.supplyAsync(outputPayloadSupplier, executor)
                                    .thenComposeAsync(outputPayload ->
                                                    selectValidation(validationConfig)
                                                            .validateFeatureActionOutput(featureThingModel,
                                                                    featureId, messageSubject, outputPayload,
                                                                    resourcePath, context
                                                            ),
                                            executor
                                    );
                    return validateWithAlternatives(
                            fetchResolveAndValidateWith(
                                    Optional.ofNullable(featureDefinition)
                                            .map(FeatureDefinition::getFirstIdentifier).orElse(null),
                                    dittoHeaders, validation
                            ),
                            validationConfig, context, "validateFeatureActionOutput",
                            againstAlternativeFeatureSubmodel(featureId, context,
                                    (alternativeThingModel, featureThingModels, alternativeFeatureThingModel) ->
                                            validation.apply(alternativeFeatureThingModel)
                            )
                    );
                })
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
                .map(validationConfig -> {
                    final Function<ThingModel, CompletionStage<Void>> validation = featureThingModel ->
                            CompletableFuture.supplyAsync(dataPayloadSupplier, executor)
                                    .thenComposeAsync(dataPayload ->
                                                    selectValidation(validationConfig)
                                                            .validateFeatureEventData(featureThingModel,
                                                                    featureId, messageSubject, dataPayload,
                                                                    resourcePath, context
                                                            ),
                                            executor
                                    );
                    return validateWithAlternatives(
                            fetchResolveAndValidateWith(
                                    Optional.ofNullable(featureDefinition)
                                            .map(FeatureDefinition::getFirstIdentifier).orElse(null),
                                    dittoHeaders, validation
                            ),
                            validationConfig, context, "validateFeatureEventData",
                            againstAlternativeFeatureSubmodel(featureId, context,
                                    (alternativeThingModel, featureThingModels, alternativeFeatureThingModel) ->
                                            validation.apply(alternativeFeatureThingModel)
                            )
                    );
                })
                .orElseGet(DefaultWotThingModelValidator::success);
    }

    private WotThingModelValidation selectValidation(final TmValidationConfig validationConfig) {
        return WotThingModelValidation.of(validationConfig, executor, jsonSchemaCache);
    }

    private Optional<TmValidationConfig> provideValidationConfigIfWotValidationEnabled(
            final ValidationContext context
    ) {
        if (FeatureToggle.isWotIntegrationFeatureEnabled() && dynamicConfig.isEnabled()) {
            return Optional.of(dynamicConfig.withValidationContext(context));
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

    private CompletionStage<Void> validateWithAlternatives(final CompletionStage<Void> primaryValidation,
            final TmValidationConfig validationConfig,
            final ValidationContext context,
            final String loggingHintSource,
            final Function<ThingModel, CompletionStage<Void>> alternativeThingModelValidation
    ) {
        return primaryValidation
                .exceptionallyComposeAsync(primaryError -> {
                    final Throwable cause = unwrapCompletionException(primaryError);
                    final List<ThingDefinition> alternatives = validationConfig.getAlternativeThingDefinitions()
                            .stream()
                            .filter(alternative -> !alternative.equals(context.thingDefinition()))
                            .toList();
                    if (cause instanceof WotThingModelPayloadValidationException && !alternatives.isEmpty()) {
                        return validateAgainstAlternative(alternatives, 0, primaryError, context,
                                loggingHintSource, alternativeThingModelValidation);
                    } else {
                        return CompletableFuture.failedStage(primaryError);
                    }
                }, executor)
                .handleAsync(applyLogingErrorOnlyStrategy(validationConfig, context, loggingHintSource), executor);
    }

    private CompletionStage<Void> validateAgainstAlternative(final List<ThingDefinition> alternatives,
            final int index,
            final Throwable primaryError,
            final ValidationContext context,
            final String loggingHintSource,
            final Function<ThingModel, CompletionStage<Void>> alternativeThingModelValidation
    ) {
        if (index >= alternatives.size()) {
            return CompletableFuture.failedStage(primaryError);
        }
        final ThingDefinition alternative = alternatives.get(index);
        return thingModelResolver.resolveThingModel(alternative.getUrl().orElseThrow(), context.dittoHeaders())
                .thenComposeAsync(alternativeThingModelValidation, executor)
                .thenApply(aVoid -> {
                    onAcceptedByAlternative(context, loggingHintSource, alternative);
                    return aVoid;
                })
                .exceptionallyComposeAsync(alternativeError -> {
                    logAlternativeFailure(context, loggingHintSource, alternative, alternativeError);
                    return validateAgainstAlternative(alternatives, index + 1, primaryError, context,
                            loggingHintSource, alternativeThingModelValidation);
                }, executor);
    }

    private Function<ThingModel, CompletionStage<Void>> againstAlternativeFeatureSubmodel(final String featureId,
            final ValidationContext context,
            final AlternativeFeatureValidation alternativeFeatureValidation
    ) {
        return alternativeThingModel ->
                thingModelResolver.resolveThingModelSubmodels(alternativeThingModel, context.dittoHeaders())
                        .thenComposeAsync(subModels -> {
                            final Map<String, ThingModel> featureThingModels =
                                    reduceSubmodelMapKeyToFeatureId(subModels);
                            return Optional.ofNullable(featureThingModels.get(featureId))
                                    .map(featureThingModel -> alternativeFeatureValidation.validate(
                                            alternativeThingModel, featureThingModels, featureThingModel))
                                    .orElseGet(() -> featureNotModeledInAlternative(featureId, context));
                        }, executor);
    }

    @FunctionalInterface
    private interface AlternativeFeatureValidation {

        CompletionStage<Void> validate(ThingModel alternativeThingModel,
                Map<String, ThingModel> featureThingModels,
                ThingModel featureThingModel);
    }

    private static CompletionStage<Void> featureNotModeledInAlternative(final String featureId,
            final ValidationContext context
    ) {
        return CompletableFuture.failedStage(WotThingModelPayloadValidationException
                .newBuilder("The Feature <" + featureId + "> is not modeled in the alternative Thing model")
                .dittoHeaders(context.dittoHeaders())
                .build());
    }

    private static void onAcceptedByAlternative(final ValidationContext context,
            final String loggingHintSource,
            final ThingDefinition alternative
    ) {
        DittoMetrics.counter(ALTERNATIVE_ACCEPTED_METRIC)
                .tag(TAG_THING_DEFINITION, String.valueOf(context.thingDefinition()))
                .tag(TAG_ALTERNATIVE_THING_DEFINITION, alternative.toString())
                .increment();
        withLoggingMdc(context.dittoHeaders(), () ->
                LOG.info("WoT based validation of Thing <{}> in <{}()> failed for <TD {}>, " +
                                "but passed for the alternative <TD {}>",
                        context.thingId(), loggingHintSource, context.thingDefinition(), alternative
                )
        );
    }

    private static void logAlternativeFailure(final ValidationContext context,
            final String loggingHintSource,
            final ThingDefinition alternative,
            final Throwable throwable
    ) {
        withLoggingMdc(context.dittoHeaders(), () ->
                LOG.debug("WoT based validation of Thing <{}> in <{}()> also failed for the alternative <TD {}> " +
                                "due to: <{}>",
                        context.thingId(), loggingHintSource, alternative,
                        unwrapCompletionException(throwable).toString()
                )
        );
    }

    private static Throwable unwrapCompletionException(final Throwable throwable) {
        return (throwable instanceof CompletionException ce && ce.getCause() != null) ? ce.getCause() : throwable;
    }

    private static void logValidationWarning(final boolean logAsWarning,
            final ValidationContext context,
            final String loggingHintSource,
            final Throwable throwable
    ) {
        withLoggingMdc(context.dittoHeaders(), () -> {
            final LoggingEventBuilder logBuilder = logAsWarning ? LOG.atWarn() : LOG.atInfo();
            logBuilder.log("WoT based validation of Thing <{}> in <{}()> failed for <TD {}>/<FD {}> due to: <{}>",
                    context.thingId(), loggingHintSource, context.thingDefinition(), context.featureDefinition(),
                    throwable.toString()
            );
        });
    }

    private static void withLoggingMdc(final DittoHeaders dittoHeaders, final Runnable logging) {
        dittoHeaders.getCorrelationId().ifPresent(cId -> MDC.put("correlation-id", cId));
        // positions defined by https://www.w3.org/TR/trace-context/#traceparent-header-field-values to contain the "trace-id"
        dittoHeaders.getTraceParent().ifPresent(traceParent -> {
                    MDC.put("traceparent-trace-id", traceParent.substring(3, 35));
                    MDC.put("traceparent-span-id", traceParent.substring(36, 52));
                }
        );
        logging.run();
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

    private CompletionStage<Void> doValidateMergeThing(final ThingDefinition thingDefinition,
            final ThingModel thingModel,
            final MergeThing mergeThing,
            final Thing thing,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final JsonValue mergeValue = mergeThing.getEntity().orElseGet(mergeThing::getValue);
        final Set<JsonPointer> affectedLeaves = calculateLeaves(mergeThing.getResourcePath(), mergeValue);

        if (isDefinitionChangedByMerge(affectedLeaves, mergeValue, thing)) {
            // if the definition is affected and is different then before, we need to validate the whole thing
            return doValidateThing(thingDefinition, thingModel, thing, context, validationConfig);
        } else {
            final MergeThingContext mergeThingContext = calculateMergeThingContext(affectedLeaves);

            final CompletionStage<Void> firstStage;
            if (validationConfig.getThingValidationConfig().isForbidThingDescriptionDeletion() &&
                    thing.getDefinition().isEmpty()) {
                firstStage = validateThingDefinitionDeletion(thingDefinition, context.dittoHeaders());
            } else {
                firstStage = success();
            }
            return firstStage.thenComposeAsync(unused ->
                            mergeThingContext.containsAttributes().get() ? doValidateThingAttributes(thingModel,
                                    thing.getAttributes().orElse(null),
                                    Thing.JsonFields.ATTRIBUTES.getPointer(),
                                    context,
                                    validationConfig
                            ) : CompletableFuture.completedStage(null),
                    executor
            ).thenComposeAsync(aVoid ->
                            mergeThingContext.containsFeatures().get() ?
                                    thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                                            .thenComposeAsync(subModels ->
                                                            doValidateMergeThingAffectedFeatures(thing, context,
                                                                    validationConfig,
                                                                    subModels,
                                                                    mergeThingContext.affectedFeatures()
                                                            ),
                                                    executor
                                            ) : CompletableFuture.completedStage(null),
                    executor
            );
        }
    }

    private CompletionStage<Void> doValidateMergeThingChanges(final ThingDefinition thingDefinition,
            final ThingModel thingModel,
            final MergeThing mergeThing,
            final Thing thing,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final JsonValue mergeValue = mergeThing.getEntity().orElseGet(mergeThing::getValue);
        final Set<JsonPointer> affectedLeaves = calculateLeaves(mergeThing.getResourcePath(), mergeValue);
        if (isDefinitionChangedByMerge(affectedLeaves, mergeValue, thing)) {
            return doValidateThing(thingDefinition, thingModel, thing, context, validationConfig);
        }

        final Set<JsonPointer> changedParts = affectedLeaves.stream()
                .map(DefaultWotThingModelValidator::toChangedPart)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return thingModelResolver.resolveThingModelSubmodels(thingModel, context.dittoHeaders())
                .thenComposeAsync(subModels -> CompletableFuture.allOf(changedParts.stream()
                                .map(changedPart -> validateChangedPart(changedPart, thingModel, subModels, thing,
                                        context, validationConfig))
                                .map(CompletionStage::toCompletableFuture)
                                .toArray(CompletableFuture[]::new)
                        ),
                        executor
                );
    }

    // a nested leaf is validated via its top-level attribute/property, as partial objects would miss required fields
    private static Optional<JsonPointer> toChangedPart(final JsonPointer leaf) {
        final Optional<JsonKey> root = leaf.getRoot();
        if (root.filter(Thing.JsonFields.ATTRIBUTES.getPointer().getRoot().orElseThrow()::equals).isPresent()) {
            return leaf.getPrefixPointer(Math.min(leaf.getLevelCount(), 2));
        } else if (root.filter(Thing.JsonFields.FEATURES.getPointer().getRoot().orElseThrow()::equals).isPresent()) {
            final boolean isPropertiesLeaf = leaf.get(2)
                    .filter(key -> isFeatureProperties(key) || isFeatureDesiredProperties(key))
                    .isPresent();
            return leaf.getPrefixPointer(isPropertiesLeaf ? Math.min(leaf.getLevelCount(), 4) :
                    Math.min(leaf.getLevelCount(), 2));
        } else {
            return Optional.empty();
        }
    }

    private CompletionStage<Void> validateChangedPart(final JsonPointer changedPart,
            final ThingModel thingModel,
            final Map<ThingSubmodel, ThingModel> subModels,
            final Thing thing,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final WotThingModelValidation validation = selectValidation(validationConfig);
        final Map<String, ThingModel> featureThingModels = reduceSubmodelMapKeyToFeatureId(subModels);
        final boolean concernsAttributes = changedPart.getRoot()
                .filter(Thing.JsonFields.ATTRIBUTES.getPointer().getRoot().orElseThrow()::equals)
                .isPresent();
        if (concernsAttributes) {
            if (changedPart.getLevelCount() == 1) {
                return thing.getAttributes()
                        .map(attributes ->
                                validation.validateThingAttributes(thingModel, attributes, changedPart, context))
                        .orElseGet(() -> validation.validateThingScopedDeletion(thingModel, featureThingModels,
                                changedPart, context));
            }
            final JsonPointer attributePointer = changedPart.getSubPointer(1).orElseThrow();
            return thing.getAttributes()
                    .flatMap(attributes -> attributes.getValue(attributePointer))
                    .map(attributeValue -> validation.validateThingAttribute(thingModel, attributePointer,
                            attributeValue, changedPart, context))
                    .orElseGet(() -> validation.validateThingScopedDeletion(thingModel, featureThingModels,
                            changedPart, context));
        }

        if (changedPart.getLevelCount() == 1) {
            return doValidateFeatures(subModels, thing.getFeatures().orElse(null), JsonPointer.empty(), context,
                    validationConfig);
        }
        final String featureId = changedPart.get(1).orElseThrow().toString();
        final ThingModel featureThingModel = featureThingModels.get(featureId);
        if (featureThingModel == null) {
            return featureNotModeledInAlternative(featureId, context);
        }
        final Optional<Feature> feature = thing.getFeatures().flatMap(features -> features.getFeature(featureId));
        if (changedPart.getLevelCount() == 2) {
            return feature
                    .map(f -> validation.validateFeature(featureThingModel, f, changedPart, context))
                    .orElseGet(() -> validation.validateFeatureScopedDeletion(featureThingModels, featureThingModel,
                            featureId, changedPart, context));
        }
        final boolean desiredProperties = changedPart.get(2)
                .filter(DefaultWotThingModelValidator::isFeatureDesiredProperties)
                .isPresent();
        final Optional<FeatureProperties> properties =
                feature.flatMap(desiredProperties ? Feature::getDesiredProperties : Feature::getProperties);
        final Optional<CompletionStage<Void>> validationOfPresentPart = changedPart.getSubPointer(3)
                .map(propertyPointer -> properties.flatMap(p -> p.getValue(propertyPointer))
                        .map(propertyValue -> validation.validateFeatureProperty(featureThingModel, featureId,
                                propertyPointer, propertyValue, desiredProperties, changedPart, context)))
                .orElseGet(() -> properties
                        .map(p -> validation.validateFeatureProperties(featureThingModel, featureId, p,
                                desiredProperties, changedPart, context)));
        return validationOfPresentPart
                .orElseGet(() -> validation.validateFeatureScopedDeletion(featureThingModels, featureThingModel,
                        featureId, changedPart, context));
    }

    private static boolean isFeatureProperties(final JsonKey key) {
        return Feature.JsonFields.PROPERTIES.getPointer().getRoot().orElseThrow().equals(key);
    }

    private static boolean isFeatureDesiredProperties(final JsonKey key) {
        return Feature.JsonFields.DESIRED_PROPERTIES.getPointer().getRoot().orElseThrow().equals(key);
    }

    private static boolean isDefinitionChangedByMerge(final Set<JsonPointer> affectedLeaves,
            final JsonValue mergeValue,
            final Thing thing
    ) {
        return affectedLeaves.contains(Thing.JsonFields.DEFINITION.getPointer()) &&
                !mergeValue.asObject().getValue(Thing.JsonFields.DEFINITION)
                        .equals(thing.getDefinition().map(ThingDefinition::toJson));
    }

    private MergeThingContext calculateMergeThingContext(final Set<JsonPointer> affectedLeaves) {
        final Set<String> affectedFeatures = new HashSet<>();
        final AtomicBoolean containsAttributes = new AtomicBoolean(false);
        final AtomicBoolean containsFeatures = new AtomicBoolean(false);

        affectedLeaves.forEach(leave -> {
            if (!containsAttributes.get() &&
                    Thing.JsonFields.ATTRIBUTES.getPointer().equals(leave.getPrefixPointer(1).orElse(null))
            ){
                containsAttributes.set(true);
            }
            if (!containsFeatures.get() &&
                    Thing.JsonFields.FEATURES.getPointer().equals(leave.getPrefixPointer(1).orElse(null))
            ) {
                containsFeatures.set(true);
            }

            if (containsFeatures.get()) {
                leave.getPrefixPointer(2)
                        .flatMap(p -> p.getSubPointer(1))
                        .flatMap(JsonPointer::getRoot)
                        .map(JsonKey::toString)
                        .ifPresent(affectedFeatures::add);
            }
        });
        return new MergeThingContext(affectedFeatures, containsAttributes, containsFeatures);
    }

    private record MergeThingContext(Set<String> affectedFeatures, AtomicBoolean containsAttributes, AtomicBoolean containsFeatures) {}

    private CompletionStage<Void> doValidateMergeThingAffectedFeatures(final Thing thing,
            final ValidationContext context,
            final TmValidationConfig validationConfig,
            final Map<ThingSubmodel, ThingModel> subModels,
            final Set<String> affectedFeatures
    ) {
        final Map<ThingSubmodel, ThingModel> filteredSubModels = subModels.entrySet()
                .stream()
                .filter(subModel ->
                        affectedFeatures.contains(subModel.getKey().instanceName())
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Thing submodels: Duplicate key %s", u))
                            .dittoHeaders(context.dittoHeaders())
                            .build();
                }, LinkedHashMap::new));
        final Features filteredFeatures = thing.getFeatures()
                .map(features -> Features.newBuilder()
                        .setAll(features.stream()
                                .filter(feature ->
                                        affectedFeatures.contains(feature.getId()))
                                .toList()
                        )
                        .build()
                )
                .orElse(null);
        return doValidateFeatures(filteredSubModels,
                filteredFeatures,
                JsonPointer.empty(),
                context,
                validationConfig
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

    private CompletionStage<Void> doValidateFeatureProperties(final ThingModel featureThingModel,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final TmValidationConfig validationConfig
    ) {
        final Function<ThingModel, CompletionStage<Void>> validation = thingModel ->
                selectValidation(validationConfig)
                        .validateFeatureProperties(thingModel, featureId, featureProperties,
                                desiredProperties, resourcePath, context
                        );
        return validateWithAlternatives(validation.apply(featureThingModel),
                validationConfig, context, "validateFeatureProperties",
                againstAlternativeFeatureSubmodel(featureId, context,
                        (alternativeThingModel, featureThingModels, alternativeFeatureThingModel) ->
                                validation.apply(alternativeFeatureThingModel)
                )
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
                        .filter(definitionIdentifier -> definitionIdentifier.getUrl()
                                .isPresent()) // only for URLs in the definition
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

    private static Set<JsonPointer> calculateLeaves(final JsonPointer path, final JsonValue value) {
        if (value.isObject()) {
            return value.asObject().stream()
                    .map(f -> {
                        final JsonKey key = f.getKey();
                        if (isMergeWithNulledKeysByRegex(key, f.getValue())) {
                            // if regex is contained, this path is a leaf
                            return Set.of(path);
                        } else {
                            return calculateLeaves(path.append(key.asPointer()), f.getValue());
                        }
                    })
                    .reduce(new HashSet<>(), DefaultWotThingModelValidator::addAll,
                            DefaultWotThingModelValidator::addAll);
        } else {
            return Set.of(path);
        }
    }

    private static Set<JsonPointer> addAll(final Set<JsonPointer> result, final Set<JsonPointer> toBeAdded) {
        result.addAll(toBeAdded);
        return result;
    }

    private static boolean isMergeWithNulledKeysByRegex(final JsonKey key, final JsonValue value) {
        final String keyString = key.toString();
        if (keyString.startsWith("{{") && keyString.endsWith("}}")) {
            final String keyRegexWithoutCurly = keyString.substring(2, keyString.length() - 2).trim();
            return keyRegexWithoutCurly.startsWith("/") && keyRegexWithoutCurly.endsWith("/") &&
                    value.isNull();
        } else {
            return false;
        }
    }
}
