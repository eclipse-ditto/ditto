/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.coap;

import static org.eclipse.californium.core.coap.CoAP.ResponseCode.BAD_GATEWAY;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.BAD_REQUEST;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.CHANGED;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.CONFLICT;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.CONTENT;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.CREATED;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.DELETED;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.FORBIDDEN;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.GATEWAY_TIMEOUT;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.INTERNAL_SERVER_ERROR;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.METHOD_NOT_ALLOWED;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.NOT_ACCEPTABLE;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.NOT_FOUND;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.NOT_IMPLEMENTED;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.PRECONDITION_FAILED;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.REQUEST_ENTITY_TOO_LARGE;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.SERVICE_UNAVAILABLE;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.UNAUTHORIZED;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.UNSUPPORTED_CONTENT_FORMAT;
import static org.eclipse.californium.core.coap.CoAP.ResponseCode.VALID;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_JSON;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.TEXT_PLAIN;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.UNDEFINED;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.Token;
import org.eclipse.californium.core.observe.ObserveNotificationOrderer;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.auth.ExtensiblePrincipal;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.ResponseEntity;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.sse.EventStreamUnmarshalling;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

final class DittoCoapResourceFacade extends CoapResource {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(DittoCoapResourceFacade.class);

    private final ActorSystem actorSystem;
    private final Flow<HttpRequest, HttpResponse, NotUsed> httpFlow;
    private final ObserveNotificationOrderer notificationOrderer;
    private final Map<Token, ActiveObserve> activeObserveSessions;

    DittoCoapResourceFacade(final String name, final ActorSystem actorSystem, final Route rootRoute) {

        super(name, true);
        this.actorSystem = actorSystem;
        httpFlow = rootRoute.flow(actorSystem);

        getAttributes().setTitle("Ditto CoAP RootResource");
        getAttributes().addContentType(APPLICATION_JSON);

        setObservable(true);
        setObserveType(CoAP.Type.CON); // TODO TJ which observeType to use? do I need 2 resources for 2 observeTypes?

        this.notificationOrderer = new ObserveNotificationOrderer();
        activeObserveSessions = new ConcurrentHashMap<>(); // TODO TJ concurrent or not?
    }

    /**
     * Gets an authenticated device's {@link AuthorizationContext} for a CoAP request.
     * TODO TJ use
     *
     * @param exchange The CoAP exchange with AuthorizationContext of the authenticated device.
     * @return The AuthorizationContext or empty optional if the request has not been authenticated.
     */
    private static Optional<AuthorizationContext> getAuthorizationContext(final CoapExchange exchange) {

        return Optional.ofNullable(exchange.advanced().getRequest().getSourceContext())
                .map(EndpointContext::getPeerIdentity)
                .filter(ExtensiblePrincipal.class::isInstance)
                .map(ExtensiblePrincipal.class::cast)
                .map(ExtensiblePrincipal::getExtendedInfo)
                .map(info ->
                        info.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), AuthorizationContext.class));
    }

    @Override
    public Executor getExecutor() {
        return super.getExecutor(); // TODO TJ configure an Akka dispatcher as executor?
    }

    @Override
    public Resource getChild(final String name) {
        // always return 'this' resource for all children in order to handle all requests with this resource
        return this;
    }

    /**
     * Performs actions to perform when shutting down this single root resource.
     */
    public void shutdown() {
        activeObserveSessions.values()
                .forEach(activeObserve -> activeObserve.getKillSwitch().shutdown());
    }

    @Override
    public void handleGET(final CoapExchange exchange) {
        if (exchange.getRequestOptions().hasObserve()) {
            handleCoapObserve(exchange);
        } else {
            handleCoapRequest(exchange, HttpMethods.GET);
        }
    }

    private void handleCoapObserve(final CoapExchange exchange) {
        final Token token = exchange.advanced().getRequest().getToken();
        if (exchange.advanced().getRequest().isObserve()) {
            if (activeObserveSessions.containsKey(token)) {
                // observe session is already active, no need to re-subscribe ..
                LOGGER.withCorrelationId(token.getAsString())
                        .info("Observe for token <{}> is still active, updating..", token);
                final ActiveObserve updatedObserve = activeObserveSessions.get(token).withCoapExchange(exchange);
                activeObserveSessions.put(token, updatedObserve);
            } else {
                handleCoapObserveRequest(exchange)
                        .thenAccept(uniqueKillSwitch -> {
                            if (null != uniqueKillSwitch) {
                                activeObserveSessions.put(token,
                                        new ActiveObserve(token, exchange, uniqueKillSwitch)
                                );
                            }
                        });
            }
            // TODO TJ if observe on message path, respond with no content instead of handling "normal":
            handleCoapRequest(exchange, HttpMethods.GET);
        } else if (exchange.advanced().getRequest().isObserveCancel()) {
            if (activeObserveSessions.containsKey(token)) {
                // cancel observe
                LOGGER.withCorrelationId(token.getAsString())
                        .info("Unobserving for token <{}>", token);
                activeObserveSessions.remove(token)
                        .getKillSwitch()
                        .shutdown();
            }
            exchange.respond(VALID); // TODO TJ find out what to send back to an observe cancel ..
        }
    }

    @Override
    public void handlePOST(final CoapExchange exchange) {
        handleCoapRequest(exchange, HttpMethods.POST);
    }

    @Override
    public void handlePUT(final CoapExchange exchange) {
        handleCoapRequest(exchange, HttpMethods.PUT);
    }

    @Override
    public void handleDELETE(final CoapExchange exchange) {
        handleCoapRequest(exchange, HttpMethods.DELETE);
    }

    @Override
    public void handlePATCH(final CoapExchange exchange) {
        handleCoapRequest(exchange, HttpMethods.PATCH);
    }

    @Override
    public void handleIPATCH(final CoapExchange exchange) {
        handleCoapRequest(exchange, HttpMethods.PATCH);
    }

    @Override
    public int getNotificationSequenceNumber() {
        return notificationOrderer.getCurrent();
    }

    private void handleCoapRequest(final CoapExchange exchange, final HttpMethod httpMethod) {
        final String coapToken = exchange.advanced().getRequest().getTokenString();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.withCorrelationId(coapToken)
                    .info("Handling CoAP <{}> request with MID <{}> and token <{}>: <{}>",
                            httpMethod.name(),
                            exchange.advanced().getRequest().getMID(),
                            exchange.advanced().getRequest().getTokenString(),
                            exchange.getRequestOptions().getUriString());
        }

        final Request request = exchange.advanced().getRequest();
        final int accept;
        if (request.getOptions().getAccept() == UNDEFINED) {
            accept = APPLICATION_JSON;
        } else {
            accept = request.getOptions().getAccept();
        }

        if (accept == APPLICATION_JSON) {
            Source.single(exchange)
                    .map(coapExchange -> translateCoapRequestToHttpRequest(coapExchange, httpMethod))
                    .via(httpFlow)
                    .mapAsync(1, httpResponse ->
                            translateHttpResponseToCoapResponse(actorSystem, httpResponse, accept))
                    .runWith(Sink.foreach(exchange::respond), actorSystem);
        } else {
            final String ct = MediaTypeRegistry.toString(accept);
            exchange.respond(NOT_ACCEPTABLE, "Type \"" + ct + "\" is not supported for this resource!", TEXT_PLAIN);
        }
    }

    private CompletionStage<UniqueKillSwitch> handleCoapObserveRequest(final CoapExchange exchange) {
        final Token token = exchange.advanced().getRequest().getToken();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.withCorrelationId(token.getAsString())
                    .info("Handling CoAP observe request <{}> with MID <{}> and  token <{}>: <{}>",
                            exchange.getRequestOptions().getObserve(),
                            exchange.advanced().getRequest().getMID(),
                            token.getAsString(),
                            exchange.getRequestOptions().getUriString());
        }

        return Source.single(exchange)
                .map(coapExchange -> HttpRequest.create()
                        .withMethod(HttpMethods.GET)
                        .withUri(coapExchange.getRequestOptions().getUriString())
                        .withHeaders(List.of(
                                HttpHeader.parse(DittoHeaderDefinition.ACCEPT.getKey(), "text/event-stream"),
                                HttpHeader.parse(DittoHeaderDefinition.CORRELATION_ID.getKey(), token.getAsString()),
                                HttpHeader.parse("ditto-coap-proxy", "true")
                        ))
                        .withEntity(coapExchange.getRequestPayload()))
                .via(httpFlow)
                .mapAsync(1, httpResponse -> {
                    final ResponseEntity entity = httpResponse.entity();
                    if (entity.getContentType().mediaType().equals(MediaTypes.TEXT_EVENT_STREAM)) {
                        // SSE opened
                        return EventStreamUnmarshalling.fromEventsStream(actorSystem)
                                .unmarshal(entity, actorSystem)
                                .thenApply(source -> source
                                        .viaMat(KillSwitches.single(), Keep.right())
                                        .toMat(Sink.foreach(sse -> {
                                            notificationOrderer.getNextObserveNumber();
                                            Optional.ofNullable(activeObserveSessions.get(token))
                                                    .map(ActiveObserve::getCoapExchange)
                                                    .orElse(exchange)
                                                    .respond(CONTENT, sse.getData(), APPLICATION_JSON);
                                        }), Keep.left())
                                        .run(actorSystem)
                                );
                    } else {
                        return CompletableFuture.completedStage(null);
                    }
                })
                .runWith(Sink.head(), actorSystem);
    }

    private static CompletionStage<Response> translateHttpResponseToCoapResponse(
            final ActorSystem actorSystem,
            final HttpResponse httpResponse,
            final int accept) {

        final CoAP.ResponseCode responseCode = translateHttpStatusCodeToCoapResponseCode(httpResponse.status());
        final Response response = new Response(responseCode);
        response.getOptions().setContentFormat(accept);

        return httpResponse.entity().getDataBytes()
                .fold(ByteString.emptyByteString(), ByteString::concat)
                .map(ByteString::utf8String)
                .runWith(Sink.head(), actorSystem)
                .thenApply(str -> (Response) response.setPayload(str));
    }

    private static CoAP.ResponseCode translateHttpStatusCodeToCoapResponseCode(final StatusCode statusCode) {
        return switch (statusCode.intValue()) {
            case 200:
                yield CONTENT;
            case 201:
                yield CREATED;
            case 202:
                yield DELETED;
            case 204:
                yield CHANGED;
            case 304:
                yield VALID;
            case 400:
                yield BAD_REQUEST;
            case 401:
                yield UNAUTHORIZED;
            case 403:
                yield FORBIDDEN;
            case 404:
                yield NOT_FOUND;
            case 405:
                yield METHOD_NOT_ALLOWED;
            case 406:
                yield NOT_ACCEPTABLE;
            case 409:
                yield CONFLICT;
            case 412:
                yield PRECONDITION_FAILED;
            case 413:
                yield REQUEST_ENTITY_TOO_LARGE;
            case 415:
                yield UNSUPPORTED_CONTENT_FORMAT;
            case 500:
                yield INTERNAL_SERVER_ERROR;
            case 501:
                yield NOT_IMPLEMENTED;
            case 502:
                yield BAD_GATEWAY;
            case 503:
                yield SERVICE_UNAVAILABLE;
            case 504:
                yield GATEWAY_TIMEOUT;
            default:
                yield NOT_IMPLEMENTED;
        };
    }

    private static HttpRequest translateCoapRequestToHttpRequest(
            final CoapExchange coapExchange,
            final HttpMethod httpMethod) {

        final String coapToken = coapExchange.advanced().getRequest().getTokenString();
        return HttpRequest.create()
                .withMethod(httpMethod)
                .withUri(coapExchange.getRequestOptions().getUriString())
                .withHeaders(List.of(
                        HttpHeader.parse(DittoHeaderDefinition.CORRELATION_ID.getKey(), coapToken),
                        HttpHeader.parse("ditto-coap-proxy", "true")
                )) // TODO TJ map headers like If-Match, etc .. which CoAP supports
                .withEntity(coapExchange.getRequestPayload());
    }

    private static final class ActiveObserve {
        private final Token token;
        private final CoapExchange coapExchange;
        private final Instant lastObserveTimestamp;
        private final KillSwitch killSwitch;

        private ActiveObserve(final Token token, final CoapExchange coapExchange, final KillSwitch killSwitch) {
            this.token = token;
            this.coapExchange = coapExchange;
            lastObserveTimestamp = Instant.now();
            // TODO TJ cancel the observation after lastObserveTimestamp + "maxAge" seconds (+5?)
            this.killSwitch = killSwitch;
        }

        ActiveObserve withCoapExchange(final CoapExchange coapExchange) {
            return new ActiveObserve(token, coapExchange, killSwitch);
        }

        Token getToken() {
            return token;
        }

        CoapExchange getCoapExchange() {
            return coapExchange;
        }

        Instant getLastObserveTimestamp() {
            return lastObserveTimestamp;
        }

        KillSwitch getKillSwitch() {
            return killSwitch;
        }
    }

}
