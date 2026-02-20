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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.EntryAddition;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntriesAdditions;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntriesAdditions;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImports;

import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;

/**
 * Builder for creating Pekko HTTP routes for Policy {@code /imports}.
 */
final class PolicyImportsRoute extends AbstractRoute {

    private static final String PATH_SUFFIX_ENTRIES = "entries";
    private static final String PATH_SUFFIX_ENTRIES_ADDITIONS = "entriesAdditions";

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
                policyImportEntriesAdditions(ctx, dittoHeaders, policyId),
                policyImportEntriesAdditionsEntry(ctx, dittoHeaders, policyId),
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
     * Describes {@code /imports/<importedPolicyId>/entriesAdditions} route.
     */
    private Route policyImportEntriesAdditions(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), importedPolicyId ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_ENTRIES_ADDITIONS), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        get(() -> // GET /imports/<importedPolicyId>/entriesAdditions
                                                handlePerRequest(ctx,
                                                        RetrievePolicyImportEntriesAdditions.of(policyId,
                                                                PolicyId.of(importedPolicyId), dittoHeaders))
                                        ),
                                        put(() -> // PUT /imports/<importedPolicyId>/entriesAdditions
                                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource ->
                                                                handlePerRequest(ctx, dittoHeaders,
                                                                        payloadSource,
                                                                        additionsJson ->
                                                                                ModifyPolicyImportEntriesAdditions.of(
                                                                                        policyId,
                                                                                        PolicyId.of(importedPolicyId),
                                                                                        createEntriesAdditionsForPut(additionsJson),
                                                                                        dittoHeaders)))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /imports/<importedPolicyId>/entriesAdditions/<label>} route.
     */
    private Route policyImportEntriesAdditionsEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), importedPolicyId ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_ENTRIES_ADDITIONS), () ->
                        rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), label ->
                                pathEndOrSingleSlash(() ->
                                        concat(
                                                get(() -> // GET /imports/<id>/entriesAdditions/<label>
                                                        handlePerRequest(ctx,
                                                                RetrievePolicyImportEntryAddition.of(policyId,
                                                                        PolicyId.of(importedPolicyId),
                                                                        Label.of(label), dittoHeaders))
                                                ),
                                                put(() -> // PUT /imports/<id>/entriesAdditions/<label>
                                                        ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                                dittoHeaders,
                                                                payloadSource ->
                                                                        handlePerRequest(ctx, dittoHeaders,
                                                                                payloadSource,
                                                                                additionJson ->
                                                                                        ModifyPolicyImportEntryAddition.of(
                                                                                                policyId,
                                                                                                PolicyId.of(importedPolicyId),
                                                                                                createEntryAdditionForPut(additionJson, label),
                                                                                                dittoHeaders)))
                                                ),
                                                delete(() -> // DELETE /imports/<id>/entriesAdditions/<label>
                                                        handlePerRequest(ctx,
                                                                DeletePolicyImportEntryAddition.of(policyId,
                                                                        PolicyId.of(importedPolicyId),
                                                                        Label.of(label), dittoHeaders))
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static EntriesAdditions createEntriesAdditionsForPut(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return PoliciesModelFactory.newEntriesAdditions(jsonObject);
    }

    private static EntryAddition createEntryAdditionForPut(final String jsonString, final CharSequence label) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return PoliciesModelFactory.newEntryAddition(Label.of(label), jsonObject);
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
