/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.policies;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownTopicPathException;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.notifications.policies.PolicyNotification;

/**
 * Adapter for mapping a {@link PolicyNotification} to and from an {@link org.eclipse.ditto.protocoladapter.Adaptable}.
 */
final class PolicyNotificationAdapter extends AbstractPolicyAdapter<PolicyNotification<?>> {

    private PolicyNotificationAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getPolicyNotificationMappingStrategies(),
                SignalMapperFactory.newPolicyNotificationSignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new PolicyNotificationAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyNotificationAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyNotificationAdapter(requireNonNull(headerTranslator));
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return Collections.singleton(TopicPath.Criterion.NOTIFICATIONS);
    }

    @Override
    public Set<TopicPath.Action> getActions() {
        return Collections.emptySet();
    }

    @Override
    public boolean isForResponses() {
        return false;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final String commandName =
                topicPath.getSubject().orElseThrow(() -> UnknownTopicPathException.newBuilder(topicPath).build());
        return topicPath.getGroup() + "." + getTypeCriterionAsString(topicPath) + ":" + commandName;
    }

}
