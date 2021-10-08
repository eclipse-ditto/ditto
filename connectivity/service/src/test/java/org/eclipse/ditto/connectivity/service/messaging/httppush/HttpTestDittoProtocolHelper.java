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

 import java.util.Collections;
 import java.util.function.Function;

 import org.eclipse.ditto.base.model.json.Jsonifiable;
 import org.eclipse.ditto.base.model.signals.Signal;
 import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
 import org.eclipse.ditto.connectivity.api.OutboundSignal;
 import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
 import org.eclipse.ditto.connectivity.model.Target;
 import org.eclipse.ditto.protocol.Adaptable;
 import org.eclipse.ditto.protocol.ProtocolFactory;
 import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;

 import akka.actor.ActorRef;

 public final class HttpTestDittoProtocolHelper {

     private HttpTestDittoProtocolHelper() {
         throw new AssertionError("nope");
     }

     private static final Function<Signal<?>, Adaptable> TO_ADAPTABLE = DittoProtocolAdapter.newInstance()::toAdaptable;

     public static String signalToJsonString(final Signal<?> signal) {
      return TO_ADAPTABLE
              .andThen(ProtocolFactory::wrapAsJsonifiableAdaptable)
              .andThen(Jsonifiable::toJson)
              .andThen(Object::toString)
              .apply(signal);
     }

     public static OutboundSignal.MultiMapped toMultiMapped(final Signal<?> signal,
             final Target target,
             final ActorRef sender) {

         final var outboundSignal =
                 OutboundSignalFactory.newOutboundSignal(signal, Collections.singletonList(target));

         final var externalMessage =
                 ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                         .withText("payload")
                         .build();

         return TO_ADAPTABLE
                 .andThen(a -> OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, a, externalMessage))
                 .andThen(Collections::singletonList)
                 .andThen(ls -> OutboundSignalFactory.newMultiMappedOutboundSignal(ls, sender))
                 .apply(signal);
     }
 }
