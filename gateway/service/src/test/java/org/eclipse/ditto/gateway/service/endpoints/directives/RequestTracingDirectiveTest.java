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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.W3C_TRACEPARENT;
import static org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.W3C_TRACESTATE;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.internal.utils.tracing.TraceInformationGenerator;
import org.eclipse.ditto.internal.utils.tracing.span.KamonTracingInitResource;
import org.eclipse.ditto.internal.utils.tracing.span.PreparedSpan;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;

/**
 * Unit test for {@link RequestTracingDirective}.
 */
public final class RequestTracingDirectiveTest extends JUnitRouteTest {

    @ClassRule
    public static final KamonTracingInitResource KAMON_TRACING_INIT_RESOURCE = KamonTracingInitResource.newInstance(
            KamonTracingInitResource.KamonTracingConfig.defaultValues()
                    .withIdentifierSchemeSingle()
                    .withSamplerAlways()
                    .withTickInterval(Duration.ofMillis(100L))
    );

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE = DittoTracingInitResource.newInstance(
            DittoTracingInitResource.TracingConfigBuilder.defaultValues()
                    .withTracingEnabled()
                    .build()
    );

    private static final SpanOperationName DISABLED_SPAN_OPERATION_NAME = SpanOperationName.of("GET /ws/2");

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(
                RequestTracingDirective.class,
                areImmutable(),
                provided(TraceInformationGenerator.class).isAlsoImmutable(),
                assumingFields("disabledSpanOperationNames")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void traceRequestWithNullInnerRouteSupplierThrowsNullPointerException() {

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> RequestTracingDirective.traceRequest(null, null))
                .withMessage("The innerRouteSupplier must not be null!")
                .withNoCause();
    }

    @Test
    public void traceRequestCallsDittoTracingIfTracingIsEnabledForResolvedSpanOperationName() {

        final var headersMap = Map.ofEntries(
                Map.entry(
                        DittoHeaderDefinition.CORRELATION_ID.getKey(),
                        testNameCorrelationId.getCorrelationId().toString()
                ),
                Map.entry("foo", "bar")
        );

        try (final var dittoTracingMockedStatic = Mockito.mockStatic(DittoTracing.class)) {
            final var preparedSpan = Mockito.mock(PreparedSpan.class);
            Mockito.when(preparedSpan.correlationId(Mockito.any())).thenReturn(preparedSpan);
            Mockito.when(preparedSpan.start()).thenReturn(Mockito.mock(StartedSpan.class));
            dittoTracingMockedStatic.when(() -> DittoTracing.newPreparedSpan(
                            Mockito.eq(headersMap),
                            Mockito.eq(DISABLED_SPAN_OPERATION_NAME))
                    )
                    .thenReturn(preparedSpan);
            final var testRoute = testRoute(
                    extractRequestContext(
                            requestContext -> RequestTracingDirective.traceRequest(
                                    () -> Mockito.mock(Route.class),
                                    testNameCorrelationId.getCorrelationId()
                            )
                    )
            );

            testRoute.run(
                    HttpRequest.create()
                            .withUri(Uri.create("/ws/2"))
                            .withMethod(HttpMethods.GET)
                            .withHeaders(headersMap.entrySet()
                                    .stream()
                                    .map(entry -> HttpHeader.parse(entry.getKey(), entry.getValue()))
                                    .collect(Collectors.toList()))
            );
            Mockito.verify(preparedSpan).correlationId(Mockito.eq(testNameCorrelationId.getCorrelationId()));
            Mockito.verify(preparedSpan).start();
        }
    }

    @Test
    public void traceRequestWithExistingW3cTracingHeadersReplacesThoseHeadersWithCurrentSpanContextHeaders() {
        final var expectedStatus = StatusCodes.NO_CONTENT;
        final var effectiveHttpRequestHeader = new CompletableFuture<Map<String, String>>();
        final var routeFactory = new AllDirectives() {
            public Route createRoute() {
                return path(
                        "my-route",
                        () -> extractRequest(request -> {
                            effectiveHttpRequestHeader.complete(
                                    StreamSupport.stream(request.getHeaders().spliterator(), false)
                                            .collect(Collectors.toMap(HttpHeader::name,
                                                    HttpHeader::value,
                                                    (oldValue, newValue) -> newValue))
                            );
                            return get(() -> complete(expectedStatus));
                        })
                );
            }
        };
        final var tracestateHeaderValue = ";";
        final var traceparentHeaderValue = "00-00000000000000002d773e5f58ee5636-28cae4bd320cbc11-0";
        final var fooHeaderValue = "bar";
        final var testRoute = testRoute(
                RequestTracingDirective.traceRequest(routeFactory::createRoute, testNameCorrelationId.getCorrelationId())
        );

        final var testRouteResult = testRoute.run(HttpRequest.create()
                .withUri(Uri.create("/my-route"))
                .withMethod(HttpMethods.GET)
                .addHeader(HttpHeader.parse(W3C_TRACESTATE.getKey(), tracestateHeaderValue))
                .addHeader(HttpHeader.parse(W3C_TRACEPARENT.getKey(), traceparentHeaderValue))
                .addHeader(HttpHeader.parse("foo", fooHeaderValue)));

        testRouteResult.assertStatusCode(expectedStatus);
        assertThat(effectiveHttpRequestHeader)
                .succeedsWithin(Duration.ofSeconds(1L))
                .satisfies(httpRequestHeaders -> assertThat(httpRequestHeaders)
                        .containsEntry("foo", fooHeaderValue)
                        .containsKeys(W3C_TRACEPARENT.getKey(), W3C_TRACESTATE.getKey())
                        .doesNotContainValue(tracestateHeaderValue)
                        .doesNotContainValue(traceparentHeaderValue));
    }

}
