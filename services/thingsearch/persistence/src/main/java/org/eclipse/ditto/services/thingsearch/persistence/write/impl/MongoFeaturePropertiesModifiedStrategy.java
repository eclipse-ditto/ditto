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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import java.util.Collections;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;

/**
 * Strategy that creates {@link Bson} for {@link FeaturePropertiesModified} events.
 */
public final class MongoFeaturePropertiesModifiedStrategy extends
        MongoEventToPersistenceStrategy<FeaturePropertiesModified> {

    @Override
    public final List<Bson> thingUpdates(final FeaturePropertiesModified event,
            final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
        return FeaturesUpdateFactory.createUpdateForFeatureProperties(indexLengthRestrictionEnforcer,
                event.getFeatureId(),
                event.getProperties());
    }

    @Override
    public final List<PolicyUpdate> policyUpdates(final FeaturePropertiesModified event,
            final Enforcer policyEnforcer) {

        if (isPolicyRevelant(event.getImplementedSchemaVersion())) {
            return Collections.singletonList(PolicyUpdateFactory.createFeaturePropertiesUpdate(
                    event.getThingId(),
                    event.getFeatureId(),
                    event.getProperties(),
                    policyEnforcer));
        }
        return Collections.emptyList();
    }
}
