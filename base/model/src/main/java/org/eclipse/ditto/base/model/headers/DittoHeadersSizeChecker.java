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
package org.eclipse.ditto.base.model.headers;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException;

/**
 * Checks whether Ditto headers are small enough to send around the cluster.
 */
@Immutable
public final class DittoHeadersSizeChecker {

    private final long maxSize;
    private final int maxAuthSubjects;

    private DittoHeadersSizeChecker(final long maxSize, final int maxAuthSubjects) {
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
    public static DittoHeadersSizeChecker of(final long maxSize, final int maxAuthSubjects) {
        return new DittoHeadersSizeChecker(maxSize, maxAuthSubjects);
    }

    /**
     * Checks if the specified Ditto headers are too large.
     *
     * @param dittoHeaders the headers to check.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException if {@code dittoHeaders} are too large.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public void check(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "DittoHeaders to be checked");

        checkAuthorizationContext(dittoHeaders);

        if (dittoHeaders.isEntriesSizeGreaterThan(maxSize)) {
            throw DittoHeadersTooLargeException.newSizeLimitBuilder(maxSize)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private void checkAuthorizationContext(final DittoHeaders dittoHeaders) {
        final int authSubjectsCount = dittoHeaders.getAuthorizationContext().getSize();
        if (authSubjectsCount > maxAuthSubjects) {
            throw DittoHeadersTooLargeException.newAuthSubjectsLimitBuilder(authSubjectsCount, maxAuthSubjects)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

}
