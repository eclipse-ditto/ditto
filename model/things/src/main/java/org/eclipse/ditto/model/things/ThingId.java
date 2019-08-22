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
 package org.eclipse.ditto.model.things;

 import static org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId.fromName;

 import java.util.Objects;
 import java.util.UUID;
 import java.util.function.Supplier;

 import javax.annotation.concurrent.Immutable;

 import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
 import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;
 import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdInvalidException;

 /**
  * Java representation of a validated Thing ID.
  */
 @Immutable
 public final class ThingId implements NamespacedEntityId {

     private static final ThingId PLACE_HOLDER_ID = ThingId.of(DefaultNamespacedEntityId.placeholder());
     private final NamespacedEntityId entityId;

     private ThingId(final NamespacedEntityId entityId) {
         this.entityId = entityId;
     }

     /**
      * Returns a {@link ThingId} based on the given thingId CharSequence. May return the same instance as
      * the parameter if the given parameter is already a ThingId. Skips validation if the given
      * {@code thingId} is an instance of NamespacedEntityId.
      *
      * @param thingId The thing ID.
      * @return the thing ID.
      */
     public static ThingId of(final CharSequence thingId) {

         if (thingId instanceof ThingId) {
             return (ThingId) thingId;
         }

         return wrapInThingIdInvalidException(() -> new ThingId(DefaultNamespacedEntityId.of(thingId)));
     }

     /**
      * Creates a new {@link ThingId} with the given namespace and name.
      * @param namespace the namespace of the thing.
      * @param name the name of the thing.
      * @return the created instance of {@link ThingId}
      */
     public static ThingId of(final String namespace, final String name) {
         return wrapInThingIdInvalidException(() -> new ThingId(DefaultNamespacedEntityId.of(namespace, name)));
     }

     /**
      * Creates {@link ThingId} with default namespace placeholder.
      * @param name the name of the thing.
      * @return the created thing ID.
      */
     public static ThingId inDefaultNamespace(final String name) {
         return wrapInThingIdInvalidException(() -> new ThingId(fromName(name)));
     }

     /**
      * Generates a new thing ID with the default namespace placeholder and a unique name.
      * @return the generated thing ID.
      */
     public static ThingId generateRandom() {
         return wrapInThingIdInvalidException(() -> new ThingId(fromName(UUID.randomUUID().toString())));
     }

     private static <T> T wrapInThingIdInvalidException(final Supplier<T> supplier) {
         try {
             return supplier.get();
         } catch (final NamespacedEntityIdInvalidException e) {
             throw ThingIdInvalidException.newBuilder(e.getEntityId().orElse(null)).cause(e).build();
         }
     }

     /**
      * Returns a dummy {@link ThingId}. This ID should not be used. It can be identified by
      * checking {@link ThingId#isPlaceholder()}.
      * @return the dummy ID.
      */
     public static ThingId placeholder() {
         return PLACE_HOLDER_ID;
     }

     @Override
     public boolean isPlaceholder() {
         return PLACE_HOLDER_ID.equals(this);
     }

     @Override
     public String getName() {
         return entityId.getName();
     }

     @Override
     public String getNamespace() {
         return entityId.getNamespace();
     }


     @Override
     public boolean equals(final Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         final ThingId thingId = (ThingId) o;
         return Objects.equals(entityId, thingId.entityId);
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
