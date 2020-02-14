/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingIdNotExplicitlySettableException;
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
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinition;
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
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * Adapter for mapping a {@link ThingModifyCommand} to and from an {@link Adaptable}.
 */
final class ThingModifyCommandAdapter extends AbstractAdapter<ThingModifyCommand> {

    private ThingModifyCommandAdapter(final Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies,
            final HeaderTranslator headerTranslator) {

        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new ThingModifyCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingModifyCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingModifyCommandAdapter(getMappingStrategies(), headerTranslator);
    }

    private static Map<String, JsonifiableMapper<ThingModifyCommand>> getMappingStrategies() {
        final Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(CreateThing.TYPE, ThingModifyCommandAdapter::createThingFrom);
        mappingStrategies.put(ModifyThing.TYPE, ThingModifyCommandAdapter::getModifyThingCommand);
        mappingStrategies.put(DeleteThing.TYPE,
                adaptable -> DeleteThing.of(getThingId(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyAcl.TYPE,
                adaptable -> ModifyAcl.of(getThingId(adaptable), getAclOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyAclEntry.TYPE,
                adaptable -> ModifyAclEntry.of(getThingId(adaptable), getAclEntryOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteAclEntry.TYPE,
                adaptable -> DeleteAclEntry.of(getThingId(adaptable), getAuthorizationSubject(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyAttributes.TYPE,
                adaptable -> ModifyAttributes.of(getThingId(adaptable), getAttributesOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteAttributes.TYPE,
                adaptable -> DeleteAttributes.of(getThingId(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyAttribute.TYPE,
                adaptable -> ModifyAttribute.of(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                        getAttributeValueOrThrow(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteAttribute.TYPE,
                adaptable -> DeleteAttribute.of(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyThingDefinition.TYPE,
                adaptable -> ModifyThingDefinition.of(getThingId(adaptable), getThingDefinitionOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteThingDefinition.TYPE,
                adaptable -> DeleteThingDefinition.of(getThingId(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeatures.TYPE,
                adaptable -> ModifyFeatures.of(getThingId(adaptable), getFeaturesOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeatures.TYPE,
                adaptable -> DeleteFeatures.of(getThingId(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeature.TYPE,
                adaptable -> ModifyFeature.of(getThingId(adaptable), getFeatureOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeature.TYPE,
                adaptable -> DeleteFeature.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeatureDefinition.TYPE,
                adaptable -> ModifyFeatureDefinition.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeatureDefinitionOrThrow(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeatureDefinition.TYPE,
                adaptable -> DeleteFeatureDefinition.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeatureProperties.TYPE,
                adaptable -> ModifyFeatureProperties.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertiesOrThrow(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeatureProperties.TYPE,
                adaptable -> DeleteFeatureProperties.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeatureProperty.TYPE,
                adaptable -> ModifyFeatureProperty.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertyPointerOrThrow(adaptable), getFeaturePropertyValueOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeatureProperty.TYPE,
                adaptable -> DeleteFeatureProperty.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertyPointerOrThrow(adaptable), adaptable.getDittoHeaders()));

        return mappingStrategies;
    }

    private static CreateThing createThingFrom(final Adaptable adaptable) {
        return CreateThing.of(getThingToCreateOrModify(adaptable), getInitialPolicyForCreateThingOrNull(adaptable),
                getPolicyIdOrPlaceholderForCreateThingOrNull(adaptable), adaptable.getDittoHeaders());
    }

    private static ModifyThing getModifyThingCommand(final Adaptable adaptable) {
        final Thing thing = getThingToCreateOrModify(adaptable);
        final ThingId thingId = thing.getEntityId().orElseThrow(
                () -> new IllegalStateException("ID should have been enforced in thingToCreateOrModifyFrom"));
        return ModifyThing.of(thingId, thing, getInitialPolicyForModifyThingOrNull(adaptable),
                getPolicyIdOrPlaceholderForModifyThingOrNull(adaptable), adaptable.getDittoHeaders());
    }

    private static Thing getThingToCreateOrModify(final Adaptable adaptable) {
        final Thing thing = getThingOrThrow(adaptable);

        return thing.getEntityId()
                .filter(thingId -> thingId.equals(getThingId(adaptable)))
                .map(thingId -> thing.toBuilder().setId(thingId).build())
                .orElseThrow(() -> ThingIdNotExplicitlySettableException.forDittoProtocol().build());
    }

    @Nullable
    private static JsonObject getInitialPolicyForCreateThingOrNull(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .flatMap(o -> o.getValue(CreateThing.JSON_INLINE_POLICY).map(JsonValue::asObject))
                .orElse(null);
    }

    @Nullable
    private static String getPolicyIdOrPlaceholderForCreateThingOrNull(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .flatMap(o -> o.getValue(CreateThing.JSON_COPY_POLICY_FROM))
                .orElse(null);
    }

    @Nullable
    private static JsonObject getInitialPolicyForModifyThingOrNull(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .flatMap(o -> o.getValue(ModifyThing.JSON_INLINE_POLICY).map(JsonValue::asObject))
                .orElse(null);
    }

    @Nullable
    private static String getPolicyIdOrPlaceholderForModifyThingOrNull(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .flatMap(o -> o.getValue(ModifyThing.JSON_COPY_POLICY_FROM))
                .orElse(null);
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getActionOrThrow(topicPath) + upperCaseFirst(PathMatcher.match(path));
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
    }

    @Override
    public Adaptable constructAdaptable(final ThingModifyCommand command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(command.getThingEntityId());

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

}
