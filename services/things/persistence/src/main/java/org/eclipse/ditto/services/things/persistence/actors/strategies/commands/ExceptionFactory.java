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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.text.MessageFormat;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.things.exceptions.AclModificationInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;

/**
 * A factory for various exceptions which can be raised by {@code CommandStrategy}s.
 */
@Immutable
final class ExceptionFactory {

    private ExceptionFactory() {
        throw new AssertionError();
    }

    static DittoRuntimeException attributesNotFound(final String thingId, final DittoHeaders dittoHeaders) {
        return AttributesNotAccessibleException.newBuilder(thingId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException attributeNotFound(final String thingId, final JsonPointer attributeKey,
            final DittoHeaders dittoHeaders) {

        return AttributeNotAccessibleException.newBuilder(thingId, attributeKey)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featureNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return FeatureNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featuresNotFound(final String thingId, final DittoHeaders dittoHeaders) {
        return FeaturesNotAccessibleException.newBuilder(thingId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static DittoRuntimeException aclInvalid(final String thingId, final Optional<String> message,
            final DittoHeaders dittoHeaders) {

        return AclModificationInvalidException.newBuilder(thingId)
                .description(message.orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException aclEntryNotFound(final String thingId, final AuthorizationSubject authorizationSubject,
            final DittoHeaders dittoHeaders) {

        return AclNotAccessibleException.newBuilder(thingId, authorizationSubject)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featureDefinitionNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return FeatureDefinitionNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featurePropertyNotFound(final String thingId,
            final String featureId,
            final JsonPointer jsonPointer,
            final DittoHeaders dittoHeaders) {

        return FeaturePropertyNotAccessibleException.newBuilder(thingId, featureId, jsonPointer)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static DittoRuntimeException featurePropertiesNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {

        return FeaturePropertiesNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static IllegalArgumentException unhandled(final WithId command) {
        final String msgPattern = "This Thing Actor did not handle the requested Thing with ID <{0}>!";
        throw new IllegalArgumentException(MessageFormat.format(msgPattern, command.getId()));
    }

}
