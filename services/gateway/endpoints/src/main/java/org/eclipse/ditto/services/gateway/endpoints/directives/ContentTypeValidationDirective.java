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

 public final class ContentTypeValidationDirective {

     public static final ContentTypeValidationDirective CT_ONLY_APPLICATION_JSON =
             new ContentTypeValidationDirective(List.of(ContentTypes.APPLICATION_JSON), false);

     private static final Logger LOGGER = LoggerFactory.getLogger(ContentTypeValidationDirective.class);
     private final List<ContentType> allowedContentTypes;
     private final boolean allowNoneContentType;

     private ContentTypeValidationDirective(final List<ContentType> allowedContentTypes, boolean allowNoneContentType) {

         this.allowedContentTypes = checkNotNull(allowedContentTypes);
         this.allowNoneContentType = allowNoneContentType;
     }

     public Route ensureValidContentType(RequestContext ctx, DittoHeaders dittoHeaders, Supplier<Route> inner) {
         return enhanceLogWithCorrelationId(dittoHeaders.getCorrelationId(), () -> {
             final ContentType contentType =
                     ctx.getRequest().entity().getContentType();

             LOGGER.info("verifyContentType hit with content-type: {}, unmatched-path: {}", contentType,
                     ctx.getRequest().getUri().getPathString());
             if (isContentTypeValid(this.allowedContentTypes, this.allowNoneContentType, contentType)) {
                 return inner.get();
             } else {
                 final String msgPatten = "The Content-Type <{0}> is not supported for this endpoint. Allowed " +
                         "Content-Types are: <{1}>";
                 return complete(StatusCodes.UNSUPPORTED_MEDIA_TYPE,
                         MessageFormat.format(msgPatten, contentType, allowedContentTypes.toString()));
             }
         });
     }

     static boolean isContentTypeValid(List<ContentType> allowedContentTypes,
             boolean allowNoneContentType, ContentType contentTypeToCheck) {

         if (contentTypeToCheck != null && allowedContentTypes.contains(contentTypeToCheck)) {
             return true;
         }
         if (allowNoneContentType) {
             if (contentTypeToCheck == null || contentTypeToCheck.equals(ContentTypes.NO_CONTENT_TYPE)) {
                 return true;
             }
         }
         return false;
     }
 }