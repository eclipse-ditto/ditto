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
package org.eclipse.ditto.protocoladapter.adaptables;

import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.protocoladapter.UnknownCommandResponseException;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

final class ThingQueryResponseAdaptableConstructor
        extends AbstractQueryAdaptableConstructor<ThingQueryCommandResponse<?>> {

    @Override
    public void validate(final ThingQueryCommandResponse<?> commandResponse) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }
    }

    @Override
    public TopicPathBuilder getTopicPathBuilder(final ThingQueryCommandResponse<?> command) {
        return ProtocolFactory.newTopicPathBuilder(command.getThingEntityId()).things();
    }

    @Override
    public void enhancePayloadBuilder(final ThingQueryCommandResponse<?> commandResponse,
            final PayloadBuilder payloadBuilder) {
        payloadBuilder.withStatus(commandResponse.getStatusCode());
        payloadBuilder.withValue(commandResponse.getEntity(commandResponse.getImplementedSchemaVersion()));
    }
}
