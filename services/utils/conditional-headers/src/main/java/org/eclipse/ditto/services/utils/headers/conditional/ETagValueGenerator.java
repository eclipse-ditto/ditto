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
package org.eclipse.ditto.services.utils.headers.conditional;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.Entity;
import org.eclipse.ditto.model.base.entity.Revision;

import akka.http.javadsl.model.headers.EntityTag;

/**
 * Responsible for creating the value for the ETag Header.
 */
public final class ETagValueGenerator {

    private ETagValueGenerator() {}

    /**
     * Generates a value for the ETag Header for the given object.
     * For Classes that extends {@link Entity} the revision will be the etag.
     * For all other objects the hashcode of the object will be used.
     *
     * @param object The object you want to get an ETag value for.
     * @return An optional of the generated ETag value. If no value could be generated the optional is empty.
     */
    public static Optional<CharSequence> generate(@Nullable Object object) {
        if (object == null) {
            return Optional.empty();
        }

        if (object instanceof Entity) {
            return generateForTopLevelEntity((Entity<? extends Revision>) object);
        } else {
            return generateForSubEntity(object);
        }
    }

    private static Optional<CharSequence> generateForTopLevelEntity(final Entity<? extends Revision> topLevelEntity) {
        if (topLevelEntity.isDeleted()) {
            return Optional.empty();
        }

        return topLevelEntity.getRevision()
                .map(Revision::toString)
                .map(ETagValueGenerator::toETagValue);
    }

    private static Optional<CharSequence> generateForSubEntity(final Object object) {
        return Optional.of(Integer.toString(object.hashCode()))
                .map(ETagValueGenerator::toETagValue);
    }

    private static CharSequence toETagValue(String value) {
        return EntityTag.create(value, false).tag();
    }
}
