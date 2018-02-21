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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static akka.http.javadsl.server.Directives.delete;
import static akka.http.javadsl.server.Directives.extractDataBytes;
import static akka.http.javadsl.server.Directives.extractUnmatchedPath;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.parameter;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathEnd;
import static akka.http.javadsl.server.Directives.pathEndOrSingleSlash;
import static akka.http.javadsl.server.Directives.post;
import static akka.http.javadsl.server.Directives.put;
import static akka.http.javadsl.server.Directives.rawPathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;
import static org.eclipse.ditto.services.gateway.endpoints.directives.CustomPathMatchers.mergeDoubleSlashes;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.utils.UriEncoding;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingIdNotExplicitlySettableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP routes for {@code /things}.
 */
public final class ThingsRoute extends AbstractRoute {

    public static final String PATH_THINGS = "things";
    private static final String PATH_POLICY_ID = "policyId";
    private static final String PATH_ATTRIBUTES = "attributes";
    private static final String PATH_ACL = "acl";

    private final FeaturesRoute featuresRoute;
    private final MessagesRoute messagesRoute;

    /**
     * Constructs the {@code /things} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @param messagesDefaultTimeout the duration of the default message timeout.
     * @param messagesMaxTimeout the max duration of the message timeout.
     * @param messagesDefaultClaimTimeout the duration of the default claim timeout.
     * @param messagesMaxClaimTimeout the max duration of the claim timeout.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public ThingsRoute(final ActorRef proxyActor, final ActorSystem actorSystem,
            final Duration messagesDefaultTimeout, final Duration messagesMaxTimeout,
            final Duration messagesDefaultClaimTimeout, final Duration messagesMaxClaimTimeout) {
        super(proxyActor, actorSystem);

        featuresRoute = new FeaturesRoute(proxyActor, actorSystem,
                messagesDefaultTimeout, messagesMaxTimeout, messagesDefaultClaimTimeout, messagesMaxClaimTimeout);
        messagesRoute = new MessagesRoute(proxyActor, actorSystem,
                messagesDefaultTimeout, messagesMaxTimeout, messagesDefaultClaimTimeout, messagesMaxClaimTimeout);
    }

    private static String decodePath(final String attributePointerStr) {
        final String duplicateSlashesEliminated = attributePointerStr.replace("//", "/");
        return UriEncoding.decode(duplicateSlashesEliminated, UriEncoding.EncodingType.RFC3986);
    }

    /**
     * Builds the {@code /things} route.
     *
     * @return the {@code /things} route.
     */
    public Route buildThingsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_THINGS), () ->
                route(
                        things(ctx, dittoHeaders),
                        rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()),
                                thingId -> // /things/<thingId>
                                        route(
                                                thingsEntry(ctx, dittoHeaders, thingId),
                                                thingsEntryPolicyId(ctx, dittoHeaders, thingId),
                                                thingsEntryAcl(ctx, dittoHeaders, thingId),
                                                thingsEntryAclEntry(ctx, dittoHeaders, thingId),
                                                thingsEntryAttributes(ctx, dittoHeaders, thingId),
                                                thingsEntryAttributesEntry(ctx, dittoHeaders, thingId),
                                                thingsEntryFeatures(ctx, dittoHeaders, thingId),
                                                thingsEntryInboxOutbox(ctx, dittoHeaders, thingId)
                                        )
                        )
                )
        );
    }

    /*
     * Describes {@code /things} route.
     *
     * @return {@code /things} route.
     */
    private Route things(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return pathEndOrSingleSlash(() ->
                route( //
                        get(() -> // GET /things?ids=<idsString>&fields=<fieldsString>
                                parameter(ThingsParameter.IDS.toString(), idsString ->
                                        parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                                handlePerRequest(ctx, RetrieveThings
                                                        .getBuilder(idsString.isEmpty() ? null :
                                                                idsString.split(","))
                                                        .selectedFields(calculateSelectedFields(fieldsString))
                                                        .dittoHeaders(dittoHeaders).build())
                                        )
                                )
                        ),
                        post(() -> // POST /things
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource, thingJson ->
                                                CreateThing.of(createThingForPost(thingJson),
                                                        createInlinePolicyJson(thingJson), dittoHeaders))
                                )
                        )
                )
        );
    }

    private static Thing createThingForPost(final String jsonString) {
        final JsonObject inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        if (inputJson.contains(Thing.JsonFields.ID.getPointer())) {
            throw ThingIdNotExplicitlySettableException.newBuilder(true).build();
        }

        final String thingId = ThingBuilder.generateRandomThingId();

        final JsonObjectBuilder outputJsonBuilder = inputJson.toBuilder();
        outputJsonBuilder.set(Thing.JsonFields.ID.getPointer(), thingId);

        return ThingsModelFactory.newThingBuilder(outputJsonBuilder.build())
                .setId(ThingBuilder.generateRandomThingId())
                .build();
    }

    private static JsonObject createInlinePolicyJson(final String jsonString) {
        final JsonObject inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return inputJson.getValue(Policy.INLINED_FIELD_NAME)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .filter(obj -> {
                    try {
                        PoliciesModelFactory.newPolicy(obj.set(Thing.JsonFields.POLICY_ID, "empty:empty"));
                        // only accept valid inline policies
                        return true;
                    } catch (final RuntimeException e) {
                        return false;
                    }
                })
                .orElse(null);
    }

    /*
     * Describes {@code /things/<thingId>} route.
     * @return {@code /things/<thingId>} route.
     */
    private Route thingsEntry(final RequestContext ctx, final DittoHeaders dittoHeaders, final String thingId) {
        return pathEndOrSingleSlash(() ->
                route(
                        get(() -> // GET /things/things/<thingId>?fields=<fieldsString>
                                parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                        handlePerRequest(ctx, RetrieveThing.getBuilder(thingId, dittoHeaders)
                                                .withSelectedFields(calculateSelectedFields(fieldsString).orElse(null))
                                                .build())
                                )
                        ),
                        put(() -> // PUT /things/things/<thingId>
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                thingJson -> ModifyThing.of(thingId, ThingsModelFactory.newThing(
                                                        createThingJsonObjectForPut(thingJson, thingId)),
                                                        createInlinePolicyJson(thingJson),
                                                        dittoHeaders))
                                )
                        ),
                        delete(() -> // DELETE /things/things/<thingId>
                                handlePerRequest(ctx, DeleteThing.of(thingId, dittoHeaders))
                        )
                )
        );
    }

    private JsonObject createThingJsonObjectForPut(final String jsonString, final String thingId) {
        final JsonObject inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        final JsonObjectBuilder outputJsonBuilder = inputJson.toBuilder();
        final Optional<JsonValue> optThingId = inputJson.getValue(Thing.JsonFields.ID.getPointer());

        // verifies that thing ID agrees with ID from route
        if (optThingId.isPresent()) {
            final JsonValue thingIdFromBody = optThingId.get();
            if (!thingIdFromBody.isString() || !thingId.equals(thingIdFromBody.asString())) {
                throw ThingIdNotExplicitlySettableException.newBuilder(false).build();
            }
        } else {
            outputJsonBuilder.set(Thing.JsonFields.ID, thingId).build();
        }

        return outputJsonBuilder.build();
    }

    // sets policyId to be equal to thing ID if absent
    private static void useThingIdAsDefaultPolicyId(final String thingId, final JsonObject inputJson,
            final JsonObjectBuilder outputJsonBuilder) {

        final Optional<JsonValue> policyIdOpt = inputJson.getValue(Thing.JsonFields.POLICY_ID.getPointer());

        // sets policy ID only if policyId is absent from thing
        if (!policyIdOpt.isPresent()) {
            outputJsonBuilder.set(Thing.JsonFields.POLICY_ID, thingId);
        }
    }

    /*
     * Describes {@code /things/<thingId>/policyId} route.
     *
     * @return {@code /things/<thingId>/policyId} route.
     */
    private Route thingsEntryPolicyId(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId) {
        return path(PATH_POLICY_ID, () -> // /things/<thingId>/policyId
                route(
                        get(() -> // GET /things/<thingId>/policyId
                                handlePerRequest(ctx, RetrievePolicyId.of(thingId, dittoHeaders))
                        ),
                        put(() -> // GET /things/<thingId>/policyId
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                policyIdJson -> ModifyPolicyId.of(thingId,
                                                        Optional.of(JsonFactory.readFrom(policyIdJson))
                                                                .filter(JsonValue::isString)
                                                                .map(JsonValue::asString)
                                                                .orElse(policyIdJson), dittoHeaders)
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /things/<thingId>/acl} route.
     *
     * @return {@code /things/<thingId>/acl} route.
     */
    private Route thingsEntryAcl(final RequestContext ctx, final DittoHeaders dittoHeaders, final String thingId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_ACL), () -> // /things/<thingId>/acl
                pathEndOrSingleSlash(() ->
                        route(
                                get(() -> // GET /things/<thingId>/acl
                                        handlePerRequest(ctx, RetrieveAcl.of(thingId, dittoHeaders))
                                ),
                                put(() -> // PUT /things/<thingId>/acl
                                        extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource, aclJson ->
                                                        ModifyAcl.of(thingId,
                                                                ThingsModelFactory.newAcl(aclJson),
                                                                dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /things/<thingId>/acl/<authorizationSubject>} route.
     *
     * @return {@code /things/<thingId>/acl/<authorizationSubject>} route.
     */
    private Route thingsEntryAclEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_ACL), () ->
                rawPathPrefix(mergeDoubleSlashes().concat(PathMatchers.segment()), subject ->
                        pathEndOrSingleSlash(() ->
                                route(
                                        get(() -> // GET
                                                // /things/<thingId>/acl/<authorizationSubject>?fields=<fieldsString>
                                                parameterOptional(ThingsParameter.FIELDS.toString(),
                                                        fieldsString ->
                                                                handlePerRequest(ctx, RetrieveAclEntry
                                                                        .of(thingId,
                                                                                AuthorizationModelFactory.newAuthSubject(
                                                                                        subject),
                                                                                calculateSelectedFields(
                                                                                        fieldsString).orElse(
                                                                                        null), dittoHeaders))
                                                )
                                        ),
                                        put(() -> // PUT /things/<thingId>/acl/<authorizationSubject>
                                                extractDataBytes(payloadSource ->
                                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                aclEntryJson ->
                                                                        ModifyAclEntry.of(thingId,
                                                                                ThingsModelFactory
                                                                                        .newAclEntry(subject,
                                                                                                JsonFactory.readFrom(
                                                                                                        aclEntryJson)),
                                                                                dittoHeaders))
                                                )
                                        ),
                                        delete(() -> // DELETE /things/<thingId>/acl/<authorizationSubject>
                                                handlePerRequest(ctx, DeleteAclEntry
                                                        .of(thingId, AuthorizationModelFactory.newAuthSubject(
                                                                subject), dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /things/<thingId>/attributes} route.
     *
     * @return {@code /things/<thingId>/attributes} route.
     */
    private Route thingsEntryAttributes(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_ATTRIBUTES), () ->
                pathEndOrSingleSlash(() ->
                        route(
                                get(() -> // GET /things/<thingId>/attributes?fields=<fieldsString>
                                        parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                                handlePerRequest(ctx, RetrieveAttributes
                                                        .of(thingId,
                                                                calculateSelectedFields(fieldsString).orElse(
                                                                        null), dittoHeaders))
                                        )
                                ),
                                put(() -> // PUT /things/<thingId>/attributes
                                        extractDataBytes(payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        attributesJson ->
                                                                ModifyAttributes.of(thingId,
                                                                        ThingsModelFactory.newAttributes(
                                                                                attributesJson),
                                                                        dittoHeaders))
                                        )
                                ),
                                delete(() -> // DELETE /things/<thingId>/attributes
                                        handlePerRequest(ctx, DeleteAttributes.of(thingId, dittoHeaders))
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /things/<thingId>/attributes/<attributesSelector>} route.
     *
     * {@code attributeJsonPointer} JSON pointer to GET/UPDATE/DELETE. E.g.:
     * <pre>
     *    GET /things/fancy-car-1/attributes/model
     *    PUT /things/fancy-car-1/attributes/someProp
     *    DELETE /things/fancy-car-1/attributes/foo/bar
     * </pre>
     *
     * @return {@code /things/<thingId>/attributes/<attributeJsonPointer>} route.
     */
    private Route thingsEntryAttributesEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId) {
        return rawPathPrefix(mergeDoubleSlashes().concat(PATH_ATTRIBUTES), () ->
                route(
                        get(() -> // GET /things/<thingId>/attributes
                                pathEnd(() -> // GET /things/<thingId>/attributes/
                                        handlePerRequest(ctx, RetrieveAttributes.of(thingId, dittoHeaders))
                                ).orElse( // GET /things/<thingId>/attributes/<attributePointerStr>
                                        extractUnmatchedPath(attributePointerStr ->
                                                handlePerRequest(ctx, RetrieveAttribute
                                                        .of(thingId, JsonFactory.newPointer(
                                                                decodePath(attributePointerStr)),
                                                                dittoHeaders))
                                        )
                                )
                        ),
                        put(() -> // PUT /things/<thingId>/attributes
                                extractDataBytes(payloadSource ->
                                        pathEnd(() -> // PUT /things/<thingId>/attributes/
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        attributesJson ->
                                                                ModifyAttributes.of(thingId,
                                                                        ThingsModelFactory.newAttributes(
                                                                                attributesJson),
                                                                        dittoHeaders))
                                        ).orElse( // PUT /things/<thingId>/attributes/<attributePointerStr>
                                                extractUnmatchedPath(attributePointerStr ->
                                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                attributeValueJson ->
                                                                        ModifyAttribute.of(thingId,
                                                                                JsonFactory.newPointer(
                                                                                        decodePath(
                                                                                                attributePointerStr)),
                                                                                JsonFactory.readFrom(
                                                                                        attributeValueJson),
                                                                                dittoHeaders))
                                                )
                                        )
                                )
                        ),
                        delete(() -> // DELETE /things/<thingId>/attributes
                                pathEnd(() -> // DELETE /things/<thingId>/attributes/
                                        handlePerRequest(ctx, DeleteAttributes.of(thingId, dittoHeaders))
                                ).orElse( // DELETE /things/<thingId>/attributes/<attributePointerStr>
                                        extractUnmatchedPath(attributePointerStr ->
                                                handlePerRequest(ctx, DeleteAttribute
                                                        .of(thingId, JsonFactory.newPointer(
                                                                decodePath(attributePointerStr)),
                                                                dittoHeaders))
                                        )
                                )
                        )
                )
        );
    }

    /*
     * Describes {@code /things/<thingId>/features} route.
     *
     * @return {@code /things/<thingId>/features} route.
     */
    private Route thingsEntryFeatures(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId) {
        return featuresRoute.buildFeaturesRoute(ctx, dittoHeaders, thingId); // /things/<thingId>/features
    }

    /*
     * Describes {@code /things/<thingId>/{inbox|outbox}} route.
     *
     * @return {@code /things/<thingId>/{inbox|outbox}} route.
     */
    private Route thingsEntryInboxOutbox(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final String thingId) {
        return messagesRoute.buildThingsInboxOutboxRoute(ctx, dittoHeaders,
                thingId); // /things/<thingId>/<inbox|outbox>
    }

}
