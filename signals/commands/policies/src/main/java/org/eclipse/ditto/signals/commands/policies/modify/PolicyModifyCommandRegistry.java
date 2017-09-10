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
package org.eclipse.ditto.signals.commands.policies.modify;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link PolicyModifyCommand}s.
 */
@Immutable
public final class PolicyModifyCommandRegistry extends AbstractCommandRegistry<PolicyModifyCommand> {

    private PolicyModifyCommandRegistry(final Map<String, JsonParsable<PolicyModifyCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code PolicyModifyCommandRegistry}.
     *
     * @return the command registry.
     */
    public static PolicyModifyCommandRegistry newInstance() {
        final Map<String, JsonParsable<PolicyModifyCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(CreatePolicy.TYPE, CreatePolicy::fromJson);
        parseStrategies.put(ModifyPolicy.TYPE, ModifyPolicy::fromJson);
        parseStrategies.put(DeletePolicy.TYPE, DeletePolicy::fromJson);

        parseStrategies.put(ModifyPolicyEntries.TYPE, ModifyPolicyEntries::fromJson);
        parseStrategies.put(ModifyPolicyEntry.TYPE, ModifyPolicyEntry::fromJson);
        parseStrategies.put(DeletePolicyEntry.TYPE, DeletePolicyEntry::fromJson);

        parseStrategies.put(ModifySubjects.TYPE, ModifySubjects::fromJson);
        parseStrategies.put(ModifySubject.TYPE, ModifySubject::fromJson);
        parseStrategies.put(DeleteSubject.TYPE, DeleteSubject::fromJson);

        parseStrategies.put(ModifyResources.TYPE, ModifyResources::fromJson);
        parseStrategies.put(ModifyResource.TYPE, ModifyResource::fromJson);
        parseStrategies.put(DeleteResource.TYPE, DeleteResource::fromJson);

        return new PolicyModifyCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return PolicyCommand.TYPE_PREFIX;
    }

}
