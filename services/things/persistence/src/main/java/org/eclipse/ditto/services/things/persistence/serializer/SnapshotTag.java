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
package org.eclipse.ditto.services.things.persistence.serializer;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An enumeration of snapshot tags.
 */
public enum SnapshotTag {

    /**
     * This tag signals that the associated snapshot must not be deleted.
     */
    PROTECTED,

    /**
     * This tag signals that the associated snapshot is not protected and thus may be subject for deletion.
     */
    UNPROTECTED;

    /**
     * Returns the SnapshotTag with the specified name. This is basically the same as {@link String#valueOf(Object)}.
     * The main difference is that this method does not throw an exception if no value can be found but returns an
     * empty Optional instead.
     *
     * @param snapshotTagName the name of the SnapshotTag to be returned.
     * @return the snapshot tag with {@code snapshotTagName} as its name or an empty Optional.
     */
    @Nonnull
    public static Optional<SnapshotTag> getValueFor(@Nullable final CharSequence snapshotTagName) {
        for (final SnapshotTag snapshotTag : values()) {
            if (Objects.equals(snapshotTag.name(), String.valueOf(snapshotTagName))) {
                return Optional.of(snapshotTag);
            }
        }
        return Optional.empty();
    }

}
