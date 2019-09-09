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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Determines an entity for eTag generation based on a command and Thing.
 *
 * @param <C> The type of the handled command.
 * @param <E> The type of the addressed entity.
 */
public interface ETagEntityProvider<C extends Command, E> {

    /**
     * Determines the value based on which an eTag will be generated.
     *
     * @param command the thing command.
     * @param thing The thing, may be {@code null}.
     * @return An optional of the eTag header value. Optional can be empty if no eTag header should be added.
     */
    Optional<E> determineETagEntity(final C command, @Nullable final Thing thing);

    default WithDittoHeaders appendETagHeaderIfProvided(final C command,
            final WithDittoHeaders withDittoHeaders, @Nullable final Thing thing) {

        final Optional<E> eTagEntityOpt = determineETagEntity(command, thing);
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
