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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.services.utils.headers.conditional.IfMatchPreconditionHeader;
import org.eclipse.ditto.services.utils.headers.conditional.IfNoneMatchPreconditionHeader;
import org.eclipse.ditto.services.utils.headers.conditional.PreconditionHeader;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingPreconditionFailedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingPreconditionNotModifiedException;

/**
 * Responsible to check conditional (http) headers based on a given ETag.
 *
 * @param <C> The type of the handled command.
 */
@Immutable
final class ThingsConditionalHeaderInterceptor<C extends Command<C>> {

    /**
     * Checks if the in the given {@code command} contained
     * {@link org.eclipse.ditto.services.utils.headers.conditional.PreconditionHeader precondition headers} meet their
     * condition for the given entity tag. Throws {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}
     * when a condition fails.
     *
     * @param command The command that contains the headers.
     * @param currentETagValue the entity-tag of the entity targeted by the given {@code command}
     * @throws ThingPreconditionFailedException when if-match header does not meet its condition and check is not
     * skipped according to {@link #skipPreconditionHeaderCheck(Command, EntityTag)}.
     * @throws ThingPreconditionFailedException when if-none-match header does not meet its condition,
     * {@link Command#getCategory() command category} is not a{@link Command.Category#QUERY query} and check is not
     * skipped according to {@link #skipPreconditionHeaderCheck(Command, EntityTag)}.
     * @throws ThingPreconditionNotModifiedException when if-none-match header does not meet its condition,
     * {@link Command#getCategory() command category} is a{@link Command.Category#QUERY query} and check is not
     * skipped according to {@link #skipPreconditionHeaderCheck(Command, EntityTag)}.
     */
    void checkConditionalHeaders(final C command,
            @Nullable final EntityTag currentETagValue) {

        if (skipPreconditionHeaderCheck(command, currentETagValue)) {
            return;
        }

        checkIfMatch(command, currentETagValue);
        checkIfNoneMatch(command, currentETagValue);
    }

    private boolean skipPreconditionHeaderCheck(final C command, @Nullable final EntityTag currentETagValue) {
        return currentETagValue == null &&
                (DELETE.equals(command.getCategory()) || QUERY.equals(command.getCategory()));
    }

    private void checkIfMatch(final C command, @Nullable final EntityTag currentETagValue) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        IfMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).ifPresent(ifMatch -> {
            if (!ifMatch.meetsConditionFor(currentETagValue)) {
                throw buildPreconditionFailedException(ifMatch, dittoHeaders, currentETagValue);
            }
        });
    }

    private void checkIfNoneMatch(final C command, @Nullable final EntityTag currentETagValue) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        IfNoneMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).ifPresent(ifNoneMatch -> {
            if (!ifNoneMatch.meetsConditionFor(currentETagValue)) {
                if (command.getCategory().equals(QUERY)) {
                    throw buildNotModifiedException(ifNoneMatch, dittoHeaders, currentETagValue);
                } else {
                    throw buildPreconditionFailedException(ifNoneMatch, dittoHeaders, currentETagValue);
                }
            }
        });
    }

    private ThingPreconditionFailedException buildPreconditionFailedException(
            final PreconditionHeader preconditionHeader,
            final DittoHeaders dittoHeaders, @Nullable final EntityTag currentETagValue) {
        final String headerKey = preconditionHeader.getKey();
        final String headerValue = preconditionHeader.getValue();

        return ThingPreconditionFailedException
                .newBuilder(headerKey, headerValue, String.valueOf(currentETagValue))
                .dittoHeaders(appendETagIfNotNull(dittoHeaders, currentETagValue))
                .build();
    }

    private ThingPreconditionNotModifiedException buildNotModifiedException(final PreconditionHeader preconditionHeader,
            final DittoHeaders dittoHeaders, @Nullable final EntityTag currentETagValue) {
        return ThingPreconditionNotModifiedException
                .newBuilder(preconditionHeader.getValue(), String.valueOf(currentETagValue))
                .dittoHeaders(appendETagIfNotNull(dittoHeaders, currentETagValue))
                .build();
    }

    private DittoHeaders appendETagIfNotNull(final DittoHeaders dittoHeaders, @Nullable final EntityTag entityTag) {
        if (entityTag == null) {
            return dittoHeaders;
        }
        return dittoHeaders.toBuilder().eTag(entityTag).build();
    }
}
