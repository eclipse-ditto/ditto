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
package org.eclipse.ditto.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ExpressionResolverTest {

    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private ExpressionResolver expressionResolver;

    @Before
    public void setupExpressionResolver() {
        final Map<String, String> headersMap = new HashMap<>();
        headersMap.put("header-name", "header-val");
        headersMap.put("header:with:colon", "value:with:colon");
        expressionResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, headersMap)
        );
    }

    @Test
    public void testPlaceholderFunctionDefaultWithConstant() {

        assertThat(expressionResolver.resolve("{{ header:nonexistent | fn:default('fallback-val') }}"))
                .contains("fallback-val");
    }

    @Test
    public void testHeaderWithColon() {
        assertThat(expressionResolver.resolve("{{ header:header:with:colon }}"))
                .contains("value:with:colon");
    }

    @Test
    public void testPlaceholderFunctionDefaultWithPlaceholder() {
        assertThat(expressionResolver.resolve("{{ header:nonexistent | fn:default(header:header-name) }}"))
                .contains("header-val");
    }

    @Test
    public void testPlaceholderFunctionDefaultWithPlaceholderNonExistingDefault() {
        assertThat(expressionResolver.resolve("{{ header:nonexistent | fn:default(header:alsoNotThere) }}"))
                .isEmpty();
    }

    @Test
    public void testPlaceholderFunctionSubstringBefore() {
        assertThat(expressionResolver.resolve(
                "{{ header:header:with:colon }}:{{header:header:with:colon| fn:substring-before(':') }}"))
                .contains("value:with:colon:value");
    }

    @Test
    public void testPlaceholderFunctionSubstringBeforeWithDefaultFallback() {
        assertThat(expressionResolver.resolve(
                "{{ header:header-name }}:{{header:header-name | fn:substring-before('_') | fn:default(header:header-name)}}"))
                .contains("header-val:header-val");
    }

    @Test
    public void testPlaceholderFunctionSubstringAfterWithUpper() {
        assertThat(expressionResolver.resolve("{{ header:header:with:colon | fn:substring-after(':') | fn:upper() }}"))
                .contains("WITH:COLON");
    }

    @Test
    public void testLoneDelete() {
        assertThat(expressionResolver.resolve("{{ fn:delete() }}"))
                .isEqualTo(PipelineElement.deleted());
        assertThat(expressionResolver.resolve("{{ fn:delete() }}{{ fn:delete() }}"))
                .isEqualTo(PipelineElement.deleted());
        assertThat(expressionResolver.resolve("{{ fn:delete() }}{{ fn:delete() }}{{ fn:delete() }}"))
                .isEqualTo(PipelineElement.deleted());
        assertThat(expressionResolver.resolve("{{ fn:default(fn:delete()) }}"))
                .isEqualTo(PipelineElement.deleted());
    }

    @Test
    public void testLoneDefault() {
        assertThat(expressionResolver.resolve("{{ fn:default(header:header-name) }}"))
                .contains("header-val");
    }
    @Test
    public void testEmptyTemplate() {
        assertThat(expressionResolver.resolve("")).isEqualTo(PipelineElement.resolved(""));
        assertThat(expressionResolver.resolve(" ")).isEqualTo(PipelineElement.resolved(" "));
    }

    @Test
    public void testPipelineStartingWithDefault() {
        assertThat(expressionResolver.resolve("{{ fn:default(header:header-name) | fn:upper() }}"))
                .contains("HEADER-VAL");
    }

    @Test
    public void testDeleteIfUnresolved() {
        assertThat(expressionResolver.resolve("{{ header:nonexistent }}"))
                .isEqualTo(PipelineElement.unresolved());

        assertThat(expressionResolver.resolve("{{ header:nonexistent | fn:default(fn:delete()) }}"))
                .isEqualTo(PipelineElement.deleted());

        assertThat(expressionResolver.resolve("{{ header:header-name | fn:delete() }}"))
                .isEqualTo(PipelineElement.deleted());
    }

    @Test
    public void testDeleteIfResolved() {
        assertThat(expressionResolver.resolve("/{{ fn:delete() }}/{{ header:header-name }}/{{ fn:delete() }}/"))
                .isEqualTo(PipelineElement.resolved("//header-val//"));
        assertThat(expressionResolver.resolve("/{{ header:header-name | fn:delete() }}/{{ header:header-name }}/"))
                .isEqualTo(PipelineElement.resolved("//header-val/"));
        assertThat(expressionResolver.resolve("/{{ header:header-name }}/{{ header:header-name | fn:delete() }}/"))
                .isEqualTo(PipelineElement.resolved("/header-val//"));
        assertThat(expressionResolver.resolve("/{{ fn:delete() | fn:default(header:header-name) }}/{{ header:header-name | fn:delete() }}/"))
                .isEqualTo(PipelineElement.resolved("///"));
    }

    @Test
    public void testPartialResolution() {
        assertThat(expressionResolver.resolvePartiallyAsPipelineElement("{{header:header-name}}-{{unknown:placeholder|fn:unknown}}",
                Collections.singleton("header")))
                .containsOnly("header-val-{{unknown:placeholder|fn:unknown}}");
    }

    @Test
    public void testPartialResolutionWithForbiddenUnresolvedExpression() {
        final Set<String> forbiddenUnresolvedExpressionPrefixes = Collections.singleton("unknown");
        assertThatThrownBy(() ->
                expressionResolver.resolvePartiallyAsPipelineElement(
                        "{{header:header-name}}-{{unknown:placeholder|fn:unknown}}",
                        forbiddenUnresolvedExpressionPrefixes
                )
        ).isInstanceOf(UnresolvedPlaceholderException.class)
                .withFailMessage("The placeholder 'unknown:placeholder' could not be resolved.")
                .hasNoCause();
    }

}
