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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotNull;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Attributes;

import org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

/**
 * Builder for attributes.
 */
final class AttributesDocumentBuilder {

    private final Document tDocument;

    private AttributesDocumentBuilder() {
        tDocument = new Document();
        tDocument.append(PersistenceConstants.FIELD_INTERNAL, new ArrayList<Document>());
        tDocument.append(PersistenceConstants.FIELD_ATTRIBUTES, ThingDocumentMapper.newDocument());
    }

    /**
     * Create a new ThingDocumentBuilder.
     *
     * @return AttributesDocumentBuilder
     */
    public static AttributesDocumentBuilder create() {
        return new AttributesDocumentBuilder();
    }

    /**
     * Adds a map of attributes.
     *
     * @param attributes the attributes to add
     * @return AttributesDocumentBuilder
     * @throws NullPointerException if {@code attributes} is {@code null}.
     */
    public AttributesDocumentBuilder attributes(final Attributes attributes) {
        argumentNotNull(attributes);

        attributes.getKeys().forEach(key -> {
            final Object val = attributes.getValue(key).map(ThingDocumentMapper::toValue).orElse(null);
            mainAttribute(key.toString(), val);
            addInternalAttributes(key.toString(), attributes.getValue(key).orElse(null));
        });

        return this;
    }

    /**
     * Adds an attribute.
     *
     * @param key the key
     * @param value the value
     * @return AttributesDocumentBuilder
     */
    public AttributesDocumentBuilder attribute(final String key, final String value) {
        mainAttribute(key, value);
        return attributeInternally(key, value);
    }

    /**
     * Adds an attribute.
     *
     * @param key the key
     * @param value the value
     * @return AttributesDocumentBuilder
     */
    public AttributesDocumentBuilder attribute(final String key, final Number value) {
        mainAttribute(key, value);
        return attributeInternally(key, value);
    }

    /**
     * Adds an attribute.
     *
     * @param key the key
     * @param value the value
     * @return AttributesDocumentBuilder
     */
    public AttributesDocumentBuilder attribute(final String key, final Boolean value) {
        mainAttribute(key, value);
        return attributeInternally(key, value);
    }

    /**
     * Returns the built document.
     *
     * @return the document
     */
    public Document build() {
        return tDocument;
    }


    private AttributesDocumentBuilder attributeInternally(final String key, final Object value) {
        // add attribute to flat representation
        final List<Document> attributesList = ThingDocumentMapper.toList(tDocument, PersistenceConstants.FIELD_INTERNAL);
        final Document doc = ThingDocumentMapper.newDocument();
        doc.append(
                PersistenceConstants.FIELD_INTERNAL_KEY, PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + key);
        doc.append(PersistenceConstants.FIELD_INTERNAL_VALUE, value);
        attributesList.add(doc);

        return this;
    }

    private AttributesDocumentBuilder mainAttribute(final String key, final Object value) {
        final Document doc = (Document) tDocument.get(PersistenceConstants.FIELD_ATTRIBUTES);
        doc.append(KeyEscapeUtil.escape(key), value);
        return this;
    }

    private void addInternalAttributes(final String path, final JsonValue jsonValue) {
        if ((jsonValue == null) || jsonValue.isNull()) {
            attributeInternally(path, null);
        } else if (jsonValue.isString()) {
            attributeInternally(path, jsonValue.asString());
        } else if (jsonValue.isBoolean()) {
            attributeInternally(path, jsonValue.asBoolean());
        } else if (jsonValue.isNumber()) {
            addNumberAttribute(path, jsonValue);
        } else if (jsonValue.isObject()) {
            jsonValue.asObject().getKeys().forEach(key -> //
                    addInternalAttributes(path + PersistenceConstants.SLASH + key, jsonValue.asObject().getValue(key).orElse(null)));
        }
    }

    private void addNumberAttribute(final String path, final JsonValue jsonValue) {
        try {
            attributeInternally(path, jsonValue.asLong());
        } catch (final NumberFormatException e) {
            attributeInternally(path, jsonValue.asDouble());
        }
    }

}
