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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingDefinition;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownPathException;

/**
 * Provides helper methods to map from {@link Adaptable}s to things commands.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractThingMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {

    private static final int ATTRIBUTE_PATH_LEVEL = 1;
    private static final int FEATURE_PATH_LEVEL = 1;
    private static final int FEATURE_PROPERTY_PATH_LEVEL = 3;

    protected AbstractThingMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        super(mappingStrategies);
    }

    protected static AuthorizationSubject authorizationSubjectFrom(final Adaptable adaptable) {
        return AuthorizationSubject.newInstance(leafValue(adaptable.getPayload().getPath()));
    }

    @Nullable
    protected static JsonFieldSelector selectedFieldsFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getFields().orElse(null);
    }

    @Nullable
    protected static String namespaceFrom(final Adaptable adaptable) {
        final String namespace = adaptable.getTopicPath().getNamespace();
        return TopicPath.ID_PLACEHOLDER.equals(namespace) ? null : namespace;
    }

    protected static Thing thingFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newThing)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static AccessControlList aclFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(AccessControlListModelFactory::newAcl)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static AclEntry aclEntryFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(permissions -> AccessControlListModelFactory
                        .newAclEntry(leafValue(adaptable.getPayload().getPath()), permissions))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Attributes attributesFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newAttributes)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static JsonPointer attributePointerFrom(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.getSubPointer(ATTRIBUTE_PATH_LEVEL)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }

    protected static JsonValue attributeValueFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static String featureIdFrom(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.get(FEATURE_PATH_LEVEL).orElseThrow(() -> UnknownPathException.newBuilder(path).build()).toString();
    }

    protected static Features featuresFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newFeatures)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static Feature featureFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(jsonObject -> ThingsModelFactory.newFeatureBuilder(jsonObject)
                        .useId(featureIdFrom(adaptable))
                        .build())
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static FeatureDefinition featureDefinitionFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asArray)
                .map(ThingsModelFactory::newFeatureDefinition)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static FeatureProperties featurePropertiesFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newFeatureProperties)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static JsonPointer featurePropertyPointerFrom(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return path.getSubPointer(FEATURE_PROPERTY_PATH_LEVEL)
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }

    protected static JsonValue featurePropertyValueFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static PolicyId policyIdFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asString)
                .map(PolicyId::of)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static ThingDefinition thingDefinitionFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asString)
                .map(ThingsModelFactory::newDefinition)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    protected static String leafValue(final JsonPointer path) {
        return path.getLeaf().orElseThrow(() -> UnknownPathException.newBuilder(path).build()).toString();
    }

}
