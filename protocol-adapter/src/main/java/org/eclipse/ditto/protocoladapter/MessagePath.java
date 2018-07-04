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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.messages.MessageDirection;

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

}
