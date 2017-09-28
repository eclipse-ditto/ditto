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
package org.eclipse.ditto.services.gateway.proxy.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.models.things.ThingCacheEntry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import akka.actor.ActorRef;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import scala.concurrent.duration.FiniteDuration;

/**
 * A {@link EnforcerLookupFunction} for retrieving a {@link Thing} from the shard region.
 */
public final class ThingEnforcerLookupFunction implements EnforcerLookupFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingEnforcerLookupFunction.class);
    private static final Timeout ASK_TIMEOUT = Timeout.apply(FiniteDuration.apply(5, TimeUnit.SECONDS));

    private final ActorRef thingsShardRegion;
    private final ActorRef aclEnforcerShardRegion;
    private final ActorRef policyEnforcerShardRegion;
    private final Executor dispatcher;

    private ThingEnforcerLookupFunction(final ActorRef thingsShardRegion,
            final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final Executor dispatcher) {

        this.thingsShardRegion = checkNotNull(thingsShardRegion, "Things Shard Region");
        this.aclEnforcerShardRegion = checkNotNull(aclEnforcerShardRegion, "ACL enforcer Shard Region");
        this.policyEnforcerShardRegion = checkNotNull(policyEnforcerShardRegion, "PolicyEnforcer Shard Region");
        this.dispatcher = checkNotNull(dispatcher, "dispatcher");
    }

    /**
     * Returns a new {@code ThingEnforcerLookupFunction} for the given arguments.
     *
     * @param thingsShardRegion shard region to look up things.
     * @param aclEnforcerShardRegion shard region to look up actor refs for ACL enforcers.
     * @param policyEnforcerShardRegion shard region to look up actor refs for policy enforcers.
     * @param dispatcher dispatcher to execute futures.
     * @return the new ThingEnforcerLookupFunction.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ThingEnforcerLookupFunction of(final ActorRef thingsShardRegion,
            final ActorRef aclEnforcerShardRegion,
            final ActorRef policyEnforcerShardRegion,
            final Executor dispatcher) {

        return new ThingEnforcerLookupFunction(thingsShardRegion, aclEnforcerShardRegion,
                policyEnforcerShardRegion, dispatcher);
    }

    @Override
    public CompletionStage<LookupResult> lookup(final CharSequence thingId, final CharSequence correlationId) {
        argumentNotEmpty(thingId, "thing ID");
        argumentNotEmpty(correlationId, "correlation ID");

        return retrieveThing(thingId.toString(), correlationId.toString())
                .handle((thing, throwable) -> {
                    enhanceLogWithCorrelationId(correlationId.toString());
                    if (throwable != null) {
                        final Throwable error = (throwable instanceof CompletionException)
                                ? throwable.getCause()
                                : throwable;
                        LOGGER.error("Got Throwable when tried to retrieve thing with ID <{}>!", thingId.toString(),
                                error);
                        return LookupResult.withError(error);
                    } else if (thing != null) {
                        final Optional<String> policyId = thing.getPolicyId();
                        final Optional<AccessControlList> accessControlList = thing.getAccessControlList();
                        final Long revision = thing.getRevision()
                                .map(ThingRevision::toLong)
                                .orElseThrow(() -> {
                                    final String msgTemplate = "Thing <{0}> has no revision which is not an allowed" +
                                            " state for the cache entry!";
                                    return new IllegalStateException(MessageFormat.format(msgTemplate, thingId));
                                });
                        final ThingCacheEntry cacheEntry = ThingCacheEntry.of(thing.getImplementedSchemaVersion(),
                                policyId.orElse(null), revision);
                        if (policyId.isPresent()) {
                            return LookupResult.of(policyId.get(), cacheEntry, policyEnforcerShardRegion);
                        } else if (accessControlList.isPresent()) {
                            return LookupResult.of(thingId.toString(), cacheEntry, aclEnforcerShardRegion);
                        } else {
                            LOGGER.warn("Neither a policy ID nor an ACL was present for Thing <{}>!", thing);
                            return LookupResult.notFound();
                        }
                    } else {
                        LOGGER.info("Thing with ID <{}> could not be looked up.", thingId.toString());
                        return LookupResult.notFound();
                    }
                });
    }

    private CompletionStage<Thing> retrieveThing(final String thingId, final String correlationId) {
        final JsonFieldSelector jsonFieldSelector = JsonFactory.newFieldSelector(Thing.JsonFields.POLICY_ID,
                Thing.JsonFields.ACL, Thing.JsonFields.REVISION);
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .build();
        final SudoRetrieveThing sudoRetrieveThing = SudoRetrieveThing.withOriginalSchemaVersion(thingId,
                jsonFieldSelector, dittoHeaders);

        return PatternsCS.ask(thingsShardRegion, sudoRetrieveThing, ASK_TIMEOUT)
                .handleAsync((response, throwable) -> {
                    enhanceLogWithCorrelationId(correlationId);
                    if (throwable != null) {
                        throw (throwable instanceof RuntimeException)
                                ? (RuntimeException) throwable
                                : new CompletionException(throwable);
                    }
                    if (response instanceof SudoRetrieveThingResponse) {
                        return ((SudoRetrieveThingResponse) response).getThing();
                    } else if (response instanceof ThingNotAccessibleException) {
                        LOGGER.debug("Got ThingNotAccessibleException: <{}>", ((ThingNotAccessibleException) response)
                                .getMessage());
                        return null;
                    } else if (response instanceof DittoRuntimeException) {
                        LOGGER.warn("Got unexpected <{}> when asking thingsShardRegion for SudoRetrieveThing: <{}>!",
                                response.getClass().getSimpleName(), ((Throwable) response).getMessage());
                        throw (DittoRuntimeException) response;
                    } else {
                        LOGGER.warn("Got unexpected response when asking thingsShardRegion for SudoRetrieveThing: " +
                                "<{}>!", response);
                        return null;
                    }
                }, dispatcher);
    }

    private static void enhanceLogWithCorrelationId(final String correlationId) {
        MDC.clear();
        final Map<String, String> mdcMap = new HashMap<>();
        mdcMap.put("x-correlation-id", correlationId);
        MDC.setContextMap(mdcMap);
    }

}
