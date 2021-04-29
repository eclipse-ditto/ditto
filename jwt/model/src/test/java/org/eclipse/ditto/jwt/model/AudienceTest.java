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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Unit tests for {@link Audience}.
 */
public class AudienceTest {

    @Test
    public void fromJsonWithASingleString() {
        final String principleName = "myAudience";
        final JsonValue myAudience = JsonValue.of(principleName);
        final Audience audience = Audience.fromJson(myAudience);
        assertThat(audience.getSinglePrinciple()).contains(principleName);
    }

    @Test
    public void fromJsonWithAJsonArray() {
        final List<String> principles = Arrays.asList("myAudience1", "myAudience2");
        final JsonValue jsonPrinciples = JsonArray.of(principles);
        final Audience audience = Audience.fromJson(jsonPrinciples);
        assertThat(audience.getPrinciples()).containsAll(principles);
    }

    @Test
    public void empty() {
        final Audience emptyAudience = Audience.empty();
        assertThat(emptyAudience.getPrinciples()).isEmpty();
    }

    @Test
    public void getSinglePrincipleContainsTheSinglePrinciple() {
        final String principleName = "myAudience";
        final JsonValue myAudience = JsonValue.of(principleName);
        final Audience audience = Audience.fromJson(myAudience);
        assertThat(audience.getSinglePrinciple()).contains(principleName);
    }

    @Test
    public void getSinglePrincipleIsNotPresentForEmptyAudience() {
        final Audience audience = Audience.empty();
        assertThat(audience.getSinglePrinciple()).isNotPresent();
    }

    @Test
    public void getSinglePrincipleIsNotPresentForMultiplePrincipleAudience() {
        final List<String> principles = Arrays.asList("myAudience1", "myAudience2");
        final JsonValue jsonPrinciples = JsonArray.of(principles);
        final Audience audience = Audience.fromJson(jsonPrinciples);
        assertThat(audience.getSinglePrinciple()).isNotPresent();
    }

    @Test
    public void getPrinciplesContainsAllPrinciples() {
        final List<String> principles = Arrays.asList("myAudience1", "myAudience2");
        final JsonValue jsonPrinciples = JsonArray.of(principles);
        final Audience audience = Audience.fromJson(jsonPrinciples);
        assertThat(audience.getPrinciples()).containsAll(principles);
    }

    @Test
    public void fromJsonThrowsJwtAudienceInvalidException() {
        assertThatExceptionOfType(JwtAudienceInvalidException.class)
                .isThrownBy(() -> Audience.fromJson(JsonValue.of(4711)));
    }
}
