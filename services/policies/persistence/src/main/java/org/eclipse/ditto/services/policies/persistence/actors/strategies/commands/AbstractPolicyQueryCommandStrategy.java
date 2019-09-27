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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Abstract base class for
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand} are handled which are no
 * PolicyQueryCommands.
 */
abstract class AbstractPolicyQueryCommandStrategy<C extends Command<C>> extends AbstractPolicyCommandStrategy<C> {

    AbstractPolicyQueryCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public Optional<?> previousETagEntity(final C command, @Nullable final Policy policy) {
        return nextETagEntity(command, policy);
    }
}
