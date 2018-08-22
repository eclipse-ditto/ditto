/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

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
}
