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
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;

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

    /*
     * Lazily memoized JSON representation of {@link #value} for headers whose serialization type is a
     * JsonObject / JsonArray. Parsing a header value is a pure function of the (immutable) value string, so this
     * cache is an idempotent derived value: the field is intentionally non-final and unsynchronized (benign data
     * race, same pattern as {@link String#hashCode()}) and does not affect the observable immutability of Header.
     * Only populated on demand via {@link #getParsedValue()} for headers that actually hold JSON.
     */
    @Nullable
    private JsonValue parsedValue;

    /*
     * Lazily memoized value derived from {@link #parsedValue}, for header keys whose parsed JSON is repeatedly
     * converted into the same domain object (e.g. "ditto-read-subjects" into a Set of AuthorizationSubject).
     * Same benign-data-race rationale as {@link #parsedValue}: the derivation is a pure function of the immutable
     * value string, so a race can only recompute an equal result. See {@link #getDerivedValue(Function)} for the
     * two invariants callers must uphold.
     */
    @Nullable
    private Object derivedValue;

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

    /**
     * Returns the header value parsed as a {@link JsonValue}, memoizing the result for subsequent calls.
     * Must only be called for headers whose value is a JSON object or array (i.e. whose serialization type is not a
     * plain {@code CharSequence}); calling it for a non-JSON value fails the same way the previous per-access parsing
     * did.
     *
     * @return the parsed (and cached) JSON representation of the header value.
     */
    JsonValue getParsedValue() {
        JsonValue result = parsedValue;
        if (null == result) {
            result = JsonFactory.readFrom(value);
            parsedValue = result;
        }
        return result;
    }

    /**
     * Returns a value derived from {@link #getParsedValue()} by the given derivation, memoizing the result for
     * subsequent calls. Intended for header keys whose parsed JSON is converted into the same domain object on every
     * access on a hot path.
     * <p>
     * Callers must uphold two invariants:
     * <ul>
     * <li>The derivation must return a non-{@code null}, deeply immutable value whose fields are all {@code final}
     * (e.g. the result of {@link java.util.Set#copyOf}). The memo field is deliberately non-volatile, so only
     * final-field freeze semantics (JLS 17.5) guarantee that another thread observing the memo cannot see a
     * partially constructed object. A {@code Collections.unmodifiableSet(new HashSet<>(…))} would <em>not</em> be
     * safe here.</li>
     * <li>For any given header key, exactly one derivation function must ever be used. The memo slot is untyped, so
     * mixing derivations of different result types for the same key would cause a {@link ClassCastException} at the
     * call site.</li>
     * </ul>
     * Like {@link #parsedValue}, the memo does not participate in {@link #equals(Object)} / {@link #hashCode()} and
     * does not affect the observable immutability of Header.
     *
     * @param derivation the pure function deriving the value from the parsed JSON representation.
     * @param <T> the type of the derived value.
     * @return the derived (and cached) value.
     */
    @SuppressWarnings("unchecked")
    <T> T getDerivedValue(final Function<? super JsonValue, ? extends T> derivation) {
        Object result = derivedValue;
        if (null == result) {
            result = derivation.apply(getParsedValue());
            derivedValue = result;
        }
        return (T) result;
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
