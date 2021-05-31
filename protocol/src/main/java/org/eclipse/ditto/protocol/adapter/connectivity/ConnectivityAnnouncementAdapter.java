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
package org.eclipse.ditto.protocol.adapter.connectivity;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link ConnectivityAnnouncement} to and from an {@link org.eclipse.ditto.protocol.Adaptable}.
 *
 * @since 2.1.0
 */
final class ConnectivityAnnouncementAdapter extends AbstractConnectivityAdapter<ConnectivityAnnouncement<?>> {

    private ConnectivityAnnouncementAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getConnectivityAnnouncementMappingStrategies(),
                SignalMapperFactory.newConnectivityAnnouncementSignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new ConnectivityAnnouncementAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     * @throws NullPointerException if {@code headerTranslator} is null.
     */
    public static ConnectivityAnnouncementAdapter of(final HeaderTranslator headerTranslator) {
        return new ConnectivityAnnouncementAdapter(checkNotNull(headerTranslator, "headerTranslator"));
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
        return getCommandTypePrefix(topicPath) + "." + getTypeCriterionAsString(topicPath) + ":" + commandName;
    }

    /**
     * Gets the actual "command type" as found in the group of the {@code topicPath}. This method acts as a bridge
     * between the topic path view on connections {@code ".../connections/..."} and the internal signal prefix {@code
     * "connectivity. ..."}.
     *
     * @param topicPath the topic path
     * @return the command type prefix, i.e. the {@link TopicPath#getGroup()} where "connections" is replaced with
     * "connectivity".
     */
    private String getCommandTypePrefix(final TopicPath topicPath) {
        return topicPath.getGroup().toString().replace(TopicPath.Group.CONNECTIONS.getName(),
                ConnectivityAnnouncement.SERVICE_PREFIX);
    }

}
