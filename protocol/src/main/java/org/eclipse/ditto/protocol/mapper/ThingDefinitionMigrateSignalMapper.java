/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;

/**
 * A signal mapper for the {@link MigrateThingDefinition} command.
 */
public final class ThingDefinitionMigrateSignalMapper extends AbstractCommandSignalMapper<MigrateThingDefinition>{

    @Override
    void enhancePayloadBuilder(final MigrateThingDefinition command, final PayloadBuilder payloadBuilder) {
        payloadBuilder.withValue(command.toJson());
    }

    @Override
    TopicPathBuilder getTopicPathBuilder(final MigrateThingDefinition command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).things();
    }
    private static final TopicPath.Action[] SUPPORTED_ACTIONS = {TopicPath.Action.MIGRATE};

    @Override
    TopicPath.Action[] getSupportedActions() {
        return SUPPORTED_ACTIONS;
    }

}
