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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.MessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.endpoints.utils.UriEncoding;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingIdNotExplicitlySettableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Source;

/**
 * Builder for creating Akka HTTP routes for {@code /things}.
 */
public final class ThingsRoute extends AbstractRoute {

    public static final String PATH_THINGS = "things";
    private static final String PATH_POLICY_ID = "policyId";
    private static final String PATH_ATTRIBUTES = "attributes";
    private static final String PATH_THING_DEFINITION = "definition";
    private static final String PATH_ACL = "acl";

    private final FeaturesRoute featuresRoute;
    private final MessagesRoute messagesRoute;

    /**
     * Constructs the {@code /things} route builder.
     *
     * @param proxyActor an actor selection of the command delegating actor.
     * @param actorSystem the ActorSystem to use.
     * @param messageConfig the MessageConfig.
     * @param claimMessageConfig the MessageConfig for claim messages.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public ThingsRoute(final ActorRef proxyActor,
            final ActorSystem actorSystem,
            final MessageConfig messageConfig,
            final MessageConfig claimMessageConfig,
            final HttpConfig httpConfig,
            final HeaderTranslator headerTranslator) {

        super(proxyActor, actorSystem, httpConfig, headerTranslator);

        featuresRoute = new FeaturesRoute(proxyActor, actorSystem, messageConfig, claimMessageConfig, httpConfig,
                headerTranslator);
        messagesRoute = new MessagesRoute(proxyActor, actorSystem, messageConfig, claimMessageConfig, httpConfig,
                headerTranslator);
    }

    private static String decodePath(final String attributePointerStr) {
        return UriEncoding.decode(attributePointerStr, UriEncoding.EncodingType.RFC3986);
    }

    private static Thing createThingForPost(final String jsonString) {
        final JsonObject inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        if (inputJson.contains(Thing.JsonFields.ID.getPointer())) {
            throw ThingIdNotExplicitlySettableException.forPostMethod().build();
        }

        final ThingId thingId = ThingBuilder.generateRandomTypedThingId();

        final JsonObjectBuilder outputJsonBuilder = inputJson.toBuilder();
        outputJsonBuilder.set(Thing.JsonFields.ID.getPointer(), thingId.toString());

        return ThingsModelFactory.newThingBuilder(outputJsonBuilder.build())
                .setId(thingId)
                .build();
    }

    @Nullable
    private static JsonObject createInlinePolicyJson(final String jsonString) {
        final JsonObject inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return inputJson.getValue(Policy.INLINED_FIELD_NAME)
                .map(jsonValue -> wrapJsonRuntimeException(jsonValue::asObject))
                .orElse(null);
    }

    @Nullable
    private static String getCopyPolicyFrom(final String jsonString) {
        final JsonObject inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return inputJson.getValue(ModifyThing.JSON_COPY_POLICY_FROM)
                .orElse(null);
    }

    private static JsonObject createThingJsonObjectForPut(final String jsonString, final String thingId) {
        final JsonObject inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        final JsonObjectBuilder outputJsonBuilder = inputJson.toBuilder();
        final Optional<JsonValue> optThingId = inputJson.getValue(Thing.JsonFields.ID.getPointer());

        // verifies that thing ID agrees with ID from route
        if (optThingId.isPresent()) {
            final JsonValue thingIdFromBody = optThingId.get();
            if (!thingIdFromBody.isString() || !thingId.equals(thingIdFromBody.asString())) {
                throw ThingIdNotExplicitlySettableException.forPutMethod().build();
            }
        } else {
            outputJsonBuilder.set(Thing.JsonFields.ID, thingId).build();
        }

        return outputJsonBuilder.build();
    }

    /**
     * Builds the {@code /things} route.
     *
     * @return the {@code /things} route.
     */
    public Route buildThingsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_THINGS), () ->
                concat(
                        things(ctx, dittoHeaders),
                        rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()),
                                // /things/<thingId>
                                thingId -> buildThingEntryRoute(ctx, dittoHeaders, ThingId.of(thingId))
                        )
                )
        );
    }

    private Route buildThingEntryRoute(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return concat(
                thingsEntry(ctx, dittoHeaders, thingId),
                thingsEntryPolicyId(ctx, dittoHeaders, thingId),
                thingsEntryAcl(ctx, dittoHeaders, thingId),
                thingsEntryAclEntry(ctx, dittoHeaders, thingId),
                thingsEntryAttributes(ctx, dittoHeaders, thingId),
                thingsEntryAttributesEntry(ctx, dittoHeaders, thingId),
                thingsEntryDefinition(ctx, dittoHeaders, thingId),
                thingsEntryFeatures(ctx, dittoHeaders, thingId),
                thingsEntryInboxOutbox(ctx, dittoHeaders, thingId)
        );
    }

    /*
     * Describes {@code /things} route.
     *
     * @return {@code /things} route.
     */
    private Route things(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return pathEndOrSingleSlash(() ->
                concat( //
                        get(() -> // GET /things?ids=<idsString>&fields=<fieldsString>
                                buildRetrieveThingsRoute(ctx, dittoHeaders)
                        ),
                        post(() -> // POST /things
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                thingJson -> CreateThing.of(createThingForPost(thingJson),
                                                        createInlinePolicyJson(thingJson), getCopyPolicyFrom(thingJson),
                                                        dittoHeaders)
                                        )
                                )
                        )
                )
        );
    }

    private Route buildRetrieveThingsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return parameter(ThingsParameter.IDS.toString(), idsString ->
                parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                        handlePerRequest(ctx, dittoHeaders, Source.empty(), emptyRequestBody -> RetrieveThings
                                .getBuilder(
                                        (idsString).isEmpty() ? Collections.emptyList() : splitThingIdString(idsString))
                                .selectedFields(calculateSelectedFields(fieldsString))
                                .dittoHeaders(dittoHeaders).build())
                )

        );
    }

    private List<ThingId> splitThingIdString(final String thingIdString) {
        return Arrays.stream(thingIdString.split(","))
                .map(ThingId::of)
                .collect(Collectors.toList());
    }

    /*
     * Describes {@code /things/<thingId>} route.
     * @return {@code /things/<thingId>} route.
     */
    private Route thingsEntry(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return pathEndOrSingleSlash(() ->
                concat(
                        get(() -> // GET /things/<thingId>?fields=<fieldsString>
                                parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                        handlePerRequest(ctx, RetrieveThing.getBuilder(thingId, dittoHeaders)
                                                .withSelectedFields(calculateSelectedFields(fieldsString).orElse(null))
                                                .build())
                                )
                        ),
                        put(() -> // PUT /things/<thingId>
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                thingJson -> ModifyThing.of(thingId, ThingsModelFactory.newThingBuilder(
                                                        createThingJsonObjectForPut(thingJson, thingId.toString())).build(),
                                                        createInlinePolicyJson(thingJson),
                                                        getCopyPolicyFrom(thingJson),
                                                        dittoHeaders))
                                )
                        ),
                        delete(() -> // DELETE /things/<thingId>
                                handlePerRequest(ctx, DeleteThing.of(thingId, dittoHeaders))
                        )
                )
        );
    }

    /*
     * Describes {@code /things/<thingId>/policyId} route.
     *
     * @return {@code /things/<thingId>/policyId} route.
     */
    private Route thingsEntryPolicyId(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return path(PATH_POLICY_ID, () -> // /things/<thingId>/policyId
                concat(
                        get(() -> // GET /things/<thingId>/policyId
                                handlePerRequest(ctx, RetrievePolicyId.of(thingId, dittoHeaders))
                        ),
                        put(() -> // PUT /things/<thingId>/policyId
                                extractDataBytes(payloadSource ->
                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                policyIdJson -> ModifyPolicyId.of(thingId,
                                                        PolicyId.of(Optional.of(JsonFactory.readFrom(policyIdJson))
                                                                .filter(JsonValue::isString)
                                                                .map(JsonValue::asString)
                                                                .orElse(policyIdJson)), dittoHeaders)
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
    private Route thingsEntryAcl(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_ACL), () -> // /things/<thingId>/acl
                pathEndOrSingleSlash(() ->
                        concat(
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
            final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_ACL), () ->
                rawPathPrefix(PathMatchers.slash().concat(PathMatchers.segment()), subject ->
                        pathEndOrSingleSlash(() ->
                                concat(
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
            final ThingId thingId) {

        return rawPathPrefix(PathMatchers.slash().concat(PATH_ATTRIBUTES), () ->
                pathEndOrSingleSlash(() ->
                        concat(
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
            final ThingId thingId) {

        return rawPathPrefix(PathMatchers.slash().concat(PATH_ATTRIBUTES), () ->
                concat(
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
                                                                                DittoJsonException.wrapJsonRuntimeException(
                                                                                        () -> JsonFactory.readFrom(
                                                                                                attributeValueJson)),
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
     * Describes {@code /things/<thingId>/definition} route.
     *
     * @return {@code /things/<thingId>/definition} route.
     */
    private Route thingsEntryDefinition(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {

        return rawPathPrefix(PathMatchers.slash().concat(PATH_THING_DEFINITION), () ->
                pathEndOrSingleSlash(() ->
                        concat(
                                get(() -> // GET /things/<thingId>/definition

                                        handlePerRequest(ctx, RetrieveThingDefinition
                                                .of(thingId, dittoHeaders)
                                        )
                                ),
                                put(() -> // PUT /things/<thingId>/definition
                                        extractDataBytes(payloadSource ->
                                                pathEnd(() -> // PUT /things/<thingId>/definition/
                                                        handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                definitionJson ->
                                                                        ModifyThingDefinition.of(thingId,
                                                                                getDefinitionFromJson(definitionJson),
                                                                                dittoHeaders))
                                                )
                                        )
                                ),
                                delete(() -> // DELETE /things/<thingId>/definition
                                        handlePerRequest(ctx, DeleteThingDefinition.of(thingId, dittoHeaders))
                                )
                        )
                )
        );
    }

    private ThingDefinition getDefinitionFromJson(final String definitionJson) {
        return DittoJsonException.wrapJsonRuntimeException(() -> {
            final JsonValue jsonValue = JsonFactory.readFrom(definitionJson);
            if (jsonValue.isNull()) {
                return ThingsModelFactory.nullDefinition();
            } else {
                return ThingsModelFactory.newDefinition(jsonValue.asString());
            }
        });
    }

    /*
     * Describes {@code /things/<thingId>/features} route.
     *
     * @return {@code /things/<thingId>/features} route.
     */
    private Route thingsEntryFeatures(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return featuresRoute.buildFeaturesRoute(ctx, dittoHeaders, thingId); // /things/<thingId>/features
    }

    /*
     * Describes {@code /things/<thingId>/{inbox|outbox}} route.
     *
     * @return {@code /things/<thingId>/{inbox|outbox}} route.
     */
    private Route thingsEntryInboxOutbox(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {

        return messagesRoute.buildThingsInboxOutboxRoute(ctx, dittoHeaders,
                thingId); // /things/<thingId>/<inbox|outbox>
    }

}
