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
package org.eclipse.ditto.signals.commands.policies;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link PolicyCommand}s.
 */
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
