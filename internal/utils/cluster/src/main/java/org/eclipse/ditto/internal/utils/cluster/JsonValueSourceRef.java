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
package org.eclipse.ditto.internal.utils.cluster;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.ConditionChecker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import akka.NotUsed;
import akka.stream.SourceRef;
import akka.stream.javadsl.Source;

/**
 * Wraps a {@link SourceRef} of {@link JsonValue}s. The purpose of this class is to make working with SourceRef type
 * safe. Rationale: Sending a plain SourceRef through the cluster works but it discards the type information, i.e. the
 * receiver does not know the actual type of the SourceRef's elements.
 * @see <a href="https://doc.akka.io/docs/akka/2.6/stream/stream-refs.html#serialization-of-sourceref-and-sinkref">Akka
 * documentation "Serialization of SourceRef and SinkRef"</a>
 */
@Immutable
public final class JsonValueSourceRef implements AkkaJacksonCborSerializable {

    private final SourceRef<JsonValue> sourceRef;

    private JsonValueSourceRef(final SourceRef<JsonValue> sourceRef) {
        this.sourceRef = sourceRef;
    }

    /**
     * Returns an instance of {@code JsonValueSourceRef} that wraps the specified SourceRef.
     *
     * @param sourceRef the SourceRef to be wrapped.
     * @return the instance.
     * @throws NullPointerException if {@code sourceRef} is {@code null}.
     */
    @JsonCreator
    public static JsonValueSourceRef of(final SourceRef<JsonValue> sourceRef) {
        return new JsonValueSourceRef(ConditionChecker.checkNotNull(sourceRef, "sourceRef"));
    }

    /**
     * Returns the wrapped SourceRef.
     *
     * @return the SourceRef.
     */
    public SourceRef<JsonValue> getSourceRef() {
        return sourceRef;
    }

    /**
     * Returns the Source underlying to the wrapped SourceRef.
     *
     * @return the Source.
     */
    @JsonIgnore
    public Source<JsonValue, NotUsed> getSource() {
        return sourceRef.getSource();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (JsonValueSourceRef) o;
        return sourceRef.equals(that.sourceRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceRef);
    }

}
