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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * PathMatcher implementation for modify thing commands.
 *
 * TODO adapt @since annotation @since 1.6.0
 */
public class ThingModifyPathMatcher extends AbstractPathMatcher<ThingPathPatterns> {

    private static final List<ThingPathPatterns> PATH_PATTERNS = ThingPathPatterns.get();
    private static final ThingModifyPathMatcher INSTANCE =
            new ThingModifyPathMatcher(PATH_PATTERNS, getDefaultExceptionFunction());

    private ThingModifyPathMatcher(final List<ThingPathPatterns> pathPatterns,
            final Function<JsonPointer, DittoRuntimeException> exceptionFunction) {
        super(pathPatterns, exceptionFunction);
    }

    /**
     * Creates a {@link PathMatcher} instance that does not match any pattern.
     *
     * @return empty path matcher instance
     */
    public static ThingModifyPathMatcher empty() {
        return new ThingModifyPathMatcher(Collections.emptyList(), getDefaultExceptionFunction());
    }

    /**
     * Returns a new ThingModifyPathMatcher.
     *
     * @return the {@link ThingModifyPathMatcher} instance.
     */
    public static ThingModifyPathMatcher getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a new ThingModifyPathMatcher with custom exceptionFunction.
     *
     * @param exceptionFunction function to generate the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}.
     * @return the {@link ThingModifyPathMatcher} instance.
     */
    public static ThingModifyPathMatcher getInstance(final Function<JsonPointer, DittoRuntimeException> exceptionFunction) {
        return new ThingModifyPathMatcher(PATH_PATTERNS, exceptionFunction);
    }
}
