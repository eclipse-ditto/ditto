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
 package org.eclipse.ditto.connectivity.service.messaging;

 import java.util.Optional;

 import javax.annotation.Nullable;

 import org.eclipse.ditto.base.model.headers.DittoHeaders;
 import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
 import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
 import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;

 /**
  * The result of a published message holding an optional command response (which also can be an acknowledgement).
  */
 public final class SendResult implements DittoHeadersSettable<SendResult> {

     @Nullable private final MessageSendingFailedException sendFailure;
     @Nullable private final CommandResponse<?> commandResponse;
     private final DittoHeaders dittoHeaders;

     public SendResult(@Nullable final CommandResponse<?> commandResponse, final DittoHeaders dittoHeaders) {
         this(commandResponse, null, dittoHeaders);
     }

     public SendResult(@Nullable final CommandResponse<?> commandResponse,
             @Nullable MessageSendingFailedException sendFailure,
             final DittoHeaders dittoHeaders) {
         this.commandResponse = commandResponse;
         this.sendFailure = sendFailure;
         this.dittoHeaders = dittoHeaders;
     }

     @Override
     public SendResult setDittoHeaders(final DittoHeaders dittoHeaders) {
         return new SendResult(commandResponse, sendFailure, dittoHeaders);
     }

     public Optional<CommandResponse<?>> getCommandResponse() {
         return Optional.ofNullable(commandResponse);
     }

     public Optional<MessageSendingFailedException> getSendFailure() {
         return Optional.ofNullable(sendFailure);
     }

     @Override
     public DittoHeaders getDittoHeaders() {
         return dittoHeaders;
     }

 }
