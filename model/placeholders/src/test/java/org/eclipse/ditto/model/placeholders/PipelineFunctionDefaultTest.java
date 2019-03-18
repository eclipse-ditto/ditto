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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineFunctionDefaultTest {

    private static final String KNOWN_VALUE = "expected";
    private static final String KNOWN_FALLBACK = "fallback";
    private static final String KNOWN_PLACEHOLDER = "thing:name";

    private final PipelineFunctionDefault function = new PipelineFunctionDefault();

    @Mock
    private ExpressionResolver expressionResolver;

    @Test
    public void getName() {
        assertThat(function.getName()).isEqualTo("default");
    }

    @Test
    public void applyReturnsExistingValue() {
        final Optional<String> input = Optional.of(KNOWN_VALUE);
        final String params = "(\"" + KNOWN_FALLBACK + "\")";
        assertThat(function.apply(input, params, expressionResolver)).contains(KNOWN_VALUE);
    }

    @Test
    public void applyReturnsDefault() {
        final Optional<String> input = Optional.empty();
        final String params = "(\'" + KNOWN_FALLBACK + "\')";
        assertThat(function.apply(input, params, expressionResolver)).contains(KNOWN_FALLBACK);
    }

    @Test
    public void applyReturnsDefaultPlaceholder() {
        final Optional<String> input = Optional.empty();
        final String params = "(" + KNOWN_PLACEHOLDER + ")";
        when(expressionResolver.resolveSinglePlaceholder(anyString())).thenReturn(Optional.of(KNOWN_VALUE));

        assertThat(function.apply(input, params, expressionResolver)).contains(KNOWN_VALUE);

        verify(expressionResolver).resolveSinglePlaceholder(KNOWN_PLACEHOLDER);
    }

}