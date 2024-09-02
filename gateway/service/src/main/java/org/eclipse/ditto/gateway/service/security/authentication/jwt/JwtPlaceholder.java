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
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.placeholders.Placeholder;

/**
 * The placeholder that replaces {@code jwt:<body-claim>}.
 */
public final class JwtPlaceholder implements Placeholder<JsonWebToken> {

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
    public List<String> resolveValues(final JsonWebToken jwt, final String placeholder) {
        argumentNotEmpty(placeholder, "placeholder");
        checkNotNull(jwt, "jwt");
        return resolveValues(jwt.getBody(), JsonPointer.of(placeholder))
                .distinct()
                .toList();
    }

    private static Stream<String> resolveValues(final JsonValue jsonValue, final JsonPointer jsonPointer) {
        return jsonPointer.getRoot()
                .<Stream<String>>map(root -> jsonValue.isArray()
                        ? jsonValue.asArray().stream().flatMap(item -> resolveValues(item, jsonPointer))
                        : (jsonValue.isObject()
                                ? jsonValue.asObject().getValue(root)
                                        .map(v -> resolveValues(v, jsonPointer.nextLevel()))
                                        .orElseGet(Stream::empty)
                                : Stream.empty()))
                .orElseGet(() -> jsonValue.isArray()
                    ? jsonValue.asArray().stream().map(JsonValue::formatAsString)
                    : Stream.of(jsonValue.formatAsString()));
    }

}
