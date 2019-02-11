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

/**
 * The pipeline ..
 */
interface Pipeline {

    /**
     * Executes the Pipeline stages by first evaluating the placeholder variable by name.
     *
     * @param pipelineInput
     * @param expressionResolver the resolver from which placeholders are resolved
     * @return
     */
    Optional<String> executeStages(Optional<String> pipelineInput, ExpressionResolver expressionResolver);

}
