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
package org.eclipse.ditto.connectivity.service.util;

/**
 * An enumeration of well-known MDC value keys related to the connectivity functionality.
 *
 * @since 1.4.0
 */
public enum ConnectivityMdcEntryKey implements CharSequence {

    /**
     * Key of the connection ID context value.
     */
    CONNECTION_ID("connection-id"),

    /**
     * Key of the connection type context value.
     */
    CONNECTION_TYPE("connection-type");

    private final String key;

    ConnectivityMdcEntryKey(final String key) {
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
