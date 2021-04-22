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

import static java.util.Objects.requireNonNull;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * An {@link AdaptableBuilder} for building instances of {@link ImmutableAdaptable}.
 */
@NotThreadSafe
final class ImmutableAdaptableBuilder implements AdaptableBuilder {

    private final TopicPath topicPath;

    private Payload payload;
    private DittoHeaders headers;

    private ImmutableAdaptableBuilder(final TopicPath topicPath) {
        this.topicPath = topicPath;
    }

    /**
     * Returns a new ImmutableAdaptableBuilder for the specified {@code topicPath}.
     *
     * @param topicPath the topic path.
     * @return the ImmutableAdaptableBuilder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableAdaptableBuilder of(final TopicPath topicPath) {
        requireNonNull(topicPath, "topicPath");

        return new ImmutableAdaptableBuilder(topicPath);
    }

    @Override
    public AdaptableBuilder withPayload(final Payload payload) {
        this.payload = payload;
        return this;
    }

    @Override
    public AdaptableBuilder withHeaders(final DittoHeaders headers) {
        this.headers = headers;
        return this;
    }

    @Override
    public Adaptable build() {
        return ImmutableAdaptable.of(topicPath, payload, headers);
    }

}
