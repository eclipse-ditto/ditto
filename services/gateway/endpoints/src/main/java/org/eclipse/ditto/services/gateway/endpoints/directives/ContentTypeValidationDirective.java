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
 package org.eclipse.ditto.services.gateway.endpoints.directives;

 import static akka.http.javadsl.server.Directives.complete;
 import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

 import java.text.MessageFormat;
 import java.util.Optional;
 import java.util.Set;
 import java.util.function.Supplier;
 import java.util.stream.StreamSupport;

 import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
 import org.eclipse.ditto.model.base.headers.DittoHeaders;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import akka.http.javadsl.model.HttpHeader;
 import akka.http.javadsl.model.HttpRequest;
 import akka.http.javadsl.model.StatusCodes;
 import akka.http.javadsl.server.RequestContext;
 import akka.http.javadsl.server.Route;

 /**
  * Used to validate the Content-Type of a request.
  */
 public final class ContentTypeValidationDirective {

     private static final Logger LOGGER = LoggerFactory.getLogger(ContentTypeValidationDirective.class);

     /**
      * verifies that the content-type of the entity is one of the allowed media-types,
      * otherwise the request will be completed with 415 ("Unsupported Media Type").
      *
      * @param supportedMediaTypes the media-type which are allowed for the wrapped route.
      * @param ctx the context of the request.
      * @param dittoHeaders the ditto-headers of a request.
      * @param inner route to wrap.
      * @return the wrapped route.
      */
     public static Route ensureValidContentType(final Set<String> supportedMediaTypes, final RequestContext ctx,
             final DittoHeaders dittoHeaders,
             final Supplier<Route> inner) {
         return enhanceLogWithCorrelationId(dittoHeaders.getCorrelationId(), () -> {
             final String requestsMediaType = extractMediaType(ctx.getRequest());

             if(supportedMediaTypes.contains(requestsMediaType)){
                 return inner.get();
             } else {
                 LOGGER.info("Request rejected: unsupported media-type: <{}>  request: <{}>",
                         requestsMediaType, requestToLogString(ctx.getRequest()));
                 return completeWithMediaTypeNotSupported(requestsMediaType, supportedMediaTypes);
             }
         });
     }

     protected static Route completeWithMediaTypeNotSupported(final String mediaType,
             final Set<String> supportedByResource){

             final String msgPatten = "The Media-Type <{0}> is not supported for this endpoint. Allowed " +
                     "Media-Types are: <{1}>";
             final String responseMessage =
                     MessageFormat.format(msgPatten, mediaType, supportedByResource);

             return complete(StatusCodes.UNSUPPORTED_MEDIA_TYPE, responseMessage);
     }

     protected static String requestToLogString(final HttpRequest request){
         final String msgPatten = "{0} {1} {2}";
         return MessageFormat.format(msgPatten,
                 request.getUri().getHost().address(),
                 request.method().value(),
                 request.getUri().getPathString());
     }


     /**
      * Uses either the raw-header or the content-type parsed by akka-http.
      * The parsed content-type is never null, because akka-http sets a default.
      * In the case of akka's default value, the raw version is preferred.
      * The raw content-type header is not available, in case akka successfully parsed the content-type.
      * For akka-defaults:
      * {@link akka.http.impl.engine.parsing.HttpRequestParser#createLogic} <code>parseEntity</code>
      * and {@link akka.http.scaladsl.model.HttpEntity$}.
      * @see
      * <a href="https://doc.akka.io/docs/akka-http/current/common/http-model.html#http-headers">Akkas Header model</a>
      *
      * @param request the request where the media type will be extracted from.
      * @return the extracted media-type.
      */
     protected static String extractMediaType(HttpRequest request){
         final Optional<HttpHeader> rawContentType = StreamSupport.stream(request.getHeaders().spliterator(), false)
                 .filter(header -> header.name().equals(DittoHeaderDefinition.CONTENT_TYPE.getKey()))
                 .findFirst();

         return rawContentType.map(HttpHeader::value)
                 .orElse(request.entity().getContentType().mediaType().toString());
     }

 }