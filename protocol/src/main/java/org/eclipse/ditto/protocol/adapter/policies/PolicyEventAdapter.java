/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.adapter.EventAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link PolicyEvent} to and from an {@link org.eclipse.ditto.protocol.Adaptable}.
 */
final class PolicyEventAdapter extends AbstractPolicyAdapter<PolicyEvent<?>> implements EventAdapter<PolicyEvent<?>> {

    private PolicyEventAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getPolicyEventMappingStrategies(),
                SignalMapperFactory.newPolicyEventSignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new PolicyEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyEventAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyEventAdapter(requireNonNull(headerTranslator));
    }

    private static String getActionNameWithFirstLetterUpperCase(final TopicPath topicPath) {
        return topicPath.getAction()
                .map(TopicPath.Action::toString)
                .map(AbstractAdapter::upperCaseFirst)
                .orElseThrow(() -> new NullPointerException("TopicPath did not contain an Action!"));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String eventName = payloadPathMatcher.match(path) + getActionNameWithFirstLetterUpperCase(topicPath);
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + eventName;
    }

}
