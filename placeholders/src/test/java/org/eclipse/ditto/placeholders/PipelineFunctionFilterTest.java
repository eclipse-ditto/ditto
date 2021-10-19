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
import static org.mockito.Mockito.when;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class PipelineFunctionFilterTest {

    private static final String KNOWN_VALUE = "some value";
    private static final PipelineElement KNOWN_INPUT = PipelineElement.resolved(KNOWN_VALUE);
    private static final String KNOWN_BOOLEAN = "true";
    private static final PipelineElement KNOWN_INPUT_BOOLEAN = PipelineElement.resolved(KNOWN_BOOLEAN);

    private final PipelineFunctionFilter underTest = new PipelineFunctionFilter();

    @Mock
    private ExpressionResolver expressionResolver;

    @Test
    public void getName() {
        assertThat(underTest.getName()).isEqualTo("filter");
    }

    @Test
    public void getSignature() {
        final List<PipelineFunction.ParameterDefinition<?>> parameterDefinitions =
                underTest.getSignature().getParameterDefinitions();
        Assertions.assertThat(parameterDefinitions).hasSize(3);
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

    @Test
    public void eqSucceedsWithBothValuesUnresolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.unresolved());
        final String params = String.format("(%s,\"%s\",%s)", "header:reply-to", "eq", "header:some-header");
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).contains(KNOWN_VALUE);
    }

    @Test
    public void eqFailsWithOneUnresolvedValue() {
        final String params = String.format("(%s,\"%s\",%s)", "header:reply-to", "eq", "header:some-header");

        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("true"));
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.unresolved());
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).isEmpty();

        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.resolved("true"));
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).isEmpty();
    }

    @Test
    public void neFailsWithBothValuesUnresolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.unresolved());
        final String params = String.format("(%s,\"%s\",%s)", "header:reply-to", "ne", "header:some-header");
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).isEmpty();
    }

    @Test
    public void neSucceedsWithOneUnresolvedValue() {
        final String params = String.format("(%s,\"%s\",%s)", "header:reply-to", "ne", "header:some-header");

        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("true"));
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.unresolved());
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).contains(KNOWN_VALUE);

        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.resolved("true"));
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).contains(KNOWN_VALUE);
    }

    @Test
    public void filterByLike() {
        // single character case
        testPatternMatching("x", "y", false);
        testPatternMatching("x", "x", true);
        testPatternMatching("x", "*", true);
        testPatternMatching("x", "?", true);

        // argument order matters
        testPatternMatching("*y", "xy", false);
        testPatternMatching("xy", "*y", true);

        // match wildcard and single characters at various positions
        testPatternMatching("1234567", "*2?7", false);
        testPatternMatching("1234567", "*5?7", true);
        testPatternMatching("1234567", "1?4*", false);
        testPatternMatching("1234567", "1?3*", true);
        testPatternMatching("1234567", "*?1?3?*", false);
        testPatternMatching("1234567", "*?3?5?*", true);
    }

    @Test
    public void filterSucceedsWithBothValuesUnresolved() {
        final String params = String.format("(%s,\"%s\",%s)", "header:reply-to", "like", "header:some-header");

        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.unresolved());

        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).contains(KNOWN_VALUE);
    }

    @Test
    public void filterFailsWithOneUnresolvedValue() {
        final String params = String.format("(%s,\"%s\",%s)", "header:reply-to", "like", "header:some-header");

        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("true"));
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.unresolved());
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).isEmpty();

        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        when(expressionResolver.resolveAsPipelineElement("header:some-header"))
                .thenReturn(PipelineElement.resolved("*2?7"));
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver)).isEmpty();
    }

    @Test
    public void existsSucceedsWithValueResolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("true"));
        final String params = String.format("(%s,'%s')", "header:reply-to", "exists");
        assertThat(underTest.apply(KNOWN_INPUT_BOOLEAN, params, expressionResolver)).contains(KNOWN_BOOLEAN);
    }

    @Test
    public void existsFailsWithValueUnresolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        final String params = String.format("(%s,'%s')", "header:reply-to", "exists");
        assertThat(underTest.apply(KNOWN_INPUT_BOOLEAN, params, expressionResolver)).isEmpty();
    }

    @Test
    public void existsTrueSucceedsWithValueResolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("true"));
        final String params = String.format("(%s,'%s','%s')", "header:reply-to", "exists", "true");
        assertThat(underTest.apply(KNOWN_INPUT_BOOLEAN, params, expressionResolver)).contains(KNOWN_BOOLEAN);
    }

    @Test
    public void existsTrueFailsWithValueUnresolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        final String params = String.format("(%s,'%s','%s')", "header:reply-to", "exists", "true");
        assertThat(underTest.apply(KNOWN_INPUT_BOOLEAN, params, expressionResolver)).isEmpty();
    }

    @Test
    public void existsFalseSucceedsWithValueResolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.resolved("true"));
        final String params = String.format("(%s,'%s','%s')", "header:reply-to", "exists", "false");
        assertThat(underTest.apply(KNOWN_INPUT_BOOLEAN, params, expressionResolver)).isEmpty();
    }

    @Test
    public void existsFalseFailsWithValueUnresolved() {
        when(expressionResolver.resolveAsPipelineElement("header:reply-to"))
                .thenReturn(PipelineElement.unresolved());
        final String params = String.format("(%s,'%s','%s')", "header:reply-to", "exists", "false");
        assertThat(underTest.apply(KNOWN_INPUT_BOOLEAN, params, expressionResolver)).contains(KNOWN_BOOLEAN);
    }

    private void testPatternMatching(final String arg, final String pattern, final boolean shouldMatch) {
        final String params = String.format("('%s','like','%s')", arg, pattern);
        assertThat(underTest.apply(KNOWN_INPUT, params, expressionResolver))
                .describedAs("Match <%s> against <%s> should %s", arg, pattern,
                        shouldMatch ? "succeed." : "fail.")
                .isEqualTo(shouldMatch ? KNOWN_INPUT : PipelineElement.unresolved());
    }

}
