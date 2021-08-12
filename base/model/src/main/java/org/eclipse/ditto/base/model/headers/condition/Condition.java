/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.condition;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Parses a string as condition and provides information if the request should be applied or not.
 *
 * @since 2.1.0
 */
@Immutable
public final class Condition {

    private final String rqlCondition;

    private Condition(final String condition) {
        this.rqlCondition = condition;
    }

    /**
     * Creates a Condition based on the given string.
     *
     * @param condition the rql condition.
     * @return the new instance
     * @throws NullPointerException if {@code condition} was {@code null}.
     */
    public static Condition of(final String condition) {
        checkNotNull(condition, "condition");
        checkNotEmpty(condition, "condition");
        // TODO validate condition

        return new Condition(condition);
    }

    public String getRqlCondition() {
        return rqlCondition;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Condition condition1 = (Condition) o;
        return rqlCondition.equals(condition1.rqlCondition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rqlCondition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "condition=" + rqlCondition +
                "]";
    }

}
