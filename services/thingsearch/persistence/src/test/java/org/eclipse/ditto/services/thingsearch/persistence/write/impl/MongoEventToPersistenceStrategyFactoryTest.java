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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategy;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class MongoEventToPersistenceStrategyFactoryTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {AclEntryCreated.TYPE, true, MongoAclEntryCreatedStrategy.class},
                {AclEntryModified.TYPE, true, MongoAclEntryModifiedStrategy.class},
                {AclEntryDeleted.TYPE, true, MongoAclEntryDeletedStrategy.class},
                {AclModified.TYPE, true, MongoAclModifiedStrategy.class},
                {AttributeCreated.TYPE, true, MongoAttributeCreatedStrategy.class},
                {AttributeModified.TYPE, true, MongoAttributeModifiedStrategy.class},
                {AttributeDeleted.TYPE, true, MongoAttributeDeletedStrategy.class},
                {AttributesCreated.TYPE, true, MongoAttributesCreatedStrategy.class},
                {AttributesModified.TYPE, true, MongoAttributesModifiedStrategy.class},
                {AttributesDeleted.TYPE, true, MongoAttributesDeletedStrategy.class},
                {FeatureCreated.TYPE, true, MongoFeatureCreatedStrategy.class},
                {FeatureModified.TYPE, true, MongoFeatureModifiedStrategy.class},
                {FeatureDeleted.TYPE, true, MongoFeatureDeletedStrategy.class},
                {FeaturesCreated.TYPE, true, MongoFeaturesCreatedStrategy.class},
                {FeaturesModified.TYPE, true, MongoFeaturesModifiedStrategy.class},
                {FeaturesDeleted.TYPE, true, MongoFeaturesDeletedStrategy.class},
                {FeaturePropertyCreated.TYPE, true, MongoFeaturePropertyCreatedStrategy.class},
                {FeaturePropertyModified.TYPE, true, MongoFeaturePropertyModifiedStrategy.class},
                {FeaturePropertyDeleted.TYPE, true, MongoFeaturePropertyDeletedStrategy.class},
                {FeaturePropertiesCreated.TYPE, true, MongoFeaturePropertiesCreatedStrategy.class},
                {FeaturePropertiesModified.TYPE, true, MongoFeaturePropertiesModifiedStrategy.class},
                {FeaturePropertiesDeleted.TYPE, true, MongoFeaturePropertiesDeletedStrategy.class},
                {ThingDeleted.TYPE, true, MongoThingDeletedStrategy.class},
                {ThingCreated.TYPE, false, null},
                {ThingModified.TYPE, false, null},
                {"unknownType", false, null}
        });
    }

    private final MongoEventToPersistenceStrategyFactory factory = MongoEventToPersistenceStrategyFactory.getInstance();
    private final String type;
    private final ThingEvent eventMock;
    private final boolean isAllowed;
    private final Class expectedClass;

    public MongoEventToPersistenceStrategyFactoryTest(final String type, final boolean isAllowed,
            final Class expectedClass) {
        this.type = type;
        this.eventMock = mock(ThingEvent.class);
        when(eventMock.getType()).thenReturn(type);
        this.isAllowed = isAllowed;
        this.expectedClass = expectedClass;
    }


    @Test
    public void getStrategy() {
        if (isAllowed) {
            final EventToPersistenceStrategy strategy = factory.getStrategy(eventMock);
            assertThat(strategy).isNotNull();
            assertThat(strategy.getClass()).isEqualTo(expectedClass);
        } else {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> factory.getStrategy(eventMock));
        }
    }

    @Test
    public void getInstance() {
        final EventToPersistenceStrategy strategy = factory.getInstance(type);
        if (isAllowed) {
            assertThat(strategy).isNotNull();
            assertThat(strategy.getClass()).isEqualTo(expectedClass);
        } else {
            assertThat(strategy).isNull();
        }
    }

}