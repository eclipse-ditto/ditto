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
package org.eclipse.ditto.services.thingsearch.persistence.mapping;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Features;

import org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

/**
 * Features document builder.
 */
final class FeaturesDocumentBuilder {

    private final Document tDocument;

    private FeaturesDocumentBuilder() {
        tDocument = new Document();
        tDocument.append(PersistenceConstants.FIELD_INTERNAL, new ArrayList<Document>());
        tDocument.append(PersistenceConstants.FIELD_FEATURES, ThingDocumentMapper.newDocument());
    }

    /**
     * Creates a new builder.
     *
     * @return the builder
     */
    public static FeaturesDocumentBuilder create() {
        return new FeaturesDocumentBuilder();
    }

    /**
     * Adds features.
     *
     * @param features the attributes to add
     * @return FeaturesDocumentBuilder
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public FeaturesDocumentBuilder features(final Features features) {
        features.forEach(feature -> {
            feature.getProperties().ifPresent(featureProperties -> featureProperties.forEach(field -> {
                final Object value = ThingDocumentMapper.toValue(field.getValue());
                mainFeatureProperties(field.getKeyName(), value, feature.getId());
                addInternalFeatures(field.getKeyName(), field.getValue(), feature.getId());
            }));
            addDefaultFeatureEntry(feature.getId());
        });

        return this;
    }

    /**
     * Returns the built document.
     *
     * @return the document
     */
    public Document build() {
        return tDocument;
    }

    private void addDefaultFeatureEntry(final String id) {
        final List<Document> internalAttributes = ThingDocumentMapper.toList(tDocument, PersistenceConstants.FIELD_INTERNAL);
        internalAttributes.add(new Document(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, id));
    }

    private FeaturesDocumentBuilder mainFeatureProperties(final String key, final Object value,
            final String featureId) {
        final Document doc = (Document) tDocument.get(PersistenceConstants.FIELD_FEATURES);
        final Document featurePropertiesDoc = getSubDocument(doc, featureId);
        featurePropertiesDoc.append(KeyEscapeUtil.escape(key), value);
        return this;
    }

    private Document getSubDocument(final Document doc, final String featureId) {
        final String featureKey = KeyEscapeUtil.escape(featureId);
        if (doc.get(featureId) == null) {
            doc.put(featureKey, newFeaturePropertiesDocument());
        }
        return (Document) ((Document) doc.get(featureKey)).get(PersistenceConstants.FIELD_PROPERTIES);
    }

    private Document newFeaturePropertiesDocument() {
        return new Document(PersistenceConstants.FIELD_PROPERTIES, ThingDocumentMapper.newDocument());
    }

    private void addInternalFeatures(final String path, final JsonValue jsonValue, final String featureId) {
        if ((jsonValue == null) || jsonValue.isNull()) {
            featureInternally(path, null, featureId);
        } else if (jsonValue.isString()) {
            featureInternally(path, jsonValue.asString(), featureId);
        } else if (jsonValue.isBoolean()) {
            featureInternally(path, jsonValue.asBoolean(), featureId);
        } else if (jsonValue.isNumber()) {
            addNumberFeature(path, jsonValue, featureId);
        } else if (jsonValue.isObject()) {
            jsonValue.asObject().getKeys().forEach(name -> //
                    addInternalFeatures(path + "/" + name, jsonValue.asObject().getValue(name).orElse(null),
                            featureId));
        }
    }

    private void addNumberFeature(final String path, final JsonValue jsonValue, final String featureId) {
        try {
            featureInternally(path, jsonValue.asLong(), featureId);
        } catch (final NumberFormatException e) {
            featureInternally(path, jsonValue.asDouble(), featureId);
        }
    }

    private void featureInternally(final String key, final Object value, final String featureId) {
        // add attribute to flat representation
        final List<Document> attributes1 = ThingDocumentMapper.toList(tDocument, PersistenceConstants.FIELD_INTERNAL);
        final Document doc = ThingDocumentMapper.newDocument();
        doc.append(PersistenceConstants.FIELD_INTERNAL_KEY,
                PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX + PersistenceConstants.SLASH + key);
        doc.append(PersistenceConstants.FIELD_INTERNAL_VALUE, value);
        doc.append(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, featureId);
        attributes1.add(doc);
    }

}
