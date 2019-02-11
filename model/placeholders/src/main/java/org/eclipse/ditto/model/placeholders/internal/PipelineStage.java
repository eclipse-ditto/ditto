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
package org.eclipse.ditto.model.placeholders.internal;

import java.util.Optional;

import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.ExpressionStage;

/**
 * Defines a stage used in a Pipeline after the "input" stage of a
 * {@link org.eclipse.ditto.model.placeholders.Placeholder}:
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
     * @param expressionResolver
     * @return processed output value, or an empty optional otherwise.
     */
    Optional<String> apply(Optional<String> value, ExpressionResolver expressionResolver);
}
