/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineFunctionTrimTest {

    private static final PipelineElement KNOWN_INPUT_PREFIX = PipelineElement.resolved(" foo");
    private static final PipelineElement KNOWN_INPUT_SUFFIX = PipelineElement.resolved("foo   ");
    private static final PipelineElement KNOWN_INPUT_BOTH = PipelineElement.resolved("   foo ");
    private static final String TRIMMED = "foo";

    private final PipelineFunctionTrim function = new PipelineFunctionTrim();

    @Mock
    private ExpressionResolver expressionResolver;

    @After
    public void verifyExpressionResolverUnused() {
        Mockito.verifyNoInteractions(expressionResolver);
    }

    @Test
    public void getName() {
        assertThat(function.getName()).isEqualTo("trim");
    }

    @Test
    public void apply() {
        assertThat(function.apply(KNOWN_INPUT_PREFIX, "()", expressionResolver)).contains(TRIMMED);
        assertThat(function.apply(KNOWN_INPUT_SUFFIX, "()", expressionResolver)).contains(TRIMMED);
        assertThat(function.apply(KNOWN_INPUT_BOTH, "()", expressionResolver)).contains(TRIMMED);
    }

    @Test
    public void throwsOnNonZeroParameters() {
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT_PREFIX, "", expressionResolver));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT_PREFIX, "(\"string\")", expressionResolver));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT_PREFIX, "(\'string\')", expressionResolver));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT_PREFIX, "(thing:id)", expressionResolver));
    }

}
