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
package org.eclipse.ditto.model.things;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link Features}.
 */
@Immutable
final class ImmutableFeatures implements Features {

    private static final JsonFieldDefinition<Integer> JSON_SCHEMA_VERSION =
            JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                    JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    private final Map<String, Feature> features;

    private ImmutableFeatures(final Map<String, Feature> features) {
        requireNonNull(features, "The Features must not be null!");
        this.features = Collections.unmodifiableMap(new HashMap<>(features));
    }

    /**
     * Returns a new empty {@code ImmutableFeatures} instance.
     *
     * @return a new empty {@code ImmutableFeatures} instance.
     */
    public static Features empty() {
        return new ImmutableFeatures(new HashMap<>());
    }

    /**
     * Returns a new {@code ImmutableFeatures} object which is initialized with the specified {@link Feature}s.
     *
     * @param features the initial Features of the result.
     * @return a new {@code ImmutableFeatures} object.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public static Features of(final Iterable<Feature> features) {
        ConditionChecker.checkNotNull(features, "initial features");

        final Map<String, Feature> featureMap = new HashMap<>();
        features.forEach(feature -> featureMap.put(feature.getId(), feature));

        return new ImmutableFeatures(featureMap);
    }

    /**
     * Returns a new {@code ImmutableFeatures} object which is initialized with the specified {@link Feature}s.
     *
     * @param feature the initial Feature of the result.
     * @param additionalFeatures additional initial Features of the result.
     * @return a new {@code ImmutableFeatures} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Features of(final Feature feature, final Feature... additionalFeatures) {
        ConditionChecker.checkNotNull(feature, "initial Feature");
        ConditionChecker.checkNotNull(additionalFeatures, "additional initial Features");

        final Map<String, Feature> features = new HashMap<>();
        features.put(feature.getId(), feature);

        if (0 < additionalFeatures.length) {
            Arrays.stream(additionalFeatures).forEach(f -> features.put(f.getId(), f));
        }

        return new ImmutableFeatures(features);
    }

    private static void checkFeatureId(final String featureId) {
        ConditionChecker.checkNotNull(featureId, "Feature ID");
    }

    @Override
    public Optional<Feature> getFeature(final String featureId) {
        checkFeatureId(featureId);

        return Optional.ofNullable(features.get(featureId));
    }

    @Override
    public Features setFeature(final Feature feature) {
        ConditionChecker.checkNotNull(feature, "Feature to be set");

        final Feature existingFeature = features.get(feature.getId());

        Features result = this;

        if (null != existingFeature) {
            if (!existingFeature.equals(feature)) {
                result = createNewFeaturesWithNewFeature(feature);
            }
        } else {
            result = createNewFeaturesWithNewFeature(feature);
        }

        return result;
    }

    @Override
    public Features removeFeature(final String featureId) {
        checkFeatureId(featureId);

        if (!features.containsKey(featureId)) {
            return this;
        }

        final Map<String, Feature> featuresCopy = copyFeatures();
        featuresCopy.remove(featureId);

        return new ImmutableFeatures(featuresCopy);
    }

    @Override
    public Features setDefinition(final String featureId, final FeatureDefinition definition) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(definition, "definition to be set");

        Features result = this;
        final Feature feature = features.get(featureId);
        if (null != feature) {
            final Optional<FeatureDefinition> existingDefinitionOptional = feature.getDefinition();
            if (existingDefinitionOptional.isPresent()) {
                final FeatureDefinition existingDefinition = existingDefinitionOptional.get();
                if (!existingDefinition.equals(definition)) {
                    result = createNewFeaturesWithNewFeature(feature.setDefinition(definition));
                }
            } else {
                result = createNewFeaturesWithNewFeature(feature.setDefinition(definition));
            }
        } else {
            result = createNewFeaturesWithNewFeature(ThingsModelFactory.newFeature(featureId, null, definition));
        }

        return result;
    }

    @Override
    public Features removeDefinition(final String featureId) {
        checkFeatureId(featureId);

        Features result = this;

        final Feature feature = features.get(featureId);
        if (null != feature) {
            final Feature featureWithoutDefinition = feature.removeDefinition();
            if (!featureWithoutDefinition.equals(feature)) {
                final Map<String, Feature> featuresCopy = copyFeatures();
                featuresCopy.put(featureId, feature.removeDefinition());
                result = new ImmutableFeatures(featuresCopy);
            }
        }

        return result;
    }

    @Override
    public Features setProperties(final String featureId, final FeatureProperties properties) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(properties, "properties to be set");

        Features result = this;

        final Feature feature = features.get(featureId);
        if (null != feature) {
            final Optional<FeatureProperties> existingPropertiesOptional = feature.getProperties();
            if (existingPropertiesOptional.isPresent()) {
                final FeatureProperties existingProperties = existingPropertiesOptional.get();
                if (!existingProperties.equals(properties)) {
                    result = createNewFeaturesWithNewFeature(feature.setProperties(properties));
                }
            } else {
                result = createNewFeaturesWithNewFeature(feature.setProperties(properties));
            }
        } else {
            result = createNewFeaturesWithNewFeature(ThingsModelFactory.newFeature(featureId, properties));
        }

        return result;
    }

    @Override
    public Features removeProperties(final String featureId) {
        checkFeatureId(featureId);

        Features result = this;

        final Feature feature = features.get(featureId);
        if (null != feature) {
            final Feature featureWithoutProperties = feature.removeProperties();
            if (!featureWithoutProperties.equals(feature)) {
                final Map<String, Feature> featuresCopy = copyFeatures();
                featuresCopy.put(featureId, feature.removeProperties());
                result = new ImmutableFeatures(featuresCopy);
            }
        }

        return result;
    }

    @Override
    public Features setProperty(final String featureId, final JsonPointer propertyPath, final JsonValue propertyValue) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(propertyPath, "JSON pointer to the property to be set");
        ConditionChecker.checkNotNull(propertyValue, "value of the property to be set");

        Features result = this;

        final Feature feature = features.get(featureId);
        if (null != feature) {
            final Optional<JsonValue> propertyValueOptional = feature.getProperty(propertyPath);
            if (propertyValueOptional.isPresent()) {
                final JsonValue existingPropertyValue = propertyValueOptional.get();
                if (!existingPropertyValue.equals(propertyValue)) {
                    result = createNewFeaturesWithNewFeature(feature.setProperty(propertyPath, propertyValue));
                }
            } else {
                result = createNewFeaturesWithNewFeature(feature.setProperty(propertyPath, propertyValue));
            }
        } else {
            final Feature newFeature = ThingsModelFactory.newFeature(featureId,
                    ThingsModelFactory.newFeaturePropertiesBuilder() //
                            .set(propertyPath, propertyValue) //
                            .build());
            result = createNewFeaturesWithNewFeature(newFeature);
        }

        return result;
    }

    @Override
    public Features removeProperty(final String featureId, final JsonPointer propertyPath) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(propertyPath, "JSON pointer to the property to be removed");

        Features result = this;

        final Feature feature = features.get(featureId);
        if (null != feature) {
            final Optional<JsonValue> propertyOptional = feature.getProperty(propertyPath);
            if (propertyOptional.isPresent()) {
                result = createNewFeaturesWithNewFeature(feature.removeProperty(propertyPath));
            }
        }

        return result;
    }

    @Override
    public int getSize() {
        return features.size();
    }

    @Override
    public boolean isEmpty() {
        return features.isEmpty();
    }

    @Override
    public Stream<Feature> stream() {
        return features.values().stream();
    }

    @Override
    public Iterator<Feature> iterator() {
        return new HashSet<>(features.values()).iterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JSON_SCHEMA_VERSION, schemaVersion.toInt(), predicate);

        features.values()
                .forEach(feature -> {
                    final JsonKey key = JsonFactory.newKey(feature.getId());
                    final JsonValue value = feature.toJson(schemaVersion, thePredicate);
                    final JsonFieldDefinition<JsonObject> fieldDefinition =
                            JsonFactory.newJsonObjectFieldDefinition(key, FieldType.REGULAR, JsonSchemaVersion.V_1,
                                    JsonSchemaVersion.V_2);
                    final JsonField field = JsonFactory.newField(key, value, fieldDefinition);

                    jsonObjectBuilder.set(field, predicate);
                });

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFeatures that = (ImmutableFeatures) o;
        return Objects.equals(features, that.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(features);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "features=" + features.values() + "]";
    }

    private Map<String, Feature> copyFeatures() {
        return new HashMap<>(features);
    }

    private Features createNewFeaturesWithNewFeature(final Feature newFeature) {
        final Map<String, Feature> featuresCopy = copyFeatures();
        featuresCopy.put(newFeature.getId(), newFeature);
        return new ImmutableFeatures(featuresCopy);
    }

}
