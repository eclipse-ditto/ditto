/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.gateway.api.GatewayServiceUnavailableException;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedSignals;
import org.eclipse.ditto.gateway.service.streaming.signals.Connect;
import org.eclipse.ditto.gateway.service.streaming.signals.StartStreaming;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.protocol.PartialAccessSignalFilter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.eclipse.ditto.thingsearch.api.commands.sudo.StreamThings;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.MediaTypes;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import org.apache.pekko.stream.javadsl.StreamRefs;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Unit test for the route built with {@link ThingsSseRouteBuilder}.
 */
public final class ThingsSseRouteBuilderTest extends EndpointTestBase {

    private static final String THINGS_ROUTE = "/things";
    private static final String SEARCH_ROUTE = "/search/things";

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    // Uses a dedicated daemon thread instead of ForkJoinPool.commonPool() to avoid
    // blocking the common pool's single thread on CI environments with 1-2 cores.
    private static final Executor ASYNC_TEST_EXECUTOR = command -> {
        final Thread thread = new Thread(command, "ThingsSseRouteBuilderTest-async");
        thread.setDaemon(true);
        thread.start();
    };

    private static ActorSystem actorSystem;
    private static HttpHeader acceptHeader;

    private CharSequence connectionCorrelationId;
    private TestProbe streamingActor;
    private TestRoute underTest;
    private TestProbe proxyActor;

    @BeforeClass
    public static void setUpClass() {
        actorSystem = ActorSystem.create(ThingsSseRouteBuilderTest.class.getSimpleName(), ConfigFactory.load("test"));
        acceptHeader = HttpHeader.parse("Accept", "text/event-stream");
    }

    @AfterClass
    public static void tearDownClass() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Before
    public void setUp() {
        streamingActor = TestProbe.apply("streaming", actorSystem);
        proxyActor = TestProbe.apply("proxy", actorSystem);

        connectionCorrelationId = testNameCorrelationId.getCorrelationId();

        final Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier =
                () -> CompletableFuture.completedFuture(dittoHeaders);

        final var sseRouteBuilder =
                ThingsSseRouteBuilder.getInstance(actorSystem, streamingActor.ref(), streamingConfig, proxyActor.ref(),
                        HeaderTranslator.empty());
        sseRouteBuilder.withProxyActor(proxyActor.ref());
        final Route sseRoute = extractRequestContext(ctx -> sseRouteBuilder.build(ctx, dittoHeadersSupplier));
        underTest = testRoute(handleExceptions(() -> sseRoute));
    }

    @Test
    public void getWithoutAcceptHeaderFails() {
        underTest.run(HttpRequest.GET(THINGS_ROUTE)).assertStatusCode(StatusCodes.NOT_FOUND);
        underTest.run(HttpRequest.GET(SEARCH_ROUTE)).assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putWithAcceptHeaderFails() {
        underTest.run(HttpRequest.PUT(THINGS_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        underTest.run(HttpRequest.PUT(SEARCH_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postWithAcceptHeaderFails() {
        underTest.run(HttpRequest.POST(THINGS_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        underTest.run(HttpRequest.POST(SEARCH_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void deleteWithAcceptHeaderFails() {
        underTest.run(HttpRequest.DELETE(THINGS_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        underTest.run(HttpRequest.DELETE(SEARCH_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getWithAcceptHeaderAndNoQueryParametersOpensSseConnection() {
        executeThingsRouteTest(HttpRequest.GET(THINGS_ROUTE).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                Collections.emptySet())).build());
    }

    @Test
    public void getWithAcceptHeaderAndNoQueryParametersOnSpecificThingOpensSseConnection() {
        executeThingsRouteTest(HttpRequest.GET(THINGS_ROUTE + "/org.eclipse.ditto:my-thing-1").addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                Collections.emptySet()))
                        .withThingIds(List.of(ThingId.of("org.eclipse.ditto:my-thing-1")))
                        .build());
    }

    @Test
    public void searchWithoutQueryParameters() {
        final TestRouteResult routeResult = underTest.run(HttpRequest.GET(SEARCH_ROUTE).addHeader(acceptHeader));
        final CompletableFuture<Void> assertions =
                CompletableFuture.runAsync(() -> {
                    routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                    routeResult.assertStatusCode(StatusCodes.OK);
                }, ASYNC_TEST_EXECUTOR);
        proxyActor.expectMsgClass(StreamThings.class);
        replySourceRef(proxyActor, Source.lazily(Source::empty));
        assertions.join();
    }

    @Test
    public void searchWithQueryParameters() {
        final String filter = "not(exists(thingId))";
        final String option = "sort(-policyId)";
        final String namespaces = "a,b,c";
        final String url = SEARCH_ROUTE + "?filter=" + filter + "&option=" + option + "&namespaces=" + namespaces;
        final TestRouteResult routeResult = underTest.run(HttpRequest.GET(url).addHeader(acceptHeader));
        final CompletableFuture<Void> assertions =
                CompletableFuture.runAsync(() -> {
                    routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                    routeResult.assertStatusCode(StatusCodes.OK);
                }, ASYNC_TEST_EXECUTOR);
        final StreamThings streamThings = proxyActor.expectMsgClass(StreamThings.class);
        replySourceRef(proxyActor, Source.lazily(Source::empty));
        assertions.join();
        assertThat(streamThings.getFilter()).contains(filter);
        assertThat(streamThings.getNamespaces()).contains(Set.of("a", "b", "c"));
        // sort options are parsed and appended with +/thingId
        assertThat(streamThings.getSort()).contains("sort(-/policyId,+/thingId)");
    }

    @Test
    public void searchWithResumption() {
        final String lastEventId = "my:lastEventId";
        final HttpHeader lastEventIdHeader = HttpHeader.parse("Last-Event-ID", lastEventId);
        final TestRouteResult routeResult = underTest.run(
                HttpRequest.GET(SEARCH_ROUTE)
                        .addHeader(acceptHeader)
                        .addHeader(lastEventIdHeader)
        );
        final CompletableFuture<Void> assertions =
                CompletableFuture.runAsync(() -> {
                    routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                    routeResult.assertStatusCode(StatusCodes.OK);
                }, ASYNC_TEST_EXECUTOR);
        final RetrieveThing retrieveThing = proxyActor.expectMsgClass(RetrieveThing.class);
        final Thing thing = Thing.newBuilder().setId(ThingId.of(lastEventId)).build();
        proxyActor.reply(RetrieveThingResponse.of(ThingId.of(lastEventId),
                thing,
                null,
                null,
                retrieveThing.getDittoHeaders()
        ));
        final StreamThings streamThings = proxyActor.expectMsgClass(StreamThings.class);
        replySourceRef(proxyActor, Source.lazily(Source::empty));
        assertions.join();
        assertThat(streamThings.getSortValues()).contains(JsonArray.of(JsonValue.of(lastEventId)));
    }

    @Test
    public void getWithAcceptHeaderAndFilterParameterOpensSseConnection() {
        final String filter = "eq(attributes/manufacturer,\"ACME\")";

        final String requestUrl = THINGS_ROUTE + "?filter=" + filter;

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withFilter(filter)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndSpecificThingAndFilterOpensSseConnection() {
        final String filter = "eq(attributes/manufacturer,\"ACME\")";

        final String requestUrl = THINGS_ROUTE + "/org.eclipse.ditto:my-thing-1?filter=" + filter;

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withThingIds(List.of(ThingId.of("org.eclipse.ditto:my-thing-1")))
                        .withFilter(filter)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndPlaceholderFilterOpensSseConnection() {
        final String filter = "and(eq(entity:id,\"org.eclipse.ditto:my-thing-1\"),eq(thing:name,\"my-thing-1\")," +
                "eq(feature:id,\"lamp\"),eq(topic:action,\"modified\"),eq(resource:path,\"/\"))";

        final String requestUrl = THINGS_ROUTE + "?filter=" + filter;

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withFilter(filter)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndUnsupportedPlaceholderFilterFails() {
        final String requestUrl = THINGS_ROUTE + "?filter=eq(policy:id,\"org.eclipse.ditto:my-policy\")";

        underTest.run(HttpRequest.GET(requestUrl).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getHistoryWithAcceptHeaderAndThingPlaceholderFilterFails() {
        // the filter of a history subscription is evaluated by the things service, which does not resolve "thing:"
        final String requestUrl = THINGS_ROUTE + "/org.eclipse.ditto:my-thing-1?from-historical-revision=1" +
                "&filter=eq(thing:name,\"my-thing-1\")";

        underTest.run(HttpRequest.GET(requestUrl).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getWithAcceptHeaderAndSpecificThingAndSubpathSseConnection() {
        final String requestUrl = THINGS_ROUTE + "/org.eclipse.ditto:my-thing-1/attributes/manufacturer";

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withThingIds(List.of(ThingId.of("org.eclipse.ditto:my-thing-1")))
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndSpecificThingForMessageInboxOpensSseConnection() {
        final String thingId = "org.eclipse.ditto:my-thing-1";
        final String requestUrl = THINGS_ROUTE + "/" + thingId + "/inbox/messages";

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.MESSAGES, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withThingIds(List.of(ThingId.of("org.eclipse.ditto:my-thing-1")))
                        .withFilter(String.format("and(eq(entity:id,'%s'),like(resource:path,'%s*'))", thingId,
                                "/inbox/messages"))
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndSpecificThingForMessageInboxSpecifiedSubjectOpensSseConnection() {
        final String thingId = "org.eclipse.ditto:my-thing-1";
        final String requestUrl = THINGS_ROUTE + "/" + thingId + "/inbox/messages/hello-world";

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.MESSAGES, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withThingIds(List.of(ThingId.of("org.eclipse.ditto:my-thing-1")))
                        .withFilter(String.format("and(eq(entity:id,'%s'),eq(resource:path,'%s'))", thingId,
                                "/inbox/messages/hello-world"))
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndIdsParameterOpensSseConnection() {
        final List<ThingId> thingIds =
                List.of(ThingId.of("org.eclipse.ditto:my-thing-1"), ThingId.of("org.eclipse.ditto:my-thing-2"));

        final String requestUrl = THINGS_ROUTE + "?ids=" + thingIds.get(0) + "," + thingIds.get(1);

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withThingIds(thingIds)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndNamespacesParameterOpensSseConnection() {
        final Collection<String> namespaces = Lists.list("john", "frum", "tanna");

        final String requestUrl = THINGS_ROUTE + "?namespaces=" + String.join(",", namespaces);

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withNamespaces(namespaces)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndExtraFieldsParameterOpensSseConnection() {
        final ThingFieldSelector extraFields = ThingFieldSelector.fromJsonFieldSelector(
                JsonFieldSelector.newInstance("attributes", "features/location"));

        final String requestUrl = THINGS_ROUTE + "?extraFields=" + extraFields;

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                        Collections.emptySet()))
                        .withExtraFields(extraFields)
                        .build());
    }

    @Test
    public void partialReaderSeesOnlyGrantedFieldsAndThingId() {
        final Thing thing = Thing.newBuilder()
                .setId(ThingId.of("test:thing"))
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .setFeatures(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(FeatureProperties.newBuilder()
                                        .set("baz", 42)
                                        .set("qux", 100)
                                        .build())
                                .withId("fluxCompensator")
                                .build())
                        .build())
                .build();
        final AuthorizationSubject partialReader = AuthorizationSubject.newInstance("test:partial-reader");

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:partial-reader\"],\"paths\":{\"attributes\":[0],\"attributes/foo\":[0],\"features/fluxCompensator/properties/baz\":[0]}}")
                .readGrantedSubjects(Collections.singletonList(partialReader))
                .build();

        final ThingModifiedEvent<?> event = ThingModified.of(thing, 1L, null, headers, null);

        final AuthorizationContext subscriberAuthContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, partialReader);

        final JsonObject filtered = readableThingJson(thing, event, subscriberAuthContext);

        // thingId is an entity-identity field that must always be visible to any subscriber
        // holding at least partial read access. _namespace/_revision/_modified/_created are
        // verified separately in partialReaderSeesSelectedAuditFields
        // (they only appear in the SSE payload when the user explicitly selects them via
        // ?fields=... since they are HIDDEN fields by default).
        assertThat(filtered.getValue("thingId")).contains(JsonValue.of("test:thing"));
        assertThat(filtered.getValue("attributes")).isPresent();
        assertThat(filtered.getValue("attributes").get().asObject().getValue("foo")).isPresent();
        assertThat(filtered.getValue("features")).isPresent();
        assertThat(filtered.getValue("features").get().asObject().getValue("fluxCompensator")).isPresent();
        assertThat(filtered.getValue("features").get().asObject()
                .getValue("fluxCompensator").get().asObject()
                .getValue("properties").get().asObject()
                .getValue("baz")).isPresent();
        assertThat(filtered.getValue("policyId")).isEmpty();
    }

    @Test
    public void partialReaderSeesSelectedAuditFields() {
        final java.time.Instant created = java.time.Instant.parse("2026-01-01T00:00:00Z");
        final java.time.Instant modified = java.time.Instant.parse("2026-06-01T12:00:00Z");
        final Thing thing = Thing.newBuilder()
                .setId(ThingId.of("test:thing"))
                .setPolicyId(org.eclipse.ditto.policies.model.PolicyId.of("test:policy"))
                .setRevision(42L)
                .setCreated(created)
                .setModified(modified)
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .build();
        final AuthorizationSubject partialReader = AuthorizationSubject.newInstance("test:partial-reader");

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:partial-reader\"],\"paths\":{\"attributes\":[0]}}")
                .readGrantedSubjects(Collections.singletonList(partialReader))
                .build();

        final ThingModifiedEvent<?> event = ThingModified.of(thing, 42L, modified, headers, null);

        final AuthorizationContext subscriberAuthContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, partialReader);

        // Build the thing JSON with SPECIAL fields included, mirroring the production path where a
        // caller has explicitly opted into hidden identity/audit fields via ?fields=_revision,...
        final JsonObject thingJson = thing.toJson(org.eclipse.ditto.base.model.json.JsonSchemaVersion.LATEST,
                org.eclipse.ditto.base.model.json.FieldType.regularOrSpecial());
        final JsonObject filtered = readableThingJson(thingJson, event, subscriberAuthContext);

        // Identity + audit fields must pass through even though the policy only grants /attributes:
        assertThat(filtered.getValue("thingId")).contains(JsonValue.of("test:thing"));
        assertThat(filtered.getValue("_namespace")).contains(JsonValue.of("test"));
        assertThat(filtered.getValue("_revision")).contains(JsonValue.of(42L));
        assertThat(filtered.getValue("_modified")).contains(JsonValue.of(modified.toString()));
        assertThat(filtered.getValue("_created")).contains(JsonValue.of(created.toString()));
        // policyId is not allowlisted and must remain filtered out:
        assertThat(filtered.getValue("policyId")).isEmpty();
        // The actually-granted resource is present:
        assertThat(filtered.getValue("attributes")).isPresent();
    }

    @Test
    public void unrestrictedReaderSeesWholeThing() {
        final Thing thing = Thing.newBuilder().setId(ThingId.of("test:thing")).build();
        final AuthorizationSubject fullReader = AuthorizationSubject.newInstance("test:full-reader");
        final AuthorizationSubject partialReader = AuthorizationSubject.newInstance("test:partial-reader");

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:partial-reader\"],\"paths\":{\"attributes\":[0]}}")
                .readGrantedSubjects(List.of(fullReader, partialReader))
                .build();

        final ThingModifiedEvent<?> event = ThingModified.of(thing, 1L, null, headers, null);

        final AuthorizationContext subscriberAuthContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, fullReader);

        final JsonObject filtered = readableThingJson(thing, event, subscriberAuthContext);

        assertThat(filtered).isEqualTo(thing.toJson());
    }

    @Test
    public void subscriberWithPartialAndFullSubjectSeesWholeThing() {
        final Thing thing = Thing.newBuilder()
                .setId(ThingId.of("test:thing"))
                .setAttributes(Attributes.newBuilder().set("foo", "bar").build())
                .setFeatures(Features.newBuilder()
                        .set(Feature.newBuilder()
                                .properties(FeatureProperties.newBuilder()
                                        .set("baz", 42)
                                        .build())
                                .withId("fluxCompensator")
                                .build())
                        .build())
                .build();
        final AuthorizationSubject fullReader = AuthorizationSubject.newInstance("test:full-reader");
        final AuthorizationSubject partialReader = AuthorizationSubject.newInstance("test:partial-reader");

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:partial-reader\"],\"paths\":{\"attributes\":[0],\"attributes/foo\":[0]}}")
                .readGrantedSubjects(List.of(fullReader, partialReader))
                .build();

        final ThingModifiedEvent<?> event = ThingModified.of(thing, 1L, null, headers, null);

        // Subscriber has BOTH partial and full-access subjects — unrestricted must take precedence
        final AuthorizationContext subscriberAuthContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, partialReader, fullReader);

        final JsonObject filtered = readableThingJson(thing, event, subscriberAuthContext);

        assertThat(filtered).isEqualTo(thing.toJson());
    }

    @Test
    public void subscriberWithoutReadAccessSeesNothing() {
        final Thing thing = Thing.newBuilder().setId(ThingId.of("test:thing")).build();
        final AuthorizationSubject partialReader = AuthorizationSubject.newInstance("test:partial-reader");
        final AuthorizationSubject noAccessReader = AuthorizationSubject.newInstance("test:no-access");

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:partial-reader\"],\"paths\":{\"attributes\":[0]}}")
                .readGrantedSubjects(Collections.singletonList(partialReader))
                .build();

        final ThingModifiedEvent<?> event = ThingModified.of(thing, 1L, null, headers, null);

        final AuthorizationContext subscriberAuthContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, noAccessReader);

        final JsonObject filtered = readableThingJson(thing, event, subscriberAuthContext);

        assertThat(filtered).isEmpty();
    }

    @Test
    public void eventWithoutAccessPathsHeaderIsNotFiltered() {
        final Thing thing = Thing.newBuilder().setId(ThingId.of("test:thing")).build();
        final AuthorizationSubject reader = AuthorizationSubject.newInstance("test:reader");

        final DittoHeaders headers = DittoHeaders.newBuilder()
                .readGrantedSubjects(Collections.singletonList(reader))
                .build();

        final ThingModifiedEvent<?> event = ThingModified.of(thing, 1L, null, headers, null);

        final AuthorizationContext subscriberAuthContext = AuthorizationContext.newInstance(
                DittoAuthorizationContextType.UNSPECIFIED, reader);

        final JsonObject filtered = readableThingJson(thing, event, subscriberAuthContext);

        assertThat(filtered).isEqualTo(thing.toJson());
    }

    private static JsonObject readableThingJson(final Thing thing,
            final ThingModifiedEvent<?> event,
            final AuthorizationContext subscriberAuthContext) {
        return readableThingJson(thing.toJson(), event, subscriberAuthContext);
    }

    private static JsonObject readableThingJson(final JsonObject thingJson,
            final ThingModifiedEvent<?> event,
            final AuthorizationContext subscriberAuthContext) {
        return PartialAccessSignalFilter.viewFor(event, subscriberAuthContext)
                .map(view -> view.filterThingJson(thingJson))
                .orElseGet(JsonObject::empty);
    }

    @Test
    public void sessionSummaryCountsSignalsOfTheSession() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:my-thing-1");
        final ThingId otherThingId = ThingId.of("org.eclipse.ditto:other-thing");
        final String filter = "eq(attributes/manufacturer,\"ACME\")";
        final ThingFieldSelector extraFields =
                ThingFieldSelector.fromJsonFieldSelector(JsonFieldSelector.newInstance("attributes"));
        final String requestUrl = THINGS_ROUTE + "?ids=" + thingId + "&filter=" + filter;

        final List<Map<String, String>> summaryMdcs = new CopyOnWriteArrayList<>();
        final Logger statisticsLogger = (Logger) LoggerFactory.getLogger(SseSessionStatistics.class);
        final Level previousLevel = statisticsLogger.getLevel();
        final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(final ILoggingEvent event) {
                // the MDC is only present while logging, so it has to be copied right away
                summaryMdcs.add(Map.copyOf(event.getMDCPropertyMap()));
            }
        };
        appender.start();
        statisticsLogger.addAppender(appender);
        statisticsLogger.setLevel(Level.INFO);
        try {
            new TestKit(actorSystem) {{
                final TestRouteResult routeResult = underTest.run(HttpRequest.GET(requestUrl).addHeader(acceptHeader));
                final CompletableFuture<Void> routeTestAssertions = CompletableFuture.runAsync(() -> {
                    routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                    routeResult.assertStatusCode(StatusCodes.OK);
                }, ASYNC_TEST_EXECUTOR);

                final Connect receivedConnect = streamingActor.expectMsgClass(Connect.class);
                streamingActor.reply(streamingActor.ref());
                streamingActor.expectMsgClass(StartStreaming.class);
                final SourceQueueWithComplete<SessionedJsonifiable> publisherQueue =
                        receivedConnect.getEventAndResponsePublisher();

                // delivered:
                offer(publisherQueue, manufacturerModified(thingId, "ACME"), filter, null);
                // filtered out by the RQL filter:
                offer(publisherQueue, manufacturerModified(thingId, "Foo Inc."), filter, null);
                // not targeted as the session only subscribed for "thingId":
                offer(publisherQueue, manufacturerModified(otherThingId, "ACME"), filter, null);
                // extra fields retrieval fails as no enrichment facade is configured, the error is delivered:
                offer(publisherQueue, manufacturerModified(thingId, "ACME"), filter, extraFields);
                publisherQueue.complete();
                routeTestAssertions.join();

                awaitAssert(Duration.ofSeconds(5), () -> {
                    assertThat(summaryMdcs).hasSize(1);
                    return null;
                });
            }};
        } finally {
            statisticsLogger.detachAppender(appender);
            statisticsLogger.setLevel(previousLevel);
        }

        assertThat(summaryMdcs.get(0))
                .containsEntry(SseSessionStatistics.MDC_PATH, THINGS_ROUTE)
                .containsEntry(SseSessionStatistics.MDC_FILTER, "true")
                .containsEntry(SseSessionStatistics.MDC_EXTRA_FIELDS, "false")
                .containsEntry(SseSessionStatistics.MDC_RECEIVED, "4")
                .containsEntry(SseSessionStatistics.MDC_TARGETED, "3")
                .containsEntry(SseSessionStatistics.MDC_FILTERED_OUT, "1")
                .containsEntry(SseSessionStatistics.MDC_ENRICHMENT_FAILED, "1")
                .containsEntry(SseSessionStatistics.MDC_DELIVERED, "2");
    }

    @Test
    public void filterOfPartialReaderDoesNotSeeUnreadableFieldsMergedIntoTheEnrichmentCache() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:my-thing-1");
        final AuthorizationSubject carol = AuthorizationSubject.newInstance("nginx:carol");
        final DittoHeaders partialForCarol = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"nginx:carol\"],\"paths\":{\"attributes/pub\":[0]}}")
                .readGrantedSubjects(List.of(carol))
                .build();
        final DittoHeaders carolSessionHeaders = dittoHeaders.toBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        carol))
                .build();
        final ThingFieldSelector extraFields =
                ThingFieldSelector.fromJsonFieldSelector(JsonFieldSelector.newInstance("attributes"));
        final AtomicInteger roundTrips = new AtomicInteger();
        final SignalEnrichmentFacade roundTripFacade = (id, selector, headers, signal) -> {
            roundTrips.incrementAndGet();
            return CompletableFuture.completedFuture(
                    JsonObject.of("{\"_revision\":3,\"attributes\":{\"pub\":\"p0\"}}"));
        };
        final SignalEnrichmentFacade facade = DittoCachingSignalEnrichmentFacade.newInstance(roundTripFacade,
                DefaultCacheConfig.of(ConfigFactory.parseString("cache { maximum-size = 10\n" +
                        "expire-after-create = 2m }"), "cache"),
                Runnable::run, "test");
        final var sseRouteBuilder = ThingsSseRouteBuilder.getInstance(actorSystem, streamingActor.ref(),
                streamingConfig, proxyActor.ref(), HeaderTranslator.empty());
        sseRouteBuilder.withProxyActor(proxyActor.ref());
        sseRouteBuilder.withSignalEnrichmentProvider(request -> CompletableFuture.completedFuture(facade));
        final Route sseRoute = extractRequestContext(ctx -> sseRouteBuilder.build(ctx,
                () -> CompletableFuture.completedFuture(dittoHeaders)));
        final TestRoute route = testRoute(handleExceptions(() -> sseRoute));

        final AttributeModified rev3 = AttributeModified.of(thingId, JsonPointer.of("pub"), JsonValue.of("p0"), 3L,
                null, partialForCarol, null);
        final AttributesModified rev4 = AttributesModified.of(thingId,
                Attributes.newBuilder().set("pub", "p2").set("secret", "s2").build(), 4L, null, partialForCarol,
                null);

        final AtomicReference<String> entity = new AtomicReference<>();
        new TestKit(actorSystem) {{
            final TestRouteResult routeResult = route.run(HttpRequest.GET(THINGS_ROUTE + "?ids=" + thingId +
                    "&extraFields=attributes").addHeader(acceptHeader));
            final CompletableFuture<Void> routeTestAssertions = CompletableFuture.runAsync(() -> {
                routeResult.assertStatusCode(StatusCodes.OK);
                entity.set(routeResult.entityString());
            }, ASYNC_TEST_EXECUTOR);

            final Connect receivedConnect = streamingActor.expectMsgClass(Connect.class);
            streamingActor.reply(streamingActor.ref());
            streamingActor.expectMsgClass(StartStreaming.class);
            final SourceQueueWithComplete<SessionedJsonifiable> publisherQueue =
                    receivedConnect.getEventAndResponsePublisher();

            offerSessioned(publisherQueue, SessionedSignals.of(rev3, carolSessionHeaders, null, extraFields));
            offerSessioned(publisherQueue, SessionedSignals.of(rev4, carolSessionHeaders,
                    "eq(attributes/secret,\"s2\")", extraFields));
            offerSessioned(publisherQueue, SessionedSignals.of(rev4, carolSessionHeaders,
                    "eq(attributes/pub,\"p2\")", extraFields));
            publisherQueue.complete();
            routeTestAssertions.join();
        }};

        assertThat(roundTrips).hasValue(1);
        assertThat(entity.get()).doesNotContain("secret").doesNotContain("s2");
        assertThat(entity.get().split("data:", -1)).hasSize(3);
        assertThat(entity.get()).contains("\"pub\":\"p2\"");
    }

    @Test
    public void unreadableEventsAreDroppedAndCountedAsFilteredOut() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto:my-thing-1");
        final AuthorizationSubject alice = AuthorizationSubject.newInstance("nginx:alice");
        final DittoHeaders revokedForGroupX = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"nginx:group-x\"],\"paths\":{\"attributes/secret\":[0]}}")
                .readGrantedSubjects(List.of(alice))
                .build();
        final DittoHeaders sessionHeaders = dittoHeaders.toBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        alice, AuthorizationSubject.newInstance("nginx:group-x")))
                .build();
        final ThingMerged secretDeleted = ThingMerged.of(thingId, JsonPointer.empty(),
                JsonObject.of("{\"attributes\":{\"secret\":null}}"), 2L, null, revokedForGroupX, null);
        final AttributeModified secretModified = AttributeModified.of(thingId, JsonPointer.of("secret"),
                JsonValue.of("s"), 3L, null, revokedForGroupX, null);
        final AttributeModified pubModified = AttributeModified.of(thingId, JsonPointer.of("pub"),
                JsonValue.of("p"), 4L, null, revokedForGroupX, null);

        final List<Map<String, String>> summaryMdcs = new CopyOnWriteArrayList<>();
        final Logger statisticsLogger = (Logger) LoggerFactory.getLogger(SseSessionStatistics.class);
        final Level previousLevel = statisticsLogger.getLevel();
        final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(final ILoggingEvent event) {
                summaryMdcs.add(Map.copyOf(event.getMDCPropertyMap()));
            }
        };
        appender.start();
        statisticsLogger.addAppender(appender);
        statisticsLogger.setLevel(Level.INFO);
        final AtomicReference<String> entity = new AtomicReference<>();
        try {
            new TestKit(actorSystem) {{
                final TestRouteResult routeResult =
                        underTest.run(HttpRequest.GET(THINGS_ROUTE + "?ids=" + thingId).addHeader(acceptHeader));
                final CompletableFuture<Void> routeTestAssertions = CompletableFuture.runAsync(() -> {
                    routeResult.assertStatusCode(StatusCodes.OK);
                    entity.set(routeResult.entityString());
                }, ASYNC_TEST_EXECUTOR);

                final Connect receivedConnect = streamingActor.expectMsgClass(Connect.class);
                streamingActor.reply(streamingActor.ref());
                streamingActor.expectMsgClass(StartStreaming.class);
                final SourceQueueWithComplete<SessionedJsonifiable> publisherQueue =
                        receivedConnect.getEventAndResponsePublisher();

                offerSessioned(publisherQueue, SessionedSignals.of(secretDeleted, sessionHeaders, null, null));
                offerSessioned(publisherQueue, SessionedSignals.of(secretModified, sessionHeaders, null, null));
                offerSessioned(publisherQueue, SessionedSignals.of(pubModified, sessionHeaders, null, null));
                publisherQueue.complete();
                routeTestAssertions.join();

                awaitAssert(Duration.ofSeconds(5), () -> {
                    assertThat(summaryMdcs).hasSize(1);
                    return null;
                });
            }};
        } finally {
            statisticsLogger.detachAppender(appender);
            statisticsLogger.setLevel(previousLevel);
        }

        assertThat(entity.get().split("data:", -1)).hasSize(2);
        assertThat(entity.get()).doesNotContain("secret");
        assertThat(summaryMdcs.get(0))
                .containsEntry(SseSessionStatistics.MDC_TARGETED, "3")
                .containsEntry(SseSessionStatistics.MDC_FILTERED_OUT, "2")
                .containsEntry(SseSessionStatistics.MDC_DELIVERED, "1");
    }

    private static void offerSessioned(final SourceQueueWithComplete<SessionedJsonifiable> publisherQueue,
            final SessionedJsonifiable sessionedJsonifiable) {
        publisherQueue.offer(sessionedJsonifiable).toCompletableFuture().join();
    }

    private void offer(final SourceQueueWithComplete<SessionedJsonifiable> publisherQueue,
            final ThingModified event,
            final String filter,
            @Nullable final ThingFieldSelector extraFields) {

        publisherQueue.offer(SessionedSignals.of(event, dittoHeaders, filter, extraFields))
                .toCompletableFuture()
                .join();
    }

    private static ThingModified manufacturerModified(final ThingId thingId, final String manufacturer) {
        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(Attributes.newBuilder().set("manufacturer", manufacturer).build())
                .build();
        return ThingModified.of(thing, 1L, null, DittoHeaders.empty(), null);
    }

    /*
     * Please change this method only if you know exactly what you do.
     * The order of statements and everything else is crucial as SSE route testing is not provided by Pekko HTTP
     * route test kit.
     */
    private void executeThingsRouteTest(final HttpRequest httpRequest, final StartStreaming expectedStartStreaming) {
        new TestKit(actorSystem) {{
            final TestRouteResult routeResult = underTest.run(httpRequest);

            final CompletableFuture<Void> routeTestAssertions = CompletableFuture.runAsync(() -> {
                routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                routeResult.assertStatusCode(StatusCodes.OK);
            }, ASYNC_TEST_EXECUTOR);

            final Connect receivedConnect = streamingActor.expectMsgClass(Connect.class);
            streamingActor.reply(streamingActor.ref());

            final SourceQueueWithComplete<SessionedJsonifiable> publisherQueue =
                    receivedConnect.getEventAndResponsePublisher();

            final StartStreaming receivedStartStreaming = streamingActor.expectMsgClass(StartStreaming.class);

            // exclude connectionCorrelationId from being equal as the backend adds a random UUID to it:
            assertThat(receivedStartStreaming.getAuthorizationContext())
                    .isEqualTo(expectedStartStreaming.getAuthorizationContext());
            assertThat(receivedStartStreaming.getExtraFields())
                    .isEqualTo(expectedStartStreaming.getExtraFields());
            assertThat(receivedStartStreaming.getFilter())
                    .isEqualTo(expectedStartStreaming.getFilter());
            assertThat(receivedStartStreaming.getNamespaces())
                    .isEqualTo(expectedStartStreaming.getNamespaces());
            assertThat(receivedStartStreaming.getThingIds())
                    .isEqualTo(expectedStartStreaming.getThingIds());
            assertThat(receivedStartStreaming.getStreamingType())
                    .isEqualTo(expectedStartStreaming.getStreamingType());

            publisherQueue.fail(GatewayServiceUnavailableException.newBuilder().build());

            try {
                routeTestAssertions.join();
            } catch (final CompletionException e) {
                // Ignore as the Future is expected to fail because of exceptionally closing the SSE stream.
            }
        }};
    }

    private static void replySourceRef(final TestProbe testProbe, final Source<?, ?> source) {
        testProbe.reply(source.toMat(StreamRefs.sourceRef(), Keep.right()).run(actorSystem));
    }
}
