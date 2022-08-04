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
package org.eclipse.ditto.protocol.adapter.policies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.QueryCommandAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link PolicyQueryCommand} to and from an {@link Adaptable}.
 */
final class PolicyQueryCommandAdapter extends AbstractPolicyAdapter<PolicyQueryCommand<?>>
        implements QueryCommandAdapter<PolicyQueryCommand<?>> {

    private PolicyQueryCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getPolicyQueryCommandMappingStrategies(),
                SignalMapperFactory.newPolicyQuerySignalMapper(), headerTranslator);
    }

    /**
     * Returns a new PolicyQueryCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyQueryCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyQueryCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        return topicPath.getCriterion().getName();
    }
}
