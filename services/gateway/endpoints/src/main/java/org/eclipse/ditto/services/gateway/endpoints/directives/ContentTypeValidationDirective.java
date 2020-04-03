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
 import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
 import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;

 import java.text.MessageFormat;
 import java.util.List;
 import java.util.function.Function;
 import java.util.function.Supplier;

 import javax.annotation.Nullable;

 import org.eclipse.ditto.model.base.headers.DittoHeaders;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import akka.http.javadsl.model.ContentType;
 import akka.http.javadsl.model.ContentTypes;
 import akka.http.javadsl.model.StatusCodes;
 import akka.http.javadsl.server.RequestContext;
 import akka.http.javadsl.server.Route;
 import akka.stream.javadsl.Source;
 import akka.util.ByteString;

 import static akka.http.javadsl.server.Directives.extractDataBytes;

 public final class ContentTypeValidationDirective {

     /**
      * Static list containing:
      * <ul>
      *     <li>application/json</li>
      * </ul>
      */
     public static List<ContentType> ONLY_JSON = List.of(
             ContentTypes.APPLICATION_JSON //,
     );

     private static final Logger LOGGER = LoggerFactory.getLogger(ContentTypeValidationDirective.class);

     /**
      *
      */
     public static Route ensureValidContentType(final List<ContentType> allowedContentTypes,
             RequestContext ctx, DittoHeaders dittoHeaders, Supplier<Route> inner) {
         return enhanceLogWithCorrelationId(dittoHeaders.getCorrelationId(), () -> {
             final ContentType contentType =
                     ctx.getRequest().entity().getContentType();

             if (contentType != null && allowedContentTypes.contains(contentType)) {
                 return inner.get();
             } else {
                 final String msgPatten = "The Content-Type <{0}> is not supported for this endpoint. Allowed " +
                         "Content-Types are: <{1}>";
                 final String responseMessage =
                         MessageFormat.format(msgPatten, contentType, allowedContentTypes.toString());
                 LOGGER.info(responseMessage);
                 return complete(StatusCodes.UNSUPPORTED_MEDIA_TYPE, responseMessage);
             }
         });
     }

     public static Route ensureContentTypeAndExtractDataBytes(final List<ContentType> allowedContentTypes,
             RequestContext ctx,
             DittoHeaders dittoHeaders,
             Function<Source<ByteString, Object>, Route> inner) {

         return ensureValidContentType(
                 allowedContentTypes,
                 ctx,
                 dittoHeaders,
                 () -> extractDataBytes(inner));
     }

 }