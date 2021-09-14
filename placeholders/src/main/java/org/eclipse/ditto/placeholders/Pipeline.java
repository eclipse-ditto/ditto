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

/**
 * A Pipeline is able to execute its {@code stageExpressions} starting with a {@code pipelineInput} derived from a
 * {@link Placeholder}.
 */
interface Pipeline {

    /**
     * Executes the Pipeline function expressions by first evaluating the placeholder variable by name.
     *
     * @param pipelineInput the input into the pipe, usually an already resolved {@link Placeholder} - may also be an
     * empty optional as there are pipeline stages which can make use of a default fallback value.
     * @param expressionResolver the resolver from which placeholders are resolved.
     * @return the result of the Pipeline execution after all stages were handled.
     */
    PipelineElement execute(PipelineElement pipelineInput, ExpressionResolver expressionResolver);

    /**
     * Validates the instantiated Pipeline and checks whether all configured {@code stageExpressions} are supported.
     *
     * @throws PlaceholderFunctionUnknownException if a function is contained in this Pipeline which is not supported.
     */
    void validate();

}
