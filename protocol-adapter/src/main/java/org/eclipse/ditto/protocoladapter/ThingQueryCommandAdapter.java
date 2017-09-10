/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * Adapter for mapping a {@link ThingQueryCommand} to and from an {@link Adaptable}.
 */
final class ThingQueryCommandAdapter extends AbstractAdapter<ThingQueryCommand> {

    private ThingQueryCommandAdapter(final Map<String, JsonifiableMapper<ThingQueryCommand>> mappingStrategies) {
        super(mappingStrategies);
    }

    public static ThingQueryCommandAdapter newInstance() {
        return new ThingQueryCommandAdapter(mappingStrategies());
    }

    private static Map<String, JsonifiableMapper<ThingQueryCommand>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingQueryCommand>> mappingStrategies = new HashMap<>();

        // the snapshot revision is not yet relevant for Protocol Adapter as it is only used by Topologies
        mappingStrategies.put(RetrieveThing.TYPE, adaptable -> RetrieveThing.getBuilder(thingIdFrom(adaptable),
                dittoHeadersFrom(adaptable))
                  .withSelectedFields(selectedFieldsFrom(adaptable))
                  .build());

        mappingStrategies.put(RetrieveAcl.TYPE, adaptable -> RetrieveAcl.of(thingIdFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAclEntry.TYPE, adaptable -> RetrieveAclEntry.of(thingIdFrom(adaptable),
                authorizationSubjectFrom(adaptable), selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributes.TYPE, adaptable -> RetrieveAttributes.of(thingIdFrom(adaptable),
                selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttribute.TYPE, adaptable -> RetrieveAttribute.of(thingIdFrom(adaptable),
                attributePointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatures.TYPE, adaptable -> RetrieveFeatures.of(thingIdFrom(adaptable),
                selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeature.TYPE, adaptable -> RetrieveFeature.of(thingIdFrom(adaptable),
                featureIdFrom(adaptable), selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureProperties.TYPE, adaptable ->
                RetrieveFeatureProperties.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureProperty.TYPE, adaptable ->
                RetrieveFeatureProperty.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = topicPath.getAction().get() + upperCaseFirst(PathMatcher.match(path));
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommand command, final TopicPath.Channel channel) {
        return handleSingleRetrieve(command, channel);
    }

    private static Adaptable handleSingleRetrieve(final ThingQueryCommand<?> command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = DittoProtocolAdapter.newTopicPathBuilder(command.getThingId());

        final CommandsTopicPathBuilder commandsTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {
            commandsTopicPathBuilder = topicPathBuilder.twin().commands();
        } else if (channel == TopicPath.Channel.LIVE) {
            commandsTopicPathBuilder = topicPathBuilder.live().commands();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (!commandName.startsWith(TopicPath.Action.RETRIEVE.toString())) {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());
        final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
        selectedFields.ifPresent(payloadBuilder::withFields);

        return Adaptable.newBuilder(commandsTopicPathBuilder.retrieve().build()) //
                .withPayload(payloadBuilder.build()) //
                .withHeaders(DittoProtocolAdapter.newHeaders(command.getDittoHeaders())) //
                .build();
    }

}
