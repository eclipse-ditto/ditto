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
package org.eclipse.services.thingsearch.persistence.write.impl;

import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.EACH;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.EXISTS;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURES;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_FEATURE_ID;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_VALUE;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.FIELD_PROPERTIES;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.PULL;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.PUSH;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.REGEX;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.REGEX_FIELD_END;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.REGEX_FIELD_START;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.SET;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.SLASH;
import static org.eclipse.services.thingsearch.persistence.PersistenceConstants.UNSET;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.services.thingsearch.persistence.MongoSortKeyMappingFunction;

import org.eclipse.services.thingsearch.persistence.read.document.DocumentMapper;

/**
 * Factory which creates updates for features.
 */
final class FeaturesUpdateFactory {

    private FeaturesUpdateFactory() {
        throw new AssertionError();
    }

    /**
     * Deletes a given feature.
     *
     * @param featureId the id of the feature to delete
     * @return the update Bson
     */
    static Bson createDeleteFeatureUpdate(final String featureId) {
        final Document update = new Document();
        update.append(UNSET, new Document(MongoSortKeyMappingFunction.mapSortKey(FIELD_FEATURES, featureId), ""));
        update.append(PULL, new Document(FIELD_INTERNAL, new Document(FIELD_INTERNAL_FEATURE_ID, featureId)));
        return update;
    }

    /**
     * Creates the delete update for feature properties.
     *
     * @param featureId the id of the feature
     * @return the update
     */
    static Bson createDeleteFeaturePropertiesUpdate(final String featureId) {
        final Document update = new Document();
        update.append(UNSET, new Document(
                MongoSortKeyMappingFunction.mapSortKey(FIELD_FEATURES, featureId, FIELD_PROPERTIES), ""));
        update.append(PULL, new Document(FIELD_INTERNAL,
                new Document(FIELD_INTERNAL_FEATURE_ID, featureId).append(FIELD_INTERNAL_KEY,
                        new Document(EXISTS, true))));
        return update;
    }

    /**
     * Creates the delete update for a feature property.
     *
     * @param featureId the id of the feature
     * @param propertyPath the propertyPath of the feature
     * @return the update
     */
    static Bson createDeleteFeaturePropertyUpdate(final String featureId, final JsonPointer propertyPath) {
        final Document update = new Document();
        update.append(UNSET,
                new Document(MongoSortKeyMappingFunction.mapSortKey(FIELD_FEATURES, featureId,
                        FIELD_PROPERTIES + propertyPath.toString()), ""));
        update.append(PULL, new Document(FIELD_INTERNAL, new Document(FIELD_INTERNAL_FEATURE_ID, featureId)
                .append(FIELD_INTERNAL_KEY, new Document(REGEX, createPrefixRegex(propertyPath)))));
        return update;
    }

    /**
     * Updates a given feature.
     *
     * @param feature the id of the feature
     * @param created indicates whether this is a new feature
     * @return the update
     */
    static CombinedUpdates createUpdateForFeature(final Feature feature, final boolean created) {
        final List<Document> featurePropertyPushes = createPushes(feature);

        final Bson pushPart = new Document()
                .append(SET,
                        new Document().append(MongoSortKeyMappingFunction.mapSortKey(FIELD_FEATURES, feature.getId()),
                        createComplexFeatureDocument(feature)))
                .append(PUSH, new Document().append(FIELD_INTERNAL, new Document(EACH, featurePropertyPushes)));

        if (created) {
            return CombinedUpdates.of(pushPart);
        } else {
            final Document pullPart = new Document()
                    .append(PULL,
                            new Document(FIELD_INTERNAL, new Document(FIELD_INTERNAL_FEATURE_ID, feature.getId())));
            return CombinedUpdates.of(pullPart, pushPart);
        }
    }

    /**
     * Updates the properties of a given feature.
     *
     * @param featureId the feature id
     * @param properties the properties to update
     * @return the update
     */
    static CombinedUpdates createUpdateForFeatureProperties(final String featureId,
            final FeatureProperties properties) {
        final List<Document> featurePropertyPushes = createFlatFeaturesRepresentation(properties, featureId);
        featurePropertyPushes.add(createDefaultFeatureDoc(featureId));

        final Bson pushPart = new Document()
                .append(SET, new Document().append(
                        MongoSortKeyMappingFunction.mapSortKey(FIELD_FEATURES, featureId, FIELD_PROPERTIES),
                        createComplexPropertiesRepresentations(properties)))
                .append(PUSH, new Document().append(FIELD_INTERNAL, new Document(EACH, featurePropertyPushes)));

        final Document pullPart = createPullFeaturesById(featureId);
        return CombinedUpdates.of(pullPart, pushPart);
    }


    /**
     * Updates a single property of a feature.
     *
     * @param featureId the id of the feature
     * @param featurePointer the path of the feature
     * @param propertyValue the new property value
     * @return the update
     */
    static CombinedUpdates createUpdateForFeatureProperty(final String featureId,
            final JsonPointer featurePointer,
            final JsonValue propertyValue) {
        final Bson update1 = createSortStructureUpdate(featureId, featurePointer, propertyValue);
        final Bson update2 = createPullFeatures(featureId, featurePointer);
        final List<Document> flatRepresentations =
                toFlatFeaturesList(featurePointer.toString(), featureId, propertyValue, new ArrayList<>());

        final Bson update3 = createPushUpdate(flatRepresentations);

        return CombinedUpdates.of(update1, update2, update3);
    }

    /**
     * Removes all existing features.
     *
     * @return the update
     */
    static Bson deleteFeatures() {
        return new Document()
                .append(PULL, new Document(FIELD_INTERNAL,
                        new Document(FIELD_INTERNAL_FEATURE_ID, new Document(EXISTS, Boolean.TRUE))))
                .append(UNSET, new Document(FIELD_FEATURES, ""));
    }

    /**
     * Updates all features.
     *
     * @param features the features to update
     * @return the update
     */
    static CombinedUpdates updateFeatures(final Features features) {
        final Bson update1 = new Document()
                .append(PULL, new Document(FIELD_INTERNAL, new Document(FIELD_INTERNAL_FEATURE_ID,
                        new Document(EXISTS, Boolean.TRUE))))
                .append(UNSET, new Document(FIELD_FEATURES, ""));

        final List<Document> pushes = new ArrayList<>();
        final JsonObjectBuilder featuresObjectBuilder = JsonFactory.newObjectBuilder();
        features.forEach(f -> {
            final FeatureProperties properties =
                    f.getProperties().orElse(ThingsModelFactory.emptyFeatureProperties());
            final JsonObject propertiesJson = JsonFactory.newObjectBuilder().set(FIELD_PROPERTIES, properties).build();
            pushes.addAll(createPushes(f));
            featuresObjectBuilder.set(f.getId(), propertiesJson);
        });
        final Object featuresDocument = DocumentMapper.toValue(featuresObjectBuilder.build());

        final Bson update2 = new Document()
                .append(SET, new Document().append(FIELD_FEATURES, featuresDocument))
                .append(PUSH, new Document().append(FIELD_INTERNAL, new Document(EACH, pushes)));

        return CombinedUpdates.of(update1, update2);
    }

    private static Document createPushUpdate(final List<Document> flatRepresentations) {
        return new Document().append(PUSH,
                new Document().append(FIELD_INTERNAL, new Document(EACH, flatRepresentations)));
    }

    private static Bson createSortStructureUpdate(final String featureId, final JsonPointer featurePointer,
            final JsonValue propertyValue) {
        final Document update = new Document();
        update.append(
                MongoSortKeyMappingFunction.mapSortKey(FIELD_FEATURES, featureId,
                        FIELD_PROPERTIES + featurePointer.toString()),
                DocumentMapper.toValue(propertyValue));
        return new Document(SET, update);
    }

    private static Document createPullFeaturesById(final String featureId) {
        return new Document()
                .append(PULL, new Document(FIELD_INTERNAL, new Document(FIELD_INTERNAL_FEATURE_ID, featureId)));
    }

    private static Document createPullFeatures(final String featureId, final JsonPointer pointer) {
        return new Document()
                .append(PULL, new Document(FIELD_INTERNAL, new Document(FIELD_INTERNAL_FEATURE_ID, featureId)
                        .append(FIELD_INTERNAL_KEY, new Document(REGEX, createPrefixRegex(pointer)))));
    }

    private static Object createComplexFeatureDocument(final Feature feature) {
        final Document featureDocument = new Document();
        final Object propertiesDocument;

        if (feature.getProperties().isPresent()) {
            propertiesDocument = createComplexPropertiesRepresentations(feature.getProperties().get());
        } else {
            propertiesDocument = new Document();
        }

        featureDocument.append(FIELD_PROPERTIES, propertiesDocument);
        return featureDocument;
    }

    private static String createPrefixRegex(final JsonPointer jsonPointer) {
        return REGEX_FIELD_START + FIELD_FEATURE_PROPERTIES_PREFIX + jsonPointer.toString() + REGEX_FIELD_END;
    }

    private static Object createComplexPropertiesRepresentations(final FeatureProperties properties) {
        return DocumentMapper.toValue(properties);
    }

    private static List<Document> createPushes(final Feature feature) {
        final Document defaultFeatureDoc = createDefaultFeatureDoc(feature.getId());
        if (feature.getProperties().isPresent()) {
            final List<Document> pushDocuments =
                    createFlatFeaturesRepresentation(feature.getProperties().get(), feature.getId());
            pushDocuments.add(defaultFeatureDoc);
            return pushDocuments;
        } else {
            final List<Document> pushDocuments = new ArrayList<>();
            pushDocuments.add(defaultFeatureDoc);
            return pushDocuments;
        }
    }

    private static Document createDefaultFeatureDoc(final String id) {
        final Document defaultFeatureDoc = new Document();
        defaultFeatureDoc.append(FIELD_INTERNAL_FEATURE_ID, id);
        return defaultFeatureDoc;
    }

    private static List<Document> createFlatFeaturesRepresentation(final FeatureProperties properties,
            final String featureId) {
        final List<Document> flatFeatures = new ArrayList<>();
        properties
                .forEach(field -> //
                        toFlatFeaturesList(SLASH + field.getKeyName(), featureId, field.getValue(), flatFeatures));
        return flatFeatures;
    }

    private static List<Document> toFlatFeaturesList(final String path, final String featureId, final JsonValue value,
            final List<Document> flatFeatures) {
        if (value.isString()) {
            flatFeatures.add(createFlatSubDocument(path, featureId, value.asString()));
        } else if (value.isBoolean()) {
            flatFeatures.add(createFlatSubDocument(path, featureId, value.asBoolean()));
        } else if (value.isNumber()) {
            try {
                flatFeatures.add(createFlatSubDocument(path, featureId, value.asLong()));
            } catch (final NumberFormatException e) {
                flatFeatures.add(createFlatSubDocument(path, featureId, value.asDouble()));
            }
        } else if (value.isNull()) {
            flatFeatures.add(createFlatSubDocument(path, featureId, null));
        } else if (value.isObject()) {
            handleObject(path, value, featureId, flatFeatures);
        }
        return flatFeatures;
    }

    private static void handleObject(final String path, final JsonValue value, final String featureId,
            final List<Document> flatAttributes) {
        final JsonObject jsonObject = value.asObject();
        jsonObject.getKeys().forEach(key -> {
            final String newPath = path + SLASH + key;
            final JsonValue innerValue = jsonObject.getValue(key).orElse(null);
            toFlatFeaturesList(newPath, featureId, innerValue, flatAttributes);
        });
    }

    private static Document createFlatSubDocument(final String key, final String featureId, final Object value) {
        final Document doc = new Document();
        doc.append(FIELD_INTERNAL_KEY, FIELD_FEATURE_PROPERTIES_PREFIX + key);
        doc.append(FIELD_INTERNAL_VALUE, value);
        doc.append(FIELD_INTERNAL_FEATURE_ID, featureId);
        return doc;
    }

}
