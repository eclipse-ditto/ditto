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
package org.eclipse.ditto.model.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.things.id.ThingId;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ExpressionResolverTest {

    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
    private static final ThingPlaceholder THING_PLACEHOLDER = PlaceholderFactory.newThingPlaceholder();
    private static final TopicPathPlaceholder TOPIC_PLACEHOLDER = PlaceholderFactory.newTopicPathPlaceholder();

    private static final String THING_NS = "the.thing";
    private static final String THING_NAME = "the.id:the-rest";
    private static final ThingId THING_ID = ThingId.of(THING_NS, THING_NAME);

    private ExpressionResolver expressionResolver;

    @Before
    public void setupExpressionResolver() {
        final Map<String, String> headersMap = new HashMap<>();
        headersMap.put("header-name", "header-val");
        expressionResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headersMap),
                PlaceholderFactory.newPlaceholderResolver(THING_PLACEHOLDER, THING_ID),
                PlaceholderFactory.newPlaceholderResolver(TOPIC_PLACEHOLDER, null)
        );
    }

    @Test
    public void testPlaceholderFunctionDefaultWithConstant() {

        assertThat(
                expressionResolver.resolve("{{ header:nonexisting | fn:default('fallback-val') }}",
                true)
        ).isEqualTo("fallback-val");
    }

    @Test
    public void testPlaceholderFunctionDefaultWithPlaceholder() {

        assertThat(
                expressionResolver.resolve("{{ header:nonexisting | fn:default(header:header-name) }}",
                        true)
        ).isEqualTo("header-val");
    }

    @Test
    public void testPlaceholderFunctionDefaultWithPlaceholderNonExistingDefault() {

        assertThat(
                expressionResolver.resolve("{{ header:nonexisting | fn:default(header:alsoNotThere) }}",
                        true)
        ).isEqualTo("header:alsoNotThere");
    }

    @Test
    public void testPlaceholderFunctionSubstringBefore() {

        assertThat(
                expressionResolver.resolve("{{ thing:namespace }}:{{thing:name | fn:substring-before(':') }}",
                        true)
        ).isEqualTo(THING_NS + ":" + "the.id");
    }

    @Test
    public void testPlaceholderFunctionSubstringBeforeWithDefaultFallback() {

        assertThat(expressionResolver.resolve(
                "{{ thing:namespace }}:{{thing:name | fn:substring-before('_') | fn:default(thing:name)}}", true)
        ).isEqualTo(THING_ID.toString());
    }

    @Test
    public void testPlaceholderFunctionSubstringAfterWithUpper() {

        assertThat(
                expressionResolver.resolve("{{ thing:name | fn:substring-after(':') | fn:upper() }}",
                        true)
        ).isEqualTo("the-rest".toUpperCase());
    }

}
