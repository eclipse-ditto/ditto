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

import java.util.Optional;

/**
 * A Pipeline is able to execute its {@link FunctionExpression}s starting with a {@code pipelineInput} derived from a
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
    Optional<String> execute(Optional<String> pipelineInput, ExpressionResolver expressionResolver);

}
