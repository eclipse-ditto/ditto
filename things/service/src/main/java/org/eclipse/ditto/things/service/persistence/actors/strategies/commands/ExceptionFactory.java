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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDesiredPropertiesNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureDesiredPropertyNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeaturesNotAccessibleException;

/**
 * A factory for various exceptions which can be raised by {@code CommandStrategy}s.
 */
@Immutable
final class ExceptionFactory {

    private ExceptionFactory() {
        throw new AssertionError();
    }

    static DittoRuntimeException attributesNotFound(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return AttributesNotAccessibleException.newBuilder(thingId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException attributeNotFound(final ThingId thingId, final JsonPointer attributeKey,
            final DittoHeaders dittoHeaders) {

        return AttributeNotAccessibleException.newBuilder(thingId, attributeKey)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featureNotFound(final ThingId thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return FeatureNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featuresNotFound(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return FeaturesNotAccessibleException.newBuilder(thingId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featureDefinitionNotFound(final ThingId thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return FeatureDefinitionNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featurePropertyNotFound(final ThingId thingId,
            final String featureId,
            final JsonPointer jsonPointer,
            final DittoHeaders dittoHeaders) {

        return FeaturePropertyNotAccessibleException.newBuilder(thingId, featureId, jsonPointer)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featurePropertiesNotFound(final ThingId thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return FeaturePropertiesNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featureDesiredPropertyNotFound(final ThingId thingId,
            final String featureId,
            final JsonPointer jsonPointer,
            final DittoHeaders dittoHeaders) {

        return FeatureDesiredPropertyNotAccessibleException.newBuilder(thingId, featureId, jsonPointer)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featureDesiredPropertiesNotFound(final ThingId thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return FeatureDesiredPropertiesNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }
}
