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
package org.eclipse.ditto.protocoladapter.signals;

import java.util.Collection;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

final class RetrieveThingsSignalMapper extends AbstractQuerySignalMapper<RetrieveThings> {

    @Override
    TopicPathBuilder getTopicPathBuilder(final RetrieveThings command) {
        final String namespace = command.getNamespace().orElse(TopicPath.ID_PLACEHOLDER);
        return ProtocolFactory.newTopicPathBuilderFromNamespace(namespace);
    }

    @Override
    void enhancePayloadBuilder(final RetrieveThings command, final PayloadBuilder payloadBuilder) {
        command.getSelectedFields().ifPresent(payloadBuilder::withFields);
        payloadBuilder.withValue(createIdsPayload(command.getThingEntityIds()));
    }

    private static JsonValue createIdsPayload(final Collection<ThingId> ids) {
        final JsonArray thingIdsArray = ids.stream()
                .map(String::valueOf)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
        return JsonFactory.newObject().setValue(RetrieveThings.JSON_THING_IDS.getPointer(), thingIdsArray);
    }

}
