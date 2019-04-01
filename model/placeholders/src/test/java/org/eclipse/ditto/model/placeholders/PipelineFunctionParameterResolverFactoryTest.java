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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineFunctionParameterResolverFactoryTest {

    private static final String KNOWN_VALUE = "expected";
    private static final String KNOWN_PLACEHOLDER = "thing:name";

    @Mock
    private ExpressionResolver expressionResolver;

    @Test
    public void singleStringResolverAcceptsDoubleQuotes() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String params = "(\"" + KNOWN_VALUE + "\")";
        assertThat(parameterResolver.apply(params, expressionResolver)).contains(KNOWN_VALUE);

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleStringResolverAcceptsSingleQuotes() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String params = "(\'" + KNOWN_VALUE + "\')";
        assertThat(parameterResolver.apply(params, expressionResolver)).contains(KNOWN_VALUE);

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleResolverUsesFirstOfMultipleParameters() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String paramsDoubleQuoted = "(\"" + KNOWN_VALUE + "\", \"otherValue\")";
        final String paramsSingleQuoted = "(\'" + KNOWN_VALUE + "\', \'otherValue\')";
        final String paramsPlaceholders = "(" + KNOWN_PLACEHOLDER + ", topic:full)";

        assertThat(parameterResolver.apply(paramsDoubleQuoted, expressionResolver)).isEmpty();
        assertThat(parameterResolver.apply(paramsSingleQuoted, expressionResolver)).isEmpty();
        assertThat(parameterResolver.apply(paramsPlaceholders, expressionResolver)).isEmpty();

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleResolverResolvesPlaceholder() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String params = "(" + KNOWN_PLACEHOLDER + ")";
        when(expressionResolver.resolveSinglePlaceholder(anyString())).thenReturn(Optional.of(KNOWN_VALUE));

        assertThat(parameterResolver.apply(params, expressionResolver)).contains(KNOWN_VALUE);

        verify(expressionResolver).resolveSinglePlaceholder(KNOWN_PLACEHOLDER);
    }

    @Test
    public void singleResolverAllowsWhitespaceInStrings() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String value = "   " + KNOWN_VALUE + "      ";
        final String stringSingle = "(\'" + value + "\')";
        final String stringDouble = "(\"" + value + "\")";

        assertThat(parameterResolver.apply(stringSingle, expressionResolver)).contains(value);
        assertThat(parameterResolver.apply(stringDouble, expressionResolver)).contains(value);
    }

    @Test
    public void singleResolverAllowsWhitespaceBetweenParentheses() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String stringSingle = "(   \'" + KNOWN_VALUE + "\'   )";
        final String stringDouble = "(   \"" + KNOWN_VALUE + "\"   )";
        final String stringPlaceholder = "(    " + KNOWN_PLACEHOLDER + "   )";

        when(expressionResolver.resolveSinglePlaceholder(anyString())).thenReturn(Optional.of(KNOWN_VALUE));

        assertThat(parameterResolver.apply(stringSingle, expressionResolver)).contains(KNOWN_VALUE);
        assertThat(parameterResolver.apply(stringDouble, expressionResolver)).contains(KNOWN_VALUE);
        assertThat(parameterResolver.apply(stringPlaceholder, expressionResolver)).contains(KNOWN_VALUE);

        verify(expressionResolver).resolveSinglePlaceholder(KNOWN_PLACEHOLDER);
    }

    @Test
    public void singlePlaceholderResolverReturnsPlaceholderIfNotResolvable() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String stringPlaceholder = "(    " + KNOWN_PLACEHOLDER + "   )";

        when(expressionResolver.resolveSinglePlaceholder(anyString())).thenReturn(Optional.empty());

        assertThat(parameterResolver.apply(stringPlaceholder, expressionResolver)).contains(KNOWN_PLACEHOLDER);

        verify(expressionResolver).resolveSinglePlaceholder(KNOWN_PLACEHOLDER);
    }

    @Test
    public void singleStringResolverReturnsEmptyIfNotResolvable() {
        final BiFunction<String, ExpressionResolver, Optional<String>> stringResolver =
                PipelineFunctionParameterResolverFactory.forStringParameter();
        final String stringPlaceholder = "(    " + KNOWN_PLACEHOLDER + "   )";

        assertThat(stringResolver.apply(stringPlaceholder, expressionResolver)).isEmpty();

        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void singleStringResolverDoesNotResolvePlaceholders() {

        final BiFunction<String, ExpressionResolver, Optional<String>> stringResolver =
                PipelineFunctionParameterResolverFactory.forStringParameter();
        final String stringSingle = "(\'" + KNOWN_VALUE + "\')";
        final String stringDouble = "(\"" + KNOWN_VALUE + "\")";
        final String stringPlaceholder = "(" + KNOWN_PLACEHOLDER + ")";

        assertThat(stringResolver.apply(stringSingle, expressionResolver)).contains(KNOWN_VALUE);
        assertThat(stringResolver.apply(stringDouble, expressionResolver)).contains(KNOWN_VALUE);
        assertThat(stringResolver.apply(stringPlaceholder, expressionResolver)).isEmpty();

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
