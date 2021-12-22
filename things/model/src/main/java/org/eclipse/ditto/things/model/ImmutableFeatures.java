/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link Features}.
 */
@Immutable
final class ImmutableFeatures implements Features {

    private final Map<String, Feature> features;

    private ImmutableFeatures(final Map<String, Feature> features) {
        this.features = Collections.unmodifiableMap(new LinkedHashMap<>(checkNotNull(features, "features")));
    }

    /**
     * Returns a new empty {@code ImmutableFeatures} instance.
     *
     * @return a new empty {@code ImmutableFeatures} instance.
     */
    public static ImmutableFeatures empty() {
        return new ImmutableFeatures(new LinkedHashMap<>());
    }

    /**
     * Returns a new {@code ImmutableFeatures} object which is initialized with the specified {@link Feature}s.
     *
     * @param features the initial Features of the result.
     * @return a new {@code ImmutableFeatures} object.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public static ImmutableFeatures of(final Iterable<Feature> features) {
        checkNotNull(features, "initial features");

        final Map<String, Feature> featureMap = new LinkedHashMap<>();
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
    public static ImmutableFeatures of(final Feature feature, final Feature... additionalFeatures) {
        checkNotNull(feature, "initial Feature");
        checkNotNull(additionalFeatures, "additional initial Features");

        final Map<String, Feature> features = new LinkedHashMap<>(1 + additionalFeatures.length);
        features.put(feature.getId(), feature);
        for (final Feature additionalFeature : additionalFeatures) {
            features.put(additionalFeature.getId(), additionalFeature);
        }

        return new ImmutableFeatures(features);
    }

    @Override
    public Optional<Feature> getFeature(final String featureId) {
        checkFeatureId(featureId);

        return Optional.ofNullable(features.get(featureId));
    }

    private static String checkFeatureId(final String featureId) {
        return checkNotNull(featureId, "Feature ID");
    }

    @Override
    public Features setFeature(final Feature feature) {
        checkNotNull(feature, "Feature to be set");

        final Feature existingFeature = getFeatureOrNull(feature.getId());
        if (!Objects.equals(existingFeature, feature)) {
            return createNewFeaturesWithNewFeature(feature);
        }
        return this;
    }

    @Nullable
    private Feature getFeatureOrNull(final String featureId) {
        return features.get(checkFeatureId(featureId));
    }

    @Nullable
    private Feature getFeatureOrNull(final CharSequence featureId) {
        return features.get(checkFeatureId(featureId.toString()));
    }

    private Features createNewFeaturesWithNewFeature(final Feature newFeature) {
        final Map<String, Feature> featuresCopy = copyFeatures();
        featuresCopy.put(newFeature.getId(), newFeature);
        return new ImmutableFeatures(featuresCopy);
    }

    @Override
    public Features removeFeature(final String featureId) {
        if (!features.containsKey(checkFeatureId(featureId))) {
            return this;
        }

        final Map<String, Feature> featuresCopy = copyFeatures();
        featuresCopy.remove(featureId);

        return new ImmutableFeatures(featuresCopy);
    }

    private Map<String, Feature> copyFeatures() {
        return new LinkedHashMap<>(features);
    }

    @Override
    public Features setDefinition(final String featureId, final FeatureDefinition definition) {
        checkNotNull(definition, "definition to be set");

        Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            feature = feature.setDefinition(definition);
        } else {
            feature = ThingsModelFactory.newFeature(featureId, definition);
        }
        return setFeature(feature);
    }

    @Override
    public Features removeDefinition(final String featureId) {
        final Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            return setFeature(feature.removeDefinition());
        }
        return this;
    }

    @Override
    public Features setProperties(final String featureId, final FeatureProperties properties) {
        checkNotNull(properties, "properties to be set");

        Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            feature = feature.setProperties(properties);
        } else {
            feature = ThingsModelFactory.newFeature(featureId, properties);
        }
        return setFeature(feature);
    }

    @Override
    public Features removeProperties(final String featureId) {
        final Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            return setFeature(feature.removeProperties());
        }

        return this;
    }

    @Override
    public Features setProperty(final String featureId, final JsonPointer propertyPath, final JsonValue propertyValue) {
        Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            feature = feature.setProperty(propertyPath, propertyValue);
        } else {
            feature = ThingsModelFactory.newFeature(featureId, ThingsModelFactory.newFeaturePropertiesBuilder()
                    .set(propertyPath, propertyValue)
                    .build());
        }
        return setFeature(feature);
    }

    @Override
    public Features removeProperty(final String featureId, final JsonPointer propertyPath) {
        final Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            return setFeature(feature.removeProperty(propertyPath));
        }
        return this;
    }

    @Override
    public Features setDesiredProperties(final CharSequence featureId, final FeatureProperties desiredProperties) {
        checkNotNull(desiredProperties, "desiredProperties");

        Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            feature = feature.setDesiredProperties(desiredProperties);
        } else {
            feature = ThingsModelFactory.newFeature(featureId, null, null, desiredProperties);
        }
        return setFeature(feature);
    }

    @Override
    public Features removeDesiredProperties(final CharSequence featureId) {
        final Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            return setFeature(feature.removeDesiredProperties());
        }

        return this;
    }

    @Override
    public Features setDesiredProperty(final CharSequence featureId, final JsonPointer desiredPropertyPath,
            final JsonValue desiredPropertyValue) {

        Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            feature = feature.setDesiredProperty(desiredPropertyPath, desiredPropertyValue);
        } else {
            feature = ThingsModelFactory.newFeature(featureId, null, null,
                    ThingsModelFactory.newFeaturePropertiesBuilder()
                            .set(desiredPropertyPath, desiredPropertyValue)
                            .build());
        }
        return setFeature(feature);
    }

    @Override
    public Features removeDesiredProperty(final CharSequence featureId, final JsonPointer desiredPropertyPath) {
        final Feature feature = getFeatureOrNull(featureId);
        if (null != feature) {
            return setFeature(feature.removeDesiredProperty(desiredPropertyPath));
        }
        return this;
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
        return new LinkedHashSet<>(features.values()).iterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        features.values()
                .forEach(feature -> {
                    final JsonKey key = JsonFactory.newKey(feature.getId());
                    final JsonValue value = feature.toJson(schemaVersion, thePredicate);
                    final JsonFieldDefinition<JsonObject> fieldDefinition =
                            JsonFactory.newJsonObjectFieldDefinition(key, FieldType.REGULAR,
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

}
