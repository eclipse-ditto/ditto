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
package org.eclipse.ditto.gateway.service.endpoints.routes.things;

import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch.ThingSearchParameter;
import org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.gateway.service.util.config.endpoints.MessageConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotDeletableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotExplicitlySettableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingMergeInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.thingsearch.model.SearchResult;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpCharsets;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.headers.Link;
import akka.http.javadsl.model.headers.LinkParams;
import akka.http.javadsl.model.headers.LinkValue;
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
    private static final String PATH_THING_DEFINITION = "definition";
    private static final String NAMESPACE_PARAMETER = "namespace";

    private final FeaturesRoute featuresRoute;
    private final MessagesRoute messagesRoute;

    /**
     * Constructs a {@code ThingsRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @param messageConfig the MessageConfig.
     * @param claimMessageConfig the MessageConfig for claim messages.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public ThingsRoute(final RouteBaseProperties routeBaseProperties,
            final MessageConfig messageConfig,
            final MessageConfig claimMessageConfig) {

        super(routeBaseProperties);
        featuresRoute = new FeaturesRoute(routeBaseProperties, messageConfig, claimMessageConfig);
        messagesRoute = new MessagesRoute(routeBaseProperties, messageConfig, claimMessageConfig);
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

        return inputJson.getValue(ModifyThing.JSON_COPY_POLICY_FROM).orElse(null);
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

    private Route buildThingEntryRoute(final RequestContext ctx,
            final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return concat(
                thingsEntry(ctx, dittoHeaders, thingId),
                thingsEntryPolicyId(ctx, dittoHeaders, thingId),
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
                concat(
                        // GET /things?ids=<idsString>&fields=<fieldsString>
                        get(() -> buildRetrieveThingsRoute(ctx, dittoHeaders)),

                        // POST /things
                        post(() -> buildPostThingsRoute(ctx, dittoHeaders))
                )
        );
    }

    private Route buildRetrieveThingsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        // GET /things?ids=...
        return parameter(ThingsParameter.IDS.toString(), idsString ->
                parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                        handlePerRequest(ctx, RetrieveThings.getBuilder(splitThingIdString(idsString))
                                        .selectedFields(calculateSelectedFields(fieldsString))
                                        .dittoHeaders(dittoHeaders)
                                        .build(),
                                (responseValue, response) ->
                                        response.withEntity(determineResponseContentType(ctx), responseValue.toString())
                        )
                )
        ).orElse( // GET /things
                thingSearchParameterOptional(params ->
                        parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                handlePerRequest(ctx, QueryThings.of(null, // allow filter only on /search/things but not here
                                                ThingSearchRoute.calculateOptions(params.get(ThingSearchParameter.OPTION)),
                                                calculateSelectedFields(fieldsString).orElse(null),
                                                ThingSearchRoute.calculateNamespaces(
                                                        params.get(ThingSearchParameter.NAMESPACES)),
                                                dittoHeaders),
                                        (responseValue, response) ->
                                                transformQueryThingsResult(ctx, responseValue, response)
                                )
                        )
                )
        );
    }

    private Route thingSearchParameterOptional(
            final Function<EnumMap<ThingSearchParameter, Optional<String>>, Route> inner) {
        return thingSearchParameterOptionalImpl(ThingSearchParameter.values(),
                new EnumMap<>(ThingSearchParameter.class), inner);
    }

    private Route thingSearchParameterOptionalImpl(final ThingSearchParameter[] values,
            final EnumMap<ThingSearchParameter, Optional<String>> accumulator,
            final Function<EnumMap<ThingSearchParameter, Optional<String>>, Route> inner) {
        if (accumulator.size() >= values.length) {
            return inner.apply(accumulator);
        } else {
            final ThingSearchParameter parameter = values[accumulator.size()];
            return parameterOptional(parameter.toString(), parameterValueOptional -> {
                accumulator.put(parameter, parameterValueOptional);
                return thingSearchParameterOptionalImpl(values, accumulator, inner);
            });
        }
    }

    private HttpResponse transformQueryThingsResult(final RequestContext ctx,
            final JsonValue responseValue,
            final HttpResponse response) {

        HttpResponse theResponse = response;
        final JsonArray resultArray;
        if (responseValue.isObject()) {
            final JsonObject responseObject = responseValue.asObject();
            resultArray = responseObject
                    .getValue(SearchResult.JsonFields.ITEMS)
                    .orElse(JsonArray.empty());

            theResponse = theResponse.addHeader(Link.create(
                    LinkValue.create(ctx.getRequest().getUri().toRelative(), LinkParams.rel("canonical"))
            ));

            final Optional<String> cursor = responseObject
                    .getValue(SearchResult.JsonFields.CURSOR);
            if (cursor.isPresent()) {
                // Link: </api/2/things?option=cursor(...)>; rel="next"
                theResponse = theResponse.addHeader(Link.create(
                        LinkValue.create(ctx.getRequest().getUri().toRelative()
                                        .rawQueryString("option=cursor(" + cursor.get() + ")"),
                                LinkParams.next)
                ));
            }
        } else {
            resultArray = JsonArray.empty();
        }

        return theResponse.withEntity(determineResponseContentType(ctx), resultArray.toString());
    }

    private static akka.http.javadsl.model.ContentType.NonBinary determineResponseContentType(
            final RequestContext ctx) {
        final akka.http.javadsl.model.ContentType.NonBinary contentType;
        if (ctx.getRequest().getHeader(Accept.class)
                .filter(accept -> accept.value().equals(ContentType.APPLICATION_TD_JSON.getValue()))
                .isPresent()) {
            contentType = ContentTypes.create(MediaTypes.applicationWithFixedCharset("td+json", HttpCharsets.UTF_8));
        } else {
            contentType = ContentTypes.APPLICATION_JSON;
        }
        return contentType;
    }

    private static List<ThingId> splitThingIdString(final String thingIdString) {
        final List<ThingId> result;
        if (thingIdString.isEmpty()) {
            result = List.of();
        } else {
            result = Stream.of(thingIdString.split(","))
                    .map(ThingId::of)
                    .toList();
        }

        return result;
    }

    private Route buildPostThingsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return parameterOptional(DittoHeaderDefinition.CHANNEL.getKey(), channelOpt -> {
            if (isLiveChannel(channelOpt, dittoHeaders)) {
                throw ThingNotCreatableException.forLiveChannel(dittoHeaders);
            }
            return parameterOptional(NAMESPACE_PARAMETER, namespaceOpt ->
                    ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                            payloadSource ->
                                    handlePerRequest(ctx, dittoHeaders, payloadSource,
                                            thingJson -> CreateThing.of(
                                                    createThingForPost(thingJson, namespaceOpt.orElse(null)),
                                                    createInlinePolicyJson(thingJson),
                                                    getCopyPolicyFrom(thingJson),
                                                    dittoHeaders)))
            );
        });
    }

    private static boolean isLiveChannel(final Optional<String> channelOpt, final DittoHeaders dittoHeaders) {
        final Predicate<String> isLiveChannel = channelValue -> "live".equalsIgnoreCase(channelValue.trim());
        final var isLiveChannelQueryParameter = channelOpt.filter(isLiveChannel).isPresent();
        final var isLiveChannelHeader = dittoHeaders.getChannel().filter(isLiveChannel).isPresent();

        return isLiveChannelQueryParameter || isLiveChannelHeader;
    }

    private static Thing createThingForPost(final String jsonString, @Nullable final String namespace) {
        final var inputJson = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        if (inputJson.contains(Thing.JsonFields.ID.getPointer())) {
            throw ThingIdNotExplicitlySettableException.forPostMethod().build();
        }

        return ThingsModelFactory.newThingBuilder(inputJson)
                .setId(ThingBuilder.generateRandomTypedThingId(namespace))
                .build();
    }

    /*
     * Describes {@code /things/<thingId>} route.
     * @return {@code /things/<thingId>} route.
     */
    private Route thingsEntry(final RequestContext ctx, final DittoHeaders dittoHeaders, final ThingId thingId) {
        return pathEndOrSingleSlash(() ->
                concat(
                        // GET /things/<thingId>?fields=<fieldsString>
                        get(() -> parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                        handlePerRequest(ctx, RetrieveThing.getBuilder(thingId, dittoHeaders)
                                                .withSelectedFields(calculateSelectedFields(fieldsString)
                                                        .orElse(null))
                                                .build())
                                )
                        ),
                        // PUT /things/<thingId>
                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                        thingJson -> ModifyThing.of(thingId,
                                                ThingsModelFactory.newThingBuilder(
                                                        ThingJsonObjectCreator.newInstance(thingJson,
                                                                thingId.toString()).forPut()).build(),
                                                createInlinePolicyJson(thingJson),
                                                getCopyPolicyFrom(thingJson),
                                                dittoHeaders)))
                        ),
                        // PATCH /things/<thingId>
                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                        thingJson -> MergeThing.withThing(thingId,
                                                thingFromJsonForPatch(thingJson, thingId, dittoHeaders),
                                                dittoHeaders)))
                        ),
                        // DELETE /things/<thingId>
                        delete(() -> handlePerRequest(ctx, DeleteThing.of(thingId, dittoHeaders)))
                )
        );
    }

    private static Thing thingFromJsonForPatch(final String thingJson,
            final ThingId thingId,
            final DittoHeaders dittoHeaders) {

        if (JsonFactory.readFrom(thingJson).isNull() &&
                dittoHeaders.getSchemaVersion().filter(JsonSchemaVersion.V_2::equals).isPresent()) {
            throw ThingMergeInvalidException.fromMessage(
                    "The provided json value can not be applied at this resource", dittoHeaders);
        }

        return ThingsModelFactory.newThingBuilder(
                ThingJsonObjectCreator.newInstance(thingJson, thingId.toString()).forPatch()).build();
    }

    /*
     * Describes {@code /things/<thingId>/policyId} route.
     *
     * @return {@code /things/<thingId>/policyId} route.
     */
    private Route thingsEntryPolicyId(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        // /things/<thingId>/policyId
        return path(PATH_POLICY_ID, () ->
                concat(
                        // GET /things/<thingId>/policyId
                        get(() -> handlePerRequest(ctx, RetrievePolicyId.of(thingId, dittoHeaders))),
                        // PUT /things/<thingId>/policyId
                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                policyIdJson -> ModifyPolicyId.of(thingId, policyIdFromJson(policyIdJson),
                                                        dittoHeaders))
                                )
                        ),
                        // PATCH /things/<thingId>/policyId
                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                policyIdJson -> MergeThing.withPolicyId(thingId,
                                                        policyIdFromJsonForPatch(policyIdJson),
                                                        dittoHeaders))
                                )
                        )
                )
        );
    }

    private static PolicyId policyIdFromJsonForPatch(final String policyIdJson) {
        if (JsonFactory.readFrom(policyIdJson).isNull()) {
            throw PolicyIdNotDeletableException.newBuilder().build();
        }

        return policyIdFromJson(policyIdJson);
    }

    private static PolicyId policyIdFromJson(final String policyIdJson) {
        return PolicyId.of(Optional.of(JsonFactory.readFrom(policyIdJson))
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .orElse(policyIdJson));
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
                                // GET /things/<thingId>/attributes?fields=<fieldsString>
                                get(() -> parameterOptional(ThingsParameter.FIELDS.toString(), fieldsString ->
                                                handlePerRequest(ctx, RetrieveAttributes.of(thingId,
                                                        calculateSelectedFields(fieldsString).orElse(null),
                                                        dittoHeaders))
                                        )
                                ),
                                // PUT /things/<thingId>/attributes
                                put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        attributesJson -> ModifyAttributes.of(thingId,
                                                                ThingsModelFactory.newAttributes(attributesJson),
                                                                dittoHeaders))
                                        )
                                ),
                                // PATCH /things/<thingId>/attributes
                                patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        attributesJson -> MergeThing.withAttributes(thingId,
                                                                ThingsModelFactory.newAttributes(attributesJson), dittoHeaders))
                                        )
                                ),
                                // DELETE /things/<thingId>/attributes
                                delete(() -> handlePerRequest(ctx, DeleteAttributes.of(thingId, dittoHeaders)))
                        )
                )
        );
    }

    /*
     * Describes {@code /things/<thingId>/attributes/<attributesSelector>} route.
     *
     * {@code attributeJsonPointer} JSON pointer to GET/PUT/PATCH/DELETE e.g.:
     * <pre>
     *    GET /things/fancy-car-1/attributes/model
     *    PUT /things/fancy-car-1/attributes/someProp
     *    PATCH /things/fancy-car-1/attributes/someProp
     *    DELETE /things/fancy-car-1/attributes/foo/bar
     * </pre>
     *
     * @return {@code /things/<thingId>/attributes/<attributeJsonPointer>} route.
     */
    private Route thingsEntryAttributesEntry(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        return rawPathPrefix(PathMatchers.slash()
                        .concat(PATH_ATTRIBUTES)
                        .concat(PathMatchers.slash())
                        .concat(PathMatchers.remaining())
                        .map(path -> UriEncoding.decode(path, UriEncoding.EncodingType.RFC3986))
                        .map(path -> "/" + path), // Prepend slash to path to fail request with double slashes
                jsonPointerString -> concat(
                        // GET /things/<thingId>/attributes/<attributePointerStr>
                        get(() ->
                                handlePerRequest(ctx, RetrieveAttribute.of(thingId,
                                        JsonFactory.newPointer(jsonPointerString),
                                        dittoHeaders))
                        ),
                        // PUT /things/<thingId>/attributes/<attributePointerStr>
                        put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource, attributeValueJson ->
                                                        ModifyAttribute.of(thingId,
                                                                JsonFactory.newPointer(jsonPointerString),
                                                                DittoJsonException.wrapJsonRuntimeException(() ->
                                                                        JsonFactory.readFrom(attributeValueJson)),
                                                                dittoHeaders))
                                )
                        ),
                        // PATCH /things/<thingId>/attributes/<attributePointerStr>
                        patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource, attributeValueJson ->
                                                        MergeThing.withAttribute(thingId,
                                                                JsonFactory.newPointer(jsonPointerString),
                                                                DittoJsonException.wrapJsonRuntimeException(() ->
                                                                        JsonFactory.readFrom(attributeValueJson)),
                                                                dittoHeaders)
                                                )
                                )
                        ),
                        // DELETE /things/<thingId>/attributes/<attributePointerStr>
                        delete(() -> handlePerRequest(ctx,
                                DeleteAttribute.of(thingId, JsonFactory.newPointer(jsonPointerString), dittoHeaders))
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
                                // GET /things/<thingId>/definition
                                get(() -> handlePerRequest(ctx, RetrieveThingDefinition.of(thingId, dittoHeaders)
                                        )
                                ),
                                // PUT /things/<thingId>/definition
                                put(() -> ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource ->
                                                        pathEnd(() -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                definitionJson -> ModifyThingDefinition.of(thingId,
                                                                        getDefinitionFromJson(definitionJson),
                                                                        dittoHeaders))
                                                        )
                                        )
                                ),
                                // PATCH /things/<thingId>/definition
                                patch(() -> ensureMediaTypeMergePatchJsonThenExtractDataBytes(ctx, dittoHeaders,
                                                payloadSource ->
                                                        pathEnd(() -> handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                                definitionJson -> MergeThing.withThingDefinition(thingId,
                                                                        getDefinitionFromJson(definitionJson), dittoHeaders))
                                                        )
                                        )
                                ),
                                // DELETE /things/<thingId>/definition
                                delete(() -> handlePerRequest(ctx, DeleteThingDefinition.of(thingId, dittoHeaders)))
                        )
                )
        );
    }

    private ThingDefinition getDefinitionFromJson(final String definitionJson) {
        return DittoJsonException.wrapJsonRuntimeException(() -> {
            final ThingDefinition result;
            final JsonValue jsonValue = JsonFactory.readFrom(definitionJson);
            if (jsonValue.isNull()) {
                result = ThingsModelFactory.nullDefinition();
            } else {
                result = ThingsModelFactory.newDefinition(jsonValue.asString());
            }
            return result;
        });
    }

    /*
     * Describes {@code /things/<thingId>/features} route.
     *
     * @return {@code /things/<thingId>/features} route.
     */
    private Route thingsEntryFeatures(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        // /things/<thingId>/features
        return featuresRoute.buildFeaturesRoute(ctx, dittoHeaders, thingId);
    }

    /*
     * Describes {@code /things/<thingId>/{inbox|outbox}} route.
     *
     * @return {@code /things/<thingId>/{inbox|outbox}} route.
     */
    private Route thingsEntryInboxOutbox(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ThingId thingId) {
        // /things/<thingId>/<inbox|outbox>
        return messagesRoute.buildThingsInboxOutboxRoute(ctx, dittoHeaders, thingId);
    }

}
