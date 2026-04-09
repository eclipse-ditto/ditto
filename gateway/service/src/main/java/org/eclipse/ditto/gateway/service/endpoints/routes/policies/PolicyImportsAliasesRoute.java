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
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.ImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveImportsAliases;

import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;

/**
 * Builder for creating Pekko HTTP routes for Policy {@code /importsAliases}.
 *
 * @since 3.9.0
 */
final class PolicyImportsAliasesRoute extends AbstractRoute {

    /**
     * Constructs the {@code /importsAliases} route builder.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    PolicyImportsAliasesRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
    }

    /**
     * Builds the {@code /importsAliases} route.
     *
     * @return the {@code /importsAliases} route.
     */
    Route buildImportsAliasesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {
        return concat(
                importsAliases(ctx, dittoHeaders, policyId),
                importsAlias(ctx, dittoHeaders, policyId)
        );
    }

    /*
     * Describes {@code /importsAliases} route.
     */
    private Route importsAliases(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /importsAliases
                                handlePerRequest(ctx,
                                        RetrieveImportsAliases.of(policyId, dittoHeaders))
                        ),
                        put(() -> // PUT /importsAliases
                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        importsAliasesJson -> {
                                                            final JsonObject jsonObject =
                                                                    wrapJsonRuntimeException(() ->
                                                                            JsonFactory.newObject(importsAliasesJson));
                                                            final ImportsAliases importsAliases =
                                                                    PoliciesModelFactory.newImportsAliases(jsonObject);
                                                            return ModifyImportsAliases.of(policyId, importsAliases,
                                                                    dittoHeaders);
                                                        }
                                                )
                                )
                        ),
                        delete(() -> // DELETE /importsAliases
                                handlePerRequest(ctx,
                                        DeleteImportsAliases.of(policyId, dittoHeaders))
                        )
                )
        );
    }

    /*
     * Describes {@code /importsAliases/{label}} route.
     */
    private Route importsAlias(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), labelString ->
                pathEndOrSingleSlash(() -> {
                    final Label label = Label.of(labelString);
                    return concat(
                            get(() -> // GET /importsAliases/{label}
                                    handlePerRequest(ctx,
                                            RetrieveImportsAlias.of(policyId, label, dittoHeaders))
                            ),
                            put(() -> // PUT /importsAliases/{label}
                                    ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                            payloadSource ->
                                                    handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                            importsAliasJson -> {
                                                                final JsonObject jsonObject =
                                                                        wrapJsonRuntimeException(() ->
                                                                                JsonFactory.newObject(
                                                                                        importsAliasJson));
                                                                final ImportsAlias importsAlias =
                                                                        PoliciesModelFactory.newImportsAlias(label,
                                                                                jsonObject);
                                                                return ModifyImportsAlias.of(policyId, importsAlias,
                                                                        dittoHeaders);
                                                            }
                                                    )
                                    )
                            ),
                            delete(() -> // DELETE /importsAliases/{label}
                                    handlePerRequest(ctx,
                                            DeleteImportsAlias.of(policyId, label, dittoHeaders))
                            )
                    );
                })
        );
    }

}
