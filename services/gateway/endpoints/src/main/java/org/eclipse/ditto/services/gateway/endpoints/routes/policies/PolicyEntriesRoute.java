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
import static org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute.extractJwt;

import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.signals.commands.policies.actions.ActivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResource;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntries;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResource;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResources;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResources;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjects;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for Policy {@code /entries}.
 */
final class PolicyEntriesRoute extends AbstractRoute {

    private static final String PATH_SUFFIX_SUBJECTS = "subjects";
    private static final String PATH_SUFFIX_RESOURCES = "resources";

    private static final String PATH_ACTIONS = "actions";

    private final TokenIntegrationSubjectIdFactory tokenIntegrationSubjectIdFactory;

    /**
     * Constructs the {@code /entries} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param commandConfig the configuration settings of the Gateway service's incoming command processing.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param tokenIntegrationSubjectIdFactory factory of token integration subject IDs.
     * @throws NullPointerException if any argument is {@code null}.
     */
    PolicyEntriesRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig,
            final HeaderTranslator headerTranslator,
            final TokenIntegrationSubjectIdFactory tokenIntegrationSubjectIdFactory) {

        super(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator);
        this.tokenIntegrationSubjectIdFactory = tokenIntegrationSubjectIdFactory;
    }

    /**
     * Builds the {@code /entries} route.
     *
     * @return the {@code /entries} route.
     */
    Route buildPolicyEntriesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders, final PolicyId policyId,
            final AuthenticationResult authResult) {
        return concat(
                policyEntries(ctx, dittoHeaders, policyId),
                policyEntry(ctx, dittoHeaders, policyId),
                policyEntrySubjects(ctx, dittoHeaders, policyId),
                policyEntrySubjectsEntry(ctx, dittoHeaders, policyId),
                policyEntryResources(ctx, dittoHeaders, policyId),
                policyEntryResourcesEntry(ctx, dittoHeaders, policyId),
                policyEntryActions(ctx, dittoHeaders, policyId, authResult)
        );
    }

    /*
     * Describes {@code /entries} route.
     *
     * @return {@code /entries} route.
     */
    private Route policyEntries(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /entries
                                handlePerRequest(ctx,
                                        RetrievePolicyEntries.of(policyId, dittoHeaders))
                        ),
                        put(() -> // PUT /entries
                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        policyEntriesJson ->
                                                                ModifyPolicyEntries.of(policyId,
                                                                        PoliciesModelFactory.newPolicyEntries(
                                                                                policyEntriesJson),
                                                                        dittoHeaders))
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /entries/<label>} route.
     *
     * @return {@code /entries/<label>} route.
     */
    private Route policyEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), label ->
                pathEndOrSingleSlash(() ->
                        concat(
                                get(() -> // GET /entries/<label>
                                        handlePerRequest(ctx,
                                                RetrievePolicyEntry.of(policyId,
                                                        Label.of(label),
                                                        dittoHeaders))
                                ),
                                put(() -> // PUT /entries/<label>
                                        ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource ->
                                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                policyEntryJson -> ModifyPolicyEntry
                                                                        .of(policyId,
                                                                                createPolicyEntryForPut(policyEntryJson,
                                                                                        label), dittoHeaders))
                                        )
                                ),
                                delete(() -> // DELETE /entries/<label>
                                        handlePerRequest(ctx,
                                                DeletePolicyEntry.of(policyId, Label.of(label),
                                                        dittoHeaders)))
                        )
                )
        );
    }

    private static PolicyEntry createPolicyEntryForPut(final String jsonString, final CharSequence labelString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        final Subjects subjects =
                PoliciesModelFactory.newSubjects(jsonObject.getValueOrThrow(PolicyEntry.JsonFields.SUBJECTS));
        final Resources resources =
                PoliciesModelFactory.newResources(jsonObject.getValueOrThrow(PolicyEntry.JsonFields.RESOURCES));

        return PoliciesModelFactory.newPolicyEntry(Label.of(labelString), subjects, resources);
    }

    /*
     * Describes {@code /entries/<label>/subjects} route.
     *
     * @return {@code /entries/<label>/subjects} route.
     */
    private Route policyEntrySubjects(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), label ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_SUBJECTS), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        get(() -> // GET /entries/<label>/subjects
                                                handlePerRequest(ctx, RetrieveSubjects.of(policyId,
                                                        Label.of(label), dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/subjects
                                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource ->
                                                                handlePerRequest(ctx, dittoHeaders,
                                                                        payloadSource, subjectsJson ->
                                                                                ModifySubjects.of(policyId,
                                                                                        Label.of(label),
                                                                                        PoliciesModelFactory.newSubjects(
                                                                                                JsonFactory.newObject(
                                                                                                        subjectsJson)),
                                                                                        dittoHeaders)))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /entries/<label>/subjects/<subjectId>} route.
     *
     * @return {@code /entries/<label>/subjects/<subjectId>} route.
     */
    private Route policyEntrySubjectsEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), label ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_SUBJECTS), () ->
                        rawPathPrefix(PathMatchers.slash().concat(PathMatchers.remaining()), subjectId ->
                                concat(
                                        get(() -> // GET /entries/<label>/subjects/<subjectId>
                                                handlePerRequest(ctx, RetrieveSubject.of(policyId,
                                                        Label.of(label),
                                                        SubjectId.newInstance(subjectId),
                                                        dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/subjects/<subjectId>
                                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource ->
                                                                handlePerRequest(ctx, dittoHeaders,
                                                                        payloadSource,
                                                                        subjectJson -> ModifySubject
                                                                                .of(policyId, Label.of(
                                                                                        label),
                                                                                        createSubjectForPut(subjectJson,
                                                                                                subjectId),
                                                                                        dittoHeaders))
                                                )
                                        ),
                                        delete(() -> // DELETE /entries/<label>/subjects/<subjectId>
                                                handlePerRequest(ctx, DeleteSubject.of(policyId,
                                                        Label.of(label),
                                                        SubjectId.newInstance(subjectId),
                                                        dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    private static Subject createSubjectForPut(final String jsonString, final CharSequence subjectId) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return PoliciesModelFactory.newSubject(SubjectId.newInstance(subjectId), jsonObject);
    }

    /*
     * Describes {@code /entries/<label>/resources} route.
     *
     * @return {@code /entries/<label>/resources} route.
     */
    private Route policyEntryResources(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), label ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_RESOURCES), () ->
                        pathEndOrSingleSlash(() ->
                                concat(
                                        get(() -> // GET /entries/<label>/resources
                                                handlePerRequest(ctx, RetrieveResources.of(policyId,
                                                        Label.of(label), dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/resources
                                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource ->
                                                                handlePerRequest(ctx, dittoHeaders,
                                                                        payloadSource,
                                                                        policyEntryResourcesJson ->
                                                                                ModifyResources.of(policyId,
                                                                                        Label.of(
                                                                                                label),
                                                                                        PoliciesModelFactory.newResources(
                                                                                                JsonFactory.newObject(
                                                                                                        policyEntryResourcesJson)),
                                                                                        dittoHeaders)))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /entries/<label>/resources/<resource>} route.
     *
     * @return {@code /entries/<label>/resources/<resource>} route.
     */
    private Route policyEntryResourcesEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), label ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_SUFFIX_RESOURCES), () ->
                        extractUnmatchedPath(resource ->
                                concat(
                                        get(() -> // GET /entries/<label>/resources/<resource>
                                                handlePerRequest(ctx, RetrieveResource.of(policyId,
                                                        Label.of(label),
                                                        resourceKeyFromUnmatchedPath(resource),
                                                        dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/resources/<resource>
                                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx,
                                                        dittoHeaders,
                                                        payloadSource ->
                                                                handlePerRequest(ctx, dittoHeaders,
                                                                        payloadSource, resourceJson ->
                                                                                ModifyResource.of(policyId,
                                                                                        Label.of(
                                                                                                label),
                                                                                        createResourceForPut(
                                                                                                resourceJson,
                                                                                                resourceKeyFromUnmatchedPath(
                                                                                                        resource)),
                                                                                        dittoHeaders))
                                                )
                                        ),
                                        delete(() -> // DELETE /entries/<label>/resources/<resource>
                                                handlePerRequest(ctx, DeleteResource.of(policyId,
                                                        Label.of(label),
                                                        resourceKeyFromUnmatchedPath(resource),
                                                        dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /entries/<label>/actions} route.
     */
    private Route policyEntryActions(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final PolicyId policyId, final AuthenticationResult authResult) {

        return rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), label ->
                rawPathPrefix(PathMatchers.slash().concat(PATH_ACTIONS), () -> concat(
                        // POST /entries/<label>/actions/activateTokenIntegration
                        rawPathPrefix(PathMatchers.slash().concat(ActivateTokenIntegration.NAME), () ->
                                pathEndOrSingleSlash(() ->
                                        extractJwt(dittoHeaders, authResult, ActivateTokenIntegration.NAME, jwt ->
                                                post(() -> handlePerRequest(ctx, activateTokenIntegration(
                                                        dittoHeaders, policyId, label, jwt)))
                                        )
                                )
                        ),
                        // POST /entries/<label>/actions/deactivateTokenIntegration
                        rawPathPrefix(PathMatchers.slash().concat(DeactivateTokenIntegration.NAME), () ->
                                pathEndOrSingleSlash(() ->
                                        extractJwt(dittoHeaders, authResult, DeactivateTokenIntegration.NAME, jwt ->
                                                post(() -> handlePerRequest(ctx, deactivateTokenIntegration(
                                                        dittoHeaders, policyId, label, jwt)))
                                        )
                                )
                        )
                ))
        );
    }

    private ActivateTokenIntegration activateTokenIntegration(final DittoHeaders dittoHeaders, final PolicyId policyId,
            final String label, final JsonWebToken jwt) {
        final Set<SubjectId> subjectIds = tokenIntegrationSubjectIdFactory.getSubjectIds(dittoHeaders, jwt);
        return ActivateTokenIntegration.of(policyId, Label.of(label), subjectIds, jwt.getExpirationTime(), dittoHeaders);
    }

    private DeactivateTokenIntegration deactivateTokenIntegration(final DittoHeaders dittoHeaders,
            final PolicyId policyId, final String label, final JsonWebToken jwt) {
        final Set<SubjectId> subjectIds = tokenIntegrationSubjectIdFactory.getSubjectIds(dittoHeaders, jwt);
        return DeactivateTokenIntegration.of(policyId, Label.of(label), subjectIds, dittoHeaders);
    }

    private static ResourceKey resourceKeyFromUnmatchedPath(final String resource) {
        // cut off leading "/" if there is one:
        return resource.startsWith("/")
                ? ResourceKey.newInstance(resource.substring(1))
                : ResourceKey.newInstance(resource);
    }

    private static Resource createResourceForPut(final String jsonString, final ResourceKey resourceKey) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return PoliciesModelFactory.newResource(resourceKey, jsonObject);
    }

}
