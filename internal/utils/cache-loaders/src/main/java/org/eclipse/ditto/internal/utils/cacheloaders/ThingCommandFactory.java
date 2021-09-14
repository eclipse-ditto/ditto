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
package org.eclipse.ditto.internal.utils.cacheloaders;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.internal.utils.cache.CacheLookupContext;
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
                Optional.ofNullable(cacheLookupContext).flatMap(CacheLookupContext::getDittoHeaders)
                        .map(headers -> DittoHeaders.newBuilder()
                                .authorizationContext(headers.getAuthorizationContext())
                                .schemaVersion(headers.getImplementedSchemaVersion())
                                .correlationId("sudoRetrieveThing-" +
                                        headers.getCorrelationId().orElseGet(() -> UUID.randomUUID().toString()))
                                .putHeader(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(), "true")
                                .build()
                        )
                        .orElseGet(() ->
                                DittoHeaders.newBuilder()
                                        .correlationId("sudoRetrieveThing-" + UUID.randomUUID().toString())
                                        .putHeader(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(), "true")
                                        .build())
        );
    }

}
