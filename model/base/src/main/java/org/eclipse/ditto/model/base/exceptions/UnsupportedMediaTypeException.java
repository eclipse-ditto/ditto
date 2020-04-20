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
 import javax.annotation.concurrent.NotThreadSafe;

 import org.eclipse.ditto.json.JsonObject;
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

     private static final String DEFAULT_MESSAGE = "The Media-Type is not supported.";
     private static final String MESSAGE_PATTERN = "The Media-Type <{0}> is not supported for this Resource.";
     private static final String DESCRIPTION_ALLOWED_TYPES_PATTERN = "Allowed Media-Types are: <{0}>.";

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
      * A mutable builder for a {@code UnsupportedMediaTypeException} where the message contains detailed information
      * about the actual used media-type and the description information about media-types are supported for the
      * requested resource.
      *
      * @param callersMediaType the unsupported media-type used in the call.
      * @param mediaTypeSupportedByCalledResource media-types which are supported.
      * @return the new UnsupportedMediaTypeException.
      */
     public static DittoRuntimeExceptionBuilder<UnsupportedMediaTypeException> withDetailedInformationBuilder(
             final String callersMediaType,
             final Set<String> mediaTypeSupportedByCalledResource) {

         final String msgPattern = MessageFormat.format(MESSAGE_PATTERN, callersMediaType);
         final String descriptionPattern = MessageFormat.format(DESCRIPTION_ALLOWED_TYPES_PATTERN,
                 mediaTypeSupportedByCalledResource);

         return new Builder().message(msgPattern).description(descriptionPattern);
     }

     /**
      * Constructs a new {@code UnsupportedMediaTypeException} object with the exception message extracted from the
      * given JSON object.
      *
      * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
      * @param dittoHeaders the headers of the command which resulted in this exception.
      * @return the new UnsupportedMediaTypeException.
      * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
      * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
      */
     public static UnsupportedMediaTypeException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
         return new UnsupportedMediaTypeException.Builder()
                 .dittoHeaders(dittoHeaders)
                 .message(readMessage(jsonObject))
                 .description(readDescription(jsonObject).orElse(null))
                 .href(readHRef(jsonObject).orElse(null))
                 .build();
     }

     /**
      * A mutable builder with a fluent API for a {@link org.eclipse.ditto.model.base.exceptions.UnsupportedMediaTypeException}.
      */
     @NotThreadSafe
     public static final class Builder extends DittoRuntimeExceptionBuilder<UnsupportedMediaTypeException> {

         private Builder() {
             message(DEFAULT_MESSAGE);
         }

         @Override
         protected UnsupportedMediaTypeException doBuild(final DittoHeaders dittoHeaders,
                 @Nullable final String message,
                 @Nullable final String description,
                 @Nullable final Throwable cause,
                 @Nullable final URI href) {

             return new UnsupportedMediaTypeException(dittoHeaders, message, description, cause, href);
         }
     }

 }