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
package org.eclipse.ditto.base.model.entity;

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
     * Creates a new revision by incrementing this revision by one.
     *
     * @return the incremented revision.
     */
    T increment();

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
