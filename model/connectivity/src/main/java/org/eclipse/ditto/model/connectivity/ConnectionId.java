 /*
  * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.model.connectivity;

 import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;

 /**
  * Java representation of a connection ID.
  */
 @Immutable
 public final class ConnectionId implements EntityId {

     private static final ConnectionId PLACE_HOLDER_ID = new ConnectionId(DefaultEntityId.placeholder());
     private final EntityId entityId;

     private ConnectionId(final EntityId entityId) {
         this.entityId = entityId;
     }

     /**
      * Generates a random unique connection ID.
      *
      * @return the generated connection ID.
      */
     public static ConnectionId generateRandom() {
         return new ConnectionId(DefaultEntityId.generateRandom());
     }

     /**
      * Creates a connection ID based on the given char sequence. May return the same instance as the parameter if
      * it is already an instance of ConnectionId.
      * @param connectionId the connection id.
      * @return the connection ID.
      */
     public static ConnectionId of(final CharSequence connectionId) {
         if (connectionId instanceof ConnectionId) {
             return (ConnectionId) connectionId;
         }

         return new ConnectionId(DefaultEntityId.of(connectionId));
     }

     /**
      * Returns a dummy {@link ConnectionId}. This ID should not be used. It can be identified by
      * checking {@link ConnectionId#isPlaceholder()}.
      * @return the dummy ID.
      */
     public static ConnectionId placeholder() {
         return PLACE_HOLDER_ID;
     }

     @Override
     public boolean isPlaceholder() {
         return entityId.isPlaceholder();
     }

     @Override
     public boolean equals(final Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         final ConnectionId connectionId = (ConnectionId) o;
         return Objects.equals(entityId, connectionId.entityId);
     }

     @Override
     public int hashCode() {
         return Objects.hash(entityId);
     }


     @Override
     public String toString() {
         return entityId.toString();
     }
 }
