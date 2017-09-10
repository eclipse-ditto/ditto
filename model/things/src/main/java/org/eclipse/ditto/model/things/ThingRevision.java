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
package org.eclipse.ditto.model.things;

/**
 * Represents the current revision of a Thing.
 */
public interface ThingRevision extends Comparable<ThingRevision> {

    /**
     * Returns a new immutable {@link ThingRevision} which is initialised with the given revision number.
     *
     * @param revisionNumber the {@code long} value of the revision.
     * @return the new immutable {@code ThingRevision}.
     */
    static ThingRevision newInstance(final long revisionNumber) {
        return ThingsModelFactory.newThingRevision(revisionNumber);
    }

    /**
     * Indicates if this revision is greater than the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is greater than {@code other}, {@code false} else.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isGreaterThan(ThingRevision other);

    /**
     * Indicates if this revision is greater than or equal to the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is greater than or equal to {@code other}, {@code false} if this revision
     * is lower than {@code other}.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isGreaterThanOrEqualTo(ThingRevision other);

    /**
     * Indicates if this revision is lower than the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is lower than {@code other}, {@code false} else.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isLowerThan(ThingRevision other);

    /**
     * Indicates if this revision is lower than or equal to the given revision.
     *
     * @param other the other revision to compare this revision with.
     * @return {@code true} if this revision is lower than or equal to {@code other}, {@code false} if this revision is
     * greater than {@code other}.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    boolean isLowerThanOrEqualTo(ThingRevision other);

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
