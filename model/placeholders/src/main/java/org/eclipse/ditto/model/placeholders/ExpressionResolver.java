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


/**
 * The ExpressionResolver is able to:
 * <ul>
 * <li>resolve {@link Placeholder}s in a passed {@code template}</li>
 * <li>execute optional pipeline stages in a passed {@code template}</li>
 * </ul>
 * As a result, a resolved String is returned.
 * <p>
 * For example, following expressions can be resolved:
 * <ul>
 * <li>{@code {{ thing:id }} }</li>
 * <li>{@code {{ header:device_id }} }</li>
 * <li>{@code {{ topic:full }} }</li>
 * <li>{@code {{ thing:name | fn:substring-before(':') | fn:default(thing:name) }} }</li>
 * <li>{@code {{ header:unknown | fn:default('fallback') }} }</li>
 * </ul>
 * </p>
 *
 * TODO TJ docs
 */
public interface ExpressionResolver {

    /**
     * Resolves {@link Placeholder}s and executes optional pipeline stages in the passed String {@code
     * expressionTemplate}.
     *
     * @param expressionTemplate the String expressionTemplate to resolve {@link Placeholder}s and and execute optional
     * pipeline stages
     * @param allowUnresolved whether it should be allowed that unresolved placeholder may be present after processing
     * @return the resolved String or the original {@code expressionTemplate} if {@code allowUnresolved} was set to
     * {@code true} and placeholders could not be resolved.
     * @throws org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException thrown if {@code allowUnresolved} was
     * set to {@code false} and the passed in {@code expressionTemplate} could not be resolved
     */
    String resolve(String expressionTemplate, boolean allowUnresolved);

    String resolveSinglePlaceholder(String placeholder, boolean allowUnresolved);

}
