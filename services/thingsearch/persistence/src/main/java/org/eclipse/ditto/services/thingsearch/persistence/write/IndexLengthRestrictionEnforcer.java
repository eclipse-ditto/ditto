/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.AttributesBuilder;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.FeaturePropertiesBuilder;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.FeaturesBuilder;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

import akka.event.LoggingAdapter;


/**
 * Class that helps to enforce size restrictions on the things model.
 */
public final class IndexLengthRestrictionEnforcer {

    /**
     * Max allowed length of feature properties.
     */
    static final int MAX_FEATURE_PROPERTY_VALUE_LENGTH = 950;
    /**
     * Max allowed length of attributes.
     */
    static final int MAX_ATTRIBUTE_VALUE_LENGTH = 950;

    /**
     * The overhead caused by the json key of attribute entries. Use {@link IndexLengthRestrictionEnforcer#attributeOverhead()}
     * ()} for calculating the concrete overhead.
     */
    private static final int ATTRIBUTE_KEY_OVERHEAD = ("attributes").length();

    /**
     * The overhead caused by the json key of feature properties. Use {@link IndexLengthRestrictionEnforcer#featurePropertyOverhead(String)}
     * for calculating the concrete overhead.
     */
    private static final int FEATURE_PROPERTY_KEY_OVERHEAD = ("features" +
            PersistenceConstants.SLASH
            // feature id
            + PersistenceConstants.SLASH
            + "properties").length();

    /**
     * The logging adapter used to log size restriction enforcements.
     */
    private final LoggingAdapter log;

    /**
     * Default constructor.
     *
     * @param log the logging adapter used to log size restriction enforcements.
     */
    private IndexLengthRestrictionEnforcer(final LoggingAdapter log) {
        this.log = log;
    }

    /**
     * Create a new instance of {@link IndexLengthRestrictionEnforcer}.
     *
     * @param loggingAdapter the logging adapter used to log size restriction enforcements.
     * @return the instance.
     */
    public static IndexLengthRestrictionEnforcer newInstance(final LoggingAdapter loggingAdapter) {
        return new IndexLengthRestrictionEnforcer(loggingAdapter);
    }

    /**
     * Enforce size restrictions on a thing and build a new instance that satisfies the thresholds.
     *
     * @param thing the thing.
     * @return The thing with content that satisfies the thresholds.
     */
    public Thing enforceRestrictions(final Thing thing) {
        // check if features exceed limits
        final Map<Feature, Set<JsonField>> exceededFeatures =
                calculateThresholdViolations(thing.getFeatures().orElse(Features.newBuilder().build()));
        // check if attributes exceed limits
        final Set<JsonField> exceededAttributes =
                calculateThresholdViolations(thing.getAttributes().orElse(Attributes.newBuilder().build()));

        // update thing if needed
        Thing intermediateThing = thing;
        if (!exceededFeatures.isEmpty()) {
            log.warning("Thing <{}> contains properties of following features that exceed size restrictions: <{}>",
                    getThingId(thing),
                    exceededFeatures.keySet()
                            .stream()
                            .map(Feature::getId)
                            .collect(Collectors.toList()));

            final ThingBuilder.FromCopy builder = intermediateThing.toBuilder();
            exceededFeatures.forEach((feature, jsonFields) -> jsonFields.forEach(
                    jsonField -> builder.setFeatureProperty(
                            feature.getId(),
                            jsonField.getKey().asPointer(),
                            fixViolation(jsonField.getKey().asPointer(),
                                    jsonField.getValue(),
                                    featurePropertyOverhead(feature.getId()),
                                    MAX_FEATURE_PROPERTY_VALUE_LENGTH))));
            intermediateThing = builder.build();
        }

        if (!exceededAttributes.isEmpty()) {
            log.warning("Attributes of Thing <{}> exceed size restrictions: <{}>",
                    getThingId(thing),
                    exceededAttributes.stream()
                            .map(JsonField::getKey)
                            .map(CharSequence::toString)
                            .collect(Collectors.toList()));

            final ThingBuilder.FromCopy builder = intermediateThing.toBuilder();
            final Attributes newAttributes = fixViolations(
                    exceededAttributes,
                    intermediateThing.getAttributes().orElse(Attributes.newBuilder().build()));

            builder.setAttributes(newAttributes);
            intermediateThing = builder.build();
        }
        return intermediateThing;
    }

    /**
     * Enforce size restrictions on the features and build a new instance that satisfies the thresholds.
     *
     * @param features the features.
     * @return The features with content that satisfies the thresholds.
     */
    public Features enforceRestrictions(@Nonnull final Features features) {
        // check if features exceed limits
        final Map<Feature, Set<JsonField>> exceededFeatures = calculateThresholdViolations(features);
        if (!exceededFeatures.isEmpty()) {
            log.warning("Properties of following features exceed size restrictions: <{}>",
                    exceededFeatures.keySet()
                            .stream()
                            .map(Feature::getId)
                            .collect(Collectors.toList()));
            final FeaturesBuilder builder = features.toBuilder();
            exceededFeatures.forEach((feature, jsonFields) -> {
                final FeatureProperties newProps = fixViolations(
                        feature.getId(),
                        jsonFields,
                        feature.getProperties().orElse(FeatureProperties.newBuilder().build()));
                builder.set(feature.setProperties(newProps));
            });
            return builder.build();
        }
        return features;
    }

    /**
     * Enforce size restrictions on a feature property create a new property value that satisfies the thresholds.
     *
     * @param featureId the feature to which the property belongs.
     * @param featurePointer the key of the feature property.
     * @param propertyValue the current value of the feature property.
     * @return The value that satisfies the thresholds.
     */
    public JsonValue enforceRestrictionsOnFeatureProperty(final String featureId, final JsonPointer featurePointer,
            final JsonValue propertyValue) {
        if (violatesThreshold(featurePointer,
                propertyValue,
                featurePropertyOverhead(featureId),
                MAX_FEATURE_PROPERTY_VALUE_LENGTH)) {
            log.warning("Feature Property <{}> of Feature <{}> exceeds size restrictions.",
                    featurePointer.toString(),
                    featureId);
            return fixViolation(featurePointer,
                    propertyValue,
                    featurePropertyOverhead(featureId),
                    MAX_FEATURE_PROPERTY_VALUE_LENGTH);
        }
        return propertyValue;
    }

    /**
     * Enforce size restrictions on FeatureProperties and build a new instance that satisfies the thresholds.
     *
     * @param featureId the feature to which the properties belong.
     * @param properties the FeatureProperties.
     * @return The properties with content that satisfies the thresholds.
     */
    public FeatureProperties enforceRestrictions(final String featureId, final FeatureProperties properties) {
        final Set<JsonField> exceededProps = calculateThresholdViolations(featureId, properties);
        if (!exceededProps.isEmpty()) {
            log.warning("Feature properties of feature <{}> exceed size restrictions: <{}>", featureId,
                    exceededProps.stream()
                            .map(JsonField::getKey)
                            .map(CharSequence::toString)
                            .collect(Collectors.toList()));
            return fixViolations(featureId, exceededProps, properties);
        }
        return properties;
    }

    /**
     * Enforce size restrictions on the feature and build a new instance that satisfies the thresholds.
     *
     * @param feature the feature.
     * @return The feature with content that satisfies the thresholds.
     */
    public Feature enforceRestrictions(final Feature feature) {
        final Set<JsonField> exceededProps = calculateThresholdViolations(
                feature.getId(),
                feature.getProperties().orElse(FeatureProperties.newBuilder().build()));
        if (!exceededProps.isEmpty()) {
            log.warning("Feature <{}> exceeds size restriction for properties: <{}>",
                    feature.getId(),
                    exceededProps.stream()
                            .map(JsonField::getKey)
                            .map(CharSequence::toString)
                            .collect(Collectors.toList()));
            final FeatureProperties newProps = fixViolations(
                    feature.getId(),
                    exceededProps,
                    feature.getProperties().orElse(FeatureProperties.newBuilder().build()));
            return feature.setProperties(newProps);
        }
        return feature;
    }

    /**
     * Enforce size restrictions on an attribute and build a new value that satisfies the thresholds.
     *
     * @param attributeKey the key of the attribute.
     * @param attributeValue the value of the attribute.
     * @return The attribute value that satisfies the thresholds.
     */
    public JsonValue enforceRestrictionsOnAttributeValue(final JsonPointer attributeKey,
            final JsonValue attributeValue) {
        if (violatesThreshold(attributeKey, attributeValue,
                attributeOverhead(),
                MAX_ATTRIBUTE_VALUE_LENGTH)) {
            log.warning("Attribute <{}> exceeds size restrictions", attributeKey.toString());
            return fixViolation(attributeKey, attributeValue, attributeOverhead(), MAX_ATTRIBUTE_VALUE_LENGTH);
        }
        return attributeValue;
    }

    /**
     * Enforce size restrictions on attributes and build a new instance that satisfies the thresholds.
     *
     * @param attributes the attributes.
     * @return The attributes with content that satisfies the thresholds.
     */
    public Attributes enforceRestrictions(final Attributes attributes) {
        final Set<JsonField> exceededAttributes = calculateThresholdViolations(attributes);

        if (!exceededAttributes.isEmpty()) {
            log.warning("Attributes exceed size restrictions: <{}>", exceededAttributes
                    .stream()
                    .map(JsonField::getKey)
                    .collect(Collectors.toList()));
            return fixViolations(exceededAttributes, attributes);
        }
        return attributes;
    }

    private Attributes fixViolations(final Set<JsonField> exceededAttributes,
            final Attributes attributes) {
        final AttributesBuilder builder = attributes.toBuilder();
        exceededAttributes.forEach(field -> builder.set(
                field.getKey().toString(),
                fixViolation(field.getKey().asPointer(),
                        field.getValue(),
                        attributeOverhead(),
                        MAX_ATTRIBUTE_VALUE_LENGTH)));
        return builder.build();
    }

    private FeatureProperties fixViolations(final String featureId,
            final Set<JsonField> exceededProps,
            final FeatureProperties featureProperties) {
        final FeaturePropertiesBuilder featurePropertiesBuilder = featureProperties.toBuilder();
        exceededProps.forEach(
                jsonField -> {
                    final JsonValue restrictedValue = fixViolation(
                            jsonField.getKey().asPointer(),
                            jsonField.getValue(),
                            featurePropertyOverhead(featureId),
                            MAX_FEATURE_PROPERTY_VALUE_LENGTH);
                    featurePropertiesBuilder.set(jsonField.getKey().asPointer(), restrictedValue);
                });

        return featurePropertiesBuilder.build();
    }

    private JsonValue fixViolation(final JsonPointer key,
            final JsonValue value,
            final int overhead,
            final int threshold) {
        final int cutAt = threshold - totalOverhead(key, overhead);
        return JsonValue.of(value.asString().substring(0, cutAt));
    }

    private Set<JsonField> calculateThresholdViolations(final Attributes attributes) {
        return attributes
                .stream()
                .filter(field -> violatesThreshold(field.getKey().asPointer(),
                        field.getValue(),
                        attributeOverhead(),
                        MAX_ATTRIBUTE_VALUE_LENGTH))
                .collect(Collectors.toSet());
    }

    private Map<Feature, Set<JsonField>> calculateThresholdViolations(final Features features) {
        return features
                .stream()
                .map(feature -> {
                    final Set<JsonField> exceededProps = calculateThresholdViolations(
                            feature.getId(),
                            feature.getProperties().orElse(FeatureProperties.newBuilder().build()));
                    if (!exceededProps.isEmpty()) {
                        return Collections.singletonMap(feature, exceededProps);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(HashMap::new, Map::putAll, Map::putAll);
    }

    private Set<JsonField> calculateThresholdViolations(final String featureId,
            final FeatureProperties featureProperties) {
        return featureProperties.stream()
                .filter(field -> violatesThreshold(
                        field.getKey().asPointer(),
                        field.getValue(),
                        featurePropertyOverhead(featureId),
                        MAX_FEATURE_PROPERTY_VALUE_LENGTH))
                .collect(Collectors.toSet());
    }

    private boolean violatesThreshold(final JsonPointer key,
            final JsonValue value,
            final int overhead,
            final int threshold) {
        return null != value
                && value.isString()
                && !value.isNull()
                && threshold < value.asString().length() + totalOverhead(key, overhead);
    }

    private int totalOverhead(final JsonPointer key, final int additionalOverhead) {
        return key.toString().length() + additionalOverhead;
    }

    /**
     * Get the overhead for attribute entries in json.
     *
     * @return The overhead as a positive int.
     */
    private int attributeOverhead() {
        return ATTRIBUTE_KEY_OVERHEAD;
    }

    /**
     * Get the overhead for feature property entries in json.
     *
     * @param featureKey The id of the feature to which this property belongs.
     * @return The overhead as a positive int.
     */
    private int featurePropertyOverhead(final String featureKey) {
        return FEATURE_PROPERTY_KEY_OVERHEAD + featureKey.length();
    }


    /**
     * Get the id of the thing.
     *
     * @param thing The thing.
     * @return The id or an {@link IllegalArgumentException} if no id is present.
     */
    private String getThingId(final Thing thing) {
        return thing.getId().orElseThrow(() -> new IllegalArgumentException("The thing has no ID!"));
    }

}

