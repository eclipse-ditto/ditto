/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.headers;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoHeadersTooLargeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Checks whether Ditto headers are small enough to send around the cluster.
 */
public final class DittoHeadersSizeChecker {

    private final int maxSize;
    private final int maxAuthSubjects;

    private DittoHeadersSizeChecker(final int maxSize, final int maxAuthSubjects) {
        this.maxSize = maxSize;
        this.maxAuthSubjects = maxAuthSubjects;
    }

    /**
     * Create a headers size checker from maximum size and authorization subjects.
     *
     * @param maxSize maximum allowed size of headers in bytes.
     * @param maxAuthSubjects maximum allowed number of authorization subjects.
     * @return the header validator.
     */
    public static DittoHeadersSizeChecker of(final int maxSize, final int maxAuthSubjects) {
        return new DittoHeadersSizeChecker(maxSize, maxAuthSubjects);
    }

    /**
     * Check whether Ditto headers are too large.
     *
     * @param dittoHeaders the headers to check.
     * @return an optional error if the headers are too large or an empty optional otherwise.
     */
    public Optional<DittoRuntimeException> check(final DittoHeaders dittoHeaders) {
        return check(dittoHeaders, dittoHeaders.getAuthorizationContext());
    }

    /**
     * Check whether Ditto headers are too large.
     *
     * @param dittoHeaders the headers to check.
     * @param authorizationContext unmapped authorization context.
     * @return an optional error if the headers are too large or an empty optional otherwise.
     */
    public Optional<DittoRuntimeException> check(final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext) {

        final int authSubjectsCount = authorizationContext.getSize();
        if (authSubjectsCount > maxAuthSubjects) {
            return Optional.of(
                    DittoHeadersTooLargeException.newAuthSubjectsLimitBuilder(authSubjectsCount, maxAuthSubjects)
                            .dittoHeaders(dittoHeaders)
                            .build());
        } else if (areHeadersTooLarge(dittoHeaders)) {
            return Optional.of(
                    DittoHeadersTooLargeException.newSizeLimitBuilder(maxSize)
                            .dittoHeaders(dittoHeaders)
                            .build());
        } else {
            return Optional.empty();
        }
    }

    public <T> T run(final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext,
            final Function<DittoHeaders, T> onSuccess,
            final Function<DittoRuntimeException, T> onError) {

        return check(dittoHeaders, authorizationContext).map(onError).orElseGet(() -> onSuccess.apply(dittoHeaders));
    }

    boolean areHeadersTooLarge(final Map<? extends CharSequence, ? extends CharSequence> headersStream) {
        int quota = maxSize;
        for (final Map.Entry<? extends CharSequence, ? extends CharSequence> entry : headersStream.entrySet()) {
            // iteratively subtract header values from quota and stop at first negative number
            // to deal with integer overflow and underflow
            if (quota < 0 || (quota -= entry.getKey().length()) < 0 || (quota -= entry.getValue().length()) < 0) {
                return true;
            }
        }
        return false;
    }
}
