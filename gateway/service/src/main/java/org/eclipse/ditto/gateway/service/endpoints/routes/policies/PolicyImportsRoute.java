/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportTransitiveImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportTransitiveImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImports;

import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;

/**
 * Builder for creating Pekko HTTP routes for Policy {@code /imports}.
 */
final class PolicyImportsRoute extends AbstractRoute {

    private static final String PATH_SUFFIX_ENTRIES = "entries";
    private static final String PATH_SUFFIX_TRANSITIVE_IMPORTS = "transitiveImports";

    /**
     * Constructs the {@code /imports} route builder.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    PolicyImportsRoute(final RouteBaseProperties routeBaseProperties) {

        super(routeBaseProperties);
    }

    /**
     * Builds the {@code /imports} route.
     *
     * @return the {@code /imports} route.
     */
    Route buildPolicyImportsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders, final PolicyId policyId) {
        return concat(
                policyImports(ctx, dittoHeaders, policyId),
                policyImportEntries(ctx, dittoHeaders, policyId),
                policyImportTransitiveImports(ctx, dittoHeaders, policyId),
                policyImport(ctx, dittoHeaders, policyId)
        );
    }

    /*
     * Describes {@code /imports} route.
     *
     * @return {@code /imports} route.
     */
    private Route policyImports(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /imports
                                handlePerRequest(ctx,
                                        RetrievePolicyImports.of(policyId, dittoHeaders))
                        ),
                        put(() -> // PUT /imports
                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        policyImportsJson ->
                                                                ModifyPolicyImports.of(policyId,
                                                                        createPolicyImportsForPut(policyImportsJson),
                                                                        dittoHeaders))
                                )
                        ),
                        delete(() -> // DELETE /imports
                                handlePerRequest(ctx,
                                        DeletePolicyImports.of(policyId, dittoHeaders))
                        )
                )
        );
    }

    private static PolicyImports createPolicyImportsForPut(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return PoliciesModelFactory.newPolicyImports(jsonObject);
    }

    /*
     * Describes {@code /imports/<importedPolicyId>/entries} route.
     */
    private Route policyImportEntries(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), importedPolicyId ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_ENTRIES), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        get(() -> // GET /imports/<importedPolicyId>/entries
                                                handlePerRequest(ctx,
                                                        RetrievePolicyImportEntries.of(policyId,
                                                                PolicyId.of(importedPolicyId), dittoHeaders))
                                        ),
                                        put(() -> // PUT /imports/<importedPolicyId>/entries
                                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource ->
                                                                handlePerRequest(ctx, dittoHeaders,
                                                                        payloadSource,
                                                                        entriesJson ->
                                                                                ModifyPolicyImportEntries.of(
                                                                                        policyId,
                                                                                        PolicyId.of(importedPolicyId),
                                                                                        createImportedLabelsForPut(entriesJson),
                                                                                        dittoHeaders)))
                                        )
                                )
                        )
                )
        );
    }

    private static ImportedLabels createImportedLabelsForPut(final String jsonString) {
        final JsonArray jsonArray = wrapJsonRuntimeException(() -> JsonFactory.newArray(jsonString));
        return PoliciesModelFactory.newImportedEntries(jsonArray);
    }

    /*
     * Describes {@code /imports/<importedPolicyId>/transitiveImports} route.
     */
    private Route policyImportTransitiveImports(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), importedPolicyId ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_TRANSITIVE_IMPORTS), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        get(() -> // GET /imports/<importedPolicyId>/transitiveImports
                                                handlePerRequest(ctx,
                                                        RetrievePolicyImportTransitiveImports.of(policyId,
                                                                PolicyId.of(importedPolicyId), dittoHeaders))
                                        ),
                                        put(() -> // PUT /imports/<importedPolicyId>/transitiveImports
                                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource ->
                                                                handlePerRequest(ctx, dittoHeaders,
                                                                        payloadSource,
                                                                        transitiveImportsJson ->
                                                                                ModifyPolicyImportTransitiveImports.of(
                                                                                        policyId,
                                                                                        PolicyId.of(importedPolicyId),
                                                                                        createTransitiveImportsForPut(transitiveImportsJson),
                                                                                        dittoHeaders)))
                                        )
                                )
                        )
                )
        );
    }

    private static List<PolicyId> createTransitiveImportsForPut(final String jsonString) {
        final JsonArray jsonArray = wrapJsonRuntimeException(() -> JsonFactory.newArray(jsonString));
        jsonArray.forEach(element -> {
            if (!element.isString()) {
                throw PolicyImportInvalidException.newBuilder()
                        .message("The 'transitiveImports' array contains a non-string element: " + element)
                        .description("Every element in 'transitiveImports' must be a string " +
                                "representing a valid policy ID.")
                        .build();
            }
        });
        return jsonArray.stream()
                .map(JsonValue::asString)
                .map(PolicyId::of)
                .collect(Collectors.toList());
    }

    /*
     * Describes {@code /imports/<importedPolicyId>} route.
     *
     * @return {@code /imports/<importedPolicyId>} route.
     */
    private Route policyImport(final RequestContext ctx, final DittoHeaders dittoHeaders,  final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), importedPolicyId ->
                pathEndOrSingleSlash(() ->
                        concat(
                                get(() -> // GET /imports/<importedPolicyId>
                                        handlePerRequest(ctx,
                                                RetrievePolicyImport.of(policyId,
                                                        PolicyId.of(importedPolicyId),
                                                        dittoHeaders))
                                ),
                                put(() -> // PUT /imports/<importedPolicyId>
                                        ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource ->
                                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                policyImportJson -> ModifyPolicyImport
                                                                        .of(policyId,
                                                                                createPolicyImportForPut(
                                                                                        policyImportJson,
                                                                                        importedPolicyId),
                                                                                dittoHeaders))
                                        )
                                ),
                                delete(() -> // DELETE /imports/<importedPolicyId>
                                        handlePerRequest(ctx,
                                                DeletePolicyImport.of(policyId, PolicyId.of(
                                                        importedPolicyId),
                                                        dittoHeaders)))
                        )
                )
        );
    }

    private static PolicyImport createPolicyImportForPut(final String jsonString,
            final CharSequence importedPolicyString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return PoliciesModelFactory.newPolicyImport(PolicyId.of(importedPolicyString), jsonObject);
    }
}
