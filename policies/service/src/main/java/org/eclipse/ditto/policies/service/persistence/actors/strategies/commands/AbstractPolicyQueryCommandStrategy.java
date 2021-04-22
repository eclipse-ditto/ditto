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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;

/**
 * Abstract base class for all policy query commands.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.policies.api.commands.sudo.SudoCommand} are handled which are no
 * PolicyQueryCommands.
 */
abstract class AbstractPolicyQueryCommandStrategy<C extends Command<C>>
        extends AbstractPolicyCommandStrategy<C, PolicyEvent<?>> {

    AbstractPolicyQueryCommandStrategy(final Class<C> theMatchingClass, final PolicyConfig policyConfig) {
        super(theMatchingClass, policyConfig);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final C command, @Nullable final Policy policy) {
        return nextEntityTag(command, policy);
    }
}
