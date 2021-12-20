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
package org.eclipse.ditto.jwt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;

/**
 * Java representation of the audience field specified by:
 * <a href="https://tools.ietf.org/html/rfc7519#section-4.1.3">rfc7519</a>.
 */
public final class Audience {

    private final List<String> principles;

    private Audience(final List<String> principles) {
        this.principles = Collections.unmodifiableList(new ArrayList<>(principles));
    }

    /**
     * Builds an audience based on the given json value.
     *
     * @param audValue the value of the "aud" field of a JWT.
     * @return the built audience.
     * @throws JwtAudienceInvalidException if the aud field is neither an array nor a single string.
     */
    static Audience fromJson(final JsonValue audValue) {
        final JsonArray principles;

        if (audValue.isArray()) {
            principles = audValue.asArray();
        } else if (audValue.isString()) {
            principles = JsonArray.of(audValue);
        } else {
            throw JwtAudienceInvalidException
                    .newBuilder(audValue)
                    .build();
        }

        final List<String> stringPrinciples = principles.stream()
                .map(JsonValue::asString)
                .distinct()
                .collect(Collectors.toList());

        return new Audience(stringPrinciples);
    }

    /**
     * builds and Audience without any principles.
     *
     * @return the empty audience.
     */
    public static Audience empty() {
        return new Audience(Collections.emptyList());
    }

    /**
     * Returns the single principle if this audience does contain only a single principle.
     *
     * @return Optional of a principle. Optional is empty if either the principles are empty or the principles
     * contain more than one principle.
     */
    public Optional<String> getSinglePrinciple() {
        if (principles.size() != 1) {
            return Optional.empty();
        } else {
            return Optional.of(principles.get(0));
        }
    }

    /**
     * Returns the principles of this audience.
     *
     * @return the principles of this audience.
     */
    public List<String> getPrinciples() {
        return principles;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Audience audience = (Audience) o;
        return Objects.equals(principles, audience.principles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principles);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "principles=" + principles +
                "]";
    }

}
