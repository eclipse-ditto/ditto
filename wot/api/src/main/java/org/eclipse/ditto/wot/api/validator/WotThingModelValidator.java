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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.wot.api.config.WotConfig;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Validates different aspects of Ditto a {@link Thing} against a WoT {@link ThingModel} linked in the Thing's
 * {@link ThingDefinition}:
 * <ul>
 * <li>Attributes</li>
 * <li>Features</li>
 * <li>Feature properties</li>
 * <li>Feature desired properties</li>
 * <li>Thing messages</li>
 * <li>Feature messages</li>
 * </ul>
 *
 * @since 3.6.0
 */
public interface WotThingModelValidator {

    /**
     * Validates the provided {@code thing} against its contained {@code thingDefinition} (if this links to a WoT TM).
     *
     * @param thing the Thing to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThing(Thing thing,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code thing} against the provided {@code thingDefinition} (if this links to a WoT TM).
     *
     * @param thingDefinition the ThingDefinition to retrieve the WoT TM from
     * @param thing the Thing to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThing(@Nullable ThingDefinition thingDefinition,
            Thing thing,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code thing} against the provided {@code thingModel}.
     *
     * @param thingModel the ThingModel to validate against
     * @param thing the Thing to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThing(ThingModel thingModel,
            Thing thing,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code thing} in context of a modification of its {@code thingDefinition}
     * (e.g. in order to update it to a new version).
     *
     * @param thingDefinition the new, updated ThingDefinition to retrieve the WoT TM from to validate against
     * @param thing the Thing to validate
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThingDefinitionModification(ThingDefinition thingDefinition,
            Thing thing,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code attributes} against on the provided {@code thingDefinition}
     * (if this links to a WoT TM).
     *
     * @param thingDefinition the ThingDefinition to retrieve the WoT TM from
     * @param attributes the attributes to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThingAttributes(@Nullable ThingDefinition thingDefinition,
            @Nullable Attributes attributes,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code attributes} against on the provided {@code thingModel}.
     *
     * @param thingModel the ThingModel to validate against
     * @param attributes the attributes to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThingAttributes(ThingModel thingModel,
            @Nullable Attributes attributes,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code attribute} against on the provided {@code thingDefinition}
     * (if this links to a WoT TM).
     *
     * @param thingDefinition the ThingDefinition to retrieve the WoT TM from
     * @param attributePointer the pointer (path) of the attribute to validate
     * @param attributeValue the attribute value to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThingAttribute(@Nullable ThingDefinition thingDefinition,
            JsonPointer attributePointer,
            JsonValue attributeValue,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code inputPayload} of the message with subject {@code messageSubject} against the
     * provided {@code thingDefinition}.
     *
     * @param thingDefinition the ThingDefinition to retrieve the WoT TM from
     * @param messageSubject the (Thing) message subject
     * @param inputPayload the input payload to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThingMessageInput(@Nullable ThingDefinition thingDefinition,
            String messageSubject,
            @Nullable JsonValue inputPayload,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code outputPayload} of the message with subject {@code messageSubject} against the
     * provided {@code thingDefinition}.
     *
     * @param thingDefinition the ThingDefinition to retrieve the WoT TM from
     * @param messageSubject the (Thing) message subject
     * @param outputPayload the output payload to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateThingMessageOutput(@Nullable ThingDefinition thingDefinition,
            String messageSubject,
            @Nullable JsonValue outputPayload,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code features} of a Thing against the provided {@code thingDefinition}
     * (if this links to a WoT TM).
     *
     * @param thingDefinition the ThingDefinition to retrieve the WoT TM from
     * @param features the features to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatures(@Nullable ThingDefinition thingDefinition,
            Features features,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code features} of a Thing against the provided {@code thingModel}.
     *
     * @param thingModel the ThingModel to validate against
     * @param features the features to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatures(ThingModel thingModel,
            Features features,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code feature} of a Thing against the provided {@code thingDefinition}
     * (if this links to a WoT TM).
     *
     * @param thingDefinition the ThingDefinition to retrieve the WoT TM from
     * @param feature the feature to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeature(@Nullable ThingDefinition thingDefinition,
            Feature feature,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code feature} of a Thing against the provided {@code thingModel}.
     *
     * @param thingModel the ThingModel to validate against
     * @param feature the feature to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeature(@Nullable ThingModel thingModel,
            @Nullable ThingModel featureThingModel,
            Feature feature,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code feature} in context of a modification of its {@code featureDefinition}
     * (e.g. in order to update it to a new version).
     *
     * @param featureDefinition the new, updated FeatureDefinition to retrieve the WoT TM from to validate against
     * @param feature the Feature to validate
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatureDefinitionModification(FeatureDefinition featureDefinition,
            Feature feature,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code featureProperties} against the provided {@code featureDefinition}
     * (if this links to a WoT TM).
     *
     * @param featureDefinition the FeatureDefinition to retrieve the WoT TM from
     * @param featureId the ID of the feature to validate the properties for
     * @param featureProperties the properties to validate
     * @param desiredProperties whether the desired properties should be validated
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatureProperties(@Nullable FeatureDefinition featureDefinition,
            String featureId,
            @Nullable FeatureProperties featureProperties,
            boolean desiredProperties,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code featureProperties} against the provided {@code featureDefinition}
     * (if this links to a WoT TM).
     *
     * @param featureThingModel the feature's ThingModel to validate against
     * @param featureId the ID of the feature to validate the properties for
     * @param featureProperties the properties to validate
     * @param desiredProperties whether the desired properties should be validated
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatureProperties(ThingModel featureThingModel,
            String featureId,
            @Nullable FeatureProperties featureProperties,
            boolean desiredProperties,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided feature property against the provided {@code featureDefinition}
     * (if this links to a WoT TM).
     *
     * @param featureDefinition the FeatureDefinition to retrieve the WoT TM from
     * @param featureId the ID of the feature to validate the properties for
     * @param propertyPointer the pointer (path) of the property to validate
     * @param propertyValue the property value to validate
     * @param desiredProperty whether a desired property should be validated
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatureProperty(@Nullable FeatureDefinition featureDefinition,
            String featureId,
            JsonPointer propertyPointer,
            JsonValue propertyValue,
            boolean desiredProperty,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code inputPayload} of the feature message with subject {@code messageSubject} against
     * the provided {@code featureDefinition}.
     *
     * @param featureDefinition the FeatureDefinition to retrieve the WoT TM from
     * @param featureId the ID of the feature to validate the message input payload for
     * @param messageSubject the (Feature) message subject
     * @param inputPayload the input payload to validate
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatureMessageInput(@Nullable FeatureDefinition featureDefinition,
            String featureId,
            String messageSubject,
            @Nullable JsonValue inputPayload,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Validates the provided {@code outputPayload} of the feature message with subject {@code messageSubject} against
     * the provided {@code featureDefinition}.
     *
     * @param featureDefinition the FeatureDefinition to retrieve the WoT TM from
     * @param featureId the ID of the feature to validate the message output payload for
     * @param outputPayload the output payload to validate
     * @param messageSubject the (Feature) message subject
     * @param resourcePath the originating path of the command which caused validation
     * @param dittoHeaders the DittoHeaders to use in order to build a potential exception
     * @return a CompletionStage finished successfully with {@code null} or finished exceptionally in case of a
     * validation error - exceptionally finished with a {@link org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException}
     */
    CompletionStage<Void> validateFeatureMessageOutput(@Nullable FeatureDefinition featureDefinition,
            String featureId,
            String messageSubject,
            @Nullable JsonValue outputPayload,
            JsonPointer resourcePath,
            DittoHeaders dittoHeaders
    );

    /**
     * Creates a new instance of WotThingModelValidator with the given {@code wotConfig}.
     *
     * @param wotConfig the WoT config to use.
     * @param thingModelResolver the ThingModel resolver to fetch and resolve (extensions, refs) of linked other
     * ThingModels during the generation process.
     * @param executor the executor to use to run async tasks.
     * @return the created WotThingModelValidator.
     */
    static WotThingModelValidator of(final WotConfig wotConfig,
            final WotThingModelResolver thingModelResolver,
            final Executor executor
    ) {
        return new DefaultWotThingModelValidator(wotConfig, thingModelResolver, executor);
    }
}
