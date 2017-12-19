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
package org.eclipse.ditto.services.models.streaming;

import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents the ID of an entity with a revision of the entity.
 */
public interface EntityIdWithRevision extends Jsonifiable, StreamingMessage {

    /**
     * Returns the ID of the modified entity.
     *
     * @return the ID of the modified entity.
     */
    String getId();

    /**
     * Returns the revision of the modified entity.
     *
     * @return the revision of the modified entity.
     */
    long getRevision();

    /**
     * Returns this tag as an identifier in the format {@code <id>:<revision>}.
     *
     * @return the tag as an identifier
     */
    default String asIdentifierString() {
        return getId() + ":" + getRevision();
    }
}
