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
 package org.eclipse.ditto.base.model.exceptions;

 import java.net.URI;

 import javax.annotation.Nullable;
 import javax.annotation.concurrent.Immutable;
 import javax.annotation.concurrent.NotThreadSafe;

 import org.eclipse.ditto.base.model.common.HttpStatus;
 import org.eclipse.ditto.base.model.headers.DittoHeaders;
 import org.eclipse.ditto.base.model.json.JsonParsableException;
 import org.eclipse.ditto.json.JsonObject;

 /**
  * Thrown if a CloudEvent request with missing payload is being made.
  *
  * @since 1.5.0
  */
 @Immutable
 @JsonParsableException(errorCode = CloudEventMissingPayloadException.ERROR_CODE)
 public final class CloudEventMissingPayloadException extends DittoRuntimeException implements GeneralException {

     /**
      * Error code of this exception.
      */
     public static final String ERROR_CODE = ERROR_CODE_PREFIX + "cloudevent.payload.missing";

     private static final String DEFAULT_MESSAGE = "The Cloud Event's payload is missing.";
     private static final String DEFAULT_DESCRIPTION = "Ensure to provide payload in the Cloud Event.";

     private static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

     /**
      * Constructs a new {@code CloudEventMissingPayloadException} object.
      *
      * @param dittoHeaders the headers with which this Exception should be reported back to the user.
      * @param message the detail message for later retrieval with {@link #getMessage()}.
      * @param description a description with further information about the exception.
      * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
      * @param href a link to a resource which provides further information about the exception.
      * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
      */
     private CloudEventMissingPayloadException(final DittoHeaders dittoHeaders,
             @Nullable final String message,
             @Nullable final String description,
             @Nullable final Throwable cause,
             @Nullable final URI href) {

         super(ERROR_CODE, HTTP_STATUS, dittoHeaders, message, description, cause, href);
     }

     /**
      * A mutable builder for a {@code CloudEventMissingPayloadException} where the message contains detailed information
      * about the actual used data schema and the description information about data schemas are supported for the
      * requested resource.
      *
      * @return the new CloudEventMissingPayloadException.
      */
     public static DittoRuntimeExceptionBuilder<CloudEventMissingPayloadException> withDetailedInformationBuilder() {
         return new Builder().message(DEFAULT_MESSAGE).description(DEFAULT_DESCRIPTION);
     }

     /**
      * Constructs a new {@code CloudEventMissingPayloadException} object with the exception message extracted from the
      * given JSON object.
      *
      * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
      * @param dittoHeaders the headers of the command which resulted in this exception.
      * @return the new CloudEventMissingPayloadException.
      * @throws NullPointerException if any argument is {@code null}.
      * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
      * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
      * format.
      */
     public static CloudEventMissingPayloadException fromJson(final JsonObject jsonObject,
             final DittoHeaders dittoHeaders) {

         return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
     }

     @Override
     public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
         return new Builder()
                 .message(getMessage())
                 .description(getDescription().orElse(null))
                 .cause(getCause())
                 .href(getHref().orElse(null))
                 .dittoHeaders(dittoHeaders)
                 .build();
     }

     /**
      * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.exceptions.CloudEventMissingPayloadException}.
      */
     @NotThreadSafe
     public static final class Builder extends DittoRuntimeExceptionBuilder<CloudEventMissingPayloadException> {

         private Builder() {
             message(DEFAULT_MESSAGE);
         }

         @Override
         protected CloudEventMissingPayloadException doBuild(final DittoHeaders dittoHeaders,
                 @Nullable final String message,
                 @Nullable final String description,
                 @Nullable final Throwable cause,
                 @Nullable final URI href) {

             return new CloudEventMissingPayloadException(dittoHeaders, message, description, cause, href);
         }

     }

 }
