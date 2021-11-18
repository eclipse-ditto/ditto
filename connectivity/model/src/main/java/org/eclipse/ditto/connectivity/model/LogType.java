/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Defines the known log types in connection logs in close relation to {@link MetricType}.
 */
@Immutable
public enum LogType {
    /**
     * Log related to a 'consumed' metric event.
     */
    CONSUMED("consumed", LogCategory.SOURCE),

    /**
     * Log related to a 'dispatched' metric event.
     */
    DISPATCHED("dispatched", LogCategory.TARGET, LogCategory.RESPONSE),

    /**
     * Log related to a 'filtered' metric event.
     */
    FILTERED("filtered", LogCategory.TARGET, LogCategory.RESPONSE),

    /**
     * Log related to a 'mapped' metric event.
     */
    MAPPED("mapped", LogCategory.SOURCE, LogCategory.TARGET, LogCategory.RESPONSE),

    /**
     * Log related to a 'dropped' metric event.
     */
    DROPPED("dropped", LogCategory.SOURCE, LogCategory.TARGET, LogCategory.RESPONSE),

    /**
     * Log related to a 'enforced' metric event.
     */
    ENFORCED("enforced", LogCategory.SOURCE),

    /**
     * Log related to a 'published' metric event.
     */
    PUBLISHED("published", LogCategory.TARGET, LogCategory.RESPONSE),

    /**
     * Log related to a 'acknowledged' metric event.
     */
    ACKNOWLEDGED("acknowledged", LogCategory.SOURCE, LogCategory.TARGET, LogCategory.RESPONSE),

    /**
     * Log related to a 'throttled' metric event.
     */
    THROTTLED("throttled", LogCategory.SOURCE),

    /**
     * Log that is not related to any metric event.
     */
    OTHER("other", LogCategory.SOURCE, LogCategory.TARGET, LogCategory.RESPONSE, LogCategory.CONNECTION);

    private final String type;
    private final List<LogCategory> possibleCategories;

    LogType(final String theType, final LogCategory... possibleCateogries) {
        this.type = theType;
        this.possibleCategories = Arrays.asList(possibleCateogries);
    }

    /**
     * @return the label which can be used in a JSON representation.
     */
    public String getType() {
        return type;
    }

    /**
     * @param type type of the LogType.
     * @return the LogType matching the given type
     */
    public static Optional<LogType> forType(final CharSequence type) {
        checkNotNull(type, "Type");
        return Arrays.stream(values())
                .filter(c -> c.type.contentEquals(type))
                .findFirst();
    }

    /**
     * Check if this logging type is supported in the given {@code logCategory}.
     * @param logCategory the category that might be supported.
     * @return {@code true} if the type is supported for the given log category.
     */
    public boolean supportsCategory(final LogCategory logCategory) {
        return possibleCategories.contains(logCategory);
    }

    @Override
    public String toString() {
        return type;
    }

}
