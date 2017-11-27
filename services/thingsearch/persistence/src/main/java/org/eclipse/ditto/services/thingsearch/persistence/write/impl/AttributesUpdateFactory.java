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
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.services.thingsearch.persistence.MongoSortKeyMappingFunction;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.mapping.ThingDocumentMapper;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;

/**
 * Factory to create Attribute related updates.
 */
final class AttributesUpdateFactory {

    private static final Bson PULL_ATTRIBUTES = new Document(
            PersistenceConstants.PULL, new Document(PersistenceConstants.FIELD_INTERNAL,
            new Document(PersistenceConstants.FIELD_INTERNAL_KEY, new Document(
                    PersistenceConstants.REGEX, "^" + PersistenceConstants.FIELD_ATTRIBUTE_PREFIX))));


    private AttributesUpdateFactory() {
        throw new AssertionError();
    }

    /**
     * Create a list of the attributes updates for a single attribute value as Bson.
     *
     * @param jsonPointer the pointer for the path of the attribute
     * @param jsonValue the attribute value
     * @return the update bson
     */
    static List<Bson> createAttributesUpdates(final IndexLengthRestrictionEnforcer
            indexLengthRestrictionEnforcer,
            final JsonPointer jsonPointer,
            final JsonValue jsonValue) {

        final JsonValue withRestrictions =
                indexLengthRestrictionEnforcer.enforceRestrictionsOnAttributeValue(jsonPointer,
                        jsonValue);
        final List<Document> internalAttributesList =
                toFlatAttributesList(jsonPointer.toString(), withRestrictions, new ArrayList<>());
        final Document setUpdatePart = createSetUpdatePart(jsonPointer, withRestrictions);

        final Bson update1 = createSortStructureUpdate(setUpdatePart);
        final Bson update2 = createSearchStructurePull(jsonPointer);
        final Bson update3 = createSearchStructurePush(internalAttributesList);

        return Arrays.asList(update1, update2, update3);
    }

    /**
     * Creates the update for all attributes.
     *
     * @param attributes the new attributes
     * @return the created update
     */
    static List<Bson> createAttributesUpdate(final IndexLengthRestrictionEnforcer
            indexLengthRestrictionEnforcer,
            final Attributes attributes) {
        final Attributes withRestrictions = indexLengthRestrictionEnforcer.enforceRestrictions(attributes);
        final Bson update1 = createSortStructureUpdate(createSetUpdatePart(withRestrictions));
        final Bson update3 = createSearchStructurePush(createInternalAttributes(withRestrictions));

        return Arrays.asList(update1, PULL_ATTRIBUTES, update3);
    }

    /**
     * Creates a bson update for deletion of a single attribute.
     *
     * @param jsonPointer the pointer of the attribute to delete.
     * @return the bson
     */
    static Bson createAttributeDeletionUpdate(final JsonPointer jsonPointer) {
        final Document update = new Document();
        final String sortKey = MongoSortKeyMappingFunction.mapSortKey(
                PersistenceConstants.FIELD_ATTRIBUTES + jsonPointer.toString());
        update.append(PersistenceConstants.UNSET, new Document(sortKey, ""));
        update.append(PersistenceConstants.PULL, new Document(PersistenceConstants.FIELD_INTERNAL, new Document(
                PersistenceConstants.FIELD_INTERNAL_KEY, new Document(PersistenceConstants.REGEX,
                createPrefixRegex(jsonPointer)))));

        return update;
    }

    /**
     * Removes all existing attributes.
     *
     * @return the update
     */
    static Bson deleteAttributes() {
        return new Document()
                .append(PersistenceConstants.PULL, new Document(PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.FIELD_INTERNAL_KEY,
                                new Document(PersistenceConstants.EXISTS, Boolean.TRUE))
                                .append(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, new Document(
                                        PersistenceConstants.EXISTS, Boolean.FALSE))))
                .append(PersistenceConstants.UNSET, new Document(PersistenceConstants.FIELD_ATTRIBUTES, ""));
    }


    private static List<Document> createInternalAttributes(final Iterable<JsonField> attributes) {
        final List<Document> internalFlatList = new ArrayList<>();
        for (final JsonField attribute : attributes) {
            final JsonValue value = attribute.getValue();
            final JsonKey key = attribute.getKey();
            internalFlatList.addAll(toFlatAttributesList(PersistenceConstants.SLASH + key, value, new ArrayList<>()));
        }
        return internalFlatList;
    }

    private static Document createSearchStructurePush(final List<Document> internalAttributesList) {
        return new Document().append(PersistenceConstants.PUSH,
                new Document().append(PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.EACH, internalAttributesList)));
    }

    private static Document createSearchStructurePull(final JsonPointer jsonPointer) {
        return new Document().append(PersistenceConstants.PULL,
                new Document(PersistenceConstants.FIELD_INTERNAL,
                        new Document(PersistenceConstants.FIELD_INTERNAL_KEY,
                                new Document(PersistenceConstants.REGEX, createPrefixRegex(jsonPointer)))));
    }

    private static Document createSortStructureUpdate(final Document setUpdatePart) {
        return new Document().append(PersistenceConstants.SET, setUpdatePart);
    }

    private static Document createSetUpdatePart(final JsonPointer jsonPointer, final JsonValue value) {
        final Document setExecutions = new Document();
        final String sortKey = MongoSortKeyMappingFunction.mapSortKey(
                PersistenceConstants.FIELD_ATTRIBUTES + jsonPointer.toString());
        setExecutions.append(sortKey, ThingDocumentMapper.toValue(value));
        return setExecutions;
    }

    private static Document createSetUpdatePart(final JsonObject attributes) {
        final Document setExecutions = new Document();
        setExecutions.append(PersistenceConstants.FIELD_ATTRIBUTES, ThingDocumentMapper.toValue(attributes));
        return setExecutions;
    }

    private static String createPrefixRegex(final JsonPointer jsonPointer) {
        return PersistenceConstants.REGEX_FIELD_START + PersistenceConstants.FIELD_ATTRIBUTE_PREFIX +
                jsonPointer.toString() + PersistenceConstants.REGEX_FIELD_END;
    }

    private static List<Document> toFlatAttributesList(final String path, final JsonValue value,
            final List<Document> flatAttributes) {
        if (value.isString()) {
            flatAttributes.add(createFlatSubDocument(path, value.asString()));
        } else if (value.isBoolean()) {
            flatAttributes.add(createFlatSubDocument(path, value.asBoolean()));
        } else if (value.isNumber()) {
            try {
                flatAttributes.add(createFlatSubDocument(path, value.asLong()));
            } catch (final NumberFormatException e) {
                flatAttributes.add(createFlatSubDocument(path, value.asDouble()));
            }
        } else if (value.isNull()) {
            flatAttributes.add(createFlatSubDocument(path, null));
        } else if (value.isObject()) {
            handleObject(path, value, flatAttributes);
        }
        return flatAttributes;
    }

    private static void handleObject(final String path, final JsonValue value, final List<Document> flatAttributes) {
        final JsonObject jsonObject = value.asObject();

        jsonObject.getKeys().forEach(key -> {
            final String newPath = path + PersistenceConstants.SLASH + key;
            final JsonValue innerValue = jsonObject.getValue(key).orElse(null);
            toFlatAttributesList(newPath, innerValue, flatAttributes);
        });
    }

    private static Document createFlatSubDocument(final String key, final Object value) {
        final Document doc = new Document();
        doc.append(PersistenceConstants.FIELD_INTERNAL_KEY, PersistenceConstants.FIELD_ATTRIBUTE_PREFIX + key);
        doc.append(PersistenceConstants.FIELD_INTERNAL_VALUE, value);
        return doc;
    }

}
