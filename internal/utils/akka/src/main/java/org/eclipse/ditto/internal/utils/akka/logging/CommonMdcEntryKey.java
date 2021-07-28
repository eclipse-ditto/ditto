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

/**
 * An enumeration of commonly known MDC entry keys.
 *
 * @since 1.4.0
 */
public enum CommonMdcEntryKey implements CharSequence {

    CORRELATION_ID("x-correlation-id"),
    DITTO_LOG_TAG("ditto-log-tag");

    private final String key;

    CommonMdcEntryKey(final String key) {
        this.key = key;
    }

    @Override
    public int length() {
        return key.length();
    }

    @Override
    public char charAt(final int index) {
        return key.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return key.subSequence(start, end);
    }

    @Override
    public String toString() {
        return key;
    }

}
