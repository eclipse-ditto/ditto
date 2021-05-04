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
package org.eclipse.ditto.internal.utils.akka.logging;

import javax.annotation.Nullable;

import org.slf4j.Logger;

/**
 * This interface defines the means to put and remove entries to or from the MDC of a logger.
 *
 * @param <L> the type of the logger that implements this interface.
 * @since 1.4.0
 */
public interface WithMdcEntry<L extends Logger> {

    /**
     * Puts the specified diagnostic context value as identified by the specified key to this logger's MDC.
     * <p>
     * Providing {@code null} as value has the same effect as calling {@link #removeMdcEntry(CharSequence)} with the
     * specified key.
     * </p>
     *
     * @param key the key which identifies the diagnostic value.
     * @param value the diagnostic value which is identified by {@code key}.
     * @return this or a new logger instance for method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    L withMdcEntry(CharSequence key, @Nullable CharSequence value);

    /**
     * Puts the specified diagnostic context values as identified by the specified keys to this logger's MDC.
     * <p>
     * Providing {@code null} for any value has the same effect as calling {@link #removeMdcEntry(CharSequence)} with
     * its associated key.
     * </p>
     *
     * @param k1 the first key which identifies the diagnostic value {@code v1}.
     * @param v1 the first diagnostic value which is identified by {@code k1}.
     * @param k2 the second key which identifies the diagnostic value {@code v2}.
     * @param v2 the second diagnostic value which is identified by {@code k2}.
     * @return this or a new logger instance for method chaining.
     * @throws NullPointerException if {@code k1} or {@code k2} is {@code null}.
     * @throws IllegalArgumentException if {@code k1} or {@code k2} is empty.
     */
    L withMdcEntries(CharSequence k1, @Nullable CharSequence v1, CharSequence k2, @Nullable CharSequence v2);

    /**
     * Puts the specified diagnostic context values as identified by the specified keys to this logger's MDC.
     * <p>
     * Providing {@code null} for any value has the same effect as calling {@link #removeMdcEntry(CharSequence)} with
     * its associated key.
     * </p>
     *
     * @param k1 the first key which identifies the diagnostic value {@code v1}.
     * @param v1 the first diagnostic value which is identified by {@code k1}.
     * @param k2 the second key which identifies the diagnostic value {@code v2}.
     * @param v2 the second diagnostic value which is identified by {@code k2}.
     * @param k3 the third key which identifies the diagnostic value {@code v3}.
     * @param v3 the third diagnostic value which is identified by {@code k3}.
     * @return this or a new logger instance for method chaining.
     * @throws NullPointerException if {@code k1}, {@code k2} or {@code k3} is {@code null}.
     * @throws IllegalArgumentException if {@code k1}, {@code k2} or {@code k3} is empty.
     */
    L withMdcEntries(CharSequence k1, @Nullable CharSequence v1, CharSequence k2, @Nullable CharSequence v2,
            CharSequence k3, @Nullable CharSequence v3);

    /**
     * Puts the given entry (entries) to the MDC of this logger.
     *
     * @return this or a new logger instance for method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    L withMdcEntry(MdcEntry mdcEntry, MdcEntry... furtherMdcEntries);

    /**
     * Removes the diagnostic context value identified by the specified key.
     * This method does nothing if there is no previous value associated with the specified key.
     *
     * @param key the key of the value to be removed.
     * @return this or a new logger instance for method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    L removeMdcEntry(CharSequence key);

}
