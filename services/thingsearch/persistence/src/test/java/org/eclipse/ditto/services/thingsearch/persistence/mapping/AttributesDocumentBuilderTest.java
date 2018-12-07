/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil;
import org.junit.Test;

/**
 * Tests {@link AttributesDocumentBuilder}.
 */
public class AttributesDocumentBuilderTest {

    private static final String KEY_WITH_SPECIAL_CHARS = "$org.eclipse.ditto.withSpecialChars";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String VAL1 = "val1";
    private static final String VAL2 = "val2";
    private static final String KEY_BOOL = "a_boolean";
    private static final boolean VALUE_BOOL = true;
    private static final String KEY_INT = "an_integer";
    private static final int VALUE_INT = Integer.MAX_VALUE;
    private static final String KEY_LONG = "a_long";
    private static final long VALUE_LONG = Long.MIN_VALUE;
    private static final String KEY_DOUBLE = "a_double";
    private static final double VALUE_DOUBLE = 23.42D;
    private static final String KEY_OBJECT = "an_object";
    private static final JsonObject VALUE_OBJECT = JsonObject.newBuilder()
            .set("key", "value")
            .set("foo", "bar")
            .set("on", true)
            .build();

    @Test
    @SuppressWarnings("unchecked")
    public void addAttributes() {
        final Map<JsonKey, JsonValue> deeperMap = new LinkedHashMap<>();
        deeperMap.put(JsonFactory.newKey(KEY_WITH_SPECIAL_CHARS), JsonFactory.newValue(VAL1));
        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set(KEY1, VAL1)
                .set(KEY_WITH_SPECIAL_CHARS, VAL2)
                .set(KEY2, JsonFactory.newObject(deeperMap))
                .set(KEY_BOOL, VALUE_BOOL)
                .set(KEY_INT, VALUE_INT)
                .set(KEY_LONG, VALUE_LONG)
                .set(KEY_DOUBLE, VALUE_DOUBLE)
                .set(KEY_OBJECT, VALUE_OBJECT)
                .build();

        final AttributesDocumentBuilder underTest = AttributesDocumentBuilder.create();
        underTest.attributes(attributes);

        final Document doc = underTest.build();

        assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(0).get("k"))
                .isEqualTo(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY1);
        assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(0).get("v")).isEqualTo(VAL1);
        assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(1).get("k"))
                .isEqualTo(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY_WITH_SPECIAL_CHARS);
        assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(1).get("v")).isEqualTo(VAL2);

        final Document mainAttributesDoc = (Document) doc.get(FIELD_ATTRIBUTES);

        assertThat(mainAttributesDoc.get(KEY1)).isEqualTo(VAL1);
        assertThat(mainAttributesDoc.get(KeyEscapeUtil.escape(KEY_WITH_SPECIAL_CHARS))).isEqualTo(VAL2);
        assertThat(((Map) mainAttributesDoc.get(KEY2)).get(KeyEscapeUtil.escape(KEY_WITH_SPECIAL_CHARS))).isEqualTo(
                VAL1);
    }

}
