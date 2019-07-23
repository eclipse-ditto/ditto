/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for encoding/decoding parts used in names / IDs in Ditto, e.g. in:
 * <ul>
 * <li>Thing names</li>
 * <li>Feature IDs</li>
 * <li>Attribute keys</li>
 * <li>Feature property keys</li>
 * </ul>
 * The following <strong> 7 characters</strong> are encoded as using them in the above places it would lead to require
 * escaping, e.g. when accessing the HTTP API or using the DittoProtocol:
 * <pre>
 * / (slash)                used in HTTP urls and in DittoProtocol topic to delimit segments
 * ? (question mark)        used in HTTP urls to start the query parameters
 * # (hash mark)            used in HTTP urls to define a fragment identifier
 * * (asterisk)             used in Ditto's search syntax in "like" queries
 * , (comma)                used in Ditto's HTTP API to return several thing ID separated by commas
 *   (single whitespace)    must be escaped in HTTP urls to %20
 * % (percent)              used to define an escaped character itself
 * </pre>
 */
public final class DittoEncodingHelper {

    private static final Map<Character, CharSequence> ENCODE_MAP;
    private static final Map<CharSequence, Character> DECODE_MAP;

    static {
        ENCODE_MAP = new HashMap<>();
        DECODE_MAP = new HashMap<>();

        ENCODE_MAP.put('/', "%2F");
        ENCODE_MAP.put('?', "%3F");
        ENCODE_MAP.put('#', "%23");
        ENCODE_MAP.put('*', "%2A");
        ENCODE_MAP.put(',', "%2C");
        ENCODE_MAP.put(' ', "%20");
        ENCODE_MAP.put('%', "%25");
        ENCODE_MAP.put('"', "%22");
        ENCODE_MAP.forEach((key, value) -> DECODE_MAP.put(value, key));
    }

    private DittoEncodingHelper() {
        throw new AssertionError();
    }

    /**
     * Encodes the given String by "percent-encoding" the harmful characters.
     *
     * @param encodeTarget the String to encode.
     * @return the encoded String.
     */
    public static String encode(final String encodeTarget) {
        final StringBuilder stringBuilder = new StringBuilder(encodeTarget.length());
        for (final char character : encodeTarget.toCharArray()) {
            final CharSequence replacement = ENCODE_MAP.get(character);
            if (replacement != null) {
                stringBuilder.append(replacement);
            } else {
                stringBuilder.append(character);
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Decodes the given String by decoding the "percent-encoded" characters in the passed {@code decodeTarget}.
     *
     * @param decodeTarget the String to decode.
     * @return the decoded String.
     */
    public static String decode(final String decodeTarget) {
        final StringBuilder stringBuilder = new StringBuilder(decodeTarget.length());
        final char[] chars = decodeTarget.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char character = chars[i];
            if (character == '%') {
                // read the following 2 characters (and proceed with the "i" pointer):
                @SuppressWarnings("squid:ForLoopCounterChangedCheck")
                final String escaped = new String(new char[]{character, chars[++i], chars[++i]});
                final Character replacement = DECODE_MAP.get(escaped);
                stringBuilder.append(replacement);
            } else {
                stringBuilder.append(character);
            }
        }
        return stringBuilder.toString();
    }


}
