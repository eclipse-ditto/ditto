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
package org.eclipse.ditto.protocol.adapter.policies;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link PolicyAnnouncement} to and from an {@link org.eclipse.ditto.protocol.Adaptable}.
 */
final class PolicyAnnouncementAdapter extends AbstractPolicyAdapter<PolicyAnnouncement<?>> {

    private PolicyAnnouncementAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getPolicyAnnouncementMappingStrategies(),
                SignalMapperFactory.newPolicyAnnouncementSignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new PolicyAnnouncementAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyAnnouncementAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyAnnouncementAdapter(requireNonNull(headerTranslator));
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return Collections.singleton(TopicPath.Criterion.ANNOUNCEMENTS);
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
