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
package org.eclipse.ditto.thingsearch.service.persistence.write;


import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.thingsearch.service.persistence.write.IndexLengthRestrictionEnforcer.MAX_INDEX_CONTENT_LENGTH;

import java.nio.charset.StandardCharsets;

import org.assertj.core.data.Offset;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.service.persistence.util.TestStringGenerator;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for IndexLengthRestrictionEnforcer
 */
public final class IndexLengthRestrictionEnforcerTest {

    private static final String NAMESPACE = "org.eclipse.ditto.test";
    private static final String THING_ID = NAMESPACE + ":" + "myThingId";

    /**
     * Extra overhead in index key.
     */
    private static final int OVERHEAD =
            THING_ID.length() + NAMESPACE.length() + IndexLengthRestrictionEnforcer.AUTHORIZATION_SUBJECT_OVERHEAD;

    private IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;

    @Before
    public void setUp() {
        this.indexLengthRestrictionEnforcer = IndexLengthRestrictionEnforcer.newInstance(THING_ID);
    }

    @Test(expected = NullPointerException.class)
    public void newInstanceWithNullThingIdFails() {
        IndexLengthRestrictionEnforcer.newInstance(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceWithEmptyThingIdFails() {
        IndexLengthRestrictionEnforcer.newInstance("");
    }

    @Test
    public void enforceRestrictionsOnViolation() {
        // GIVEN:
        final String key = "/attributes/enforceRestrictionsOnViolation";
        final int maxAllowedValueForKey = MAX_INDEX_CONTENT_LENGTH - OVERHEAD - key.length();
        final String value = TestStringGenerator.createStringOfBytes(maxAllowedValueForKey + 1);

        // WHEN:
        final String enforcedValue = indexLengthRestrictionEnforcer.enforce(JsonPointer.of(key), JsonValue.of(value))
                .orElseThrow()
                .asString();
        final int enforcedValueBytes = enforcedValue.getBytes(StandardCharsets.UTF_8).length;

        // THEN: value is truncated to fit in the max allowed value bytes
        assertThat(value).startsWith(enforcedValue);
        assertThat(enforcedValueBytes).isLessThanOrEqualTo(maxAllowedValueForKey);

        // THEN: value is not truncated more than needed
        final int maxUtf8Bytes = 4;
        assertThat(enforcedValueBytes).isCloseTo(maxAllowedValueForKey, Offset.offset(maxUtf8Bytes));
    }

    @Test
    public void doNotTruncateWithoutViolation() {
        final String key = "/attributes/doesNotTruncateWithoutViolation";
        final int maxAllowedValueForKey = MAX_INDEX_CONTENT_LENGTH - OVERHEAD - key.length();
        final String value = TestStringGenerator.createStringOfBytes(maxAllowedValueForKey);
        assertThat(indexLengthRestrictionEnforcer.enforce(JsonPointer.of(key), JsonValue.of(value)))
                .contains(JsonValue.of(value));
    }

    @Test
    public void giveUpIfKeyIsTooLong() {
        final String value = "value";
        final String baseKey = "/attributes/giveUpIfKeyIsTooLong/";
        final int maxAllowed =
                MAX_INDEX_CONTENT_LENGTH - OVERHEAD - baseKey.length();
        final String key = baseKey + TestStringGenerator.createStringOfBytes(maxAllowed + 1);
        assertThat(indexLengthRestrictionEnforcer.enforce(JsonPointer.of(key), JsonValue.of(value)))
                .isEmpty();
    }

}
