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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.thingsearch.persistence.MongoSortKeyMappingFunction;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.mapping.ThingDocumentMapper;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;

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
        update.append(PersistenceConstants.UNSET, new Document(
                MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_FEATURES, featureId), ""));
        update.append(PersistenceConstants.PULL, new Document(
                PersistenceConstants.FIELD_INTERNAL,
                new Document(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId)));
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
        update.append(PersistenceConstants.UNSET, new Document(
                MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_FEATURES, featureId,
                        PersistenceConstants.FIELD_PROPERTIES), ""));
        update.append(PersistenceConstants.PULL, new Document(PersistenceConstants.FIELD_INTERNAL,
                new Document(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId).append(
                        PersistenceConstants.FIELD_INTERNAL_KEY,
                        new Document(PersistenceConstants.EXISTS, true))));
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
        update.append(PersistenceConstants.UNSET,
                new Document(MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_FEATURES, featureId,
                        PersistenceConstants.FIELD_PROPERTIES + propertyPath.toString()), ""));
        update.append(PersistenceConstants.PULL, new Document(
                PersistenceConstants.FIELD_INTERNAL,
                new Document(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId)
                        .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                new Document(PersistenceConstants.REGEX, createPrefixRegex(propertyPath)))));
        return update;
    }

    /**
     * Updates a given feature.
     *
     * @param indexLengthRestrictionEnforcer the restriction helper to enforce size restrictions.
     * @param feature the id of the feature
     * @param created indicates whether this is a new feature
     * @return the update
     */
    static List<Bson> createUpdateForFeature(final IndexLengthRestrictionEnforcer
            indexLengthRestrictionEnforcer,
            final Feature feature,
            final boolean created) {
        final Feature withRestrictions = indexLengthRestrictionEnforcer.enforceRestrictions(feature);
        final List<Document> featurePropertyPushes = createPushes(withRestrictions);
        final Bson pushPart = new Document()
                .append(PersistenceConstants.SET,
                        new Document().append(
                                MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_FEATURES,
                                        withRestrictions.getId()),
                                createComplexFeatureDocument(withRestrictions)))
                .append(PersistenceConstants.PUSH, new Document().append(
                        PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.EACH, featurePropertyPushes)));

        if (created) {
            return Collections.singletonList(pushPart);
        } else {
            final Document pullPart = new Document()
                    .append(PersistenceConstants.PULL,
                            new Document(PersistenceConstants.FIELD_INTERNAL, new Document(
                                    PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, withRestrictions.getId())));
            return Arrays.asList(pullPart, pushPart);
        }
    }

    /**
     * Updates the properties of a given feature.
     *
     * @param indexLengthRestrictionEnforcer the restriction helper to enforce size restrictions.
     * @param featureId the feature id
     * @param properties the properties to update
     * @return the update
     */
    static List<Bson> createUpdateForFeatureProperties(
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer, final String
            featureId,
            final FeatureProperties properties) {
        final FeatureProperties withRestrictions =
                indexLengthRestrictionEnforcer.enforceRestrictions(featureId, properties);
        final List<Document> featurePropertyPushes = createFlatFeaturesRepresentation(withRestrictions, featureId);
        featurePropertyPushes.add(createDefaultFeatureDoc(featureId));

        final Bson pushPart = new Document()
                .append(PersistenceConstants.SET, new Document().append(
                        MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_FEATURES, featureId,
                                PersistenceConstants.FIELD_PROPERTIES),
                        createComplexPropertiesRepresentations(withRestrictions)))
                .append(PersistenceConstants.PUSH, new Document().append(
                        PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.EACH, featurePropertyPushes)));

        final Document pullPart = createPullFeaturesById(featureId);
        return Arrays.asList(pullPart, pushPart);
    }

    /**
     * Updates a single property of a feature.
     *
     * @param indexLengthRestrictionEnforcer the restriction helper to enforce size restrictions.
     * @param featureId the id of the feature
     * @param featurePointer the path of the feature
     * @param propertyValue the new property value
     * @return the update
     */
    static List<Bson> createUpdateForFeatureProperty(
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer,
            final String featureId,
            final JsonPointer featurePointer,
            final JsonValue propertyValue) {
        final JsonValue withRestrictions =
                indexLengthRestrictionEnforcer.enforceRestrictionsOnFeatureProperty(featureId,
                        featurePointer, propertyValue);
        final Bson update1 = createSortStructureUpdate(featureId, featurePointer, withRestrictions);
        final Bson update2 = createPullFeatures(featureId, featurePointer);
        final List<Document> flatRepresentations =
                toFlatFeaturesList(featurePointer.toString(), featureId, withRestrictions, new ArrayList<>());

        final Bson update3 = createPushUpdate(flatRepresentations);

        return Arrays.asList(update1, update2, update3);
    }

    /**
     * Removes all existing features.
     *
     * @return the update
     */
    static Bson deleteFeatures() {
        return createDeleteFeaturesDocument();
    }


    /**
     * Updates all features.
     *
     * @param indexLengthRestrictionEnforcer the restriction helper to enforce size restrictions.
     * @param features the features to update
     * @return the update
     */
    static List<Bson> updateFeatures(final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer,
            final Features features) {
        final Features withRestrictions = indexLengthRestrictionEnforcer.enforceRestrictions(features);
        final Bson update1 = createDeleteFeaturesDocument();

        final List<Document> pushes = new ArrayList<>();
        final JsonObjectBuilder featuresObjectBuilder = JsonFactory.newObjectBuilder();
        withRestrictions.forEach(f -> {
            final FeatureProperties properties =
                    f.getProperties().orElse(ThingsModelFactory.emptyFeatureProperties());
            final JsonObject propertiesJson =
                    JsonFactory.newObjectBuilder().set(PersistenceConstants.FIELD_PROPERTIES, properties).build();
            pushes.addAll(createPushes(f));
            featuresObjectBuilder.set(f.getId(), propertiesJson);
        });
        final Object featuresDocument = ThingDocumentMapper.toValue(featuresObjectBuilder.build());

        final Bson update2 = new Document()
                .append(PersistenceConstants.SET,
                        new Document().append(PersistenceConstants.FIELD_FEATURES, featuresDocument))
                .append(PersistenceConstants.PUSH, new Document().append(
                        PersistenceConstants.FIELD_INTERNAL, new Document(PersistenceConstants.EACH, pushes)));

        return Arrays.asList(update1, update2);
    }

    private static Document createDeleteFeaturesDocument() {
        return new Document()
                .append(PersistenceConstants.PULL, new Document(
                        PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID,
                                new Document(PersistenceConstants.EXISTS, Boolean.TRUE))))
                .append(PersistenceConstants.UNSET, new Document(PersistenceConstants.FIELD_FEATURES, ""));
    }

    private static Document createPushUpdate(final List<Document> flatRepresentations) {
        return new Document().append(PersistenceConstants.PUSH,
                new Document().append(
                        PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.EACH, flatRepresentations)));
    }

    private static Bson createSortStructureUpdate(final String featureId, final CharSequence featurePointer,
            final JsonValue propertyValue) {

        final Document update = new Document();
        update.append(
                MongoSortKeyMappingFunction.mapSortKey(PersistenceConstants.FIELD_FEATURES, featureId,
                        PersistenceConstants.FIELD_PROPERTIES + featurePointer),
                ThingDocumentMapper.toValue(propertyValue));
        return new Document(PersistenceConstants.SET, update);
    }

    private static Document createPullFeaturesById(final String featureId) {
        return new Document()
                .append(PersistenceConstants.PULL, new Document(
                        PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId)));
    }

    private static Document createPullFeatures(final String featureId, final JsonPointer pointer) {
        return new Document()
                .append(PersistenceConstants.PULL, new Document(
                        PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId)
                                .append(PersistenceConstants.FIELD_INTERNAL_KEY,
                                        new Document(PersistenceConstants.REGEX, createPrefixRegex(pointer)))));
    }

    private static Object createComplexFeatureDocument(final Feature feature) {
        final Document featureDocument = new Document();
        final Object propertiesDocument = feature.getProperties()
                .map(FeaturesUpdateFactory::createComplexPropertiesRepresentations)
                .orElseGet(Document::new);

        featureDocument.append(PersistenceConstants.FIELD_PROPERTIES, propertiesDocument);
        return featureDocument;
    }

    private static Object createComplexPropertiesRepresentations(final JsonValue properties) {
        return ThingDocumentMapper.toValue(properties);
    }

    private static String createPrefixRegex(final JsonPointer jsonPointer) {
        return PersistenceConstants.REGEX_FIELD_START + PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX +
                jsonPointer + PersistenceConstants.REGEX_FIELD_END;
    }

    private static List<Document> createPushes(final Feature feature) {
        final List<Document> result = feature.getProperties()
                .map(featureProperties -> createFlatFeaturesRepresentation(featureProperties, feature.getId()))
                .orElseGet(ArrayList::new);
        result.add(createDefaultFeatureDoc(feature.getId()));

        return result;
    }

    private static Document createDefaultFeatureDoc(final String id) {
        final Document defaultFeatureDoc = new Document();
        defaultFeatureDoc.append(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, id);
        return defaultFeatureDoc;
    }

    private static List<Document> createFlatFeaturesRepresentation(final Iterable<JsonField> properties,
            final String featureId) {

        final List<Document> flatFeatures = new ArrayList<>();
        properties.forEach(field -> toFlatFeaturesList(PersistenceConstants.SLASH + field.getKeyName(), featureId,
                field.getValue(), flatFeatures));
        return flatFeatures;
    }

    private static List<Document> toFlatFeaturesList(final String path,
            final String featureId,
            final JsonValue value,
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
            handleObject(path, value.asObject(), featureId, flatFeatures);
        }
        return flatFeatures;
    }

    private static void handleObject(final String path,
            final Iterable<JsonField> jsonObject,
            final String featureId,
            final List<Document> flatAttributes) {

        jsonObject.forEach(jsonField -> {
            final String newPath = path + PersistenceConstants.SLASH + jsonField.getKey();
            final JsonValue innerValue = jsonField.getValue();
            toFlatFeaturesList(newPath, featureId, innerValue, flatAttributes);
        });
    }

    private static Document createFlatSubDocument(final String key, final String featureId, final Object value) {
        final Document doc = new Document();
        doc.append(PersistenceConstants.FIELD_INTERNAL_KEY, PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX + key);
        doc.append(PersistenceConstants.FIELD_INTERNAL_VALUE, value);
        doc.append(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId);
        return doc;
    }

}
