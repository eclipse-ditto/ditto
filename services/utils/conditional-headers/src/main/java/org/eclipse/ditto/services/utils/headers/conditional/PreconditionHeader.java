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
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;

/**
 * Interface for all precondition headers according to
 * <a href="https://tools.ietf.org/html/rfc7232#section-3">RFC7232 - Section 3</a>.
 */
public interface PreconditionHeader {

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
     * Indicates whether this {@link PreconditionHeader} meets the condition for the given {@code entityTag}
     *
     * @param entityTag The entity tag for which this {@link PreconditionHeader} should meet the condition.
     * @return True if this {@link PreconditionHeader} meets the condition. False if not.
     */
    boolean meetsConditionFor(@Nullable final EntityTag entityTag);

    /**
     * Extracts all supported {@link PreconditionHeader precondition headers} out of the given ditto headers.
     *
     * @param dittoHeaders The ditto headers where precondition headers should be extracted from.
     * @return A list of {@link PreconditionHeader precondition headers} contained in the given ditto headers. List is
     * empty if the given ditto headers don't contain any supported precondition headers.
     */
    static List<PreconditionHeader> fromDittoHeaders(final DittoHeaders dittoHeaders) {
        final List<PreconditionHeader> preconditionHeaders = new ArrayList<>();

        IfNoneMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).ifPresent(preconditionHeaders::add);
        IfMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).ifPresent(preconditionHeaders::add);

        return preconditionHeaders;
    }
}
