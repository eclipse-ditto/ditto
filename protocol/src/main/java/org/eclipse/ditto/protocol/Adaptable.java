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

import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;

/**
 * An {@code Adaptable} can be mapped to and from a {@link org.eclipse.ditto.base.model.signals.commands.Command}, {@link
 * org.eclipse.ditto.base.model.signals.commands.CommandResponse} or {@link org.eclipse.ditto.base.model.signals.events.Event}.
 */
public interface Adaptable extends DittoHeadersSettable<Adaptable> {

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
     * Indicates whether this Adaptable contains a header with the specified key.
     *
     * @param key the key to be looked up.
     * @return {@code true} if this Adaptable contains a header with key {@code key}.
     */
    boolean containsHeaderForKey(CharSequence key);

}
