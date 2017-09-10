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
package com.mongodb.util;


import org.bson.BSONCallback;

/**
 * This is a specialized MongoDB BSON converter which additionally to {@link JSON} from MongoDB takes care that in JSON
 * keys dots "." and dollar signs "$" are replaced with their unicode representations in the {@link #parse(String)}
 * function and vice versa in the {@link #serialize(Object)} function. Copied from {@link JSON} and adjusted for exactly
 * this purpose.
 */
public final class DittoBsonJSON {

    static final char DOLLAR_CHAR = '$';
    static final char DOLLAR_UNICODE_CHAR = '\uFF04';
    static final char DOT_CHAR = '.';
    static final char DOT_UNICODE_CHAR = '\uFF0E';

    /*
     * Inhibit instantiation of this utility class.
     */
    private DittoBsonJSON() {
        // no-op
    }

    /**
     * @see JSON#serialize(Object)
     */
    public static String serialize(final Object object) {
        final StringBuilder buf = new StringBuilder();
        serialize(object, buf);
        return buf.toString().replace(DOLLAR_UNICODE_CHAR, DOLLAR_CHAR).replace(DOT_UNICODE_CHAR, DOT_CHAR);
    }

    /**
     * @see JSON#serialize(Object, StringBuilder)
     */
    private static void serialize(final Object object, final StringBuilder buf) {
        JSON.serialize(object, buf);
    }

    /**
     * @see JSON#parse(String)
     */
    public static Object parse(final String jsonString) {
        return parse(jsonString, null);
    }

    /**
     * @see JSON#parse(String, BSONCallback)
     */
    public static Object parse(final String s, final BSONCallback c) {
        if (s == null || (s.trim()).equals("")) {
            return null;
        }

        final JSONParser p = new CrBsonJSONParser(s, c);
        return p.parse();
    }

    /**
     * Utility class for parsing JSON.
     */
    private static class CrBsonJSONParser extends JSONParser {

        /**
         * Create a new parser.
         */
        public CrBsonJSONParser(final String s, final BSONCallback callback) {
            super(s, callback);
        }

        /**
         * Copied and adjusted from {@link JSONParser#parseObject(String)} in order to parse JSON keys in a way that "."
         * and "$" are escaped with their unicode representations.
         *
         * @return DBObject the next object
         * @throws JSONParseException if invalid JSON is found
         */
        protected Object parseObject(final String name) {
            if (name != null) {
                _callback.objectStart(name);
            } else {
                _callback.objectStart();
            }

            read('{');
            char current = get();
            while (get() != '}') {
                final String key = parseCrKeyString();
                read(':');
                final Object value = parse(key);
                doCallback(key, value);

                if ((current = get()) == ',') {
                    read(',');
                } else {
                    break;
                }
            }
            read('}');

            return _callback.objectDone();
        }

        /**
         * Copied and adjusted from {@link JSONParser#parseString(boolean)}: parses a string with the special Ditto
         * behavior: replaces "." and "$" with their unicode-counterparts.
         *
         * @return the next string.
         */
        @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
        private String parseCrKeyString() {
            char quot = 0;
            if (check('\'')) {
                quot = '\'';
            } else if (check('\"')) {
                quot = '\"';
            }

            char current;

            if (quot > 0) {
                read(quot);
            }
            final StringBuilder buf = new StringBuilder();
            int start = pos;
            while (pos < s.length()) {
                current = s.charAt(pos);
                if (quot > 0) {
                    if (current == quot) {
                        break;
                    }
                } else {
                    if (current == ':' || current == ' ') {
                        break;
                    }
                }

                if (current == '\\') {
                    pos++;

                    char x = get();

                    char special = 0;

                    // CHECKSTYLE:OFF
                    switch (x) {
                        case 'u': // decode unicode
                            buf.append(s.substring(start, pos - 1));
                            pos++;
                            final int tempPos = pos;

                            readHex();
                            readHex();
                            readHex();
                            readHex();

                            final int codePoint = Integer.parseInt(s.substring(tempPos, tempPos + 4), 16);
                            buf.append((char) codePoint);

                            start = pos;
                            continue;
                        case 'n':
                            special = '\n';
                            break;
                        case 'r':
                            special = '\r';
                            break;
                        case 't':
                            special = '\t';
                            break;
                        case 'b':
                            special = '\b';
                            break;
                        case '"':
                            special = '\"';
                            break;
                        case '\\':
                            special = '\\';
                            break;
                        case '.': // custom Ditto
                            special = '\uFF0E'; // custom Ditto
                            break; // custom Ditto
                        case '$': // custom Ditto
                            special = '\uFF04'; // custom Ditto
                            break; // custom Ditto
                        default:
                            break;
                    }
                    // CHECKSTYLE:ON

                    buf.append(s.substring(start, pos - 1));
                    if (special != 0) {
                        pos++;
                        buf.append(special);
                    }
                    start = pos;
                    continue;
                } else if (current == DittoBsonJSON.DOT_CHAR) { // custom Ditto
                    buf.append(s.substring(start, pos));
                    pos++;
                    buf.append(DittoBsonJSON.DOT_UNICODE_CHAR);
                    start = pos;
                    continue;
                } else if (current == DittoBsonJSON.DOLLAR_CHAR) { // custom Ditto
                    buf.append(s.substring(start, pos));
                    pos++;
                    buf.append(DittoBsonJSON.DOLLAR_UNICODE_CHAR);
                    start = pos;
                    continue;
                }
                pos++;
            }
            buf.append(s.substring(start, pos));
            if (quot > 0) {
                read(quot);
            }
            return buf.toString();
        }

    }
}

