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
package org.eclipse.ditto.model.base.headers;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Package internal representation of a header with its key in the original capitalization.
 *
 * @since 1.6.0
 */
@Immutable
final class Header implements CharSequence {

    private final String key;
    private final String value;

    private Header(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    static Header fromEntry(final Map.Entry<String, String> entry) {
        return new Header(entry.getKey(), entry.getValue());
    }

    String getKey() {
        return key;
    }

    String getValue() {
        return value;
    }

    Map.Entry<String, String> toEntry() {
        return new AbstractMap.SimpleEntry<>(key.toLowerCase(), value);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof Header) {
            final Header that = (Header) other;
            return Objects.equals(key, that.key) && Objects.equals(value, that.value);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(final int i) {
        return value.charAt(i);
    }

    @Override
    public Header subSequence(final int i, final int j) {
        return new Header(key, value.substring(i, j));
    }

    @Override
    public String toString() {
        return value;
    }
}
