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
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;

/**
 * Strategy that creates {@link Bson} for {@link FeaturePropertiesDeleted} events.
 */
public final class MongoFeaturePropertiesDeletedStrategy extends
        MongoEventToPersistenceStrategy<FeaturePropertiesDeleted> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final FeaturePropertiesDeleted event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return Collections.singletonList(
                FeaturesUpdateFactory.createDeleteFeaturePropertiesUpdate(event.getFeatureId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final FeaturePropertiesDeleted event,
            final PolicyEnforcer policyEnforcer) {

        if (isPolicyRevelant(event.getImplementedSchemaVersion())) {
            return Collections.singletonList(
                    PolicyUpdateFactory.createFeaturePropertiesDeletion(event.getThingId(), event.getFeatureId()));
        }
        return Collections.emptyList();
    }
}
