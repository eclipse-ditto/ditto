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
package org.eclipse.ditto.services.thingsearch.persistence.write;


import static org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer.MAX_INDEX_CONTENT_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.persistence.util.TestStringGenerator;

/**
 * Test for IndexLengthRestrictionEnforcer
 */
public final class IndexLengthRestrictionEnforcerTest {

    private static final String NAMESPACE = "com.bosch.iot.test";
    private static final String THING_ID = NAMESPACE + ":" + "myThingId";

    /**
     * Overhead of thingId and namespace.
     */
    private static final int THING_ID_NAMESPACE_OVERHEAD = THING_ID.length() + NAMESPACE.length();

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
        final String key = "/attributes/enforceRestrictionsOnViolation";
        final int maxAllowedValueForKey = MAX_INDEX_CONTENT_LENGTH - THING_ID_NAMESPACE_OVERHEAD - key.length();
        final String value = TestStringGenerator.createString(maxAllowedValueForKey + 1);
        assertThat(indexLengthRestrictionEnforcer.enforce(JsonPointer.of(key), JsonValue.of(value)))
                .contains(JsonValue.of(value.substring(0, maxAllowedValueForKey)));
    }

    @Test
    public void doNotTruncateWithoutViolation() {
        final String key = "/attributes/doesNotTruncateWithoutViolation";
        final int maxAllowedValueForKey = MAX_INDEX_CONTENT_LENGTH - THING_ID_NAMESPACE_OVERHEAD - key.length();
        final String value = TestStringGenerator.createString(maxAllowedValueForKey);
        assertThat(indexLengthRestrictionEnforcer.enforce(JsonPointer.of(key), JsonValue.of(value)))
                .contains(JsonValue.of(value));
    }

    @Test
    public void giveUpIfKeyIsTooLong() {
        final String value = "value";
        final String baseKey = "/attributes/giveUpIfKeyIsTooLong/";
        final int maxAllowed =
                MAX_INDEX_CONTENT_LENGTH - THING_ID_NAMESPACE_OVERHEAD - baseKey.length();
        final String key = baseKey + TestStringGenerator.createString(maxAllowed + 1);
        assertThat(indexLengthRestrictionEnforcer.enforce(JsonPointer.of(key), JsonValue.of(value)))
                .isEmpty();
    }
}
