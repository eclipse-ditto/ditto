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
import java.util.Random;

import org.assertj.core.data.Offset;
import org.eclipse.ditto.json.JsonKey;
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

    // --- jsonPointerBytes: allocation-free byte length must equal toString().getBytes(UTF_8).length ---

    @Test
    public void jsonPointerBytesMatchesToStringForRootAndSimplePointers() {
        assertPointerBytesMatchesToString(JsonPointer.empty());
        assertPointerBytesMatchesToString(JsonPointer.of("/"));
        assertPointerBytesMatchesToString(JsonPointer.of("/attributes"));
        assertPointerBytesMatchesToString(JsonPointer.of("/attributes/location/latitude"));
        assertPointerBytesMatchesToString(JsonPointer.of("/features/lamp/properties/on/deep/nested/path"));
        assertPointerBytesMatchesToString(pointerOfKeys("123", "456"));
    }

    @Test
    public void jsonPointerBytesMatchesToStringForTildeEscaping() {
        assertPointerBytesMatchesToString(pointerOfKeys("~"));
        assertPointerBytesMatchesToString(pointerOfKeys("a~b"));
        assertPointerBytesMatchesToString(pointerOfKeys("~~"));
        assertPointerBytesMatchesToString(pointerOfKeys("~0", "~1"));
        assertPointerBytesMatchesToString(pointerOfKeys("~leading", "trailing~", "mid~dle"));
        assertPointerBytesMatchesToString(pointerOfKeys("a~b", "c~~d", "~"));
    }

    @Test
    public void jsonPointerBytesMatchesToStringForMultiByteUnicode() {
        assertPointerBytesMatchesToString(pointerOfKeys("café"));       // e-acute = 2-byte
        assertPointerBytesMatchesToString(pointerOfKeys("price€"));     // euro sign = 3-byte
        assertPointerBytesMatchesToString(pointerOfKeys("emoji😀")); // grinning face = 4-byte pair
        assertPointerBytesMatchesToString(pointerOfKeys("mixé€😀~end"));
        assertPointerBytesMatchesToString(pointerOfKeys("é", "€", "😀"));
    }

    @Test
    public void jsonPointerBytesMatchesToStringForUnpairedSurrogates() {
        assertPointerBytesMatchesToString(pointerOfKeys("\uD83D"));          // lone high surrogate
        assertPointerBytesMatchesToString(pointerOfKeys("\uDE00"));          // lone low surrogate
        assertPointerBytesMatchesToString(pointerOfKeys("a\uD83Db"));        // high surrogate followed by non-low
        assertPointerBytesMatchesToString(pointerOfKeys("\uD83D\uD83D"));    // two high surrogates
        assertPointerBytesMatchesToString(pointerOfKeys("\uDE00\uD83D"));    // low then high
    }

    @Test
    public void jsonPointerBytesMatchesToStringForRandomKeys() {
        final Random random = new Random(20260731L);
        for (int iteration = 0; iteration < 5000; iteration++) {
            final int levels = 1 + random.nextInt(6);
            final String[] keys = new String[levels];
            for (int level = 0; level < levels; level++) {
                keys[level] = randomKey(random);
            }
            assertPointerBytesMatchesToString(pointerOfKeys(keys));
        }
    }

    private static void assertPointerBytesMatchesToString(final JsonPointer pointer) {
        final int expected = pointer.toString().getBytes(StandardCharsets.UTF_8).length;
        assertThat(IndexLengthRestrictionEnforcer.jsonPointerBytes(pointer))
                .as("byte length of pointer <%s>", pointer)
                .isEqualTo(expected);
    }

    private static JsonPointer pointerOfKeys(final String... keys) {
        JsonPointer pointer = JsonPointer.empty();
        for (final String key : keys) {
            pointer = pointer.addLeaf(JsonKey.of(key));
        }
        return pointer;
    }

    private static String randomKey(final Random random) {
        final int targetLength = 1 + random.nextInt(12);
        final StringBuilder sb = new StringBuilder(targetLength);
        while (sb.length() < targetLength) {
            final int pick = random.nextInt(100);
            if (pick < 55) {
                sb.append((char) ('a' + random.nextInt(26)));            // ascii letters
            } else if (pick < 65) {
                sb.append((char) ('0' + random.nextInt(10)));            // digits
            } else if (pick < 75) {
                sb.append('~');                                          // tilde -> escaped to "~0"
            } else if (pick < 85) {
                sb.append((char) (0x80 + random.nextInt(0x780)));        // 2-byte code points (0x80..0x7FF)
            } else if (pick < 93) {
                int cp = 0x800 + random.nextInt(0xF800);                 // 3-byte BMP code points
                if (cp >= 0xD800 && cp <= 0xDFFF) {
                    cp = 0x800 + (cp - 0xD800);                          // skip the surrogate range
                }
                sb.append((char) cp);
            } else {
                sb.appendCodePoint(0x10000 + random.nextInt(0x100000));  // supplementary -> surrogate pair
            }
        }
        return sb.toString();
    }

}
