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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * Adapter for mapping a {@link ThingModifyCommand} to and from an {@link Adaptable}.
 */
final class ThingModifyCommandAdapter extends AbstractAdapter<ThingModifyCommand> {

    private ThingModifyCommandAdapter(final Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns a new ThingModifyCommandAdapter.
     *
     * @return the adapter.
     */
    public static ThingModifyCommandAdapter newInstance() {
        return new ThingModifyCommandAdapter(mappingStrategies());
    }

    private static Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(CreateThing.TYPE,
                adaptable -> CreateThing.of(thingFrom(adaptable), initialPolicyForCreateThingFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(ModifyThing.TYPE,
                adaptable -> ModifyThing.of(thingIdFrom(adaptable), thingFrom(adaptable),
                        initialPolicyForModifyThingFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteThing.TYPE,
                adaptable -> DeleteThing.of(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyAcl.TYPE,
                adaptable -> ModifyAcl.of(thingIdFrom(adaptable), aclFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyAclEntry.TYPE, adaptable -> ModifyAclEntry.of(thingIdFrom(adaptable),
                aclEntryFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteAclEntry.TYPE, adaptable -> DeleteAclEntry.of(thingIdFrom(adaptable),
                authorizationSubjectFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyAttributes.TYPE, adaptable -> ModifyAttributes.of(thingIdFrom(adaptable),
                attributesFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteAttributes.TYPE,
                adaptable -> DeleteAttributes.of(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyAttribute.TYPE, adaptable -> ModifyAttribute.of(thingIdFrom(adaptable),
                attributePointerFrom(adaptable), attributeValueFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteAttribute.TYPE, adaptable -> DeleteAttribute.of(thingIdFrom(adaptable),
                attributePointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeatures.TYPE, adaptable -> ModifyFeatures.of(thingIdFrom(adaptable),
                featuresFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeatures.TYPE,
                adaptable -> DeleteFeatures.of(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeature.TYPE,
                adaptable -> ModifyFeature.of(thingIdFrom(adaptable), featureFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeature.TYPE, adaptable -> DeleteFeature.of(thingIdFrom(adaptable),
                featureIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeatureDefinition.TYPE,
                adaptable -> ModifyFeatureDefinition.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featureDefinitionFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeatureDefinition.TYPE, adaptable -> DeleteFeatureDefinition
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeatureProperties.TYPE,
                adaptable -> ModifyFeatureProperties.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featurePropertiesFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeatureProperties.TYPE, adaptable -> DeleteFeatureProperties
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies
                .put(ModifyFeatureProperty.TYPE,
                        adaptable -> ModifyFeatureProperty.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                                featurePropertyPointerFrom(adaptable), featurePropertyValueFrom(adaptable),
                                dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeatureProperty.TYPE, adaptable -> DeleteFeatureProperty.of(thingIdFrom(adaptable),
                featureIdFrom(adaptable), featurePropertyPointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getAction(topicPath) + upperCaseFirst(PathMatcher.match(path));
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommand command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(command.getThingId());

        final CommandsTopicPathBuilder commandsTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (commandName.startsWith(TopicPath.Action.CREATE.toString())) {
            commandsTopicPathBuilder.create();
        } else if (commandName.startsWith(TopicPath.Action.MODIFY.toString())) {
            commandsTopicPathBuilder.modify();
        } else if (commandName.startsWith(TopicPath.Action.DELETE.toString())) {
            commandsTopicPathBuilder.delete();
        } else {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());

        final Optional<JsonValue> value = command.getEntity(command.getImplementedSchemaVersion());
        value.ifPresent(payloadBuilder::withValue);

        return Adaptable.newBuilder(commandsTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
    }

    private static JsonObject initialPolicyForCreateThingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(o -> o.getValue(CreateThing.JSON_INLINE_POLICY).map(JsonValue::asObject).orElse(null))
                .orElse(null);
    }

    private static JsonObject initialPolicyForModifyThingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(o -> o.getValue(ModifyThing.JSON_INLINE_POLICY).map(JsonValue::asObject).orElse(null))
                .orElse(null);
    }

}
