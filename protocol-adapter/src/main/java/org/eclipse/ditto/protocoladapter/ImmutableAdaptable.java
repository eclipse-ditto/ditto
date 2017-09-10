/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

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
    public static Adaptable of(final TopicPath topicPath, final Payload payload, final DittoHeaders headers) {
        requireNonNull(topicPath, "topic path");
        requireNonNull(payload, "payload");

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
    public Optional<DittoHeaders> getHeaders() {
        return Optional.ofNullable(headers);
    }

    @Override
    public boolean containsHeaderForKey(final CharSequence key) {
        return null != key && null != headers && headers.containsKey(key.toString());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAdaptable that = (ImmutableAdaptable) o;
        return Objects.equals(topicPath, that.topicPath) && Objects.equals(payload, that.payload)
                && Objects.equals(headers, that.headers);
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

}
