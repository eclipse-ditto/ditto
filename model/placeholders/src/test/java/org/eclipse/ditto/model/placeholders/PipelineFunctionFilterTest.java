/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class PipelineFunctionFilterTest {

    private static final String KNOWN_VALUE = "some value";
    private static final PipelineElement KNOWN_INPUT = PipelineElement.resolved(KNOWN_VALUE);

    private final PipelineFunctionFilter underTest = new PipelineFunctionFilter();

    @Mock
    private ExpressionResolver expressionResolver;

    @Test
    public void getName() {
        assertThat(underTest.getName()).isEqualTo("filter");
    }

    @Test
    public void getSignature() {
        final List<PipelineFunction.ParameterDefinition> parameterDefinitions =
                underTest.getSignature().getParameterDefinitions();
        assertThat(parameterDefinitions).hasSize(3);
        assertThat(parameterDefinitions.get(0).getName()).isEqualTo("filterValue");
        assertThat(parameterDefinitions.get(0).getType()).isEqualTo(String.class);
        assertThat(parameterDefinitions.get(1).getName()).isEqualTo("rqlFunction");
        assertThat(parameterDefinitions.get(1).getType()).isEqualTo(String.class);
        assertThat(parameterDefinitions.get(2).getName()).isEqualTo("comparedValue");
        assertThat(parameterDefinitions.get(2).getType()).isEqualTo(String.class);
    }

    @Test
    public void applyReturnsExistingValueIfFilterConditionSucceeds() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("true"));
        final String params = String.format("(%s,\"%s\",\"%s\")", "header:reply-to", "eq", "true");
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).contains(KNOWN_VALUE);
    }

    @Test
    public void applyReturnsUnresolvedValueIfFilterConditionFails() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("false"));
        final String params = String.format("(%s,\"%s\",\"%s\")", "header:reply-to", "eq", "true");
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).isEmpty();
    }

    @Test
    public void applyReturnsUnresolvedValueIfUnsupportedRqlFunctionDefined() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("false"));
        final String params = String.format("(%s,\"%s\",\"%s\")", "header:reply-to", "na", "true");
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).isEmpty();
    }

}
