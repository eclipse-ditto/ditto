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
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

/**
 * This factory is capable of parsing a (complex) JSON field selector string in order to create an instance of
 * {@link ImmutableJsonFieldSelector}. For example, the field selector string
 * <p>
 * {@code "thingId,attributes(acceleration,someData(foo,bar/baz)),features/key"}
 * </p>
 * would lead to a JSON field selector which consists of the following JSON pointers:
 * <ul>
 * <li>{@code "thingId"},</li>
 * <li>{@code "attributes/acceleration"},</li>
 * <li>{@code "attributes/someData/foo"},</li>
 * <li>{@code "attributes/someData/bar/baz"},</li>
 * <li>{@code "features/key"}.</li>
 * </ul>
 */
@Immutable
final class ImmutableJsonFieldSelectorFactory {

    private static final String PLACEHOLDER_GROUP_NAME = "p";
    private static final String PLACEHOLDER_START = Pattern.quote("{{");
    private static final String PLACEHOLDER_END = Pattern.quote("}}");

    /*
     * Caution: If you adapt this regex, make sure to adapt it also in org.eclipse.ditto.base.model.common.Placeholders.
     * It had to be duplicated because it couldn't be used here due to dependency cycles.
     */
    private static final String PLACEHOLDER_GROUP = "(?<" + PLACEHOLDER_GROUP_NAME + ">((}[^}]|[^}])*+))";
    private static final String ANY_NUMBER_OF_SPACES = "\\s*+";
    private static final String PLACEHOLDER_REGEX = PLACEHOLDER_START
            + ANY_NUMBER_OF_SPACES // allow arbitrary number of spaces
            + PLACEHOLDER_GROUP // the content of the placeholder
            + ANY_NUMBER_OF_SPACES  // allow arbitrary number of spaces
            + PLACEHOLDER_END; // end of placeholder
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(PLACEHOLDER_REGEX);

    private static final String OPENING_PARENTHESIS = "(";
    private static final String CLOSING_PARENTHESIS = ")";

    private final String jsonFieldSelectorString;

    private ImmutableJsonFieldSelectorFactory(final String theJsonFieldSelectorString) {
        jsonFieldSelectorString = theJsonFieldSelectorString;

        validateJsonFieldSelectorString();
    }

    /**
     * Returns a new instance of {@code ImmutableJsonFieldSelectorFactory} which constructs a {@link JsonFieldSelector}
     * based on the given string.
     *
     * @param jsonFieldSelectorString the string to create a JSON field selector from.
     * @param jsonParseOptions the JsonParseOptions to apply when parsing the {@code jsonFieldSelectorString}.
     * @return a new JSON field selector factory.
     * @throws NullPointerException if {@code jsonFieldSelectorString} is {@code null}.
     * @throws JsonFieldSelectorInvalidException if {@code jsonFieldSelectorString} is empty or if {@code
     * jsonFieldSelectorString} does not contain closing parenthesis ({@code )}) for each opening parenthesis ({@code
     * (}).
     */
    public static ImmutableJsonFieldSelectorFactory newInstance(final String jsonFieldSelectorString,
            final JsonParseOptions jsonParseOptions) {
        requireNonNull(jsonFieldSelectorString, "The JSON field selector string must not be null!");
        requireNonNull(jsonParseOptions, "The JSON parse options must not be null!");

        final String decodedJsonFieldSelectorString = tryToDecodeString(jsonFieldSelectorString, jsonParseOptions);
        return new ImmutableJsonFieldSelectorFactory(decodedJsonFieldSelectorString);
    }

    @SuppressWarnings("squid:S1166")
    private static String tryToDecodeString(final String s, final JsonParseOptions jsonParseOptions) {
        try {
            return decode(s, jsonParseOptions);
        } catch (final Exception e) {
            throw JsonFieldSelectorInvalidException.newBuilder() //
                    .fieldSelector(s) //
                    .description("Check if the field selector is correctly URL encoded.") //
                    .cause(e) //
                    .build();
        }
    }

    private static String decode(final String s, final JsonParseOptions jsonParseOptions)
            throws UnsupportedEncodingException {
        if (jsonParseOptions.isApplyUrlDecoding()) {
            return URLDecoder.decode(s, "UTF-8");
        } else {
            return s;
        }
    }

    /*
     * Splits the passed in String at the String's commas ({@code ,}) and returns a List of the single parts.
     * Thereby also handles Parentheses ({@code (} and {@code )}) in a way that they are treated as another "level" at
     * which no splitting at commas is performed.
     *
     * Example Strings and how they are split:
     * <pre>
     *    "thingId,attributes"                               List [ "thingId", "attributes" ]
     *    "thingId,attributes(someAttr)"                     List [ "thingId", "attributes" ]
     *    "thingId,attributes(someAttr,someOther,another)"   List [ "thingId", "attributes" ]
     *    "thingId,attributes(someAttr/subel,foo)"           List [ "thingId", "attributes" ]
     * </pre>
     *
     * @return a List of the single split parts.
     */
    private static List<String> splitAroundComma(final String toSplit) {
        final List<String> topLevelFields = new ArrayList<>();
        final StringBuilder sb = new StringBuilder(toSplit.length());

        int waitForClosingParenthesesCnt = 0;
        for (final Character c : toSplit.toCharArray()) {
            if (c == '(') {
                waitForClosingParenthesesCnt++;
                sb.append(c);
                continue;
            }
            if (waitForClosingParenthesesCnt > 0) {
                if (c == ')') {
                    waitForClosingParenthesesCnt--;
                }
                sb.append(c);
                continue;
            }

            if (c != ',') {
                sb.append(c);
            } else {
                topLevelFields.add(sb.toString());
                sb.setLength(0);
            }
        }
        topLevelFields.add(sb.toString());
        return topLevelFields;
    }

    private static Set<JsonPointer> flattenToJsonPointers(final Iterable<String> rawJsonKeys) {
        final Set<JsonPointer> result = new LinkedHashSet<>();

        for (final String rawJsonKey : rawJsonKeys) {
            if (isJsonSelectorFormat(rawJsonKey) && !containsPlaceholder(rawJsonKey)) {
                result.addAll(flattenToJsonPointers(rawJsonKey));
            } else {
                // slashes are already treated by the constructor of the JSON pointer
                result.add(JsonFactory.newPointer(rawJsonKey));
            }
        }

        return result;
    }

    private static boolean containsPlaceholder(final String jsonKey) {
        return PLACEHOLDER_PATTERN.matcher(jsonKey).find();
    }

    private static boolean isJsonSelectorFormat(final String jsonKey) {
        if (jsonKey.contains(OPENING_PARENTHESIS)) {
            final int iOpeningParenthesis = jsonKey.indexOf(OPENING_PARENTHESIS);
            final int iClosingParenthesis = jsonKey.indexOf(CLOSING_PARENTHESIS);

            return iOpeningParenthesis < iClosingParenthesis;
        }

        return false;
    }

    private static Collection<JsonPointer> flattenToJsonPointers(final String stringWithParentheses) {
        final int indexOfOpeningParenthesis = stringWithParentheses.indexOf(OPENING_PARENTHESIS);

        // the string before the first opening parenthesis
        final String commonRootKey = stringWithParentheses.substring(0, indexOfOpeningParenthesis);
        final JsonPointer commonRootPointer = JsonFactory.newPointer(commonRootKey);

        // the string within the opening and closing parenthesis
        // this string is (recursively) treated like a new field selector string (because technically it is)
        final String withoutParentheses =
                stringWithParentheses.substring(indexOfOpeningParenthesis + 1, stringWithParentheses.length() - 1);
        final Set<JsonPointer> jsonPointers = flattenToJsonPointers(splitAroundComma(withoutParentheses));

        return jsonPointers.stream() //
                .map(commonRootPointer::append) //
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateJsonFieldSelectorString() {
        if (jsonFieldSelectorString.isEmpty()) {
            throw JsonFieldSelectorInvalidException.newBuilder() //
                    .fieldSelector(jsonFieldSelectorString) //
                    .build();
        }
        if (jsonFieldSelectorString.contains(OPENING_PARENTHESIS)) {
            final int openedParenthesesCnt = getCountOf(OPENING_PARENTHESIS);
            final int closedParenthesesCnt = getCountOf(CLOSING_PARENTHESIS);
            if (openedParenthesesCnt != closedParenthesesCnt) {
                throw JsonFieldSelectorInvalidException.newBuilder() //
                        .fieldSelector(jsonFieldSelectorString) //
                        .build();
            }
        }
    }

    private int getCountOf(final CharSequence parenthesis) {
        return jsonFieldSelectorString.length() - jsonFieldSelectorString.replace(parenthesis, "").length();
    }

    /**
     * Returns a new JSON field selector based on the parsed string which was given to this class' constructor.
     *
     * @return a new JSON field selector instance.
     * @throws IllegalStateException if the JSON field selector string could not be decoded as UTF-8.
     */
    public JsonFieldSelector newJsonFieldSelector() {
        final List<String> rawJsonKeys = splitAroundComma(jsonFieldSelectorString);
        final Set<JsonPointer> jsonPointers = flattenToJsonPointers(rawJsonKeys);
        return ImmutableJsonFieldSelector.of(jsonPointers, jsonFieldSelectorString);
    }
}
