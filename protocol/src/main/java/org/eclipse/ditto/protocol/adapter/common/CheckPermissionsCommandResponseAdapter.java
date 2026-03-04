/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.common;

import static java.util.Objects.requireNonNull;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.CheckPermissionsResponse;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.adapter.EmptyPathMatcher;
import org.eclipse.ditto.protocol.mapper.SignalMapper;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;

/**
 * Adapter for mapping a {@link CheckPermissionsResponse} to and from an {@link Adaptable}.
 * <p>
 * Topic path: {@code _/_/policies/commands/checkPermissions}
 *
 * @since 3.9.0
 */
public final class CheckPermissionsCommandResponseAdapter extends AbstractAdapter<CheckPermissionsResponse> {

    private final SignalMapper<CheckPermissionsResponse> signalMapper;

    private CheckPermissionsCommandResponseAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getCheckPermissionsCommandResponseMappingStrategies(),
                headerTranslator,
                EmptyPathMatcher.getInstance());
        this.signalMapper = SignalMapperFactory.newCheckPermissionsResponseSignalMapper();
    }

    /**
     * Returns a new {@code CheckPermissionsCommandResponseAdapter}.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static CheckPermissionsCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new CheckPermissionsCommandResponseAdapter(requireNonNull(headerTranslator, "headerTranslator"));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return CheckPermissionsResponse.TYPE;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final CheckPermissionsResponse signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }

    @Override
    public TopicPath toTopicPath(final CheckPermissionsResponse signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToTopicPath(signal, channel);
    }

    @Override
    public Set<TopicPath.Group> getGroups() {
        return EnumSet.of(TopicPath.Group.POLICIES);
    }

    @Override
    public Set<TopicPath.Channel> getChannels() {
        return EnumSet.of(TopicPath.Channel.NONE);
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.COMMANDS);
    }

    @Override
    public Set<TopicPath.Action> getActions() {
        return EnumSet.of(TopicPath.Action.CHECK_PERMISSIONS);
    }

    @Override
    public boolean isForResponses() {
        return true;
    }

    @Override
    public boolean supportsWildcardTopics() {
        return true;
    }
}
