/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query;

/**
 * Which conjuncts the selective-leg CTE ({@link SearchQueryAssembler}) may materialise.
 */
public enum SelectiveLegs {

    /**
     * Only point legs ({@code eq(non-null)}, {@code in(non-empty)} without a null member): the rule before the system
     * index sf_multi.
     */
    POINT,

    /**
     * Point legs and same-path two-sided numeric ranges. When both are present, the statement counts their index
     * entries and materialises the smaller one; a two-sided range alone is never materialised (the statement keeps the
     * plan it had before range legs). Requires the valid system index {@code sf_multi}, which serves the range leg's
     * multi-valued branch.
     */
    POINT_AND_RANGE;

    /**
     * @param rangeLegsAvailable whether the system index sf_multi is valid.
     * @return {@link #POINT_AND_RANGE} if it is, else {@link #POINT}.
     */
    public static SelectiveLegs of(final boolean rangeLegsAvailable) {
        return rangeLegsAvailable ? POINT_AND_RANGE : POINT;
    }
}
