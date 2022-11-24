/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.filter;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;

/**
 * A filter to decide whether a particular operation name should be traced or not.
 */
@Immutable
public interface TracingFilter {

    /**
     * Indicates whether the specified {@code SpanOperationName} argument is accepted for tracing.
     *
     * @param operationName the name of the operation to check.
     * @return {@code true} if {@code operationName} is accepted for tracing, {@code false} else.
     * @throws NullPointerException if {@code operationName} is {@code null}.
     */
    boolean accept(SpanOperationName operationName);

}
