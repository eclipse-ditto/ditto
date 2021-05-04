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
package org.eclipse.ditto.base.model.headers;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Package internal representation of a header with its key in the original capitalization.
 * The key is only for information. Object identity only takes value into account.
 * This is important because as cache keys of CachingSignalEnrichmentFacade, where header keys should be interpreted
 * case-insensitively.
 *
 * @since 2.0.0
 */
@Immutable
final class Header implements CharSequence {

    private final String key;
    private final String value;

    private Header(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    static Header of(final String key, final String value) {
        return new Header(key, value);
    }

    String getKey() {
        return key;
    }

    String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof CharSequence) {
            return Objects.equals(value, other.toString());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public char charAt(final int i) {
        return value.charAt(i);
    }

    @Override
    public Header subSequence(final int i, final int j) {
        return new Header(key, value.substring(i, j));
    }
}
