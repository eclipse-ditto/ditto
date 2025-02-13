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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownCommandResponseException;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinitionResponse;

final class ThingMigrateResponseSignalMapper extends AbstractModifySignalMapper<MigrateThingDefinitionResponse>
        implements ResponseSignalMapper {

    @Override
    void validate(final MigrateThingDefinitionResponse commandResponse,
            final TopicPath.Channel channel) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }
    }

    private static final TopicPath.Action[] SUPPORTED_ACTIONS = {TopicPath.Action.MIGRATE};

    @Override
    TopicPathBuilder getTopicPathBuilder(final MigrateThingDefinitionResponse command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).things();
    }

    @Override
    TopicPath.Action[] getSupportedActions() {
        return SUPPORTED_ACTIONS;
    }


    @Override
    void enhancePayloadBuilder(final MigrateThingDefinitionResponse commandResponse, final PayloadBuilder payloadBuilder) {
        payloadBuilder.withStatus(commandResponse.getHttpStatus());
    }

    @Override
    DittoHeaders enhanceHeaders(final MigrateThingDefinitionResponse signal) {
        return ProtocolFactory.newHeadersWithJsonMergePatchContentType(signal.getDittoHeaders());
    }

}
