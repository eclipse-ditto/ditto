/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.protocol.UnknownCommandResponseException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingsResponse;

final class RetrieveThingsResponseSignalMapper
        extends AbstractQuerySignalMapper<RetrieveThingsResponse>
        implements ResponseSignalMapper {

    @Override
    void validate(final RetrieveThingsResponse commandResponse, final TopicPath.Channel channel) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }
    }

    @Override
    TopicPathBuilder getTopicPathBuilder(final RetrieveThingsResponse command) {
        final String namespace = command.getNamespace().orElse(TopicPath.ID_PLACEHOLDER);
        return ProtocolFactory.newTopicPathBuilderFromNamespace(namespace);
    }

    @Override
    void enhancePayloadBuilder(final RetrieveThingsResponse commandResponse,
            final PayloadBuilder payloadBuilder) {

        payloadBuilder.withStatus(commandResponse.getHttpStatus());
        payloadBuilder.withValue(commandResponse.getEntity(commandResponse.getImplementedSchemaVersion()));
    }

}
