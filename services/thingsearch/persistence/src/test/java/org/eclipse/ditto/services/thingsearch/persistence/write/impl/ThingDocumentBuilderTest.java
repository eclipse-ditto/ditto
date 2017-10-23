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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.services.thingsearch.persistence.read.document.ThingDocumentBuilder;
import org.junit.Test;

/**
 * Tests {@link ThingDocumentBuilder}.
 */
public class ThingDocumentBuilderTest {

    private static final String THING_ID = "myThing1";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String KEY3 = "key3";
    private static final String VALUE1 = "value1";
    private static final Integer VALUE2 = 5;
    private static final Boolean VALUE3 = true;
    private static final String SID = "fdaifhis42453";

    @SuppressWarnings("unchecked")
    private static void assertDocumentBuiltCorrect(final Document buildDoc) {
        assertThat(((List<Document>) buildDoc.get(FIELD_INTERNAL)).get(0).get("k"))
                .isEqualTo(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY1);
        assertThat(((List<Document>) buildDoc.get(FIELD_INTERNAL)).get(0).get("v")).isEqualTo(VALUE1);
        assertThat(((List<Document>) buildDoc.get(FIELD_INTERNAL)).get(1).get("k"))
                .isEqualTo(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY2);
        assertThat(((List<Document>) buildDoc.get(FIELD_INTERNAL)).get(1).get("v")).isEqualTo(VALUE2);
        assertThat(((List<Document>) buildDoc.get(FIELD_INTERNAL)).get(2).get("k"))
                .isEqualTo(FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KEY3);
        assertThat(((List<Document>) buildDoc.get(FIELD_INTERNAL)).get(2).get("v")).isEqualTo(VALUE3);
        assertThat(((List<Document>) buildDoc.get(FIELD_INTERNAL)).get(3).get(FIELD_ACL)).isEqualTo(SID);
    }

    /** */
    @Test
    public void buildThing() {
        final ThingDocumentBuilder builder = ThingDocumentBuilder.create(THING_ID);
        final Document buildDoc =
                builder.attribute(KEY1, VALUE1)
                        .attribute(KEY2, VALUE2)
                        .attribute(KEY3, VALUE3)
                        .aclReadEntry(SID)
                        .build();
        assertDocumentBuiltCorrect(buildDoc);
    }

}
