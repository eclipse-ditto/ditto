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
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;

final class PolicyQueryResponseSignalMapper
        extends AbstractQuerySignalMapper<PolicyQueryCommandResponse<?>>
        implements ResponseSignalMapper {

    @Override
    void validate(final PolicyQueryCommandResponse<?> commandResponse, final TopicPath.Channel channel) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }
    }

    @Override
    TopicPathBuilder getTopicPathBuilder(final PolicyQueryCommandResponse<?> command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId()).policies();
    }

    @Override
    void enhancePayloadBuilder(final PolicyQueryCommandResponse<?> commandResponse,
            final PayloadBuilder payloadBuilder) {

        payloadBuilder.withStatus(commandResponse.getHttpStatus());
        payloadBuilder.withValue(commandResponse.getEntity(commandResponse.getImplementedSchemaVersion()));
    }

}
