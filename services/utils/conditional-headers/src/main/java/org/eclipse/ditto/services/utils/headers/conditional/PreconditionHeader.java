/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.headers.conditional;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

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
    boolean meetsConditionFor(@Nullable final T objectToCheck);
}
