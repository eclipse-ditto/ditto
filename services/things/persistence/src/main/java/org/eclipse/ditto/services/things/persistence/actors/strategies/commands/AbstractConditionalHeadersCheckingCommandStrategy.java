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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.signals.commands.base.Command.Category.DELETE;
import static org.eclipse.ditto.signals.commands.base.Command.Category.QUERY;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.headers.conditional.IfMatchPreconditionHeader;
import org.eclipse.ditto.services.utils.headers.conditional.IfNoneMatchPreconditionHeader;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingPreconditionFailed;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingPreconditionNotModified;

/**
 * Responsible to check conditional (http) headers based on the thing's current eTag value.
 *
 * @param <C> The type of the handled command.
 * @param <E> The type of the addressed entity.
 */
@Immutable
public abstract class AbstractConditionalHeadersCheckingCommandStrategy<C extends Command<C>, E> extends
        AbstractCommandStrategy<C> implements ETagEntityProvider<C, E> {

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractConditionalHeadersCheckingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    /**
     * Checks conditional headers on the (sub-)entity determined by the given {@code command} and {@code thing}.
     * Currently supports only {@link IfMatchPreconditionHeader} and {@link IfNoneMatchPreconditionHeader}
     *
     * @param context the context.
     * @param thing the thing, may be {@code null}.
     * @param nextRevision the next revision number of the ThingPersistenceActor.
     * @param command the command which addresses either the whole thing or a sub-entity
     * @return Either and error result if a precondition header does not meet the condition or the result of the
     * extending strategy.
     */
    @Override
    public Result apply(final Context context, @Nullable final Thing thing, final long nextRevision, final C command) {

        final EntityTag currentETagValue = determineETagEntity(command, thing)
                .flatMap(EntityTag::fromEntity)
                .orElse(null);

        if (skipPreconditionHeaderCheck(command, currentETagValue)) {
            // For this cases a 404 is expected that should be returned by the implementing strategy.
            return super.apply(context, thing, nextRevision, command);
        }

        return Optional
                .of(checkIfMatch(command, currentETagValue))
                .orElseGet(() -> checkIfNoneMatch(command, currentETagValue))
                .orElseGet(() -> super.apply(context, thing, nextRevision, command));
    }

    private boolean skipPreconditionHeaderCheck(final C command, @Nullable final EntityTag currentETagValue) {
        return currentETagValue == null &&
                (DELETE.equals(command.getCategory()) || QUERY.equals(command.getCategory()));
    }

    private Optional<Result> checkIfMatch(final C command, @Nullable final EntityTag currentETagValue) {
        final Optional<IfMatchPreconditionHeader> ifMatchOpt =
                IfMatchPreconditionHeader.fromDittoHeaders(command.getDittoHeaders());

        return ifMatchOpt.flatMap(ifMatch -> {
            if (!ifMatch.meetsConditionFor(currentETagValue)) {
                final String headerKey = ifMatch.getKey();
                final String headerValue = ifMatch.getValue();

                final ThingPreconditionFailed exception = ThingPreconditionFailed
                        .newBuilder(headerKey, headerValue, String.valueOf(currentETagValue))
                        .dittoHeaders(appendETagIfNotNull(command.getDittoHeaders(), currentETagValue))
                        .build();

                return Optional.of(ResultFactory.newErrorResult(exception));
            }
            return Optional.empty();
        });
    }

    private Optional<Result> checkIfNoneMatch(final C command, @Nullable final EntityTag currentETagValue) {
        final Optional<IfNoneMatchPreconditionHeader> ifNoneMatchOpt =
                IfNoneMatchPreconditionHeader.fromDittoHeaders(command.getDittoHeaders());

        return ifNoneMatchOpt.flatMap(ifNoneMatch -> {
            if (!ifNoneMatch.meetsConditionFor(currentETagValue)) {

                final String headerKey = ifNoneMatch.getKey();
                final String headerValue = ifNoneMatch.getValue();

                if (command.getCategory().equals(QUERY)) {
                    final ThingPreconditionNotModified exception = ThingPreconditionNotModified
                            .newBuilder(headerValue, String.valueOf(currentETagValue))
                            .dittoHeaders(appendETagIfNotNull(command.getDittoHeaders(), currentETagValue))
                            .build();

                    return Optional.of(ResultFactory.newErrorResult(exception));
                } else {
                    final ThingPreconditionFailed exception = ThingPreconditionFailed
                            .newBuilder(headerKey, headerValue, String.valueOf(currentETagValue))
                            .dittoHeaders(appendETagIfNotNull(command.getDittoHeaders(), currentETagValue))
                            .build();
                    return Optional.of(ResultFactory.newErrorResult(exception));
                }
            }
            return Optional.empty();
        });
    }

    private DittoHeaders appendETagIfNotNull(final DittoHeaders dittoHeaders, @Nullable final EntityTag entityTag) {
        if (entityTag == null) {
            return dittoHeaders;
        }
        return dittoHeaders.toBuilder().eTag(entityTag).build();
    }
}
