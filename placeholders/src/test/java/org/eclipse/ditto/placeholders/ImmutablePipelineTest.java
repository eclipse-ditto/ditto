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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutablePipeline}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ImmutablePipelineTest {

    private static final List<String> STAGES = Arrays.asList(
            "thing:name",
            "fn:substring-before(':')",
            "fn:default(thing:id)"
    );
    private static final List<String> INVALID_STAGES = Arrays.asList(
            "thing:name",
            "fn:substring-before(':')",
            "fn:unknown('foo')"
    );
    private static final PipelineElement PIPELINE_INPUT = PipelineElement.resolved("my-gateway:my-thing");
    private static final List<PipelineElement> RESPONSES = Arrays.asList(
            PipelineElement.resolved("my-gateway"),
            PipelineElement.resolved("my-gateway")
    );

    @Mock
    private FunctionExpression functionExpression;
    @Mock
    private ExpressionResolver expressionResolver;

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutablePipeline.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(FunctionExpression.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePipeline.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void execute() {
        prepareFunctionExpressionResponses();

        final ImmutablePipeline pipeline = new ImmutablePipeline(functionExpression, STAGES);
        final PipelineElement result = pipeline.execute(PIPELINE_INPUT, expressionResolver);

        verifyResultEqualsLastResponse(result);
        verifyFunctionExpressionWasCalledWithIntermediateValues();
    }

    @Test(expected = PlaceholderFunctionUnknownException.class)
    public void validate() {
        prepareFunctionExpressionResponses();

        final ImmutablePipeline pipeline = new ImmutablePipeline(functionExpression, INVALID_STAGES);
        pipeline.validate();
    }

    private void prepareFunctionExpressionResponses() {
        Mockito.when(functionExpression.resolve(anyString(), any(PipelineElement.class), any(ExpressionResolver.class)))
                .thenReturn(RESPONSES.get(0), RESPONSES.get(1));
    }

    private void verifyResultEqualsLastResponse(final PipelineElement result) {
        assertThat(result).isEqualTo(RESPONSES.get(1));
    }

    private void verifyFunctionExpressionWasCalledWithIntermediateValues() {
        Mockito.verify(functionExpression)
                .resolve(STAGES.get(0), PIPELINE_INPUT, expressionResolver);
        Mockito.verify(functionExpression)
                .resolve(STAGES.get(1), RESPONSES.get(0), expressionResolver);
        Mockito.verify(functionExpression)
                .resolve(STAGES.get(2), RESPONSES.get(1), expressionResolver);
        Mockito.verifyNoMoreInteractions(functionExpression);
    }

}
