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
 package org.eclipse.ditto.model.base.exceptions;

 import java.net.URI;
 import java.text.MessageFormat;
 import java.util.Set;

 import javax.annotation.Nullable;
 import javax.annotation.concurrent.Immutable;

 import org.eclipse.ditto.model.base.common.HttpStatusCode;
 import org.eclipse.ditto.model.base.headers.DittoHeaders;
 import org.eclipse.ditto.model.base.json.JsonParsableException;

 /**
  * Thrown if a request with an unsupported media-type is made.
  */
 @Immutable
 @JsonParsableException(errorCode = UnsupportedMediaTypeException.ERROR_CODE)
 public final class UnsupportedMediaTypeException extends DittoRuntimeException {

     /**
      * Error code of this exception.
      */
     public static final String ERROR_CODE = "mediatype.unsupported";

     private static final String MESSAGE_PATTERN = "The Media-Type <{0}> is not supported for this Resource. Allowed " +
             "Media-Types are: <{1}>";

     private static final HttpStatusCode STATUS_CODE = HttpStatusCode.UNSUPPORTED_MEDIA_TYPE;


     /**
      * Constructs a new {@code UnsupportedMediaTypeException} object.
      *
      * @param dittoHeaders the headers with which this Exception should be reported back to the user.
      * @param message the detail message for later retrieval with {@link #getMessage()}.
      * @param description a description with further information about the exception.
      * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
      * @param href a link to a resource which provides further information about the exception.
      * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
      */
     protected UnsupportedMediaTypeException(final DittoHeaders dittoHeaders,
             @Nullable final String message,
             @Nullable final String description,
             @Nullable final Throwable cause,
             @Nullable final URI href) {
         super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
     }

     /**
      * Constructs a new {@code UnsupportedMediaTypeException} object where the message contains detailed information
      * about the actual used media-type, and which media-types are supported for the requested resource.
      *
      * @param callersMediaType the unsupported media-type used in the call.
      * @param mediaTypeSupportedByCalledResource media-types which are supported.
      * @param dittoHeaders the dittoHeaders of the call.
      * @return the new UnsupportedMediaTypeException.
      */
     public static UnsupportedMediaTypeException withDetailedMessage(
             final String callersMediaType,
             final Set<String> mediaTypeSupportedByCalledResource,
             final DittoHeaders dittoHeaders) {

         final String message =
                 MessageFormat.format(MESSAGE_PATTERN, callersMediaType, mediaTypeSupportedByCalledResource);

         return new UnsupportedMediaTypeException(dittoHeaders, message, null, null, null);
     }

 }