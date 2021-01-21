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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Base class for {@link org.eclipse.ditto.signals.commands.common.PathMatcher}s.
 *
 * TODO adapt @since annotation @since 1.6.0
 * @param <T> the type of the {@link org.eclipse.ditto.signals.commands.common.PathPatterns}s.
 */
public abstract class AbstractPathMatcher<T extends PathPatterns> implements PathMatcher<PathPatterns> {

    private final List<T> pathPatterns;
    private final Function<JsonPointer, DittoRuntimeException> exceptionFunction;

    protected AbstractPathMatcher(final List<T> pathPatterns,
            final Function<JsonPointer, DittoRuntimeException> exceptionFunction) {
        this.pathPatterns = pathPatterns;
        this.exceptionFunction = exceptionFunction;
    }

    public static Function<JsonPointer, DittoRuntimeException> getDefaultExceptionFunction() {
        return path -> PathUnknownException.newBuilder(path).build();
    }

    /**
     * Matches a given {@code path} against known schemes and returns the corresponding entity name.
     *
     * @param path the path to match.
     * @return the entity name which matched.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if {@code path} matched no known scheme.
     */
    @Override
    public T match(final JsonPointer path) {
        return pathPatterns.stream()
                .filter(patternEntry -> pathMatchesPattern(patternEntry, path))
                .findFirst()
                .orElseThrow(() -> exceptionFunction.apply(path));
    }

    private boolean pathMatchesPattern(final T patternEntry, final JsonPointer path) {
        final Pattern pattern = patternEntry.getPathPattern();
        final Matcher matcher = pattern.matcher(path);
        return matcher.matches();
    }
}
