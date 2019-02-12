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
 * Defines a stage used in a Pipeline after the "input" stage of a {@link org.eclipse.ditto.model.placeholders.Placeholder},
 * e.g. pipeline stages are functions like {@code fn:starts-with(':')} or {@code fn:default('fallback')}. Used in a
 * pipeline expression like in the following example:
 * <pre>
 * {@code
 * thing:name | fn:substring-before(':') | fn:default(thing:name)
 * }
 * </pre>
 */
interface PipelineStage extends ExpressionStage {

    /**
     * @return the expression string of this stage including prefix, e.g.: {@code fn:substring-before(':')}.
     */
    String getExpression();

    /**
     * Executes the Stage by passing in a value and returning a processed result.
     *
     * @param value the input value to process.
     * @param expressionResolver the expressionResolver to use in order to resolve placeholders occurring in the
     * pipeline expression.
     * @return processed output value, or an empty optional if this stage resolved to an empty Optional.
     */
    Optional<String> apply(Optional<String> value, ExpressionResolver expressionResolver);
}
