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
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.AclModificationInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * This extension of {@link AbstractReceiveStrategy} is for handling {@link ThingCommand}.
 *
 * @param <T> type of the class this strategy matches against.
 */
@NotThreadSafe
public abstract class AbstractThingCommandStrategy<T extends Command> extends AbstractReceiveStrategy<T> {

    /**
     * Constructs a new {@code AbstractThingCommandStrategy} object.
     *
     * @param theMatchingClass the class of the message this strategy reacts to.
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    AbstractThingCommandStrategy(final Class<T> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public BiFunction<Context, T, Boolean> getPredicate() {
        return (ctx, command) -> null != ctx.getThing() && ctx.getThing().getId()
                .filter(command.getId()::equals)
                .isPresent();
    }

    @Override
    public BiFunction<Context, T, Result> getUnhandledFunction() {
        return (context, command) -> {
            throw new IllegalArgumentException(
                    MessageFormat.format(ThingPersistenceActor.UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
        };
    }

    protected DittoRuntimeException attributesNotFound(final String thingId, final DittoHeaders dittoHeaders) {
        return AttributesNotAccessibleException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
    }

    protected DittoRuntimeException featureNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        final FeatureNotAccessibleException featureNotAccessibleException =
                FeatureNotAccessibleException.newBuilder(thingId, featureId).dittoHeaders(dittoHeaders).build();

        return featureNotAccessibleException;
    }

    protected DittoRuntimeException featuresNotFound(final String thingId, final DittoHeaders dittoHeaders) {
        return FeaturesNotAccessibleException.newBuilder(thingId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    protected DittoRuntimeException featureDefinitionNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        return FeatureDefinitionNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    protected DittoRuntimeException featurePropertyNotFound(final String thingId, final String featureId,
            final JsonPointer jsonPointer,
            final DittoHeaders dittoHeaders) {

        return FeaturePropertyNotAccessibleException.newBuilder(thingId, featureId, jsonPointer)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    protected DittoRuntimeException featurePropertiesNotFound(final String thingId, final String featureId,
            final DittoHeaders dittoHeaders) {
        return FeaturePropertiesNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build();
    }


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected DittoRuntimeException aclInvalid(final String thingId, final Optional<String> message,
            final AuthorizationContext authContext,
            final DittoHeaders dittoHeaders) {

//        log.debug("ACL could not be modified by Authorization Context <{}> due to: {}", authContext,
//                message.orElse(null));
        return AclModificationInvalidException.newBuilder(thingId)
                .description(message.orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    protected DittoRuntimeException aclEntryNotFound(final String thingId,
            final AuthorizationSubject authorizationSubject, final DittoHeaders dittoHeaders) {
        return AclNotAccessibleException.newBuilder(thingId, authorizationSubject)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    protected boolean isThingDeleted(final Thing thing) {
        return null == thing || thing.hasLifecycle(ThingLifecycle.DELETED);
    }

    protected static Result result(final DittoRuntimeException exception) {
        return ImmutableResult.of(exception);
    }

    protected static Result result(final AbstractCommandResponse response) {
        return ImmutableResult.of(response);
    }

    protected static Result result(final ThingEvent eventToPersist, final AbstractCommandResponse response) {
        return ImmutableResult.of(eventToPersist, response);
    }
}
