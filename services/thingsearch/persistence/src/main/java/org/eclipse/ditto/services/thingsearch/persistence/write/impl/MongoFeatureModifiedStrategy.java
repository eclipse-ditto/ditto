/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.FeatureModified;

/**
 * Strategy that creates {@link Bson} for {@link FeatureModified} events.
 */
public final class MongoFeatureModifiedStrategy extends MongoEventToPersistenceStrategy<FeatureModified> {

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<Bson> thingUpdates(final FeatureModified event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return FeaturesUpdateFactory.createUpdateForFeature(indexLengthRestrictionEnforcer, event.getFeature(), false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final List<PolicyUpdate> policyUpdates(final FeatureModified event, final Enforcer policyEnforcer) {
        if (isPolicyRevelant(event.getImplementedSchemaVersion())) {
            return Collections.singletonList(
                    PolicyUpdateFactory.createFeatureUpdate(event.getThingId(), event.getFeature(), policyEnforcer));
        }
        return Collections.emptyList();
    }
}
