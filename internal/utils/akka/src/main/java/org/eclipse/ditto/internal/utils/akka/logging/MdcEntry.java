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
import javax.annotation.concurrent.Immutable;

/**
 * Represents the association of a diagnostic context value with its identifying key.
 * It is intended to be put to a logger's
 * <a href="http://logback.qos.ch/manual/mdc.html">Mapped Diagnostic Context (MDC)</a>.
 *
 * @since 1.4.0
 */
@Immutable
public interface MdcEntry {

    /**
     * Returns an instance of MdcEntry with the specified key and value.
     *
     * @param key the key which identifies the diagnostic value.
     * @param value the diagnostic value which is identified by {@code key}.
     * @return the instance.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    static MdcEntry of(final CharSequence key, @Nullable final CharSequence value) {
        return LoggingFactory.newMdcEntry(key, value);
    }

    /**
     * Returns the key which identifies the particular diagnostic context value of this entry.
     *
     * @return the key.
     */
    String getKey();

    /**
     * Returns the diagnostic context value.
     *
     * @return the value.
     */
    @Nullable
    String getValueOrNull();

}
