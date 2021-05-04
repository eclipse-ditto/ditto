/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Immutable implementation of {@link Adaptable}.
 */
@Immutable
final class ImmutableAdaptable implements Adaptable {

    private final TopicPath topicPath;
    private final Payload payload;
    private final DittoHeaders headers;

    private ImmutableAdaptable(final TopicPath topicPath, final Payload payload, final DittoHeaders headers) {
        this.topicPath = topicPath;
        this.payload = payload;
        this.headers = headers;
    }

    /**
     * Returns a new {@code Adaptable} for the given {@code topicPath}, {@code payload} and {@code headers}.
     *
     * @param topicPath the topicPath.
     * @param payload the payload.
     * @param headers the headers.
     * @return the Adaptable.
     * @throws NullPointerException if {@code topicPath} or {@code payload} is {@code null}.
     */
    public static ImmutableAdaptable of(final TopicPath topicPath, final Payload payload,
            @Nullable final DittoHeaders headers) {

        checkNotNull(topicPath, "topicPath");
        checkNotNull(payload, "payload");

        return new ImmutableAdaptable(topicPath, payload, headers);
    }

    @Override
    public TopicPath getTopicPath() {
        return topicPath;
    }

    @Override
    public Payload getPayload() {
        return payload;
    }

    @Override
    public boolean containsHeaderForKey(final CharSequence key) {
        return null != key && null != headers && headers.containsKey(key.toString());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAdaptable that = (ImmutableAdaptable) o;
        return Objects.equals(topicPath, that.topicPath) &&
                Objects.equals(payload, that.payload) &&
                Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicPath, payload, headers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "topicPath=" + topicPath + ", payload=" + payload + ", headers="
                + headers + ']';
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return null != headers ? headers : DittoHeaders.empty();
    }

    @Override
    public Adaptable setDittoHeaders(@Nonnull final DittoHeaders dittoHeaders) {
        return new ImmutableAdaptable(topicPath, payload, dittoHeaders);
    }

}
