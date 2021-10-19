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
package org.eclipse.ditto.gateway.service.endpoints.routes.policies;

import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResult;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAnnouncement;
import org.eclipse.ditto.policies.model.SubjectExpiry;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.DeactivateTokenIntegration;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyActionFailedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyIdNotExplicitlySettableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.protocol.HeaderTranslator;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /policies}.
 */
public final class PoliciesRoute extends AbstractRoute {

    private static final String PATH_ACTIONS = "actions";

    public static final String PATH_POLICIES = "policies";
    private static final String PATH_IMPORTS = "imports";
    private static final String PATH_ENTRIES = "entries";

    private static final Label DUMMY_LABEL = Label.of("-");

    private static final JsonFieldDefinition<JsonObject> ACTION_ACTIVATE_TOKEN_INTEGRATION_ANNOUNCEMENT =
            Subject.JsonFields.ANNOUNCEMENT;

    private final PolicyEntriesRoute policyEntriesRoute;
    private final PolicyImportsRoute policyImportsRoute;

    private final TokenIntegrationSubjectIdFactory tokenIntegrationSubjectIdFactory;

    /**
     * Constructs the {@code /policies} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param commandConfig the configuration settings of the Gateway service's incoming command processing.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param tokenIntegrationSubjectIdFactory factory of resolvers for placeholders.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public PoliciesRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig,
            final HeaderTranslator headerTranslator,
            final TokenIntegrationSubjectIdFactory tokenIntegrationSubjectIdFactory) {

        super(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator);
        this.tokenIntegrationSubjectIdFactory = tokenIntegrationSubjectIdFactory;

        policyEntriesRoute =
                new PolicyEntriesRoute(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator,
                        tokenIntegrationSubjectIdFactory);
        policyImportsRoute =
                new PolicyImportsRoute(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator);
    }

    /**
     * Builds the {@code /policies} route.
     *
     * @return the {@code /policies} route.
     */
    public Route buildPoliciesRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final AuthenticationResult authenticationResult) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_POLICIES), () ->
                rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), policyId ->
                        // /policies/<policyId>
                        policyRoute(ctx, dittoHeaders, PolicyId.of(policyId), authenticationResult)
                )
        );
    }

    private Route policyRoute(final RequestContext ctx, final DittoHeaders dittoHeaders, final PolicyId policyId,
            final AuthenticationResult authenticationResult) {
        return concat(
                policyId(ctx, dittoHeaders, policyId),
                policyImports(ctx, dittoHeaders, policyId),
                policyEntries(ctx, dittoHeaders, policyId, authenticationResult),
                policyActions(ctx, dittoHeaders, policyId, authenticationResult)
        );
    }

    /*
     * Describes {@code /policies/<policyId>} route.
     * @return {@code /policies/<policyId>} route.
     */
    private Route policyId(final RequestContext ctx, final DittoHeaders dittoHeaders, final PolicyId policyId) {
        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /policies/<policyId>
                                handlePerRequest(ctx, RetrievePolicy.of(policyId, dittoHeaders))
                        ),
                        put(() -> // PUT /policies/<policyId>
                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        policyJson -> ModifyPolicy
                                                                .of(policyId, PoliciesModelFactory.newPolicy(
                                                                        createPolicyJsonObjectForPut(policyJson,
                                                                                policyId)),
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

    private static JsonObject createPolicyJsonObjectForPut(final String jsonString, final PolicyId policyId) {
        final JsonObject policyJsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        policyJsonObject.getValue(Policy.JsonFields.ID.getPointer())
                .ifPresent(policyIdJsonValue -> {
                    if (!policyIdJsonValue.isString() || !policyId.toString().equals(policyIdJsonValue.asString())) {
                        throw PolicyIdNotExplicitlySettableException.newBuilder().build();
                    }
                });
        return policyJsonObject.setValue(Policy.JsonFields.ID.getPointer(), JsonValue.of(policyId));
    }

    /*
     * Describes {@code /policies/<policyId>/imports} route.
     *
     * @return {@code /policies/<policyId>/imports} route.
     */
    private Route policyImports(final RequestContext ctx, final DittoHeaders dittoHeaders, final PolicyId policyId) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_IMPORTS), () -> // /policies/<policyId>/imports
                policyImportsRoute.buildPolicyImportsRoute(ctx, dittoHeaders, policyId)
        );
    }

    /*
     * Describes {@code /policies/<policyId>/entries} route.
     *
     * @return {@code /policies/<policyId>/entries} route.
     */
    private Route policyEntries(final RequestContext ctx, final DittoHeaders dittoHeaders, final PolicyId policyId,
            final AuthenticationResult authResult) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_ENTRIES), () -> // /policies/<policyId>/entries
                policyEntriesRoute.buildPolicyEntriesRoute(ctx, dittoHeaders, policyId, authResult)
        );
    }

    /*
     * Describes{@code /policies/<policyId>/actions} route.
     */
    private Route policyActions(final RequestContext ctx, final DittoHeaders dittoHeaders, final PolicyId policyId,
            final AuthenticationResult authenticationResult) {

        return rawPathPrefix(PathMatchers.slash().concat(PATH_ACTIONS), () -> concat(
                // POST /policies/<policyId>/actions/activateTokenIntegration
                rawPathPrefix(PathMatchers.slash().concat(ActivateTokenIntegration.NAME), () ->
                        pathEndOrSingleSlash(() ->
                                extractJwt(dittoHeaders, authenticationResult, ActivateTokenIntegration.NAME, jwt ->
                                        post(() -> handleSubjectAnnouncement(this, dittoHeaders, sa ->
                                                topLevelActivateTokenIntegration(dittoHeaders, policyId, jwt, sa))
                                        )
                                )
                        )
                ),
                // POST /policies/<policyId>/actions/deactivateTokenIntegration
                rawPathPrefix(PathMatchers.slash().concat(DeactivateTokenIntegration.NAME), () ->
                        pathEndOrSingleSlash(() ->
                                extractJwt(dittoHeaders, authenticationResult, DeactivateTokenIntegration.NAME, jwt ->
                                        post(() -> handlePerRequest(ctx, topLevelDeactivateTokenIntegration(
                                                dittoHeaders, policyId, jwt)))
                                )
                        )
                )
        ));
    }

    private TopLevelPolicyActionCommand topLevelActivateTokenIntegration(final DittoHeaders dittoHeaders,
            final PolicyId policyId, final JsonWebToken jwt, @Nullable final SubjectAnnouncement subjectAnnouncement) {

        final Set<SubjectId> subjectIds = resolveSubjectIdsForActivateTokenIntegrationAction(dittoHeaders, jwt);
        final SubjectExpiry expiry = SubjectExpiry.newInstance(jwt.getExpirationTime());
        final ActivateTokenIntegration activateTokenIntegration =
                ActivateTokenIntegration.of(policyId, DUMMY_LABEL, subjectIds, expiry, subjectAnnouncement,
                        dittoHeaders);
        return TopLevelPolicyActionCommand.of(activateTokenIntegration, List.of());
    }

    private TopLevelPolicyActionCommand topLevelDeactivateTokenIntegration(final DittoHeaders dittoHeaders,
            final PolicyId policyId, final JsonWebToken jwt) {

        final Set<SubjectId> subjectIds = resolveSubjectIdsForActivateTokenIntegrationAction(dittoHeaders, jwt);
        final DeactivateTokenIntegration deactivateTokenIntegration =
                DeactivateTokenIntegration.of(policyId, DUMMY_LABEL, subjectIds, dittoHeaders);
        return TopLevelPolicyActionCommand.of(deactivateTokenIntegration, List.of());
    }

    private Set<SubjectId> resolveSubjectIdsForActivateTokenIntegrationAction(final DittoHeaders dittoHeaders,
            final JsonWebToken jwt) {

        try {
            return tokenIntegrationSubjectIdFactory.getSubjectIds(dittoHeaders, jwt);
        } catch (final UnresolvedPlaceholderException e) {
            throw PolicyActionFailedException.newBuilder()
                    .action(ActivateTokenIntegration.NAME)
                    .status(HttpStatus.BAD_REQUEST)
                    .description("Mandatory placeholders could not be resolved, in detail: " + e.getMessage())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Nullable
    private static SubjectAnnouncement toSubjectAnnouncement(final String body) {
        if (body.isEmpty()) {
            return null;
        } else {
            final Optional<JsonObject> announcement = JsonObject.of(body)
                    .getValue(ACTION_ACTIVATE_TOKEN_INTEGRATION_ANNOUNCEMENT);
            return announcement.map(SubjectAnnouncement::fromJson).orElse(null);
        }
    }

    static Route extractJwt(final DittoHeaders dittoHeaders,
            final AuthenticationResult authResult,
            final String actionName,
            final Function<JsonWebToken, Route> inner) {

        if (authResult instanceof JwtAuthenticationResult) {
            final Optional<JsonWebToken> jwtOptional = ((JwtAuthenticationResult) authResult).getJwt();
            if (jwtOptional.isPresent()) {
                return inner.apply(jwtOptional.get());
            }
        }
        throw PolicyActionFailedException.newBuilderForInappropriateAuthenticationMethod(actionName)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    static Route handleSubjectAnnouncement(final AbstractRoute route, final DittoHeaders dittoHeaders,
            final Function<SubjectAnnouncement, Command<?>> commandConstructor) {
        return route.extractRequestContext(context ->
                route.handlePerRequest(context, dittoHeaders, context.getRequest().entity().getDataBytes(),
                        body -> commandConstructor.apply(toSubjectAnnouncement(body))));
    }

}
