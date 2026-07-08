/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.api;

import java.util.Objects;

import org.apache.pekko.serialization.SerializerWithStringManifest;
import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Holds one published {@link Signal} together with a lazily-computed, memoized serialized form.
 * <p>
 * A single instance is shared by all the {@link PreSerializedPublishSignal} envelopes created for one published
 * signal (one envelope per subscriber destination). The first Artery {@code Encoder} thread that serializes any
 * of those envelopes triggers {@link #getOrCompute} which serializes the signal exactly once; every subsequent
 * destination reuses the cached bytes. This removes the per-destination re-serialization of the (identical)
 * signal payload while keeping the (single) serialization on the parallel encoder threads rather than the
 * single Publisher actor thread.
 * <p>
 * The memoization uses a single {@code volatile} reference to an immutable {@link Serialized} record; a race
 * between two encoder threads is benign (both compute identical bytes, last write wins).
 *
 * @since 3.9.4
 */
public final class SignalBytesHolder {

    private final Signal<?> signal;
    private volatile Serialized cached;

    /**
     * @param signal the signal whose serialized form is to be shared across fan-out destinations.
     */
    public SignalBytesHolder(final Signal<?> signal) {
        this.signal = signal;
    }

    /**
     * @return the wrapped signal (used for local, non-serialized delivery).
     */
    public Signal<?> getSignal() {
        return signal;
    }

    /**
     * Return the serialized form of the signal, computing and caching it on first access.
     *
     * @param innerSerializer the serializer to use for the signal payload (the CBOR Jsonifiable serializer).
     * @return the memoized manifest + bytes of the serialized signal.
     */
    public Serialized getOrCompute(final SerializerWithStringManifest innerSerializer) {
        Serialized local = cached;
        if (local == null) {
            // Benign race: two threads may both compute; the results are identical, last write wins.
            local = new Serialized(innerSerializer.manifest(signal), innerSerializer.toBinary(signal));
            cached = local;
        }
        return local;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final SignalBytesHolder that)) {
            return false;
        }
        return Objects.equals(signal, that.signal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signal);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[signal=" + signal + "]";
    }

    /**
     * Immutable memoized serialized form of a signal: its serializer string-manifest and the serialized bytes.
     *
     * @param manifest the string manifest to deserialize the bytes with.
     * @param bytes the serialized signal payload.
     */
    public record Serialized(String manifest, byte[] bytes) {}
}
