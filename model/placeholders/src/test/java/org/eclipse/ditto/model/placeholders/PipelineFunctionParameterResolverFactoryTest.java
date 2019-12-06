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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Predicate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineFunctionParameterResolverFactoryTest {

    private static final String KNOWN_VALUE = "expected";
    private static final String KNOWN_PLACEHOLDER = "thing:name";
    private static final PipelineFunction DUMMY = new PipelineFunctionDelete();

    @Mock
    private ExpressionResolver expressionResolver;

    @Test
    public void singleStringResolverAcceptsDoubleQuotes() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String params = "(\"" + KNOWN_VALUE + "\")";
        assertThat(parameterResolver.apply(params, expressionResolver, DUMMY)).contains(KNOWN_VALUE);

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleStringResolverAcceptsSingleQuotes() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String params = "(\'" + KNOWN_VALUE + "\')";
        assertThat(parameterResolver.apply(params, expressionResolver, DUMMY)).contains(KNOWN_VALUE);

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleResolverThrowsExceptionOnMultipleParameters() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String paramsDoubleQuoted = "(\"" + KNOWN_VALUE + "\", \"otherValue\")";
        final String paramsSingleQuoted = "(\'" + KNOWN_VALUE + "\', \'otherValue\')";
        final String paramsPlaceholders = "(" + KNOWN_PLACEHOLDER + ", topic:full)";

        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> parameterResolver.apply(paramsDoubleQuoted, expressionResolver, DUMMY));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> parameterResolver.apply(paramsSingleQuoted, expressionResolver, DUMMY));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> parameterResolver.apply(paramsPlaceholders, expressionResolver, DUMMY));

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleResolverResolvesPlaceholder() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String params = "(" + KNOWN_PLACEHOLDER + ")";
        when(expressionResolver.resolveAsPipelineElement(anyString()))
                .thenReturn(PipelineElement.resolved(KNOWN_VALUE));

        assertThat(parameterResolver.apply(params, expressionResolver, DUMMY)).contains(KNOWN_VALUE);

        verify(expressionResolver).resolveAsPipelineElement(KNOWN_PLACEHOLDER);
    }

    @Test
    public void singleResolverAllowsWhitespaceInStrings() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String value = "   " + KNOWN_VALUE + "      ";
        final String stringSingle = "(\'" + value + "\')";
        final String stringDouble = "(\"" + value + "\")";

        assertThat(parameterResolver.apply(stringSingle, expressionResolver, DUMMY)).contains(value);
        assertThat(parameterResolver.apply(stringDouble, expressionResolver, DUMMY)).contains(value);
    }

    @Test
    public void singleResolverAllowsWhitespaceBetweenParentheses() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String stringSingle = "(   \'" + KNOWN_VALUE + "\'   )";
        final String stringDouble = "(   \"" + KNOWN_VALUE + "\"   )";
        final String stringPlaceholder = "(    " + KNOWN_PLACEHOLDER + "   )";

        when(expressionResolver.resolveAsPipelineElement(anyString())).thenReturn(
                PipelineElement.resolved(KNOWN_VALUE));

        assertThat(parameterResolver.apply(stringSingle, expressionResolver, DUMMY)).contains(KNOWN_VALUE);
        assertThat(parameterResolver.apply(stringDouble, expressionResolver, DUMMY)).contains(KNOWN_VALUE);
        assertThat(parameterResolver.apply(stringPlaceholder, expressionResolver, DUMMY)).contains(KNOWN_VALUE);

        verify(expressionResolver).resolveAsPipelineElement(KNOWN_PLACEHOLDER);
    }

    @Test
    public void singlePlaceholderResolverReturnsPlaceholderIfNotResolvable() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String stringPlaceholder = "(    " + KNOWN_PLACEHOLDER + "   )";

        when(expressionResolver.resolveAsPipelineElement(anyString())).thenReturn(PipelineElement.unresolved());

        assertThat(parameterResolver.apply(stringPlaceholder, expressionResolver, DUMMY)).isEmpty();

        verify(expressionResolver).resolveAsPipelineElement(KNOWN_PLACEHOLDER);
    }

    @Test
    public void singleStringResolverThrowsExceptionIfNotResolvable() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver stringResolver =
                PipelineFunctionParameterResolverFactory.forStringParameter();
        final String stringPlaceholder = "(    " + KNOWN_PLACEHOLDER + "   )";

        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> stringResolver.apply(stringPlaceholder, expressionResolver, DUMMY));

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleStringResolverDoesNotResolvePlaceholders() {

        final PipelineFunctionParameterResolverFactory.SingleParameterResolver stringResolver =
                PipelineFunctionParameterResolverFactory.forStringParameter();
        final String stringSingle = "(\'" + KNOWN_VALUE + "\')";
        final String stringDouble = "(\"" + KNOWN_VALUE + "\")";
        final String stringPlaceholder = "(" + KNOWN_PLACEHOLDER + ")";

        assertThat(stringResolver.apply(stringSingle, expressionResolver, DUMMY)).contains(KNOWN_VALUE);
        assertThat(stringResolver.apply(stringDouble, expressionResolver, DUMMY)).contains(KNOWN_VALUE);
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> stringResolver.apply(stringPlaceholder, expressionResolver, DUMMY));

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void emptyParameterResolverResolvesEmptyParentheses() {
        final Predicate<String> noParamResolver = PipelineFunctionParameterResolverFactory.forEmptyParameters();

        assertThat(noParamResolver.test("()")).isTrue();
        assertThat(noParamResolver.test("(  )")).isTrue();
    }

    @Test
    public void emptyParameterResolverFailsForNonEmptyParentheses() {
        final Predicate<String> noParamResolver = PipelineFunctionParameterResolverFactory.forEmptyParameters();

        assertThat(noParamResolver.test("(thing:id)")).isFalse();
        assertThat(noParamResolver.test("(\"val\")")).isFalse();
        assertThat(noParamResolver.test("(\'val\')")).isFalse();
    }

}
