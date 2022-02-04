/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.placeholders.Placeholder;

/**
 * The placeholder that replaces {@code jwt:<body-claim>}.
 */
public final class JwtPlaceholder implements Placeholder<JsonWebToken> {

    /**
     * Compiled Pattern of a string containing any unresolved non-empty JsonArray-String notations inside.
     * All strings matching this pattern are valid JSON arrays. Not all JSON arrays match this pattern.
     */
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("(\\[\"(?:\\\\\"|[^\"])*+\"(?:,\"(?:\\\\\"|[^\"])*+\")*+])");

    private static final JwtPlaceholder INSTANCE = new JwtPlaceholder();

    /**
     * JWT Placeholder prefix.
     */
    public static final String PREFIX = "jwt";

    /**
     * Get the instance of {@code JwtPlaceholder}.
     *
     * @return the instance.
     */
    public static JwtPlaceholder getInstance() {
        return INSTANCE;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return List.of();
    }

    @Override
    public boolean supports(final String name) {
        return true;
    }

    @Override
    public Optional<String> resolve(final JsonWebToken jwt, final String placeholder) {
        argumentNotEmpty(placeholder, "placeholder");
        checkNotNull(jwt, "jwt");
        return jwt.getBody().getValue(placeholder).map(JsonValue::formatAsString);
    }

    /**
     * Checks whether the passed {@code resolvedSubject} (resolved via JWT and header placeholder mechanism) contains
     * JsonArrays ({@code ["..."]} and expands those JsonArrays to multiple resolved subjects returned as resulting
     * stream of this operation.
     * <p>
     * Is able to handle an arbitrary amount of JsonArrays in the passed resolvedSubjects.
     *
     * @param resolvedSubject the resolved subjects potentially containing JsonArrays as JsonArray-String values.
     * @return a stream of a single subject when the passed in {@code resolvedSubject} did not contain any
     * JsonArray-String notation or else a stream of multiple subjects with the JsonArrays being resolved to multiple
     * results of the stream.
     */
    public static Stream<String> expandJsonArraysInResolvedSubject(final String resolvedSubject) {
        final Matcher jsonArrayMatcher = JSON_ARRAY_PATTERN.matcher(resolvedSubject);
        final int group = 1;
        if (jsonArrayMatcher.find()) {
            final String beforeMatched = resolvedSubject.substring(0, jsonArrayMatcher.start(group));
            final String matchedStr =
                    resolvedSubject.substring(jsonArrayMatcher.start(group), jsonArrayMatcher.end(group));
            final String afterMatched = resolvedSubject.substring(jsonArrayMatcher.end(group));
            return JsonArray.of(matchedStr).stream()
                    .filter(JsonValue::isString)
                    .map(JsonValue::asString)
                    .flatMap(arrayStringElem -> expandJsonArraysInResolvedSubject(beforeMatched) // recurse!
                            .flatMap(before -> expandJsonArraysInResolvedSubject(afterMatched) // recurse!
                                    .map(after -> before.concat(arrayStringElem).concat(after))
                            )
                    );
        }
        return Stream.of(resolvedSubject);
    }
}
