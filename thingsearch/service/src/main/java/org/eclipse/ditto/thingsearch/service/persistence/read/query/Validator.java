/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read.query;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

/**
 * Helper for validation of query parameters.
 */
@Immutable
final class Validator {

    private static final String LIMIT_PARAM = "limit";
    private static final String SIZE_PARAM = "size";
    private static final String SKIP_PARAM = "skip";

    private Validator() {
        throw new AssertionError();
    }

    static int checkLimit(final long limit, final int maxLimit) {
        return checkMaxLimit(checkMinLimit(limit), maxLimit);
    }

    static int checkSize(final long size, final int maxLimit) {
        return checkMaxSize(checkMinSize(size), maxLimit);
    }

    static int checkSkip(final long skip) {
        return checkMaxSkip(checkMinSkip(skip));
    }

    private static long checkMinLimit(final long limit) {
        return checkMinParamValue(limit, LIMIT_PARAM);
    }

    private static long checkMinSize(final long limit) {
        return checkMinParamValue(limit, SIZE_PARAM);
    }

    private static long checkMinSkip(final long limit) {
        return checkMinParamValue(limit, SKIP_PARAM);
    }

    private static long checkMinParamValue(final long limit, final String paramName) {
        if (limit < 0) {
            final String msgTemplate = "Parameter <{0}> must be greater than or equal to <0> but it was <{1}>!";
            throw new IllegalArgumentException(MessageFormat.format(msgTemplate, paramName, limit));
        }
        return limit;
    }

    private static int checkMaxSkip(final long skip) {
        return checkMaxParamValue(skip, Integer.MAX_VALUE, SKIP_PARAM);
    }

    private static int checkMaxLimit(final long limit, final int maxLimit) {
        return checkMaxParamValue(limit, maxLimit, LIMIT_PARAM);
    }
    private static int checkMaxSize(final long limit, final int maxLimit) {
        return checkMaxParamValue(limit, maxLimit, SIZE_PARAM);
    }

    private static int checkMaxParamValue(final long value, final int maxParamValue, final String paramName) {
        if (value > maxParamValue) {
            final String msgTemplate = "Parameter <{0}> must be less than or equal to <{1}> but it was <{2}>!";
            throw new IllegalArgumentException(MessageFormat.format(msgTemplate, paramName, maxParamValue, value));
        }
        return (int) value;
    }

}
