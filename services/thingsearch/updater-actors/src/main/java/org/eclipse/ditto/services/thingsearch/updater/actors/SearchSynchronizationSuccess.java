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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

/**
 * todo: in which module and package should this class be saved?
 * <p>
 * todo: needs this class to be serializable to be sent using akka? -> serialize localDateTime using {@link
 * LocalDateTime#toString()} and {@link LocalDateTime#parse(CharSequence)}.
 * <p>
 * Represents the success message of a synchronization of search.
 */
@ParametersAreNonnullByDefault
@Immutable
public final class SearchSynchronizationSuccess {

    private final LocalDateTime utcTimestamp;

    private SearchSynchronizationSuccess(final LocalDateTime utcTimestamp) {
        this.utcTimestamp = utcTimestamp;
    }

    /**
     * Create a new instance of SearchSynchronizationSuccess. The timestamp should use UTC zone.
     *
     * @param utcTimestamp The timestamp when the search synchronization was successfully finished.
     * @return The created object.
     */
    static SearchSynchronizationSuccess newInstance(final LocalDateTime utcTimestamp) {
        return new SearchSynchronizationSuccess(utcTimestamp);
    }

    LocalDateTime getUtcTimestamp() {
        return utcTimestamp;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SearchSynchronizationSuccess success = (SearchSynchronizationSuccess) o;
        return Objects.equals(utcTimestamp, success.utcTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(utcTimestamp);
    }

    @Override
    public String toString() {
        return "SearchSynchronizationSuccess{" +
                "utcTimestamp=" + utcTimestamp +
                '}';
    }
}
