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
package org.eclipse.ditto.signals.commands.common;

import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * PathMatcher implementation for policy commands.
 *
 * TODO adapt @since annotation @since 1.6.0
 */
public class PolicyPathMatcher extends AbstractPathMatcher<PolicyPathPatterns> {

    private static final List<PolicyPathPatterns> PATH_PATTERNS = PolicyPathPatterns.get();
    private static final PolicyPathMatcher INSTANCE = new PolicyPathMatcher(PATH_PATTERNS, getDefaultExceptionFunction());

    private PolicyPathMatcher(final List<PolicyPathPatterns> pathPatterns,
            final Function<JsonPointer, DittoRuntimeException> exceptionFunction) {
        super(pathPatterns, exceptionFunction);
    }

    /**
     * Returns a new PolicyPathMatcher.
     *
     * @return the {@link PolicyPathMatcher} instance.
     */
    public static PolicyPathMatcher getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a new PolicyPathMatcher with custom exceptionFunction.
     *
     * @param exceptionFunction function to generate the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}.
     * @return the {@link PolicyPathMatcher} instance.
     */
    public static PolicyPathMatcher getInstance(final Function<JsonPointer, DittoRuntimeException> exceptionFunction) {
        return new PolicyPathMatcher(PATH_PATTERNS, exceptionFunction);
    }

}
