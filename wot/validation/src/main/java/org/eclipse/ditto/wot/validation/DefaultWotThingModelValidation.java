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
package org.eclipse.ditto.wot.validation;

import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.enforceFeatureActionPayload;
import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.enforceFeatureEventPayload;
import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.enforceFeatureProperties;
import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.enforceFeaturePropertiesInAllSubmodels;
import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.enforceFeatureProperty;
import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.enforcePresenceOfModeledFeatures;
import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.enforcePresenceOfRequiredPropertiesUponFeatureLevelDeletion;
import static org.eclipse.ditto.wot.validation.InternalFeatureValidation.forbidNonModeledFeatures;
import static org.eclipse.ditto.wot.validation.InternalThingValidation.enforcePresenceOfRequiredPropertiesUponThingLevelDeletion;
import static org.eclipse.ditto.wot.validation.InternalThingValidation.enforceThingActionPayload;
import static org.eclipse.ditto.wot.validation.InternalThingValidation.enforceThingAttribute;
import static org.eclipse.ditto.wot.validation.InternalThingValidation.enforceThingAttributes;
import static org.eclipse.ditto.wot.validation.InternalThingValidation.enforceThingEventPayload;
import static org.eclipse.ditto.wot.validation.InternalValidation.success;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

/**
 * Default implementation for WoT ThingModel based validation/enforcement.
 */
final class DefaultWotThingModelValidation implements WotThingModelValidation {

    private final TmValidationConfig validationConfig;

    DefaultWotThingModelValidation(final TmValidationConfig validationConfig) {
        this.validationConfig = validationConfig;
    }

    @Override
    public CompletionStage<Void> validateThingAttributes(final ThingModel thingModel,
            @Nullable final Attributes attributes,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getThingValidationConfig().isEnforceAttributes() && attributes != null) {
            return enforceThingAttributes(thingModel,
                    attributes,
                    validationConfig.getThingValidationConfig().isForbidNonModeledAttributes(),
                    resourcePath,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingAttribute(final ThingModel thingModel,
            final JsonPointer attributePointer,
            final JsonValue attributeValue,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getThingValidationConfig().isEnforceAttributes()) {
            return enforceThingAttribute(thingModel,
                    attributePointer,
                    attributeValue,
                    validationConfig.getThingValidationConfig().isForbidNonModeledAttributes(),
                    resourcePath,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingScopedDeletion(final ThingModel thingModel,
            final Map<String, ThingModel> featureThingModels,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (resourcePath.getRoot().filter(DefaultWotThingModelValidation::isConcerningAttributes).isPresent() &&
                validationConfig.getThingValidationConfig().isEnforceAttributes()
        ) {
            return enforcePresenceOfRequiredPropertiesUponThingLevelDeletion(thingModel,
                    resourcePath,
                    context
            );
        }

        if (resourcePath.getRoot().filter(DefaultWotThingModelValidation::isConcerningFeatures).isPresent() &&
                validationConfig.getFeatureValidationConfig().isEnforcePresenceOfModeledFeatures() &&
                resourcePath.getLevelCount() == 1 && !featureThingModels.isEmpty()
        ) {
            final WotThingModelPayloadValidationException.Builder exceptionBuilder =
                    WotThingModelPayloadValidationException
                            .newBuilder("Could not delete all Features, " +
                                    "as there are submodels defined as in the Thing's model");
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        // all other cases should not be handled here, but in "validateFeatureScopedDeletion"

        return success();
    }

    private static boolean isConcerningAttributes(final JsonKey root) {
        return Thing.JsonFields.ATTRIBUTES.getPointer().getRoot().orElseThrow().equals(root);
    }

    private static boolean isConcerningFeatures(final JsonKey root) {
        return Thing.JsonFields.FEATURES.getPointer().getRoot().orElseThrow().equals(root);
    }

    @Override
    public CompletionStage<Void> validateThingActionInput(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getThingValidationConfig().isEnforceInboxMessagesInput()) {
            return enforceThingActionPayload(thingModel,
                    messageSubject,
                    inputPayload,
                    validationConfig.getThingValidationConfig().isForbidNonModeledInboxMessages(),
                    resourcePath,
                    true,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingActionOutput(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue outputPayload,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getThingValidationConfig().isEnforceInboxMessagesOutput()) {
            return enforceThingActionPayload(thingModel,
                    messageSubject,
                    outputPayload,
                    validationConfig.getThingValidationConfig().isForbidNonModeledInboxMessages(),
                    resourcePath,
                    false,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateThingEventData(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue dataPayload,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getThingValidationConfig().isEnforceOutboxMessages()) {
            return enforceThingEventPayload(thingModel,
                    messageSubject,
                    dataPayload,
                    validationConfig.getThingValidationConfig().isForbidNonModeledOutboxMessages(),
                    resourcePath,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeaturesPresence(final Map<String, ThingModel> featureThingModels,
            @Nullable final Features features,
            final ValidationContext context
    ) {
        final CompletableFuture<Void> firstStage;
        if (validationConfig.getFeatureValidationConfig().isEnforcePresenceOfModeledFeatures()) {
            firstStage = enforcePresenceOfModeledFeatures(features, featureThingModels.keySet(), context);
        } else {
            firstStage = success();
        }

        final CompletableFuture<Void> secondStage;
        if (validationConfig.getFeatureValidationConfig().isForbidNonModeledFeatures()) {
            secondStage = forbidNonModeledFeatures(features, featureThingModels.keySet(), context);
        } else {
            secondStage = success();
        }
        return firstStage.thenCompose(unused -> secondStage);
    }

    @Override
    public CompletionStage<Void> validateFeaturesProperties(final Map<String, ThingModel> featureThingModels,
            @Nullable final Features features,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final CompletableFuture<Void> firstStage;
        if (validationConfig.getFeatureValidationConfig().isEnforceProperties() && features != null) {
            firstStage = enforceFeaturePropertiesInAllSubmodels(
                    featureThingModels,
                    features,
                    false,
                    validationConfig.getFeatureValidationConfig().isForbidNonModeledProperties(),
                    resourcePath,
                    context
            ).thenApply(aVoid -> null);
        } else {
            firstStage = success();
        }

        final CompletableFuture<Void> secondStage;
        if (validationConfig.getFeatureValidationConfig().isEnforceDesiredProperties() && features != null) {
            secondStage = enforceFeaturePropertiesInAllSubmodels(
                    featureThingModels,
                    features,
                    true,
                    validationConfig.getFeatureValidationConfig().isForbidNonModeledDesiredProperties(),
                    resourcePath,
                    context
            ).thenApply(aVoid -> null);
        } else {
            secondStage = success();
        }
        return firstStage.thenCompose(unused -> secondStage);
    }


    @Override
    public CompletionStage<Void> validateFeaturePresence(final Map<String, ThingModel> featureThingModels,
            final Feature feature,
            final ValidationContext context
    ) {
        final Set<String> definedFeatureIds = featureThingModels.keySet();
        final String featureId = feature.getId();
        if (validationConfig.getFeatureValidationConfig().isForbidNonModeledFeatures() &&
                !definedFeatureIds.contains(featureId)) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("Attempting to update the Thing with a feature which is not " +
                            "defined in the model: <" + featureId + ">");
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeature(final ThingModel featureThingModel,
            final Feature feature,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final CompletableFuture<Void> firstStage;
        if (validationConfig.getFeatureValidationConfig().isEnforceProperties()) {
            firstStage = enforceFeatureProperties(featureThingModel,
                    feature,
                    false,
                    validationConfig.getFeatureValidationConfig().isForbidNonModeledProperties(),
                    resourcePath,
                    context
            );
        } else {
            firstStage = success();
        }

        final CompletableFuture<Void> secondStage;
        if (validationConfig.getFeatureValidationConfig().isEnforceDesiredProperties()) {
            secondStage = enforceFeatureProperties(featureThingModel,
                    feature,
                    true,
                    validationConfig.getFeatureValidationConfig().isForbidNonModeledDesiredProperties(),
                    resourcePath,
                    context
            );
        } else {
            secondStage = success();
        }
        return firstStage.thenCompose(unused -> secondStage);
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(final ThingModel featureThingModel,
            final String featureId,
            @Nullable final FeatureProperties featureProperties,
            final boolean desiredProperties,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (
                (!desiredProperties &&
                        validationConfig.getFeatureValidationConfig().isEnforceProperties()) ||
                        (desiredProperties &&
                                validationConfig.getFeatureValidationConfig().isEnforceDesiredProperties())
        ) {
            return enforceFeatureProperties(featureThingModel,
                    desiredProperties ?
                            Feature.newBuilder().desiredProperties(featureProperties).withId(featureId).build() :
                            Feature.newBuilder().properties(featureProperties).withId(featureId).build(),
                    desiredProperties,
                    desiredProperties ?
                            validationConfig.getFeatureValidationConfig().isForbidNonModeledDesiredProperties() :
                            validationConfig.getFeatureValidationConfig().isForbidNonModeledProperties(),
                    resourcePath,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureProperty(final ThingModel featureThingModel,
            final String featureId,
            final JsonPointer propertyPointer,
            final JsonValue propertyValue,
            final boolean desiredProperty,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getFeatureValidationConfig().isEnforceProperties()) {
            return enforceFeatureProperty(featureThingModel,
                    featureId,
                    propertyPointer,
                    propertyValue,
                    desiredProperty,
                    desiredProperty ?
                            validationConfig.getFeatureValidationConfig().isForbidNonModeledDesiredProperties() :
                            validationConfig.getFeatureValidationConfig().isForbidNonModeledProperties(),
                    resourcePath,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureScopedDeletion(final Map<String, ThingModel> featureThingModels,
            final ThingModel featureThingModel,
            final String featureId,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getFeatureValidationConfig().isEnforcePresenceOfModeledFeatures() &&
                resourcePath.equals(Thing.JsonFields.FEATURES.getPointer().addLeaf(JsonKey.of(featureId))) &&
                featureThingModels.containsKey(featureId)
        ) {
            final WotThingModelPayloadValidationException.Builder exceptionBuilder =
                    WotThingModelPayloadValidationException
                            .newBuilder("Could not delete Feature <" + featureId + ">, " +
                                    "as it is defined as submodel in the Thing's model");
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }

        if (validationConfig.getFeatureValidationConfig().isEnforceProperties()) {
            return enforcePresenceOfRequiredPropertiesUponFeatureLevelDeletion(featureThingModel,
                    featureId,
                    resourcePath,
                    context
            );
        }
        // desired properties do not need to be checked, as the "required" definitions do not apply for them
        //  all desired properties are optional by default
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureActionInput(final ThingModel featureThingModel,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getFeatureValidationConfig().isEnforceInboxMessagesInput()) {
            return enforceFeatureActionPayload(featureId,
                    featureThingModel,
                    messageSubject,
                    inputPayload,
                    validationConfig.getFeatureValidationConfig().isForbidNonModeledInboxMessages(),
                    resourcePath,
                    true,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureActionOutput(final ThingModel featureThingModel,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue outputPayload,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getFeatureValidationConfig().isEnforceInboxMessagesOutput()) {
            return enforceFeatureActionPayload(featureId,
                    featureThingModel,
                    messageSubject,
                    outputPayload,
                    validationConfig.getFeatureValidationConfig().isForbidNonModeledInboxMessages(),
                    resourcePath,
                    false,
                    context
            );
        }
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeatureEventData(final ThingModel featureThingModel,
            final String featureId,
            final String messageSubject,
            @Nullable final JsonValue dataPayload,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        if (validationConfig.getFeatureValidationConfig().isEnforceOutboxMessages()) {
            return enforceFeatureEventPayload(featureId,
                    featureThingModel,
                    messageSubject,
                    dataPayload,
                    validationConfig.getFeatureValidationConfig().isForbidNonModeledOutboxMessages(),
                    resourcePath,
                    context
            );
        }
        return success();
    }

}
