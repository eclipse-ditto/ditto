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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link FunctionExpression} defining which {@link PipelineFunction}s are supported.
 */
@Immutable
final class ImmutableFunctionExpression implements FunctionExpression {

    /**
     * Singleton instance of the ImmutableFunctionExpression.
     */
    static final ImmutableFunctionExpression INSTANCE = new ImmutableFunctionExpression();

    private static final List<PipelineFunction> SUPPORTED = Collections.unmodifiableList(Arrays.asList(
            new PipelineFunctionFilter(),          // fn:filter(filterValue,rqlFunction,comparedValue)
            new PipelineFunctionDefault(),         // fn:default('fallback value')
            new PipelineFunctionSubstringBefore(), // fn:substring-before(':')
            new PipelineFunctionSubstringAfter(),  // fn:substring-after(':')
            new PipelineFunctionLower(),           // fn:lower()
            new PipelineFunctionUpper(),           // fn:upper()
            new PipelineFunctionDelete(),          // fn:delete()
            new PipelineFunctionReplace(),         // fn:replace('from', 'to')
            new PipelineFunctionSplit()            // fn:split(' ')
    ));

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {

        return SUPPORTED.stream()
                .map(PipelineFunction::getName)
                .collect(Collectors.toList());
    }

    @Override
    public boolean supports(final String expressionName) {

        // it is sufficient that the passed in name starts with the function name and an opening parentheses,
        // e.g.: default('foo'). the function validates itself whether the remaining part is valid.
        return SUPPORTED.stream()
                .map(PipelineFunction::getName)
                .anyMatch(psfName -> expressionName.startsWith(psfName + "("));
    }

    @Override
    public PipelineElement resolve(final String expression, final PipelineElement resolvedInputValue,
            final ExpressionResolver expressionResolver) {

        if (!supports(expression.replaceFirst(getPrefix() + ":", ""))) {
            throw PlaceholderFunctionUnknownException.newBuilder(expression).build();
        }

        return SUPPORTED.stream()
                .filter(pf -> expression.startsWith(getPrefix() + ":" + pf.getName() + "("))
                .map(pf -> pf.apply(resolvedInputValue,
                        expression.replaceFirst(getPrefix() + ":" + pf.getName(), "").trim(),
                        expressionResolver)
                )
                .findFirst()
                .orElse(PipelineElement.unresolved());
    }

}
