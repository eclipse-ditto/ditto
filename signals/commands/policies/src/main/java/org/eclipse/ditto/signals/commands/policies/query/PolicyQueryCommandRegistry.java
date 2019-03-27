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
package org.eclipse.ditto.signals.commands.policies.query;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link PolicyQueryCommand}s.
 */
@Immutable
public final class PolicyQueryCommandRegistry extends AbstractCommandRegistry<PolicyQueryCommand> {

    private PolicyQueryCommandRegistry(final Map<String, JsonParsable<PolicyQueryCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code PolicyQueryCommandRegistry}.
     *
     * @return the command registry.
     */
    public static PolicyQueryCommandRegistry newInstance() {
        final Map<String, JsonParsable<PolicyQueryCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(RetrievePolicy.TYPE, RetrievePolicy::fromJson);

        parseStrategies.put(RetrievePolicyEntries.TYPE, RetrievePolicyEntries::fromJson);
        parseStrategies.put(RetrievePolicyEntry.TYPE, RetrievePolicyEntry::fromJson);

        parseStrategies.put(RetrieveSubjects.TYPE, RetrieveSubjects::fromJson);
        parseStrategies.put(RetrieveSubject.TYPE, RetrieveSubject::fromJson);

        parseStrategies.put(RetrieveResources.TYPE, RetrieveResources::fromJson);
        parseStrategies.put(RetrieveResource.TYPE, RetrieveResource::fromJson);

        return new PolicyQueryCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return PolicyCommand.TYPE_PREFIX;
    }

}
