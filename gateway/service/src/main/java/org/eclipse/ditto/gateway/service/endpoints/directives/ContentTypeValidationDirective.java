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
 package org.eclipse.ditto.gateway.service.endpoints.directives;


 import java.text.MessageFormat;
 import java.util.Set;
 import java.util.function.Predicate;
 import java.util.function.Supplier;
 import java.util.stream.StreamSupport;

 import org.eclipse.ditto.base.model.exceptions.UnsupportedMediaTypeException;
 import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
 import org.eclipse.ditto.base.model.headers.DittoHeaders;
 import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
 import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

 import akka.http.javadsl.model.ContentType;
 import akka.http.javadsl.model.HttpHeader;
 import akka.http.javadsl.model.HttpRequest;
 import akka.http.javadsl.model.MediaType;
 import akka.http.javadsl.model.MediaTypes;
 import akka.http.javadsl.model.RequestEntity;
 import akka.http.javadsl.server.RequestContext;
 import akka.http.javadsl.server.Route;

 /**
  * Used to validate the content-type of a http request.
  */
 public final class ContentTypeValidationDirective {

     private static final ThreadSafeDittoLogger LOGGER =
             DittoLoggerFactory.getThreadSafeLogger(ContentTypeValidationDirective.class);

     private static final String MERGE_PATCH_JSON_MEDIA_TYPE = MediaTypes.APPLICATION_MERGE_PATCH_JSON.toString();

     private ContentTypeValidationDirective() {
         throw new AssertionError();
     }

     /**
      * Verifies that the content-type of the entity is one of the allowed media-types,
      * otherwise the request will be completed with status code 415 ("Unsupported Media Type").
      *
      * @param supportedMediaTypes the media-type which are allowed for the wrapped route.
      * @param ctx the context of the request.
      * @param dittoHeaders the ditto-headers of a request.
      * @param inner route to wrap.
      * @return the wrapped route.
      */
     public static Route ensureValidContentType(final Set<String> supportedMediaTypes,
             final RequestContext ctx,
             final DittoHeaders dittoHeaders,
             final Supplier<Route> inner) {

         final String requestsMediaType = extractMediaType(ctx.getRequest());
         if (supportedMediaTypes.contains(requestsMediaType)) {
             return inner.get();
         } else {
             if (LOGGER.isInfoEnabled()) {
                 LOGGER.withCorrelationId(dittoHeaders)
                        .info("Request rejected: unsupported media-type: <{}> request: <{}>", requestsMediaType,
                                requestToLogString(ctx.getRequest()));
             }
             throw UnsupportedMediaTypeException
                     .withDetailedInformationBuilder(requestsMediaType, supportedMediaTypes)
                     .dittoHeaders(dittoHeaders)
                     .build();
         }
     }

     /**
      * Verifies that the content-type of the entity is application/merge-patch+json,
      * otherwise the request will be completed with status code 415 ("Unsupported Media Type").
      *
      * @param ctx the context of the request.
      * @param dittoHeaders the ditto-headers of a request.
      * @param inner route to wrap.
      * @return the wrapped route.
      */
     public static Route ensureMergePatchJsonContentType(final RequestContext ctx, final DittoHeaders dittoHeaders,
             final Supplier<Route> inner) {

         final String requestsMediaType = extractMediaType(ctx.getRequest());
         if (MERGE_PATCH_JSON_MEDIA_TYPE.equals(requestsMediaType)) {
             return inner.get();
         } else {
             if (LOGGER.isInfoEnabled()) {
                 LOGGER.withCorrelationId(dittoHeaders)
                         .info("Request rejected: unsupported media-type: <{}> request: <{}>", requestsMediaType,
                                 requestToLogString(ctx.getRequest()));
             }
             throw UnsupportedMediaTypeException
                     .builderForMergePatchJsonMediaType(requestsMediaType, MERGE_PATCH_JSON_MEDIA_TYPE)
                     .dittoHeaders(dittoHeaders)
                     .build();
         }
     }

     /**
      * Uses either the raw-header or the content-type parsed by akka-http.
      * The parsed content-type is never null, because akka-http sets a default.
      * In the case of akka's default value, the raw version is preferred.
      * The raw content-type header is not available, in case akka successfully parsed the content-type.
      * For akka-defaults:
      * {@link akka.http.impl.engine.parsing.HttpRequestParser#createLogic} {@code parseEntity}
      * and {@link akka.http.scaladsl.model.HttpEntity$}.
      *
      * @param request the request where the media type will be extracted from.
      * @return the extracted media-type.
      * @see <a href="https://doc.akka.io/docs/akka-http/current/common/http-model.html#http-headers">Akkas Header model</a>
      */
     private static String extractMediaType(final HttpRequest request) {
         final Iterable<HttpHeader> requestHeaders = request.getHeaders();
         final Predicate<HttpHeader> isContentTypeHeader = header -> {
             final String headerName = header.name();
             return headerName.equalsIgnoreCase(DittoHeaderDefinition.CONTENT_TYPE.getKey());
         };
         return StreamSupport.stream(requestHeaders.spliterator(), false)
                 .filter(isContentTypeHeader)
                 .findFirst()
                 .map(HttpHeader::value)
                 .orElseGet(() -> {
                     final RequestEntity requestEntity = request.entity();
                     final ContentType contentType = requestEntity.getContentType();
                     final MediaType mediaType = contentType.mediaType();
                     return mediaType.toString();
                 });
     }

     private static String requestToLogString(final HttpRequest request) {
         return MessageFormat.format("{0} {1} {2}",
                 request.getUri().getHost().address(),
                 request.method().value(),
                 request.getUri().getPathString());
     }

 }
