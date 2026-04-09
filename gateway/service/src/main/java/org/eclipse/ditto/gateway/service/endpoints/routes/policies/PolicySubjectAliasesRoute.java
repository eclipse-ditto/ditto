/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.policies;

import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectAlias;
import org.eclipse.ditto.policies.model.SubjectAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectAliases;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectAliases;

import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;

/**
 * Builder for creating Pekko HTTP routes for Policy {@code /subjectAliases}.
 *
 * @since 3.9.0
 */
final class PolicySubjectAliasesRoute extends AbstractRoute {

    /**
     * Constructs the {@code /subjectAliases} route builder.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    PolicySubjectAliasesRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
    }

    /**
     * Builds the {@code /subjectAliases} route.
     *
     * @return the {@code /subjectAliases} route.
     */
    Route buildSubjectAliasesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {
        return concat(
                subjectAliases(ctx, dittoHeaders, policyId),
                subjectAlias(ctx, dittoHeaders, policyId)
        );
    }

    /*
     * Describes {@code /subjectAliases} route.
     */
    private Route subjectAliases(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /subjectAliases
                                handlePerRequest(ctx,
                                        RetrieveSubjectAliases.of(policyId, dittoHeaders))
                        ),
                        put(() -> // PUT /subjectAliases
                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        subjectAliasesJson -> {
                                                            final JsonObject jsonObject =
                                                                    wrapJsonRuntimeException(() ->
                                                                            JsonFactory.newObject(subjectAliasesJson));
                                                            final SubjectAliases subjectAliases =
                                                                    PoliciesModelFactory.newSubjectAliases(jsonObject);
                                                            return ModifySubjectAliases.of(policyId, subjectAliases,
                                                                    dittoHeaders);
                                                        }
                                                )
                                )
                        ),
                        delete(() -> // DELETE /subjectAliases
                                handlePerRequest(ctx,
                                        DeleteSubjectAliases.of(policyId, dittoHeaders))
                        )
                )
        );
    }

    /*
     * Describes {@code /subjectAliases/{label}} route.
     */
    private Route subjectAlias(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), labelString ->
                pathEndOrSingleSlash(() -> {
                    final Label label = Label.of(labelString);
                    return concat(
                            get(() -> // GET /subjectAliases/{label}
                                    handlePerRequest(ctx,
                                            RetrieveSubjectAlias.of(policyId, label, dittoHeaders))
                            ),
                            put(() -> // PUT /subjectAliases/{label}
                                    ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                            payloadSource ->
                                                    handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                            subjectAliasJson -> {
                                                                final JsonObject jsonObject =
                                                                        wrapJsonRuntimeException(() ->
                                                                                JsonFactory.newObject(
                                                                                        subjectAliasJson));
                                                                final SubjectAlias subjectAlias =
                                                                        PoliciesModelFactory.newSubjectAlias(label,
                                                                                jsonObject);
                                                                return ModifySubjectAlias.of(policyId, subjectAlias,
                                                                        dittoHeaders);
                                                            }
                                                    )
                                    )
                            ),
                            delete(() -> // DELETE /subjectAliases/{label}
                                    handlePerRequest(ctx,
                                            DeleteSubjectAlias.of(policyId, label, dittoHeaders))
                            )
                    );
                })
        );
    }

}
