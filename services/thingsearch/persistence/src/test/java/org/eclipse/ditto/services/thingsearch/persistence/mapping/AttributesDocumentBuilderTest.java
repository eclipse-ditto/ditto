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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
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

    @Test
    @SuppressWarnings("unchecked")
    public void addAttributes() {
        final AttributesDocumentBuilder attrsDocBuilder = AttributesDocumentBuilder.create();

        final Map<JsonKey, JsonValue> deeperMap = new LinkedHashMap<>();
        deeperMap.put(JsonFactory.newKey(KEY_WITH_SPECIAL_CHARS), JsonFactory.newValue(VAL1));
        final Attributes attributes =
                ThingsModelFactory.newAttributesBuilder().set(KEY1, VAL1).set(KEY_WITH_SPECIAL_CHARS, VAL2)
                        .set(KEY2, JsonFactory.newObject(deeperMap)).build();
        attrsDocBuilder.attributes(attributes);

        final Document doc = attrsDocBuilder.build();

        Assertions.assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(0).get("k"))
                .isEqualTo(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY1);
        Assertions.assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(0).get("v")).isEqualTo(VAL1);
        Assertions.assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(1).get("k"))
                .isEqualTo(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY_WITH_SPECIAL_CHARS);
        Assertions.assertThat(((List<Document>) doc.get(FIELD_INTERNAL)).get(1).get("v")).isEqualTo(VAL2);
        final Document mainAttributesDoc = (Document) doc.get(FIELD_ATTRIBUTES);
        Assertions.assertThat(mainAttributesDoc.get(KEY1)).isEqualTo(VAL1);
        Assertions.assertThat(mainAttributesDoc.get(KeyEscapeUtil.escape(KEY_WITH_SPECIAL_CHARS))).isEqualTo(VAL2);
        Assertions.assertThat(((Map) mainAttributesDoc.get(KEY2)).get(KeyEscapeUtil.escape(KEY_WITH_SPECIAL_CHARS))).isEqualTo(
                VAL1);
    }
}
