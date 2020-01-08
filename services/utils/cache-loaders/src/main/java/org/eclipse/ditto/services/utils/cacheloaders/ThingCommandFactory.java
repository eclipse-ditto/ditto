/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.cacheloaders;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.CacheLookupContext;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates commands to access the Things service.
 */
final class ThingCommandFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingCommandFactory.class);

    private ThingCommandFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a sudo command for retrieving a thing.
     *
     * @param thingId the thingId.
     * @param cacheLookupContext the context to apply when doing the cache lookup.
     * @return the created command.
     */
    static SudoRetrieveThing sudoRetrieveThing(final EntityId thingId,
            @Nullable final CacheLookupContext cacheLookupContext) {
        return sudoRetrieveThing(ThingId.of(thingId), cacheLookupContext);
    }

    /**
     * Creates a sudo command for retrieving a thing.
     *
     * @param thingId the thingId.
     * @param cacheLookupContext the context to apply when doing the cache lookup.
     * @return the created command.
     */
    static SudoRetrieveThing sudoRetrieveThing(final ThingId thingId,
            @Nullable final CacheLookupContext cacheLookupContext) {
        LOGGER.debug("Sending SudoRetrieveThing for Thing with ID <{}>", thingId);
        return SudoRetrieveThing.withOriginalSchemaVersion(thingId,
                Optional.ofNullable(cacheLookupContext).flatMap(CacheLookupContext::getJsonFieldSelector).orElse(null),
                Optional.ofNullable(cacheLookupContext).flatMap(CacheLookupContext::getDittoHeaders).orElseGet(() ->
                        DittoHeaders.newBuilder().correlationId(getCorrelationId(thingId)).build())
        );
    }

    /**
     * Creates a command for retrieving a thing.
     *
     * @param thingId the thingId.
     * @param cacheLookupContext the context to apply when doing the cache lookup.
     * @return the created command.
     */
    static RetrieveThing retrieveThing(final EntityId thingId, @Nullable final CacheLookupContext cacheLookupContext) {
        return retrieveThing(ThingId.of(thingId), cacheLookupContext);
    }

    /**
     * Creates a command for retrieving a thing.
     *
     * @param thingId the thingId.
     * @param cacheLookupContext the context to apply when doing the cache lookup.
     * @return the created command.
     */
    static RetrieveThing retrieveThing(final ThingId thingId, @Nullable final CacheLookupContext cacheLookupContext) {
        LOGGER.debug("Sending RetrieveThing for Thing with ID <{}>", thingId);
        return RetrieveThing.getBuilder(thingId,
                Optional.ofNullable(cacheLookupContext).flatMap(CacheLookupContext::getDittoHeaders).orElseGet(() ->
                        DittoHeaders.newBuilder().correlationId(getCorrelationId(thingId)).build())
        )
                .withSelectedFields(
                        Optional.ofNullable(cacheLookupContext).flatMap(CacheLookupContext::getJsonFieldSelector)
                                .orElse(null)
                )
                .build();
    }

    private static String getCorrelationId(final ThingId thingId) {
        return LogUtil.getCorrelationId(() -> {
            final String correlationId = UUID.randomUUID().toString();
            LOGGER.debug("Found no correlation-id for (Sudo)RetrieveThing on Thing <{}>. " +
                    "Using new correlation-id: {}", thingId, correlationId);
            return correlationId;
        });
    }

}
