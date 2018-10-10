/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.protocoladapter;

import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

/**
 * An {@code Adaptable} can be mapped to and from a {@link org.eclipse.ditto.signals.commands.base.Command}, {@link
 * org.eclipse.ditto.signals.commands.base.CommandResponse} or {@link org.eclipse.ditto.signals.events.base.Event}.
 */
public interface Adaptable extends WithDittoHeaders<Adaptable> {

    /**
     * Returns a mutable builder to create immutable {@code Adaptable} instances for a given {@code topicPath}.
     *
     * @param topicPath the topic path.
     * @return the builder.
     * @throws NullPointerException if {@code topicPath} is {@code null}.
     */
    static AdaptableBuilder newBuilder(final TopicPath topicPath) {
        return ProtocolFactory.newAdaptableBuilder(topicPath);
    }

    /**
     * Returns the {@code TopicPath} of this {@code Adaptable}.
     *
     * @return the topic path.
     */
    TopicPath getTopicPath();

    /**
     * Returns the {@code Payload} of this {@code Adaptable}.
     *
     * @return the payload.
     */
    Payload getPayload();

    /**
     * Returns the {@code DittoHeaders} of this {@code Adaptable} if present.
     *
     * @return the optional headers.
     */
    Optional<DittoHeaders> getHeaders();

    /**
     * Indicates whether this Adaptable contains a header with the specified key.
     *
     * @param key the key to be looked up.
     * @return {@code true} if this Adaptable contains a header with key {@code key}.
     */
    boolean containsHeaderForKey(CharSequence key);

}
