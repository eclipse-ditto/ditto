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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategy;
import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategyFactory;
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
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * Persistence Strategy Factory that creates the {@link Bson} for persisting {@link ThingEvent}s.
 */
public final class MongoEventToPersistenceStrategyFactory<T extends ThingEvent> extends
        EventToPersistenceStrategyFactory<T,
                Bson, PolicyUpdate> {

    private static final Map<String, MongoEventToPersistenceStrategy> PERSISTENCE_STRATEGIES;

    static {
        final Map<String, MongoEventToPersistenceStrategy> creationStrategies = new HashMap<>();
        creationStrategies.put(AclEntryCreated.TYPE, new MongoAclEntryCreatedStrategy());
        creationStrategies.put(AclEntryModified.TYPE, new MongoAclEntryModifiedStrategy());
        creationStrategies.put(AclEntryDeleted.TYPE, new MongoAclEntryDeletedStrategy());
        creationStrategies.put(AclModified.TYPE, new MongoAclModifiedStrategy());
        creationStrategies.put(AttributeCreated.TYPE, new MongoAttributeCreatedStrategy());
        creationStrategies.put(AttributeModified.TYPE, new MongoAttributeModifiedStrategy());
        creationStrategies.put(AttributeDeleted.TYPE, new MongoAttributeDeletedStrategy());
        creationStrategies.put(AttributesCreated.TYPE, new MongoAttributesCreatedStrategy());
        creationStrategies.put(AttributesModified.TYPE, new MongoAttributesModifiedStrategy());
        creationStrategies.put(AttributesDeleted.TYPE, new MongoAttributesDeletedStrategy());
        creationStrategies.put(FeatureCreated.TYPE, new MongoFeatureCreatedStrategy());
        creationStrategies.put(FeatureModified.TYPE, new MongoFeatureModifiedStrategy());
        creationStrategies.put(FeatureDeleted.TYPE, new MongoFeatureDeletedStrategy());
        creationStrategies.put(FeaturesCreated.TYPE, new MongoFeaturesCreatedStrategy());
        creationStrategies.put(FeaturesModified.TYPE, new MongoFeaturesModifiedStrategy());
        creationStrategies.put(FeaturesDeleted.TYPE, new MongoFeaturesDeletedStrategy());
        creationStrategies.put(
                FeaturePropertyCreated.TYPE, new MongoFeaturePropertyCreatedStrategy());
        creationStrategies.put(
                FeaturePropertyModified.TYPE, new MongoFeaturePropertyModifiedStrategy());
        creationStrategies.put(
                FeaturePropertyDeleted.TYPE, new MongoFeaturePropertyDeletedStrategy());
        creationStrategies.put(
                FeaturePropertiesCreated.TYPE, new MongoFeaturePropertiesCreatedStrategy());
        creationStrategies.put(
                FeaturePropertiesModified.TYPE, new MongoFeaturePropertiesModifiedStrategy());
        creationStrategies.put(
                FeaturePropertiesDeleted.TYPE, new MongoFeaturePropertiesDeletedStrategy());
        creationStrategies.put(ThingCreated.TYPE, new MongoThingCreatedStrategy());
        creationStrategies.put(ThingModified.TYPE, new MongoThingModifiedStrategy());
        creationStrategies.put(ThingDeleted.TYPE, new MongoThingDeletedStrategy());
        PERSISTENCE_STRATEGIES = Collections.unmodifiableMap(creationStrategies);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EventToPersistenceStrategy<T, Bson, PolicyUpdate> getInstance(final String type) {
        return PERSISTENCE_STRATEGIES.get(type);
    }
}
