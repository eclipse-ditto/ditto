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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableFunctionExpression}.
 */
public class ImmutableFunctionExpressionTest {

    private static final Set<String> EXPECTED_FUNCTION_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "filter",
            "default",
            "substring-before",
            "substring-after",
            "lower",
            "upper",
            "delete",
            "replace",
            "split"
    )));
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private static final String HEADER_KEY = "foo1";
    private static final String HEADER_VAL = "caMelCasedStuffFOOO";
    private static final Map<String, String> HEADERS = Collections.singletonMap(HEADER_KEY, HEADER_VAL);

    private static final ExpressionResolver EXPRESSION_RESOLVER = PlaceholderFactory.newExpressionResolver(
            PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, HEADERS)
    );

    private static final ImmutableFunctionExpression UNDER_TEST = ImmutableFunctionExpression.INSTANCE;

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableFunctionExpression.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFunctionExpression.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSupportedNames() {
        assertThat(UNDER_TEST.getSupportedNames()).containsExactlyInAnyOrder(
                EXPECTED_FUNCTION_NAMES.toArray(new String[0]));
    }

    @Test
    public void testCompletenessOfRegisteredFunctions() {
        EXPECTED_FUNCTION_NAMES.stream()
                .map(name -> name + "(")
                .forEach(fn -> assertThat(UNDER_TEST.supports(fn)).isTrue());
    }

    @Test
    public void testUnknownFunction() {
        assertThatExceptionOfType(PlaceholderFunctionUnknownException.class).isThrownBy(() ->
                UNDER_TEST.resolve("fn:unknown", PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER));
    }

    @Test
    public void testFunctionUpper() {
        assertThat(UNDER_TEST.resolve("fn:upper()", PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER))
                .contains(HEADER_VAL.toUpperCase());
    }

    @Test
    public void testFunctionUpperWrongSignature() {
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                UNDER_TEST.resolve("fn:upper('foo')", PipelineElement.resolved(HEADER_VAL),
                        EXPRESSION_RESOLVER));
    }

    @Test
    public void testFunctionLower() {
        assertThat(UNDER_TEST.resolve("fn:lower()", PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER))
                .contains(HEADER_VAL.toLowerCase());
    }

    @Test
    public void testFunctionLowerWrongSignature() {
        assertThatExceptionOfType(PlaceholderFunctionUnknownException.class).isThrownBy(() ->
                UNDER_TEST.resolve("fn:lower", PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER));
    }

    @Test
    public void testFunctionDefaultWhenInputPresent() {
        assertThat(
                UNDER_TEST.resolve("fn:default('constant')", PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER))
                .contains(HEADER_VAL);
    }

    @Test
    public void testFunctionDefaultWhenInputEmptyWithConstant() {
        assertThat(UNDER_TEST.resolve("fn:default('constant')", PipelineElement.unresolved(), EXPRESSION_RESOLVER))
                .contains("constant");
    }

    @Test
    public void testFunctionDefaultWhenInputEmptyWithConstantDoubleQuotes() {
        assertThat(UNDER_TEST.resolve("fn:default(\"constant\")", PipelineElement.unresolved(), EXPRESSION_RESOLVER))
                .contains("constant");
    }

    @Test
    public void testFunctionDefaultWhenInputEmptyWithPlaceholder() {
        assertThat(UNDER_TEST.resolve("fn:default(header:foo1)", PipelineElement.unresolved(), EXPRESSION_RESOLVER))
                .contains(HEADER_VAL);
    }

    @Test
    public void testFunctionDefaultWithWrongSignature() {
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                UNDER_TEST.resolve("fn:default('constant',2)", PipelineElement.unresolved(), EXPRESSION_RESOLVER));
    }

    @Test
    public void testFunctionSubstringBefore() {
        assertThat(UNDER_TEST.resolve("fn:substring-before(\"s\")", PipelineElement.resolved(HEADER_VAL),
                EXPRESSION_RESOLVER)).contains("caMelCa");
    }

    @Test
    public void testFunctionSubstringAfter() {
        assertThat(UNDER_TEST.resolve("fn:substring-after(\"s\")", PipelineElement.resolved(HEADER_VAL),
                EXPRESSION_RESOLVER))
                .contains("edStuffFOOO");
    }

    @Test
    public void testFunctionFilterWhenConditionSucceeds() {
        assertThat(UNDER_TEST.resolve("fn:filter('true','eq','true')",
                PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER))
                .contains(HEADER_VAL);
    }

    @Test
    public void testFunctionFilterWhenConditionFails() {
        assertThat(UNDER_TEST.resolve("fn:filter('false','eq','true')",
                PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER))
                .isEmpty();
    }

    @Test
    public void testFunctionFilterWhenConditionSucceedsWithPlaceholder() {
        assertThat(UNDER_TEST.resolve(String.format("fn:filter(header:foo1,'eq','%s')", HEADER_VAL),
                PipelineElement.resolved(HEADER_VAL), EXPRESSION_RESOLVER))
                .contains(HEADER_VAL);
    }

}
