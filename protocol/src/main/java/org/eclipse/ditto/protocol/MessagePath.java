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

import java.util.Optional;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.messages.model.MessageDirection;

/**
 * Represent the {@code path} field of Ditto protocol messages.
 */
public interface MessagePath extends JsonPointer {

    /**
     * Retrieve the feature ID if this message path has a feature ID.
     *
     * @return feature ID if it exists or an empty optional otherwise.
     */
    Optional<String> getFeatureId();

    /**
     * Retrieve the direction if this message path has an inbox or outbox specification.
     *
     * @return direction based on this message path.
     */
    Optional<MessageDirection> getDirection();

    static Optional<MessageDirection> jsonKeyToDirection(final JsonKey jsonKey) {
        switch (jsonKey.toString()) {
            case "inbox":
                return Optional.of(MessageDirection.TO);
            case "outbox":
                return Optional.of(MessageDirection.FROM);
            default:
                return Optional.empty();
        }
    }

    static JsonKey directionToJsonKey(final MessageDirection direction) {
        return JsonKey.of(direction == MessageDirection.TO ? "inbox" : "outbox");
    }

}
