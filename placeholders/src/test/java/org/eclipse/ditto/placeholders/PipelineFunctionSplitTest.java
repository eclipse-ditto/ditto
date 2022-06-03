/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineFunctionSplitTest {

    private static final PipelineElement EMPTY_INPUT = PipelineElement.unresolved();

    private static final PipelineElement KNOWN_INPUT = PipelineElement.resolved("foo bar baz");
    private static final List<String> EXPECTED_OUTPUT = Arrays.asList("foo", "bar", "baz");

    private static final PipelineElement KNOWN_INPUT_NO_MATCH = PipelineElement.resolved("unsplittable");
    private static final String EXPECTED_OUTPUT_NO_MATCH = "unsplittable";

    private final PipelineFunctionSplit function = new PipelineFunctionSplit();

    @Mock
    private ExpressionResolver expressionResolver;

    @After
    public void verifyExpressionResolverUnused() {
        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void getName() {
        assertThat(function.getName()).isEqualTo("split");
    }

    @Test
    public void apply() {
        assertThat(function.apply(KNOWN_INPUT, "(' ')", expressionResolver).toStream())
                .containsAll(EXPECTED_OUTPUT);
    }

    @Test
    public void applyNoMatch() {
        assertThat(function.apply(KNOWN_INPUT_NO_MATCH, "(' ')", expressionResolver).toStream())
                .containsOnly(EXPECTED_OUTPUT_NO_MATCH);
    }

    @Test
    public void returnsEmptyForEmptyInput() {
        assertThat(function.apply(EMPTY_INPUT, "(' ')", expressionResolver)).isEmpty();
    }

    @Test
    public void throwsOnInvalidParameters() {
        // has not enough parameters
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                function.apply(KNOWN_INPUT, "()", expressionResolver)
        );
        // has too many parameters
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                function.apply(KNOWN_INPUT, "(' ',',')", expressionResolver)
        );
        // has no parameters
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                function.apply(KNOWN_INPUT, "", expressionResolver)
        );
    }

}
