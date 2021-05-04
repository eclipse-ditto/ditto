/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

/**
 * Kleene's strong logic of indeterminacy: extension of Boolean logic with UNKNOWN.
 */
enum Trilean {
    TRUE,
    FALSE,
    UNKNOWN;

    /**
     * Convert this value into a Boolean with a fallback value.
     *
     * @param fallback fallback if the Boolean value of this object is not known.
     * @return the Boolean value.
     */
    boolean orElse(final boolean fallback) {
        switch (this) {
            case TRUE:
                return true;
            case FALSE:
                return false;
            default:
                return fallback;
        }
    }

    /**
     * Lift a Boolean into a trilean.
     *
     * @param bool the Boolean.
     * @return the corresponding trilean.
     */
    static Trilean lift(final boolean bool) {
        return bool ? Trilean.TRUE : Trilean.FALSE;
    }

    /**
     * Negate a Kleene trilean.
     *
     * @param trilean the trilean.
     * @return its negation.
     */
    static Trilean not(final Trilean trilean) {
        switch (trilean) {
            case TRUE:
                return FALSE;
            case FALSE:
                return TRUE;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Compute the conjunction of 2 Kleene trilean.
     *
     * @param t1 the first trilean.
     * @param t2 the second trilean.
     * @return their conjunction.
     */
    static Trilean or(final Trilean t1, final Trilean t2) {
        switch (t1) {
            case TRUE:
                return TRUE;
            case FALSE:
                return t2;
            default:
                return t2 == TRUE ? TRUE : UNKNOWN;
        }
    }

    /**
     * Compute the disjunction of 2 Kleene trilean.
     *
     * @param t1 the first trilean.
     * @param t2 the second trilean.
     * @return their disjunction.
     */
    static Trilean and(final Trilean t1, final Trilean t2) {
        switch (t1) {
            case TRUE:
                return t2;
            case FALSE:
                return FALSE;
            default:
                return t2 == FALSE ? FALSE : UNKNOWN;
        }
    }
}
