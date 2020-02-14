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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDefinitionCreated;
import org.eclipse.ditto.signals.events.things.ThingDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.ThingDefinitionModified;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * Adapter for mapping a {@link ThingEvent} to and from an {@link Adaptable}.
 */
final class ThingEventAdapter extends AbstractAdapter<ThingEvent<?>> {

    private ThingEventAdapter(
            final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new ThingEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingEventAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingEventAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingEvent<?>>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(ThingCreated.TYPE,
                adaptable -> ThingCreated.of(getThingOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(ThingModified.TYPE,
                adaptable -> ThingModified.of(getThingOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(ThingDeleted.TYPE,
                adaptable -> ThingDeleted.of(getThingId(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(AclModified.TYPE,
                adaptable -> AclModified.of(getThingId(adaptable), getAclOrThrow(adaptable), revisionFrom(adaptable),
                        timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(AclEntryCreated.TYPE,
                adaptable -> AclEntryCreated.of(getThingId(adaptable), getAclEntryOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(AclEntryModified.TYPE,
                adaptable -> AclEntryModified.of(getThingId(adaptable), getAclEntryOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(AclEntryDeleted.TYPE,
                adaptable -> AclEntryDeleted.of(getThingId(adaptable), getAuthorizationSubject(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(AttributesCreated.TYPE,
                adaptable -> AttributesCreated.of(getThingId(adaptable), getAttributesOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(AttributesModified.TYPE,
                adaptable -> AttributesModified.of(getThingId(adaptable), getAttributesOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(AttributesDeleted.TYPE,
                adaptable -> AttributesDeleted.of(getThingId(adaptable), revisionFrom(adaptable),
                        timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(AttributeCreated.TYPE,
                adaptable -> AttributeCreated.of(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                        getAttributeValueOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(AttributeModified.TYPE,
                adaptable -> AttributeModified.of(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                        getAttributeValueOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(AttributeDeleted.TYPE,
                adaptable -> AttributeDeleted.of(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(ThingDefinitionCreated.TYPE,
                adaptable -> ThingDefinitionCreated.of(getThingId(adaptable), getThingDefinitionOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(ThingDefinitionModified.TYPE,
                adaptable -> ThingDefinitionModified.of(getThingId(adaptable), getThingDefinitionOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(ThingDefinitionDeleted.TYPE,
                adaptable -> ThingDefinitionDeleted.of(getThingId(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(FeaturesCreated.TYPE,
                adaptable -> FeaturesCreated.of(getThingId(adaptable), getFeaturesOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(FeaturesModified.TYPE,
                adaptable -> FeaturesModified.of(getThingId(adaptable), getFeaturesOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(FeaturesDeleted.TYPE,
                adaptable -> FeaturesDeleted.of(getThingId(adaptable), revisionFrom(adaptable),
                        timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(FeatureCreated.TYPE,
                adaptable -> FeatureCreated.of(getThingId(adaptable), getFeatureOrThrow(adaptable), revisionFrom(adaptable),
                        timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(FeatureModified.TYPE,
                adaptable -> FeatureModified.of(getThingId(adaptable), getFeatureOrThrow(adaptable), revisionFrom(adaptable),
                        timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(FeatureDeleted.TYPE,
                adaptable -> FeatureDeleted.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(FeatureDefinitionCreated.TYPE,
                adaptable -> FeatureDefinitionCreated.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeatureDefinitionOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(FeatureDefinitionModified.TYPE,
                adaptable -> FeatureDefinitionModified.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeatureDefinitionOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(FeatureDefinitionDeleted.TYPE,
                adaptable -> FeatureDefinitionDeleted.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(FeaturePropertiesCreated.TYPE,
                adaptable -> FeaturePropertiesCreated.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertiesOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(FeaturePropertiesModified.TYPE,
                adaptable -> FeaturePropertiesModified.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertiesOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(FeaturePropertiesDeleted.TYPE,
                adaptable -> FeaturePropertiesDeleted.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(FeaturePropertyCreated.TYPE,
                adaptable -> FeaturePropertyCreated.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertyPointerOrThrow(adaptable), getFeaturePropertyValueOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(FeaturePropertyModified.TYPE,
                adaptable -> FeaturePropertyModified.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertyPointerOrThrow(adaptable), getFeaturePropertyValueOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(FeaturePropertyDeleted.TYPE,
                adaptable -> FeaturePropertyDeleted.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertyPointerOrThrow(adaptable), revisionFrom(adaptable), timestampFrom(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(PolicyIdCreated.TYPE,
                adaptable -> PolicyIdCreated.of(getThingId(adaptable), getPolicyIdOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(PolicyIdModified.TYPE,
                adaptable -> PolicyIdModified.of(getThingId(adaptable), getPolicyIdOrThrow(adaptable),
                        revisionFrom(adaptable), timestampFrom(adaptable), adaptable.getDittoHeaders()));

        return mappingStrategies;
    }

    private static long revisionFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getRevision().orElseThrow(() -> JsonMissingFieldException.newBuilder()
                .fieldName(Payload.JsonFields.REVISION.getPointer().toString()).build());
    }

    @Nullable
    private static Instant timestampFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getTimestamp().orElse(null);
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String eventName = PathMatcher.match(path) + getActionNameWithFirstLetterUpperCase(topicPath);
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + eventName;
    }

    private static String getActionNameWithFirstLetterUpperCase(final TopicPath topicPath) {
        return topicPath.getAction()
                .map(TopicPath.Action::toString)
                .map(AbstractAdapter::upperCaseFirst)
                .orElseThrow(() -> new NullPointerException("TopicPath did not contain an Action!"));
    }

    @Override
    public Adaptable constructAdaptable(final ThingEvent<?> event, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(event.getThingEntityId());

        final EventsTopicPathBuilder eventsTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {
            eventsTopicPathBuilder = topicPathBuilder.twin().events();
        } else if (channel == TopicPath.Channel.LIVE) {
            eventsTopicPathBuilder = topicPathBuilder.live().events();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }

        final String eventName = event.getClass().getSimpleName().toLowerCase();
        if (eventName.contains(TopicPath.Action.CREATED.toString())) {
            eventsTopicPathBuilder.created();
        } else if (eventName.contains(TopicPath.Action.MODIFIED.toString())) {
            eventsTopicPathBuilder.modified();
        } else if (eventName.contains(TopicPath.Action.DELETED.toString())) {
            eventsTopicPathBuilder.deleted();
        } else {
            throw UnknownEventException.newBuilder(eventName).build();
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(event.getResourcePath())
                .withRevision(event.getRevision());
        event.getTimestamp().ifPresent(payloadBuilder::withTimestamp);

        final Optional<JsonValue> value =
                event.getEntity(event.getDittoHeaders().getSchemaVersion().orElse(event.getLatestSchemaVersion()));
        value.ifPresent(payloadBuilder::withValue);

        return Adaptable.newBuilder(eventsTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(event.getDittoHeaders()))
                .build();
    }

}
