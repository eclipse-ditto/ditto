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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructor;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructorFactory;
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
final class ThingModifyCommandAdapter extends AbstractThingAdapter<ThingModifyCommand> {

    private final AdaptableConstructor<ThingModifyCommand> adaptableConstructor =
            AdaptableConstructorFactory.newThingModifyAdaptableConstructor();

    private ThingModifyCommandAdapter(
            final Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies,
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
        return new ThingModifyCommandAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingModifyCommand>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(CreateThing.TYPE,
                adaptable -> CreateThing.of(thingFrom(adaptable), initialPolicyForCreateThingFrom(adaptable),
                        policyIdOrPlaceholderForCreateThingFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(ModifyThing.TYPE,
                adaptable -> ModifyThing.of(thingIdFrom(adaptable), thingFrom(adaptable),
                        initialPolicyForModifyThingFrom(adaptable), policyIdOrPlaceholderForModifyThingFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
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
        final String commandName = getAction(topicPath) + upperCaseFirst(pathMatcher.match(path));
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
    }

    @Override
    public Adaptable constructAdaptable(final ThingModifyCommand command, final TopicPath.Channel channel) {
        return adaptableConstructor.construct(command, channel);
    }

    private static JsonObject initialPolicyForCreateThingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(o -> o.getValue(CreateThing.JSON_INLINE_POLICY).map(JsonValue::asObject).orElse(null))
                .orElse(null);
    }

    private static String policyIdOrPlaceholderForCreateThingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .flatMap(o -> o.getValue(CreateThing.JSON_COPY_POLICY_FROM))
                .orElse(null);
    }

    private static JsonObject initialPolicyForModifyThingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(o -> o.getValue(ModifyThing.JSON_INLINE_POLICY).map(JsonValue::asObject).orElse(null))
                .orElse(null);
    }

    private static String policyIdOrPlaceholderForModifyThingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .flatMap(o -> o.getValue(ModifyThing.JSON_COPY_POLICY_FROM))
                .orElse(null);
    }

}
