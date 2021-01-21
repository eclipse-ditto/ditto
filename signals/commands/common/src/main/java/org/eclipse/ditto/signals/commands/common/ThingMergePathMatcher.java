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
 * PathMatcher implementation for merge thing commands.
 *
 * TODO adapt @since annotation @since 1.6.0
 */
public class ThingMergePathMatcher extends AbstractPathMatcher<ThingPathPatterns> {

    private static final List<ThingPathPatterns> THING_MERGE_PATH_PATTERNS = ThingPathPatterns.get(
            ThingPathPatterns.THING_PATH,
            ThingPathPatterns.POLICY_ID_PATH,
            ThingPathPatterns.DEFINITION_PATH,
            ThingPathPatterns.ATTRIBUTES_PATH,
            ThingPathPatterns.ATTRIBUTE_PATH,
            ThingPathPatterns.FEATURES_PATH,
            ThingPathPatterns.FEATURE_PATH,
            ThingPathPatterns.FEATURE_DEFINITION_PATH,
            ThingPathPatterns.FEATURE_PROPERTIES_PATH,
            ThingPathPatterns.FEATURE_PROPERTY_PATH,
            ThingPathPatterns.FEATURE_DESIRED_PROPERTIES_PATH,
            ThingPathPatterns.FEATURE_DESIRED_PROPERTY_PATH
    );

    private static final ThingMergePathMatcher INSTANCE =
            new ThingMergePathMatcher(THING_MERGE_PATH_PATTERNS, getDefaultExceptionFunction());

    private ThingMergePathMatcher(final List<ThingPathPatterns> pathPatterns,
            final Function<JsonPointer, DittoRuntimeException> exceptionFunction) {
        super(pathPatterns, exceptionFunction);
    }

    /**
     * Returns a new ThingMergePathMatcher.
     *
     * @return the {@link ThingMergePathMatcher} instance.
     */
    public static ThingMergePathMatcher getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a new ThingMergePathMatcher with custom exceptionFunction.
     *
     * @param exceptionFunction function to generate the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}.
     * @return the {@link ThingMergePathMatcher} instance.
     */
    public static ThingMergePathMatcher getInstance(
            final Function<JsonPointer, DittoRuntimeException> exceptionFunction) {
        return new ThingMergePathMatcher(THING_MERGE_PATH_PATTERNS, exceptionFunction);
    }
}
