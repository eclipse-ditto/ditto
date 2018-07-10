/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.AclModificationInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;

import akka.event.DiagnosticLoggingAdapter;

abstract class AbstractCommandStrategy<T extends Command> implements CommandStrategy<T> {

    private final Class<T> theMatchingClass;

    protected AbstractCommandStrategy(final Class<T> theMatchingClass) {this.theMatchingClass = theMatchingClass;}

    @Override
    public Result apply(final Context context, final T command) {
        if (isDefined(context, command)) {
            final DiagnosticLoggingAdapter logger = context.getLog();
            LogUtil.enhanceLogWithCorrelationId(logger, command.getDittoHeaders().getCorrelationId());
            if (logger.isDebugEnabled()) {
                logger.debug("Applying command '{}': {}", command.getType(), command.toJsonString());
            }
            return doApply(context, command);
        } else {
            return unhandled(context, command);
        }
    }

    @Override
    public Class<T> getMatchingClass() {
        return theMatchingClass;
    }

    @Override
    public boolean isDefined(final Context context, final T command) {
        return null != context
                && null != context.getThing()
                && context.getThing().getId().filter(command.getId()::equals).isPresent();
    }

    protected Result unhandled(final Context context, final T command) {
        throw new IllegalArgumentException(
                MessageFormat.format(ThingPersistenceActor.UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
    }

    protected abstract Result doApply(final Context context, final T command);

    DittoRuntimeException attributesNotFound(final String thingId, final DittoHeaders dittoHeaders) {
        return AttributesNotAccessibleException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
    }

    DittoRuntimeException attributeNotFound(final String thingId, final JsonPointer attributeKey,
            final DittoHeaders dittoHeaders) {
        return AttributeNotAccessibleException.newBuilder(thingId, attributeKey)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    DittoRuntimeException featureNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        return FeatureNotAccessibleException.newBuilder(thingId, featureId).dittoHeaders(dittoHeaders).build();
    }

    DittoRuntimeException featuresNotFound(final String thingId, final DittoHeaders dittoHeaders) {
        return FeaturesNotAccessibleException.newBuilder(thingId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    DittoRuntimeException featureDefinitionNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        return FeatureDefinitionNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    DittoRuntimeException featurePropertyNotFound(final String thingId, final String featureId,
            final JsonPointer jsonPointer,
            final DittoHeaders dittoHeaders) {

        return FeaturePropertyNotAccessibleException.newBuilder(thingId, featureId, jsonPointer)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    DittoRuntimeException featurePropertiesNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        return FeaturePropertiesNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    DittoRuntimeException aclInvalid(final String thingId, final Optional<String> message,
            final DittoHeaders dittoHeaders) {
        return AclModificationInvalidException.newBuilder(thingId)
                .description(message.orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    DittoRuntimeException aclEntryNotFound(final String thingId,
            final AuthorizationSubject authorizationSubject, final DittoHeaders dittoHeaders) {
        return AclNotAccessibleException.newBuilder(thingId, authorizationSubject)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    boolean isThingDeleted(final Thing thing) {
        return null == thing || thing.hasLifecycle(ThingLifecycle.DELETED);
    }

    protected static Instant eventTimestamp() {
        return Instant.now();
    }
}
