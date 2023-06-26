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
package org.eclipse.ditto.internal.utils.headers.conditional;

import javax.annotation.Nullable;

/**
 * Interface for all precondition headers according to
 * <a href="https://tools.ietf.org/html/rfc7232#section-3">RFC7232 - Section 3</a>.
 * @param <T> Type of the object for which a condition should be checked.
 */
public interface PreconditionHeader<T> {

    /**
     * Gets the key of this header.
     *
     * @return The key of this header.
     */
    String getKey();

    /**
     * Gets the value of this header.
     *
     * @return The value of this header.
     */
    String getValue();

    /**
     * Indicates whether this {@link PreconditionHeader} meets the condition for the given {@code objectToCheck}
     *
     * @param objectToCheck The object for which this {@link PreconditionHeader} should meet the condition.
     * @return True if this {@link PreconditionHeader} meets the condition. False if not.
     */
    boolean meetsConditionFor(@Nullable T objectToCheck);
}
