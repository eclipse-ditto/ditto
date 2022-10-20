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

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.internal.utils.tracing.TraceInformationGenerator;
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
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;

/**
 * Unit test for {@link RequestTracingDirective}.
 */
public final class RequestTracingDirectiveTest extends JUnitRouteTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE = DittoTracingInitResource.newInstance(
            DittoTracingInitResource.TracingConfigBuilder.defaultValues()
                    .withTracingEnabled()
                    .build()
    );

    private static final SpanOperationName DISABLED_SPAN_OPERATION_NAME = SpanOperationName.of("/ws/2 GET");

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
    public void newInstanceWithDisabledWithNullThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> RequestTracingDirective.newInstanceWithDisabled(null))
                .withMessage("The disabledSpanOperationNames must not be null!")
                .withNoCause();
    }

    @Test
    public void traceRequestWithNullInnerRouteSupplierThrowsNullPointerException() {
        final var underTest = RequestTracingDirective.newInstanceWithDisabled(Set.of());

        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> underTest.traceRequest(null, null))
                .withMessage("The innerRouteSupplier must not be null!")
                .withNoCause();
    }

    @Test
    public void traceRequestDoesNotCallDittoTracingIfTracingIsDisabledForResolvedSpanOperationName() {
        final var underTest = RequestTracingDirective.newInstanceWithDisabled(Set.of(DISABLED_SPAN_OPERATION_NAME));

        final var innerRoute = Mockito.mock(Route.class);
        final var httpRequest = HttpRequest.create().withUri(Uri.create("/ws/2")).withMethod(HttpMethods.GET);

        try (final var dittoTracingMockedStatic = Mockito.mockStatic(DittoTracing.class)) {
            final var testRoute = testRoute(
                    extractRequestContext(
                            requestContext -> underTest.traceRequest(
                                    () -> innerRoute,
                                    testNameCorrelationId.getCorrelationId()
                            )
                    )
            );

            testRoute.run(httpRequest);
            dittoTracingMockedStatic.verifyNoInteractions();
        }
    }

    @Test
    public void traceRequestCallsDittoTracingIfTracingIsEnabledForResolvedSpanOperationName() {
        final var underTest = RequestTracingDirective.newInstanceWithDisabled(Set.of());

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
                            requestContext -> underTest.traceRequest(
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

}
