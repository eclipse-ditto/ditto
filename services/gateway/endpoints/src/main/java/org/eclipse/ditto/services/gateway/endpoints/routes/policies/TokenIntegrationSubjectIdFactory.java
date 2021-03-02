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
package org.eclipse.ditto.services.gateway.endpoints.routes.policies;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.model.policies.SubjectId;

/**
 * Creator of token integration subject IDs.
 */
public interface TokenIntegrationSubjectIdFactory {

    /**
     * Compiled Pattern of a string containing any unresolved non-empty JsonArray-String notations inside.
     * All strings matching this pattern are valid JSON arrays. Not all JSON arrays match this pattern.
     */
    Pattern JSON_ARRAY_PATTERN = Pattern.compile("(\\[\"(?:\\\\\"|[^\"])*+\"(?:,\"(?:\\\\\"|[^\"])*+\")*+])");

    /**
     * Compute the token integration subject IDs from headers and JWT.
     *
     * @param dittoHeaders the Ditto headers.
     * @param jwt the JWT.
     * @return the computed subject IDs.
     * @throws org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException if mandatory placeholders could not
     * be resolved within the configured {@code subjectTemplate} of this TokenIntegrationSubjectIdFactory.
     */
    Set<SubjectId> getSubjectIds(DittoHeaders dittoHeaders, JsonWebToken jwt);

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
    static Stream<String> expandJsonArraysInResolvedSubject(final String resolvedSubject) {
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
