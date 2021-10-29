/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.cloudevents;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CloudEventsConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.exceptions.CloudEventMissingPayloadException;
import org.eclipse.ditto.base.model.exceptions.CloudEventNotParsableException;
import org.eclipse.ditto.base.model.exceptions.CloudEventUnsupportedDataSchemaException;
import org.eclipse.ditto.base.model.exceptions.UnsupportedMediaTypeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.gateway.service.endpoints.actors.AbstractHttpRequestActor;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;

import akka.actor.Status;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.message.MessageReader;
import io.cloudevents.http.HttpMessageFactory;
import io.cloudevents.rw.CloudEventRWException;

/**
 * Builder for creating Akka HTTP route for {@code /cloudevents}.
 */
public final class CloudEventsRoute extends AbstractRoute {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(CloudEventsRoute.class);

    /**
     * Public endpoint of cloud events.
     */
    public static final String PATH_CLOUDEVENTS = "cloudevents";

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final String DATA_SCHEMA_SCHEME = "ditto";

    private final CloudEventsConfig cloudEventsConfig;

    /**
     * Constructs a {@code CloudEventsRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @param cloudEventsConfig the configuration settings for cloud events.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public CloudEventsRoute(final RouteBaseProperties routeBaseProperties,
            final CloudEventsConfig cloudEventsConfig) {

        super(routeBaseProperties);
        this.cloudEventsConfig = cloudEventsConfig;
    }

    /**
     * Builds the {@code /cloudevents} route.
     *
     * @return the {@code /cloudevents} route.
     */
    public Route buildCloudEventsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return path(PATH_CLOUDEVENTS, () -> // /cloudevents
                post(() -> // POST
                        acceptCloudEvent(ctx, dittoHeaders)
                )
        );
    }

    private Route acceptCloudEvent(final RequestContext ctx, final DittoHeaders dittoHeaders) {

        return extractDataBytes(payloadSource -> {

            final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();

            runWithSupervisionStrategy(payloadSource
                    // collect the binary payload
                    .fold(ByteString.emptyByteString(), ByteString::concat)
                    // map the payload to a cloud event
                    .map(payload -> toCloudEvent(ctx, dittoHeaders, payload))
                    // validate the cloud event
                    .map(cloudEvent -> validateCloudEvent(cloudEvent, ctx, dittoHeaders))
                    // process the event
                    .map(cloudEvent -> {
                        try {
                            // DON'T replace this try-catch by .recover: The supervising strategy is called before recovery!
                            final Optional<Signal<?>> optionalSignal =
                                    jsonToDittoSignal(cloudEvent.getData(), dittoHeaders);
                            if (optionalSignal.isEmpty()) {
                                return new Status.Failure(CloudEventMissingPayloadException
                                        .withDetailedInformationBuilder()
                                        .dittoHeaders(dittoHeaders)
                                        .build());
                            }

                            final Signal<?> signal = optionalSignal.get();
                            final JsonSchemaVersion schemaVersion = signal.getImplementedSchemaVersion();
                            return signal.implementsSchemaVersion(schemaVersion) ? signal
                                    : CommandNotSupportedException.newBuilder(schemaVersion.toInt())
                                    .dittoHeaders(dittoHeaders)
                                    .build();
                        } catch (final Exception e) {
                            return new Status.Failure(e);
                        }
                    })
                    .to(Sink.actorRef(createHttpPerRequestActor(ctx, httpResponseFuture),
                            AbstractHttpRequestActor.COMPLETE_MESSAGE))
            );

            return completeWithFuture(httpResponseFuture);
        });
    }

    /**
     * Convert the request and payload to a cloud event.
     *
     * @param ctx The request context.
     * @param dittoHeaders the DittoHeaders to apply to the parsed Ditto Signal.
     * @param payload The binary payload, this may contain the cloud event payload, or the fully encoded cloud
     * event structure.
     * @return The cloud event
     */
    private CloudEvent toCloudEvent(final RequestContext ctx, final DittoHeaders dittoHeaders,
            final ByteString payload) {

        if (LOGGER.isTraceEnabled()) {
            final StringBuilder headers = new StringBuilder("CloudEvent raw HTTP Headers:");
            ctx.getRequest().getHeaders()
                    .forEach(header -> headers
                            .append("\n\t")
                            .append(header.name())
                            .append(" = ")
                            .append(header.value()));

            LOGGER.withCorrelationId(dittoHeaders)
                    .trace(headers.toString());

            LOGGER.withCorrelationId(dittoHeaders)
                    .trace("CloudEvent Ditto Headers: {}", dittoHeaders);
        }

        try {
            // create a reader for the message
            final MessageReader reader = HttpMessageFactory.createReader(acceptor -> {

                // NOTE: this acceptor may be run multiple times by the message reader

                // record if we saw the content type header
                final AtomicBoolean sawContentType = new AtomicBoolean();
                // consume the HTTP request headers
                ctx.getRequest().getHeaders().forEach(header -> {
                    if (header.lowercaseName().equals(DittoHeaderDefinition.CONTENT_TYPE.getKey())) {
                        sawContentType.set(true);
                    }
                    acceptor.accept(header.name(), header.value());
                });

                if (!sawContentType.get()) {
                    // we didn't see the content type in the header, so extract it from akka's request
                    acceptor.accept(DittoHeaderDefinition.CONTENT_TYPE.getKey(),
                            ctx.getRequest().entity().getContentType().mediaType().toString());
                }
            }, payload.toArray());
            return reader.toEvent();
        } catch (final CloudEventRWException | IllegalStateException e) {
            throw CloudEventNotParsableException.withDetailedInformationBuilder(e.getMessage())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private CloudEvent validateCloudEvent(final CloudEvent cloudEvent, final RequestContext ctx,
            final DittoHeaders dittoHeaders) {

        if (cloudEvent.getData() == null) {
            throw CloudEventMissingPayloadException
                    .withDetailedInformationBuilder()
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        LOGGER.withCorrelationId(dittoHeaders)
                .debug("CloudEvent: {}", cloudEvent);

        ensureDataContentType(cloudEvent.getDataContentType(), ctx, dittoHeaders);
        ensureDataSchema(cloudEvent.getDataSchema(), ctx, dittoHeaders);

        return cloudEvent;
    }

    private Optional<Signal<?>> jsonToDittoSignal(@Nullable final CloudEventData data,
            final DittoHeaders dittoHeaders) {
        if (data == null) {
            return Optional.empty();
        }
        final byte[] payload = data.toBytes();
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }

        final JsonObject jsonObject = JsonObject.of(payload);
        LOGGER.withCorrelationId(dittoHeaders)
                .debug("CloudEvent payload JSON: {}", jsonObject);

        final DittoHeaders adjustedHeaders = dittoHeaders.toBuilder()
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED))
                .build();

        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
        final Signal<?> signal = PROTOCOL_ADAPTER.fromAdaptable(jsonifiableAdaptable);
        final Signal<?> signalWithAdjustedHeaders = signal.setDittoHeaders(
                signal.getDittoHeaders().toBuilder().putHeaders(adjustedHeaders).build());
        return Optional.of(signalWithAdjustedHeaders);
    }

    private void ensureDataContentType(@Nullable final String dataContentType,
            final RequestContext ctx,
            final DittoHeaders dittoHeaders) {

        if (!isCorrectDataType(dataContentType)) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.withCorrelationId(dittoHeaders)
                        .info("CloudEvent request rejected: unsupported data-content-type: <{}>  request: <{}>",
                                dataContentType, requestToLogString(ctx.getRequest()));
            }
            throw UnsupportedMediaTypeException
                    .withDetailedInformationBuilder(dataContentType != null ? dataContentType : "none",
                            cloudEventsConfig.getDataTypes())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Test if the data type is acceptable.
     * <p>
     * A missing, empty or malformed data type is not acceptable.
     *
     * @param dataContentType The content type to check.
     * @return {@code true} if the content type is acceptable, {@code false} otherwise.
     */
    private boolean isCorrectDataType(@Nullable final String dataContentType) {
        if (dataContentType == null) {
            // no content type
            return false;
        }

        final ContentType type = ContentTypes.parse(dataContentType);
        if (type == null) {
            // failed to parse content type
            return false;
        }

        return this.cloudEventsConfig.getDataTypes().contains(type.mediaType().toString());
    }

    /**
     * Ensure that the data schema starts with {@code ditto:}.
     *
     * @param dataSchema The schema to verify
     * @param ctx The request context.
     * @param dittoHeaders The ditto headers.
     */
    private void ensureDataSchema(@Nullable final URI dataSchema,
            final RequestContext ctx,
            final DittoHeaders dittoHeaders) {

        if (dataSchema == null && cloudEventsConfig.isEmptySchemaAllowed()) {
            // early return, no schema, but no requirement to have one
            return;
        }

        if (dataSchema == null || !dataSchema.getScheme().equals(DATA_SCHEMA_SCHEME)) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.withCorrelationId(dittoHeaders)
                        .info("CloudEvent request rejected: unsupported data-schema: <{}>  request: <{}>",
                                dataSchema, requestToLogString(ctx.getRequest()));
            }
            throw CloudEventUnsupportedDataSchemaException
                    .withDetailedInformationBuilder(dataSchema != null ? dataSchema.toString() : "none")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static String requestToLogString(final HttpRequest request) {
        return MessageFormat.format("{0} {1} {2}",
                request.getUri().getHost().address(),
                request.method().value(),
                request.getUri().getPathString());
    }

}
