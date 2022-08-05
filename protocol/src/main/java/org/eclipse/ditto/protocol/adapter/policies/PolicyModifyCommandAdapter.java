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
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.ModifyCommandAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link PolicyModifyCommand} to and from an {@link Adaptable}.
 */
final class PolicyModifyCommandAdapter extends AbstractPolicyAdapter<PolicyModifyCommand<?>>
        implements ModifyCommandAdapter<PolicyModifyCommand<?>> {

    private PolicyModifyCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getPolicyModifyCommandMappingStrategies(),
                SignalMapperFactory.newPolicyModifySignalMapper(), headerTranslator);
    }

    /**
     * Returns a new PolicyModifyCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyModifyCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyModifyCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        return topicPath.getCriterion().getName();
    }
}
