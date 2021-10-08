 /*
  * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.connectivity.service.messaging.httppush;

 import java.util.Objects;
 import java.util.Optional;
 import java.util.function.Consumer;

 import javax.annotation.Nullable;

 import org.eclipse.ditto.base.model.common.HttpStatus;
 import org.eclipse.ditto.base.model.entity.id.EntityId;
 import org.eclipse.ditto.base.model.entity.id.WithEntityId;
 import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
 import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
 import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
 import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
 import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
 import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
 import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
 import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
 import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
 import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
 import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
 import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
 import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
 import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
 import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
 import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;

 public final class HttpLiveResponseHandling {

     private static final String LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE =
             "Live response of type <%s> is not of expected type <%s>.";

     private HttpLiveResponseHandling() {
         throw new AssertionError("nope");
     }

     public static boolean isLiveCommand(@Nullable final WithDittoHeaders signal) {
         return getLiveCommandWithEntityId(signal).isPresent();
     }

     public static boolean isLiveCommandResponse(@Nullable final WithDittoHeaders signal) {
         final boolean result;

         if (signal instanceof MessageCommandResponse) {
             result = true;
         } else {
             result = signal instanceof ThingCommandResponse && isChannelLive(signal);
         }
         return result;
     }

     public static boolean isChannelLive(final WithDittoHeaders withDittoHeaders) {
         return withDittoHeaders.getDittoHeaders().getChannel().map("live"::equals).isPresent();
     }

     public static Optional<SignalWithEntityId<?>> getLiveCommandWithEntityId(
             @Nullable final WithDittoHeaders potentialLiveCommand) {

         final SignalWithEntityId<?> result;
         if (potentialLiveCommand instanceof MessageCommand) {
             result = (SignalWithEntityId<?>) potentialLiveCommand;
         } else if (potentialLiveCommand instanceof ThingCommand && isChannelLive(potentialLiveCommand)) {
             result = (SignalWithEntityId<?>) potentialLiveCommand;
         } else {
             result = null;
         }
         return Optional.ofNullable(result);
     }

     public static void validateLiveResponse(
             final CommandResponse<?> commandResponse,
             final SignalWithEntityId<?> sentCommand,
             final ConnectionLogger connectionLogger) {

         Consumer<String> handleInvalidResponse = message -> connectionFailureLogAndThrow(message,
                 commandResponse,
                 connectionLogger);

         final EntityId messageThingId = sentCommand.getEntityId();
         if (!(commandResponse instanceof WithEntityId)) {
             final var message = String.format(
                     "Live response does not target the correct thing. Expected thing ID <%s>, but no ID found",
                     messageThingId);
             handleInvalidResponse.accept(message);
             return;
         }
         final var responseThingId = ((WithEntityId) commandResponse).getEntityId();

         if (!responseThingId.equals(messageThingId)) {
             final var message = String.format(
                     "Live response does not target the correct thing. Expected thing ID <%s>, but was <%s>.",
                     messageThingId, responseThingId);
             handleInvalidResponse.accept(message);
         }

         final var messageCorrelationId = sentCommand.getDittoHeaders().getCorrelationId().orElse(null);
         final var responseCorrelationId = commandResponse.getDittoHeaders().getCorrelationId().orElse(null);
         if (!Objects.equals(messageCorrelationId, responseCorrelationId)) {
             final var message = String.format(
                     "Correlation ID of response <%s> does not match correlation ID of message command <%s>. ",
                     responseCorrelationId, messageCorrelationId
             );
             handleInvalidResponse.accept(message);
         }

         // TODO baj2lol: remove spaghetti
         if (sentCommand instanceof MessageCommand) {
             switch (sentCommand.getType()) {
                 case SendClaimMessage.TYPE:
                     if (!SendClaimMessageResponse.TYPE.equalsIgnoreCase(commandResponse.getType())) {
                         final var message =
                                 String.format(LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE, commandResponse.getType(),
                                         SendClaimMessageResponse.TYPE);
                         handleInvalidResponse.accept(message);
                     }
                     break;
                 case SendThingMessage.TYPE:
                     if (!SendThingMessageResponse.TYPE.equalsIgnoreCase(commandResponse.getType())) {
                         final var message =
                                 String.format(LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE, commandResponse.getType(),
                                         SendThingMessageResponse.TYPE);
                         handleInvalidResponse.accept(message);
                     }
                     break;
                 case SendFeatureMessage.TYPE:
                     if (!SendFeatureMessageResponse.TYPE.equalsIgnoreCase(commandResponse.getType())) {
                         final var message =
                                 String.format(LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE, commandResponse.getType(),
                                         SendFeatureMessageResponse.TYPE);
                         handleInvalidResponse.accept(message);
                         return;
                     }
                     final var messageFeatureId = ((SendFeatureMessage<?>) sentCommand).getFeatureId();
                     final var responseFeatureId = ((SendFeatureMessageResponse<?>) commandResponse).getFeatureId();
                     if (!messageFeatureId.equalsIgnoreCase(responseFeatureId)) {
                         final var message = String.format("Live response does not target the correct feature. " +
                                         "Expected feature ID <%s>, but was <%s>.",
                                 messageThingId, responseThingId);
                         handleInvalidResponse.accept(message);
                     }
                     break;
                 case RetrieveThing.TYPE:


                 default:
                     handleInvalidResponse.accept("Initial message command type <{}> is unknown.");
             }
         }
     }

     private static void connectionFailureLogAndThrow(final String message,
             final CommandResponse<?> commandResponse,
             final ConnectionLogger connectionLogger) {

         final var exception = MessageSendingFailedException.newBuilder()
                 .httpStatus(HttpStatus.BAD_REQUEST)
                 .description(message)
                 .build();
         connectionLogger.failure(commandResponse, exception);
         throw exception;
     }
 }
