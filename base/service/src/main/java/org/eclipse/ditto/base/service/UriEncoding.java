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
package org.eclipse.ditto.base.service;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;

/**
 * Utility for URI encoding and decoding based on RFC 3986 or MIME format {@code application/x-www-form-urlencoded}. As
 * character encoding, UTF-8 is used by all methods.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>
 */
public final class UriEncoding {

    private static final String ENCODING = StandardCharsets.UTF_8.name();
    private static final IntPredicate ALLOWED_IN_PATH = c -> isPchar(c) || '/' == c;
    private static final IntPredicate ALLOWED_IN_PATH_SEGMENT = UriEncoding::isPchar;
    /*
     * Workaround: '+' needs to be escaped to '%2B', otherwise it will be recognized as blank when decoding with MIME
     * format {@code application/x-www-form-urlencoded} - what most servers do (such as akka-http).
     */
    private static final IntPredicate ALLOWED_IN_QUERY = c -> c != '+' && (isPchar(c) || '/' == c || '?' == c);
    private static final IntPredicate ALLOWED_IN_QUERY_PARAM =
            c -> !('=' == c || '&' == c) && ALLOWED_IN_QUERY.test(c);

    /**
     * Encodes the given path according to RFC 3986.
     *
     * @param path the path to be encoded
     * @return the encoded path
     */
    public static String encodePath(final String path) {
        requireNonNull(path);

        return encodeRFC3986UriComponent(path, ALLOWED_IN_PATH);
    }

    /**
     * Encodes the given path segment according to RFC 3986.
     *
     * @param pathSegment the path segment to be encoded
     * @return the encoded path segment
     */
    public static String encodePathSegment(final String pathSegment) {
        requireNonNull(pathSegment);

        return encodeRFC3986UriComponent(pathSegment, ALLOWED_IN_PATH_SEGMENT);
    }

    /**
     * Encodes the given query according to the specified encoding type.
     *
     * @param query the query to be encoded
     * @param encType the encoding type to be used for encoding
     * @return the encoded query
     */
    public static String encodeQuery(final String query, final EncodingType encType) {
        requireNonNull(query);
        requireNonNull(encType);

        if (encType == EncodingType.FORM_URL_ENCODED) {
            return encodeFormUrlEncoded(query) //
                    .replaceAll("%3D", "=") //
                    .replaceAll("%26", "&");
        } else {
            return encodeRFC3986UriComponent(query, ALLOWED_IN_QUERY);
        }
    }

    /**
     * Encodes the given query parameter according to the specified encoding type.
     *
     * @param queryParam the query parameter to be encoded
     * @param encType the encoding type to be used for encoding
     * @return the encoded query
     */
    public static String encodeQueryParam(final String queryParam, final EncodingType encType) {
        requireNonNull(queryParam);
        requireNonNull(encType);

        if (encType == EncodingType.FORM_URL_ENCODED) {
            return encodeFormUrlEncoded(queryParam);
        } else {
            return encodeRFC3986UriComponent(queryParam, ALLOWED_IN_QUERY_PARAM);
        }
    }

    /**
     * Encodes every character other than unreserved characters. Used in AWS request signing.
     *
     * @param string the string to encode.
     * @return the encoded string.
     */
    public static String encodeAllButUnreserved(final String string) {
        return encodeRFC3986UriComponent(string, UriEncoding::isUnreserved);
    }

    /**
     * Decodes the given encoded source String according to the specified encoding type.
     *
     * @param source the source
     * @param encType the encoding type to be used for decoding
     * @return the decoded URI
     * @throws IllegalArgumentException when the given source contains invalid encoded sequences
     */
    public static String decode(final String source, final EncodingType encType) {
        requireNonNull(source);
        requireNonNull(encType);

        if (encType == EncodingType.FORM_URL_ENCODED) {
            return decodeFormUrlEncoded(source);
        } else {
            return decodeRFC3986(source);
        }
    }

    private static String encodeFormUrlEncoded(final String s) {
        try {
            return URLEncoder.encode(s, ENCODING);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decodeFormUrlEncoded(final String source) {
        try {
            return URLDecoder.decode(source, ENCODING);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("squid:S3776") // simplification of this method would be difficult without sacrificing performance
    private static String decodeRFC3986(final String source) {
        final int length = source.length();
        final ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        boolean changed = false;

        int i = 0;
        while (i < length) {
            final int c = source.charAt(i);
            if (c == '%') {
                if ((i + 2) < length) {
                    final char hex1 = source.charAt(i + 1);
                    final char hex2 = source.charAt(i + 2);
                    final int digit1 = Character.digit(hex1, 16);
                    final int digit2 = Character.digit(hex2, 16);
                    if (digit1 < 0 || digit2 < 0) {
                        throw new IllegalArgumentException(
                                "Illegal hex characters in escape pattern - negative value: \"" + source.substring(i) +
                                        "\"!");
                    }
                    out.write((char) ((digit1 << 4) + digit2));
                    i += 2;
                    changed = true;
                } else {
                    throw new IllegalArgumentException(
                            "Incomplete trailing escape pattern: \"" + source.substring(i) + "\"!");
                }
            } else {
                out.write(c);
            }
            i++;
        }

        if (changed) {
            try {
                return new String(out.toByteArray(), ENCODING);
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        } else {
            return source;
        }
    }

    /**
     * Encode a URI component with a custom predicate of allowed characters.
     *
     * @param source the string to encode.
     * @param allowedChars what characters are not encoded.
     * @return the encoded string.
     */
    public static String encodeRFC3986UriComponent(final String source, final IntPredicate allowedChars) {
        requireNonNull(source);

        try {
            final byte[] bytes = encodeBytes(source.getBytes(ENCODING), allowedChars);
            return new String(bytes, StandardCharsets.US_ASCII.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] encodeBytes(final byte[] source, final IntPredicate allowedChars) {
        requireNonNull(source);
        requireNonNull(allowedChars);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(source.length);
        for (byte b : source) {
            if (b < 0) {
                b += 256;
            }
            if (allowedChars.test((char) b)) {
                out.write(b);
            } else {
                out.write('%');
                final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
                final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                out.write(hex1);
                out.write(hex2);
            }
        }
        return out.toByteArray();
    }

    /**
     * Whether the given char belongs to the {@code pchar} set.
     *
     * @param c the char.
     * @return whether it belongs to the {@code pchar} set.
     * @since 2.1.0
     */
    public static boolean isPchar(final int c) {
        return isUnreserved(c) || isSubDelimiter(c) || ':' == c || '@' == c;
    }

    /**
     * Whether the given char belongs to the {@code unreserved} set.
     *
     * @param c the char.
     * @return whether it belongs to the {@code unreserved} set.
     */
    public static boolean isUnreserved(final int c) {
        return isAlpha(c) || isDigit(c) || '-' == c || '.' == c || '_' == c || '~' == c;
    }

    /**
     * Whether the given char belongs to the {@code ALPHA} set.
     */
    private static boolean isAlpha(final int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    /**
     * Whether the given char belongs to the {@code DIGIT} set.
     */
    private static boolean isDigit(final int c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Whether the given char belongs to the {@code sub-delims} set.
     */
    private static boolean isSubDelimiter(final int c) {
        return '!' == c || '$' == c || '&' == c || '\'' == c || '(' == c || ')' == c || '*' == c || '+' == c || ',' == c
                || ';' == c || '=' == c;
    }

    /**
     * The type of the encoding to be used for encoding/decoding.
     */
    public enum EncodingType {
        /**
         * Encoding according to RFC 3986.
         */
        RFC3986,
        /**
         * Encoding according to MIME format {@code application/x-www-form-urlencoded}. This format is used by many
         * applications incorrectly to encode arbitrary query strings, not only when using this MIME format. For
         * compatibility reasons it is recommended to decode query strings with this format.
         **/
        FORM_URL_ENCODED
    }

}
