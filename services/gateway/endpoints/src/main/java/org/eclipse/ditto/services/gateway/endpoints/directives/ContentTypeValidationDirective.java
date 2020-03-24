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

 import java.util.function.Supplier;

 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import akka.http.javadsl.model.ContentType;
 import akka.http.javadsl.model.ContentTypes;
 import akka.http.javadsl.model.StatusCodes;
 import akka.http.javadsl.server.RequestContext;
 import akka.http.javadsl.server.Route;

 public final class ContentTypeValidationDirective {

     private static final Logger LOGGER = LoggerFactory.getLogger(ContentTypeValidationDirective.class);

     public static Route ensureValidContentType(final String correlationId, RequestContext ctx, Supplier<Route> inner) {
         return enhanceLogWithCorrelationId(correlationId, () -> {

             final ContentType contentType = ctx.getRequest().entity().getContentType();
             final String unmatchedPath = ctx.getUnmatchedPath();

             LOGGER.info("verifyContentType hit with content-type: {}, unmatched-path: {}",
                     contentType,
                     unmatchedPath);
             if (isContentTypeValidForThatPath(contentType, unmatchedPath)) {
                 return inner.get();
             } else {
                 return complete(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
             }
         });
     }

     static boolean isContentTypeValidForThatPath(ContentType contentType, String unmatchedPath) {

         if (contentType == null || contentType.equals(ContentTypes.NO_CONTENT_TYPE)) {
             return true;
         } else if (contentType.equals(ContentTypes.APPLICATION_JSON)) {
             return true;
         } else if (unmatchedPath.contains("/inbox/messages")) {
             return true;
         } else {
             return false;
         }
     }
 }