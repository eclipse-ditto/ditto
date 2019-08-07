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
package org.eclipse.ditto.services.gateway.endpoints.routes.policies;

import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyIdNotExplicitlySettableException;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /policies}.
 */
public final class PoliciesRoute extends AbstractRoute {

    private static final String PATH_POLICIES = "policies";
    private static final String PATH_ENTRIES = "entries";

    private final PolicyEntriesRoute policyEntriesRoute;

    /**
     * Constructs the {@code /policies} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public PoliciesRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final HeaderTranslator headerTranslator) {

        super(proxyActor, actorSystem, httpConfig, headerTranslator);

        policyEntriesRoute = new PolicyEntriesRoute(proxyActor, actorSystem, httpConfig, headerTranslator);
    }

    /**
     * Builds the {@code /policies} route.
     *
     * @return the {@code /policies} route.
     */
    public Route buildPoliciesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_POLICIES), () ->
                rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), policyId -> // /policies/<policyId>
                        concat(
                                policyEntry(ctx, dittoHeaders, policyId),
                                policyEntryEntries(ctx, dittoHeaders, policyId)
                        )
                )
        );
    }

    /*
     * Describes {@code /policies/<policyId>} route.
     * @return {@code /policies/<policyId>} route.
     */
    private Route policyEntry(final RequestContext ctx, final DittoHeaders dittoHeaders, final String policyId) {
        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /policies/<policyId>
                                handlePerRequest(ctx, RetrievePolicy.of(policyId, dittoHeaders))
                        ),
                        put(() -> // PUT /policies/<policyId>
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                policyJson -> ModifyPolicy
                                                        .of(policyId, PoliciesModelFactory.newPolicy(
                                                                createPolicyJsonObjectForPut(policyJson, policyId)),
                                                                dittoHeaders)
                                        )
                                )
                        ),
                        delete(() -> // DELETE /policies/<policyId>
                                handlePerRequest(ctx, DeletePolicy.of(policyId, dittoHeaders))
                        )
                )
        );
    }

    private static JsonObject createPolicyJsonObjectForPut(final String jsonString, final String policyId) {
        final JsonObject policyJsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        policyJsonObject.getValue(Policy.JsonFields.ID.getPointer())
                .ifPresent(policyIdJsonValue -> {
                    if (!policyIdJsonValue.isString() || !policyId.equals(policyIdJsonValue.asString())) {
                        throw PolicyIdNotExplicitlySettableException.newBuilder().build();
                    }
                });
        return policyJsonObject.setValue(Policy.JsonFields.ID.getPointer(), JsonValue.of(policyId));
    }

    /*
     * Describes {@code /policies/<policyId>/entries} route.
     *
     * @return {@code /policies/<policyId>/entries} route.
     */
    private Route policyEntryEntries(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_ENTRIES), () -> // /policies/<policyId>/entries
                policyEntriesRoute.buildPolicyEntriesRoute(ctx, dittoHeaders, policyId)
        );
    }

}
