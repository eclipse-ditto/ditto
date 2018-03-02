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
package org.eclipse.ditto.signals.events.things;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.events.base.AbstractEventRegistry;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.EventRegistry;

/**
 * An {@link EventRegistry} aware of all {@link ThingEvent}s.
 */
@Immutable
public final class ThingEventRegistry extends AbstractEventRegistry<ThingEvent> {

    private ThingEventRegistry(final Map<String, JsonParsable<ThingEvent>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ThingEventRegistry}.
     *
     * @return the event registry.
     */
    public static ThingEventRegistry newInstance() {
        final Map<String, JsonParsable<ThingEvent>> parseStrategies = new HashMap<>();

        parseStrategies.put(ThingCreated.TYPE, ThingCreated::fromJson);
        parseStrategies.put(ThingModified.TYPE, ThingModified::fromJson);
        parseStrategies.put(ThingDeleted.TYPE, ThingDeleted::fromJson);

        parseStrategies.put(AclModified.TYPE, AclModified::fromJson);
        parseStrategies.put(AclEntryCreated.TYPE, AclEntryCreated::fromJson);
        parseStrategies.put(AclEntryModified.TYPE, AclEntryModified::fromJson);
        parseStrategies.put(AclEntryDeleted.TYPE, AclEntryDeleted::fromJson);

        parseStrategies.put(AttributesCreated.TYPE, AttributesCreated::fromJson);
        parseStrategies.put(AttributesModified.TYPE, AttributesModified::fromJson);
        parseStrategies.put(AttributesDeleted.TYPE, AttributesDeleted::fromJson);

        parseStrategies.put(AttributeCreated.TYPE, AttributeCreated::fromJson);
        parseStrategies.put(AttributeModified.TYPE, AttributeModified::fromJson);
        parseStrategies.put(AttributeDeleted.TYPE, AttributeDeleted::fromJson);

        parseStrategies.put(FeaturesCreated.TYPE, FeaturesCreated::fromJson);
        parseStrategies.put(FeaturesModified.TYPE, FeaturesModified::fromJson);
        parseStrategies.put(FeaturesDeleted.TYPE, FeaturesDeleted::fromJson);

        parseStrategies.put(FeatureCreated.TYPE, FeatureCreated::fromJson);
        parseStrategies.put(FeatureModified.TYPE, FeatureModified::fromJson);
        parseStrategies.put(FeatureDeleted.TYPE, FeatureDeleted::fromJson);

        parseStrategies.put(FeatureDefinitionCreated.TYPE, FeatureDefinitionCreated::fromJson);
        parseStrategies.put(FeatureDefinitionModified.TYPE, FeatureDefinitionModified::fromJson);
        parseStrategies.put(FeatureDefinitionDeleted.TYPE, FeatureDefinitionDeleted::fromJson);

        parseStrategies.put(FeaturePropertiesCreated.TYPE, FeaturePropertiesCreated::fromJson);
        parseStrategies.put(FeaturePropertiesModified.TYPE, FeaturePropertiesModified::fromJson);
        parseStrategies.put(FeaturePropertiesDeleted.TYPE, FeaturePropertiesDeleted::fromJson);

        parseStrategies.put(FeaturePropertyCreated.TYPE, FeaturePropertyCreated::fromJson);
        parseStrategies.put(FeaturePropertyModified.TYPE, FeaturePropertyModified::fromJson);
        parseStrategies.put(FeaturePropertyDeleted.TYPE, FeaturePropertyDeleted::fromJson);

        parseStrategies.put(PolicyIdCreated.TYPE, PolicyIdCreated::fromJson);
        parseStrategies.put(PolicyIdModified.TYPE, PolicyIdModified::fromJson);

        return new ThingEventRegistry(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        /*
         * If type was not present (was included in V2) take "event" instead and transform to V2 format.
          * Fail if "event" also is not present.
         */
        return jsonObject.getValue(Event.JsonFields.TYPE)
                .orElseGet(() -> extractTypeV1(jsonObject)
                        .orElseThrow(() -> new JsonMissingFieldException(Event.JsonFields.TYPE)));
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod")
    private Optional<String> extractTypeV1(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID).map(event -> ThingEvent.TYPE_PREFIX + event);
    }

}
