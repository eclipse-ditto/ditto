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

 import java.util.Objects;
import java.util.UUID;

import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
 import org.eclipse.ditto.model.base.entity.id.EntityIdInvalidException;
 import org.eclipse.ditto.model.base.entity.id.EntityNameInvalidException;
import org.eclipse.ditto.model.base.entity.id.EntityNamespaceInvalidException;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;

 public final class ThingId implements NamespacedEntityId {

     private final NamespacedEntityId entityId;

     private ThingId(final NamespacedEntityId entityId) {
         this.entityId = entityId;
     }

     public static ThingId of(final CharSequence thingId) {
         try {
             return new ThingId(DefaultNamespacedEntityId.of(thingId));
         } catch (final EntityNameInvalidException e) {
             throw ThingIdInvalidException.forInvalidName(thingId).cause(e).build();
         } catch (final EntityNamespaceInvalidException e) {
             throw ThingIdInvalidException.forInvalidNamespace(thingId).cause(e).build();
         } catch (final EntityIdInvalidException e) {
             throw ThingIdInvalidException.newBuilder(thingId).cause(e).build();
         }
     }

     public static ThingId of(final String namespace, final String name) {
         return new ThingId(DefaultNamespacedEntityId.of(namespace, name));
     }

     public static ThingId inDefaultNamespace(final String name) {
         return new ThingId(DefaultNamespacedEntityId.fromName(name));
     }

     public static ThingId generateRandom() {
         return new ThingId(DefaultNamespacedEntityId.fromName(UUID.randomUUID().toString()));
     }

     public static ThingId asThingId(final CharSequence charSequence) {
         return charSequence instanceof ThingId ? (ThingId) charSequence : ThingId.of(charSequence);
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
