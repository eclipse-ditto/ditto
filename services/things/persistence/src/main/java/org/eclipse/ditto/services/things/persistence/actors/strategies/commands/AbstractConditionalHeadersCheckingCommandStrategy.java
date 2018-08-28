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

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.headers.conditional.PreconditionHeader;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingPreconditionFailed;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingPreconditionNotModified;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * Responsible to check conditional (http) headers based on the thing's current eTag value.
 *
 * @param <C> The type of the handled command.
 * @param <E> The type of the addressed entity.
 */
@Immutable
public abstract class AbstractConditionalHeadersCheckingCommandStrategy<C extends Command<C>, E> extends
        AbstractCommandStrategy<C> implements ETagEntityProvider<C, E> {

    private static final String IF_MATCH_HEADER_KEY = DittoHeaderDefinition.IF_MATCH.getKey();
    private static final String IF_NONE_MATCH_HEADER_KEY = DittoHeaderDefinition.IF_NONE_MATCH.getKey();

    /**
     * Constructs a new {@code AbstractCommandStrategy} object.
     *
     * @param theMatchingClass the class
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractConditionalHeadersCheckingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public Result apply(final Context context, @Nullable final Thing thing, final long nextRevision, final C command) {

        if (headersContainConditionalHeaders(command.getDittoHeaders())) {
            final Optional<Result> result = checkConditionalHeaders(command, thing);
            if (result.isPresent()) {
                return result.get();
            }
        }

        return super.apply(context, thing, nextRevision, command);
    }

    /**
     * Checks conditional headers on the (sub-)entity determined by the given {@code command} and {@code thing}.
     * Currently supports only {@link DittoHeaderDefinition#IF_MATCH} and {@link DittoHeaderDefinition#IF_NONE_MATCH}
     *
     * @param command the command which addresses either the whole thing or a sub-entity
     * @param thing the thing, may be {@code null}.
     * @return {@code empty} in case of success, an (error) {@link Result} otherwise.
     */
    private Optional<Result> checkConditionalHeaders(final C command, @Nullable final Thing thing) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final EntityTag currentETagValue = determineETagEntity(command, thing)
                .flatMap(EntityTag::fromEntity)
                .orElse(null);

        final List<PreconditionHeader> preconditionHeaders = PreconditionHeader.fromDittoHeaders(dittoHeaders);
        for (PreconditionHeader preconditionHeader : preconditionHeaders) {
            if (!preconditionHeader.meetsConditionFor(currentETagValue)) {
                final DittoRuntimeException exception = buildException(preconditionHeader, currentETagValue, command);
                return Optional.of(ResultFactory.newErrorResult(exception));
            }
        }

        return Optional.empty();
    }

    private DittoRuntimeException buildException(final PreconditionHeader preconditionHeader,
            @Nullable final EntityTag currentETagValue, final C command) {

        final String headerKey = preconditionHeader.getKey();
        final String headerValue = preconditionHeader.getValue();

        if (IF_MATCH_HEADER_KEY.equals(headerKey) || command instanceof ThingModifyCommand) {
            return ThingPreconditionFailed
                    .newBuilder(headerKey, headerValue, String.valueOf(currentETagValue))
                    .dittoHeaders(appendETagIfNotNull(command.getDittoHeaders(), currentETagValue))
                    .build();
        } else {
            return ThingPreconditionNotModified
                    .newBuilder(headerValue, String.valueOf(currentETagValue))
                    .dittoHeaders(appendETagIfNotNull(command.getDittoHeaders(), currentETagValue))
                    .build();
        }
    }

    private DittoHeaders appendETagIfNotNull(final DittoHeaders dittoHeaders, @Nullable final EntityTag entityTag) {
        if (entityTag == null) {
            return dittoHeaders;
        }
        return dittoHeaders.toBuilder().eTag(entityTag).build();
    }

    private boolean headersContainConditionalHeaders(final DittoHeaders dittoHeaders) {
        return dittoHeaders.containsKey(IF_MATCH_HEADER_KEY) || dittoHeaders.containsKey(IF_NONE_MATCH_HEADER_KEY);
    }
}
