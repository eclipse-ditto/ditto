/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.etags;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.signals.commands.Command;

/**
 * Determines an entity for eTag generation based on a command and Thing.
 *
 * @param <C> the type of the handled command
 * @param <S> the type of the addressed entity
 */
public interface ETagEntityProvider<C extends Command, S> {

    /**
     * Determines the value based on which the matching eTag will be determined before a command.
     *
     * @param command the command.
     * @param previousEntity The entity, may be {@code null}.
     * @return An optional of an entity against which the etag header should be matched.
     */
    Optional<EntityTag> previousEntityTag(C command, @Nullable S previousEntity);

    /**
     * Determines the value based on which an eTag will be generated after a modify command.
     *
     * @param command the command.
     * @param newEntity The entity, may be {@code null}.
     * @return An optional of the entity from which the etag header should be generated.
     */
    Optional<EntityTag> nextEntityTag(C command, @Nullable S newEntity);


    /**
     * Append an ETag header if given by the entity.
     *
     * @param command the command for whose response the ETag header is to be appended.
     * @param withDittoHeaders the response for whom the ETag header is to be appended.
     * @param entity the thing with the next revision number, or null if it is being deleted.
     * @return response with ETag header appended.
     */
    default DittoHeadersSettable<?> appendETagHeaderIfProvided(final C command,
            final DittoHeadersSettable<?> withDittoHeaders, @Nullable final S entity) {

        final Optional<EntityTag> entityTagOpt = nextEntityTag(command, entity);
        if (entityTagOpt.isPresent()) {
            final EntityTag entityTag = entityTagOpt.get();
            final DittoHeaders newDittoHeaders = withDittoHeaders.getDittoHeaders().toBuilder()
                    .eTag(entityTag)
                    .build();
            return withDittoHeaders.setDittoHeaders(newDittoHeaders);
        }
        return withDittoHeaders;
    }
}
