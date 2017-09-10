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
package org.eclipse.ditto.signals.commands.policies;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link PolicyCommand}s.
 */
@Immutable
public final class PolicyCommandRegistry extends AbstractCommandRegistry<PolicyCommand> {

    private PolicyCommandRegistry(final Map<String, JsonParsable<PolicyCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code PolicyCommandRegistry}.
     *
     * @return the command registry.
     */
    public static PolicyCommandRegistry newInstance() {
        final Map<String, JsonParsable<PolicyCommand>> parseStrategies = new HashMap<>();

        final PolicyModifyCommandRegistry policyModifyCommandRegistry = PolicyModifyCommandRegistry.newInstance();
        policyModifyCommandRegistry.getTypes()
                .forEach(type -> parseStrategies.put(type, policyModifyCommandRegistry::parse));

        final PolicyQueryCommandRegistry policyQueryCommandRegistry = PolicyQueryCommandRegistry.newInstance();
        policyQueryCommandRegistry.getTypes()
                .forEach(type -> parseStrategies.put(type, policyQueryCommandRegistry::parse));

        return new PolicyCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return PolicyCommand.TYPE_PREFIX;
    }

}
