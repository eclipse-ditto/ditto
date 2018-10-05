/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.base.entity;

import javax.annotation.concurrent.Immutable;

/**
 * Base type of all revisions.
 *
 * @param <T> The type of the implementing revision.
 */
@Immutable
public interface Revision<T extends Revision<T>> extends Comparable<T> {

    /**
     * Indicates if this revision is greater than the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is greater than {@code other}, {@code false} else.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isGreaterThan(T other);

    /**
     * Indicates if this revision is greater than or equal to the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is greater than or equal to {@code other}, {@code false} if this revision
     * is lower than {@code other}.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isGreaterThanOrEqualTo(T other);

    /**
     * Indicates if this revision is lower than the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is lower than {@code other}, {@code false} else.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isLowerThan(T other);

    /**
     * Indicates if this revision is lower than or equal to the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is lower than or equal to {@code other}, {@code false} if this revision is
     * greater than {@code other}.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isLowerThanOrEqualTo(T other);

    /**
     * Returns this revision as {@code long} value.
     *
     * @return this revision as {@code long} value.
     */
    long toLong();

    /**
     * Returns this revision as string.
     *
     * @return this revision as string.
     */
    @Override
    String toString();
}
