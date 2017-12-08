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
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Persistence Strategy Factory that creates the {@link Bson} for persisting {@link ThingEvent}s.
 */
public final class MongoEventToPersistenceStrategyFactory extends
        EventToPersistenceStrategyFactory<Bson, PolicyUpdate> {

    private static final MongoEventToPersistenceStrategyFactory INSTANCE = new MongoEventToPersistenceStrategyFactory();

    private final Map<String, MongoEventToPersistenceStrategy<? extends ThingEvent>> persistenceStrategies;

    private MongoEventToPersistenceStrategyFactory() {
        final Map<String, MongoEventToPersistenceStrategy<? extends ThingEvent>> strategies = createPersistenceStrategies();
        persistenceStrategies = Collections.unmodifiableMap(strategies);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <T extends ThingEvent> EventToPersistenceStrategy<T, Bson, PolicyUpdate> getInstance(final String type) {
        @SuppressWarnings("unchecked")
        final EventToPersistenceStrategy<T, Bson, PolicyUpdate> strategy =
                (EventToPersistenceStrategy<T, Bson, PolicyUpdate>) persistenceStrategies.get(type);
        return strategy;
    }

    /**
     * Returns the single instance of this factory.
     *
     * @return the instance.
     */
    public static MongoEventToPersistenceStrategyFactory getInstance() {
        return INSTANCE;
    }

    private static Map<String, MongoEventToPersistenceStrategy<? extends ThingEvent>> createPersistenceStrategies() {
        final Map<String, MongoEventToPersistenceStrategy<? extends ThingEvent>> strategies = new HashMap<>();

        strategies.put(AclEntryCreated.TYPE, new MongoAclEntryCreatedStrategy());
        strategies.put(AclEntryModified.TYPE, new MongoAclEntryModifiedStrategy());
        strategies.put(AclEntryDeleted.TYPE, new MongoAclEntryDeletedStrategy());
        strategies.put(AclModified.TYPE, new MongoAclModifiedStrategy());
        strategies.put(AttributeCreated.TYPE, new MongoAttributeCreatedStrategy());
        strategies.put(AttributeModified.TYPE, new MongoAttributeModifiedStrategy());
        strategies.put(AttributeDeleted.TYPE, new MongoAttributeDeletedStrategy());
        strategies.put(AttributesCreated.TYPE, new MongoAttributesCreatedStrategy());
        strategies.put(AttributesModified.TYPE, new MongoAttributesModifiedStrategy());
        strategies.put(AttributesDeleted.TYPE, new MongoAttributesDeletedStrategy());
        strategies.put(FeatureCreated.TYPE, new MongoFeatureCreatedStrategy());
        strategies.put(FeatureModified.TYPE, new MongoFeatureModifiedStrategy());
        strategies.put(FeatureDeleted.TYPE, new MongoFeatureDeletedStrategy());
        strategies.put(FeaturesCreated.TYPE, new MongoFeaturesCreatedStrategy());
        strategies.put(FeaturesModified.TYPE, new MongoFeaturesModifiedStrategy());
        strategies.put(FeaturesDeleted.TYPE, new MongoFeaturesDeletedStrategy());
        strategies.put(
                FeaturePropertyCreated.TYPE, new MongoFeaturePropertyCreatedStrategy());
        strategies.put(
                FeaturePropertyModified.TYPE, new MongoFeaturePropertyModifiedStrategy());
        strategies.put(
                FeaturePropertyDeleted.TYPE, new MongoFeaturePropertyDeletedStrategy());
        strategies.put(
                FeaturePropertiesCreated.TYPE, new MongoFeaturePropertiesCreatedStrategy());
        strategies.put(
                FeaturePropertiesModified.TYPE, new MongoFeaturePropertiesModifiedStrategy());
        strategies.put(
                FeaturePropertiesDeleted.TYPE, new MongoFeaturePropertiesDeletedStrategy());
        strategies.put(ThingDeleted.TYPE, new MongoThingDeletedStrategy());

        return strategies;
    }
}
