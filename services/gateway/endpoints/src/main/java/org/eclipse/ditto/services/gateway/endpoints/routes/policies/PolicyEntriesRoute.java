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
package org.eclipse.ditto.services.gateway.endpoints.routes.policies;

import static akka.http.javadsl.server.Directives.delete;
import static akka.http.javadsl.server.Directives.extractDataBytes;
import static akka.http.javadsl.server.Directives.extractUnmatchedPath;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.put;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
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
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for Policy {@code /entries}.
 */
class PolicyEntriesRoute extends AbstractRoute {

    private static final String PATH_SUFFIX_SUBJECTS = "subjects";
    private static final String PATH_SUFFIX_RESOURCES = "resources";

    /**
     * Constructs the {@code /entries} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @throws NullPointerException if any argument is {@code null}.
     */
    PolicyEntriesRoute(final ActorRef proxyActor, final ActorSystem actorSystem) {
        super(proxyActor, actorSystem);
    }

    /**
     * Builds the {@code /entries} route.
     *
     * @return the {@code /entries} route.
     */
    Route buildPolicyEntriesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return Directives.route(
                thingsEntryPolicyEntries(ctx, dittoHeaders, policyId),
                thingsEntryPolicyEntry(ctx, dittoHeaders, policyId),
                thingsEntryPolicyEntrySubjects(ctx, dittoHeaders, policyId),
                thingsEntryPolicyEntrySubjectsEntry(ctx, dittoHeaders, policyId),
                thingsEntryPolicyEntryResources(ctx, dittoHeaders, policyId),
                thingsEntryPolicyEntryResourcesEntry(ctx, dittoHeaders, policyId)
        );
    }

    /*
     * Describes {@code /entries} route.
     *
     * @return {@code /entries} route.
     */
    private Route thingsEntryPolicyEntries(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return pathEndOrSingleSlash(() ->
                Directives.route(
                        get(() -> // GET /entries
                                handlePerRequest(ctx,
                                        RetrievePolicyEntries.of(policyId, dittoHeaders))
                        ),
                        put(() -> // PUT /entries
                                extractDataBytes(payloadSource ->
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
    private Route thingsEntryPolicyEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), label ->
                pathEndOrSingleSlash(() ->
                        route(
                                get(() -> // GET /entries/<label>
                                        handlePerRequest(ctx,
                                                RetrievePolicyEntry.of(policyId,
                                                        Label.of(label),
                                                        dittoHeaders))
                                ),
                                put(() -> // PUT /entries/<label>
                                        extractDataBytes(payloadSource ->
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
    private Route thingsEntryPolicyEntrySubjects(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), label ->
                rawPathPrefix(mergeDoubleSlashes().concat(PATH_SUFFIX_SUBJECTS), () ->
                        pathEndOrSingleSlash(() ->
                                route(
                                        get(() -> // GET /entries/<label>/subjects
                                                handlePerRequest(ctx, RetrieveSubjects.of(policyId,
                                                        Label.of(label), dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/subjects
                                                extractDataBytes(payloadSource ->
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
    private Route thingsEntryPolicyEntrySubjectsEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), label ->
                rawPathPrefix(mergeDoubleSlashes().concat(PATH_SUFFIX_SUBJECTS), () ->
                        rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.remaining()), subjectId ->
                                route(
                                        get(() -> // GET /entries/<label>/subjects/<subjectId>
                                                handlePerRequest(ctx, RetrieveSubject.of(policyId,
                                                        Label.of(label),
                                                        SubjectId.newInstance(subjectId),
                                                        dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/subjects/<subjectId>
                                                extractDataBytes(payloadSource ->
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
        final String subjectTypeString = jsonObject.getValueOrThrow(Subject.JsonFields.TYPE);
        final SubjectType subjectType = PoliciesModelFactory.newSubjectType(subjectTypeString);

        return PoliciesModelFactory.newSubject(SubjectId.newInstance(subjectId), subjectType);
    }

    /*
     * Describes {@code /entries/<label>/resources} route.
     *
     * @return {@code /entries/<label>/resources} route.
     */
    private Route thingsEntryPolicyEntryResources(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), label ->
                rawPathPrefix(mergeDoubleSlashes().concat(PATH_SUFFIX_RESOURCES), () ->
                        pathEndOrSingleSlash(() ->
                                route(
                                        get(() -> // GET /entries/<label>/resources
                                                handlePerRequest(ctx, RetrieveResources.of(policyId,
                                                        Label.of(label), dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/resources
                                                extractDataBytes(payloadSource ->
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
    private Route thingsEntryPolicyEntryResourcesEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String policyId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), label ->
                rawPathPrefix(mergeDoubleSlashes().concat(PATH_SUFFIX_RESOURCES), () ->
                        extractUnmatchedPath(resource ->
                                route(
                                        get(() -> // GET /entries/<label>/resources/<resource>
                                                handlePerRequest(ctx, RetrieveResource.of(policyId,
                                                        Label.of(label),
                                                        resourceKeyFromUnmatchedPath(resource),
                                                        dittoHeaders))
                                        ),
                                        put(() -> // PUT /entries/<label>/resources/<resource>
                                                extractDataBytes(payloadSource ->
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
