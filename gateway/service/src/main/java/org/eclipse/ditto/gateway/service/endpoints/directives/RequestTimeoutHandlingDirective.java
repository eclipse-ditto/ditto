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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.gateway.service.endpoints.utils.HttpUtils.getRawRequestUri;

import java.time.Duration;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayServiceUnavailableException;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StoppedTimer;
import org.eclipse.ditto.internal.utils.tracing.TraceUtils;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;
import org.slf4j.Logger;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.util.ByteString;

/**
 * Custom Akka HTTP directive which handles a request timeout.
 */
public final class RequestTimeoutHandlingDirective {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(RequestTimeoutHandlingDirective.class);

    private static final Duration SEARCH_WARN_TIMEOUT_MS = Duration.ofMillis(5_000);
    private static final Duration HTTP_WARN_TIMEOUT_MS = Duration.ofMillis(1_000);

    private final HttpConfig httpConfig;

    private RequestTimeoutHandlingDirective(final HttpConfig httpConfig) {
        this.httpConfig = checkNotNull(httpConfig, "HTTP config");
    }

    /**
     * Returns an instance of {@code RequestTimeoutHandlingDirective}.
     *
     * @param httpConfig the configuration settings of the Gateway service's HTTP behaviour.
     * @return the instance.
     * @throws NullPointerException if {@code httpConfig} is {@code null}.
     */
    public static RequestTimeoutHandlingDirective getInstance(final HttpConfig httpConfig) {
        return new RequestTimeoutHandlingDirective(httpConfig);
    }

    /**
     * Handles a request timeout.
     *
     * @param correlationId the correlation ID which will be added to the log.
     * @param inner the inner Route to wrap with the response headers.
     * @return the new Route wrapping {@code inner} with the response headers.
     */
    public Route handleRequestTimeout(final CharSequence correlationId, final Supplier<Route> inner) {
        return Directives.extractActorSystem(actorSystem -> extractRequestContext(requestContext -> {
                    final StartedTimer timer = TraceUtils.newHttpRoundTripTimer(requestContext.getRequest()).start();

                    final ThreadSafeDittoLogger logger = LOGGER.withCorrelationId(correlationId);
                    logger.debug("Started mutable timer <{}>.", timer);
                    final Supplier<Route> innerWithTimer = () -> Directives.mapResponse(response -> {
                        final int statusCode = response.status().intValue();
                        if (timer.isRunning()) {
                            final StoppedTimer stoppedTimer = timer.tag(TracingTags.STATUS_CODE, statusCode).stop();
                            logger.debug("Finished timer <{}> with status <{}>.", timer, statusCode);
                            checkDurationWarning(stoppedTimer, logger);
                        }
                        return response;
                    }, inner);

                    return Directives.withRequestTimeoutResponse(request ->
                            doHandleRequestTimeout(correlationId, requestContext, timer, logger), innerWithTimer);
                }
        ));
    }

    private static void checkDurationWarning(final StoppedTimer mutableTimer, final Logger logger) {
        final Duration duration = mutableTimer.getDuration();
        final String requestPath = mutableTimer.getTag(TracingTags.REQUEST_PATH);

        if (requestPath != null && requestPath.contains("/search/things")) {
            if (SEARCH_WARN_TIMEOUT_MS.minus(duration).isNegative()) {
                logger.warn("Encountered slow search which took over <{}> ms: <{}> ms!", SEARCH_WARN_TIMEOUT_MS.toMillis(),
                        duration.toMillis());
            }
        } else if (HTTP_WARN_TIMEOUT_MS.minus(duration).isNegative()) {
            logger.warn("Encountered slow HTTP request which took over <{}> ms: <{}> ms!",
                    HTTP_WARN_TIMEOUT_MS.toMillis(), duration.toMillis());
        }
    }

    private HttpResponse doHandleRequestTimeout(final CharSequence correlationId,
            final RequestContext requestContext,
            final StartedTimer timer,
            final Logger logger) {

        final DittoRuntimeException cre = GatewayServiceUnavailableException.newBuilder()
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();

        final HttpRequest request = requestContext.getRequest();

        /* We have to log and create a trace here because the RequestResultLoggingDirective won't be called by akka
           in case of a timeout */
        final int statusCode = cre.getHttpStatus().getCode();

        final String requestMethod = request.method().name();
        final String requestUri = request.getUri().toRelative().toString();
        logger.warn("Request <{} {}> timed out after <{}>!", requestMethod, requestUri,
                httpConfig.getRequestTimeout());
        logger.info("Status code of request <{} {}> was <{}>.", requestMethod, requestUri, statusCode);
        final String rawRequestUri = getRawRequestUri(request);
        logger.debug("Raw request URI was <{}>.", rawRequestUri);

        if (timer.isRunning()) {
            timer.tag(TracingTags.STATUS_CODE, statusCode)
                    .stop();
            logger.debug("Finished mutable timer <{}> after a request timeout with status <{}>", timer, statusCode);
        } else {
            logger.warn("Wanted to stop() timer which was already stopped indicating that a requestTimeout" +
                    " was detected where it should not have been");
        }

        /*
         * We have to add security response headers explicitly here because SecurityResponseHeadersDirective won't be
         * called by akka in case of a timeout.
         */
        return HttpResponse.create()
                .withStatus(statusCode)
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(cre.toJsonString()));
    }

}
