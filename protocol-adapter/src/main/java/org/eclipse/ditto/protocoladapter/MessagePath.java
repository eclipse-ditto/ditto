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

import java.util.Optional;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.messages.MessageDirection;

/**
 * Represent the {@code path} field of Ditto protocol messages.
 */
public final class MessagePath {

    private static final JsonKey FEATURES = JsonKey.of("features");
    private static final JsonKey INBOX = JsonKey.of("inbox");
    private static final JsonKey OUTBOX = JsonKey.of("outbox");

    private final JsonPointer jsonPointer;

    /**
     * Interpret a JSON pointer as message path.
     *
     * @param jsonPointer the JSON pointer to interpret.
     */
    public MessagePath(final JsonPointer jsonPointer) {
        this.jsonPointer = jsonPointer;
    }

    /**
     * Retrieve the feature ID if this message path has a feature ID.
     *
     * @return feature ID if it exists or an empty optional otherwise.
     */
    public Optional<String> getFeatureId() {
        return jsonPointer.getRoot()
                .filter(FEATURES::equals)
                .map(features -> jsonPointer.nextLevel())
                .flatMap(JsonPointer::getRoot)
                .map(JsonKey::toString);
    }

    /**
     * Retrieve the direction if this message path has an inbox or outbox specification.
     *
     * @return direction based on this message path.
     */
    public Optional<MessageDirection> getDirection() {
        return jsonPointer.getRoot()
                .flatMap(MessagePath::jsonKeyToDirection)
                .map(Optional::of)
                .orElseGet(() -> jsonPointer.getRoot()
                        .filter(FEATURES::equals)
                        .flatMap(features -> jsonPointer.get(2))
                        .flatMap(MessagePath::jsonKeyToDirection));
    }

    private static Optional<MessageDirection> jsonKeyToDirection(final JsonKey jsonKey) {
        return INBOX.equals(jsonKey)
                ? Optional.of(MessageDirection.TO)
                : OUTBOX.equals(jsonKey)
                ? Optional.of(MessageDirection.FROM)
                : Optional.empty();
    }
}
