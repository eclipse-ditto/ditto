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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

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

        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void singleStringResolverAcceptsSingleQuotes() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String params = "('" + KNOWN_VALUE + "')";
        assertThat(parameterResolver.apply(params, expressionResolver, DUMMY)).contains(KNOWN_VALUE);

        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void singleResolverThrowsExceptionOnMultipleParameters() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String paramsDoubleQuoted = "(\"" + KNOWN_VALUE + "\", \"otherValue\")";
        final String paramsSingleQuoted = "('" + KNOWN_VALUE + "', 'otherValue')";
        final String paramsPlaceholders = "(" + KNOWN_PLACEHOLDER + ", topic:full)";

        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> parameterResolver.apply(paramsDoubleQuoted, expressionResolver, DUMMY));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> parameterResolver.apply(paramsSingleQuoted, expressionResolver, DUMMY));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> parameterResolver.apply(paramsPlaceholders, expressionResolver, DUMMY));

        verifyNoInteractions(expressionResolver);
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
        final String stringSingle = "('" + value + "')";
        final String stringDouble = "(\"" + value + "\")";

        assertThat(parameterResolver.apply(stringSingle, expressionResolver, DUMMY)).contains(value);
        assertThat(parameterResolver.apply(stringDouble, expressionResolver, DUMMY)).contains(value);
    }

    @Test
    public void singleResolverAllowsWhitespaceBetweenParentheses() {
        final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forStringOrPlaceholderParameter();

        final String stringSingle = "(   '" + KNOWN_VALUE + "'   )";
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

        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void singleStringResolverDoesNotResolvePlaceholders() {

        final PipelineFunctionParameterResolverFactory.SingleParameterResolver stringResolver =
                PipelineFunctionParameterResolverFactory.forStringParameter();
        final String stringSingle = "('" + KNOWN_VALUE + "')";
        final String stringDouble = "(\"" + KNOWN_VALUE + "\")";
        final String stringPlaceholder = "(" + KNOWN_PLACEHOLDER + ")";

        assertThat(stringResolver.apply(stringSingle, expressionResolver, DUMMY)).contains(KNOWN_VALUE);
        assertThat(stringResolver.apply(stringDouble, expressionResolver, DUMMY)).contains(KNOWN_VALUE);
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> stringResolver.apply(stringPlaceholder, expressionResolver, DUMMY));

        verifyNoInteractions(expressionResolver);
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
        assertThat(noParamResolver.test("('val')")).isFalse();
    }

    @Test
    public void tripleParameterResolverAcceptsThreeDoubleQuotedStringParameters() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forTripleStringOrPlaceholderParameter();

        resolvesThreeParametersInDoubleQuotedString(parameterResolver);
    }

    private void resolvesThreeParametersInDoubleQuotedString(
            final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver) {
        final String firstParameter = KNOWN_VALUE + 1;
        final String secondParameter = KNOWN_VALUE + 2;
        final String thirdParameter = KNOWN_VALUE + 3;
        final String parameters =
                "(\"" + firstParameter + "\", \"" + secondParameter + "\", \"" + thirdParameter + "\")";

        final List<PipelineElement> resolvedParameters = parameterResolver.apply(parameters, expressionResolver, DUMMY);

        assertThat(resolvedParameters.get(0)).contains(firstParameter);
        assertThat(resolvedParameters.get(1)).contains(secondParameter);
        assertThat(resolvedParameters.get(2)).contains(thirdParameter);

        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void tripleParameterResolverAcceptsMixOfDoubleQuotedAndSingleQuotedStringParameters() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forTripleStringOrPlaceholderParameter();

        resolvesThreeParametersInMixOfDoubleAndSingleQuotedString(parameterResolver);
    }

    private void resolvesThreeParametersInMixOfDoubleAndSingleQuotedString(
            final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver) {
        final String firstParameter = KNOWN_VALUE + 1;
        final String secondParameter = KNOWN_VALUE + 2;
        final String thirdParameter = KNOWN_VALUE + 3;
        final String parameters =
                "(\"" + firstParameter + "\", '" + secondParameter + "', \"" + thirdParameter + "\")";

        final List<PipelineElement> resolvedParameters = parameterResolver.apply(parameters, expressionResolver, DUMMY);

        assertThat(resolvedParameters.get(0)).contains(firstParameter);
        assertThat(resolvedParameters.get(1)).contains(secondParameter);
        assertThat(resolvedParameters.get(2)).contains(thirdParameter);

        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void tripleParameterResolverAcceptsMixOfDoubleQuotedAndSingleQuotedStringParametersAndPlaceholders() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forTripleStringOrPlaceholderParameter();

        resolvesThreeParametersInMixOfDoubleAndSingleQuotedStringParametersAndPlaceHolders(parameterResolver);
    }

    private void resolvesThreeParametersInMixOfDoubleAndSingleQuotedStringParametersAndPlaceHolders(
            final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver) {
        final String firstParameter = KNOWN_VALUE + 1;
        final String secondParameter = KNOWN_VALUE + 2;
        final String thirdParameter = KNOWN_VALUE + 3;
        final String parameters =
                "(\"" + firstParameter + "\", '" + secondParameter + "', " + KNOWN_PLACEHOLDER + ")";
        when(expressionResolver.resolveAsPipelineElement(KNOWN_PLACEHOLDER))
                .thenReturn(PipelineElement.resolved(thirdParameter));

        final List<PipelineElement> resolvedParameters = parameterResolver.apply(parameters, expressionResolver, DUMMY);

        assertThat(resolvedParameters.get(0)).contains(firstParameter);
        assertThat(resolvedParameters.get(1)).contains(secondParameter);
        assertThat(resolvedParameters.get(2)).contains(thirdParameter);

        verify(expressionResolver).resolveAsPipelineElement(KNOWN_PLACEHOLDER);
    }

    @Test
    public void tripleParameterResolverFailsIfLessParametersAreGiven() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forTripleStringOrPlaceholderParameter();

        failsForNumberOfParameters(parameterResolver, 2);
    }

    @Test
    public void tripleParameterResolverFailsIfMoreParametersAreGiven() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forTripleStringOrPlaceholderParameter();

        failsForNumberOfParameters(parameterResolver, 4);
    }

    @Test
    public void doubleOrTripleParameterResolverAcceptsThreeDoubleQuotedStringParameters() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        resolvesThreeParametersInDoubleQuotedString(parameterResolver);
    }

    @Test
    public void doubleOrTripleParameterResolverAcceptsTwoDoubleQuotedStringParameters() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        final String firstParameter = KNOWN_VALUE + 1;
        final String secondParameter = KNOWN_VALUE + 2;
        final String parameters =
                "(\"" + firstParameter + "\", \"" + secondParameter + "\")";

        final List<PipelineElement> resolvedParameters = parameterResolver.apply(parameters, expressionResolver, DUMMY);

        assertThat(resolvedParameters.get(0)).contains(firstParameter);
        assertThat(resolvedParameters.get(1)).contains(secondParameter);

        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void doubleOrTripleParameterResolverAcceptsMixOfDoubleQuotedAndSingleQuotedStringParameters() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        resolvesThreeParametersInMixOfDoubleAndSingleQuotedString(parameterResolver);
    }

    @Test
    public void doubleOrTripleParameterResolverAcceptsMixOfDoubleQuotedAndSingleQuotedStringParametersWithoutOptional() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        final String firstParameter = KNOWN_VALUE + 1;
        final String secondParameter = KNOWN_VALUE + 2;
        final String parameters =
                "(\"" + firstParameter + "\", '" + secondParameter + "')";

        final List<PipelineElement> resolvedParameters = parameterResolver.apply(parameters, expressionResolver, DUMMY);

        assertThat(resolvedParameters.get(0)).contains(firstParameter);
        assertThat(resolvedParameters.get(1)).contains(secondParameter);

        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void doubleOrTripleParameterResolverAcceptsMixOfDoubleQuotedAndSingleQuotedStringParametersAndPlaceholders() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        resolvesThreeParametersInMixOfDoubleAndSingleQuotedStringParametersAndPlaceHolders(parameterResolver);
    }

    @Test
    public void doubleOrTripleParameterResolverAcceptsMixOfDoubleQuotedAndSingleQuotedStringParametersAndPlaceholdersWithoutOptional() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        final String firstParameter = KNOWN_VALUE + 1;
        final String secondParameter = KNOWN_VALUE + 2;
        final String parameters =
                "(" + KNOWN_PLACEHOLDER + ", '" + secondParameter + "')";
        when(expressionResolver.resolveAsPipelineElement(KNOWN_PLACEHOLDER))
                .thenReturn(PipelineElement.resolved(firstParameter));

        final List<PipelineElement> resolvedParameters = parameterResolver.apply(parameters, expressionResolver, DUMMY);

        assertThat(resolvedParameters.get(0)).contains(firstParameter);
        assertThat(resolvedParameters.get(1)).contains(secondParameter);

        verify(expressionResolver).resolveAsPipelineElement(KNOWN_PLACEHOLDER);
    }

    @Test
    public void doubleOrTripleParameterResolverFailsIfLessParametersAreGiven() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        failsForNumberOfParameters(parameterResolver, 1);
    }

    @Test
    public void doubleOrTripleParameterResolverFailsIfMoreParametersAreGiven() {
        final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver =
                PipelineFunctionParameterResolverFactory.forDoubleOrTripleStringOrPlaceholderParameter();

        failsForNumberOfParameters(parameterResolver, 4);
    }

    private void failsForNumberOfParameters(
            final PipelineFunctionParameterResolverFactory.ParameterResolver parameterResolver,
            final int parameters) {
        final StringBuilder parametersList = new StringBuilder("(");
        Stream.iterate(0, i -> i + 1)
                .limit(parameters)
                // add , as prefix for each but first param
                .map(index -> index == 0 ? "" : ",")
                .map(parametersList::append)
                .forEach(unused -> parametersList.append("\"" + KNOWN_VALUE + "\""));
        parametersList.append(")");

        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> parameterResolver.apply(parametersList.toString(), expressionResolver, DUMMY));

        verifyNoInteractions(expressionResolver);
    }

}
