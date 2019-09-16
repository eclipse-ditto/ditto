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
package org.eclipse.ditto.services.utils.persistentactors.etags;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Determines an entity for eTag generation based on a command and Thing.
 *
 * @param <C> The type of the handled command.
 * @param <S> The type of the addressed entity.
 */
public interface ETagEntityProvider<C extends Command, S> {

    /**
     * Determines the value based on which the matching eTag will be determined before a command.
     *
     * @param command the command.
     * @param previousEntity The entity, may be {@code null}.
     * @return An optional of an entity against which the etag header should be matched.
     */
    Optional<?> previousETagEntity(final C command, @Nullable final S previousEntity);

    /**
     * Determines the value based on which an eTag will be generated after a modify command.
     *
     * @param command the command.
     * @param newEntity The entity, may be {@code null}.
     * @return An optional of the entity from which the etag header should be generated.
     */
    Optional<?> nextETagEntity(final C command, @Nullable final S newEntity);

    /**
     * Append an ETag header if given by the entity.
     *
     * @param command the command for whose response the ETag header is to be appended.
     * @param withDittoHeaders the response for whom the ETag header is to be appended.
     * @param entity the thing with the next revision number, or null if it is being deleted.
     * @return response with ETag header appended.
     */
    default WithDittoHeaders appendETagHeaderIfProvided(final C command,
            final WithDittoHeaders withDittoHeaders, @Nullable final S entity) {

        final Optional<?> eTagEntityOpt = nextETagEntity(command, entity);
        if (eTagEntityOpt.isPresent()) {
            final Optional<EntityTag> entityTagOpt = EntityTag.fromEntity(eTagEntityOpt.get());
            if (entityTagOpt.isPresent()) {
                final EntityTag entityTag = entityTagOpt.get();
                final DittoHeaders newDittoHeaders = withDittoHeaders.getDittoHeaders().toBuilder()
                        .eTag(entityTag)
                        .build();
                return withDittoHeaders.setDittoHeaders(newDittoHeaders);
            }
        }
        return withDittoHeaders;
    }
}
