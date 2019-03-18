/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Optional;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineFunctionLowerTest {

    private static final Optional<String> KNOWN_INPUT = Optional.of("CamElCase");
    private static final String LOWER_CASE = "camelcase";

    private final PipelineFunctionLower function = new PipelineFunctionLower();

    @Mock
    private ExpressionResolver expressionResolver;

    @After
    public void verifyExpressionResolverUnused() {
        verifyZeroInteractions(expressionResolver);
    }

    @Test
    public void getName() {
        assertThat(function.getName()).isEqualTo("lower");
    }

    @Test
    public void apply() {
        assertThat(function.apply(KNOWN_INPUT, "()", expressionResolver)).contains(LOWER_CASE);
    }

    @Test
    public void throwsOnNonZeroParameters() {
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT, "", expressionResolver));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT, "(\"string\")", expressionResolver));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT, "(\'string\')", expressionResolver));
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class)
                .isThrownBy(() -> function.apply(KNOWN_INPUT, "(thing:id)", expressionResolver));
    }

}