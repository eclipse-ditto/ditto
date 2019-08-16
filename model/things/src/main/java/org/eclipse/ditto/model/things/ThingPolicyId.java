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

 public final class ThingPolicyId implements NamespacedEntityId {

     private static final ThingPolicyId PLACE_HOLDER_ID = ThingPolicyId.of(DefaultNamespacedEntityId.placeholder());
     private final NamespacedEntityId entityId;

     private ThingPolicyId(final NamespacedEntityId entityId) {
         this.entityId = entityId;
     }

     public static ThingPolicyId of(final CharSequence thingPolicyId) {

         if (thingPolicyId instanceof ThingPolicyId) {
             return (ThingPolicyId) thingPolicyId;
         }

         if (thingPolicyId instanceof DefaultNamespacedEntityId) {
             return new ThingPolicyId((NamespacedEntityId) thingPolicyId);
         }

         try {
             return new ThingPolicyId(DefaultNamespacedEntityId.of(thingPolicyId));
         } catch (final EntityNameInvalidException e) {
             throw ThingPolicyIdInvalidException.forInvalidName(thingPolicyId).cause(e).build();
         } catch (final EntityNamespaceInvalidException e) {
             throw ThingPolicyIdInvalidException.forInvalidNamespace(thingPolicyId).cause(e).build();
         } catch (final EntityIdInvalidException e) {
             throw ThingPolicyIdInvalidException.newBuilder(thingPolicyId).cause(e).build();
         }
     }

     public static ThingPolicyId of(final String namespace, final String name) {
         return new ThingPolicyId(DefaultNamespacedEntityId.of(namespace, name));
     }

     public static ThingPolicyId inDefaultNamespace(final String name) {
         return new ThingPolicyId(DefaultNamespacedEntityId.fromName(name));
     }

     public static ThingPolicyId generateRandom() {
         return new ThingPolicyId(DefaultNamespacedEntityId.fromName(UUID.randomUUID().toString()));
     }

     public static ThingPolicyId placeholder() {
         return PLACE_HOLDER_ID;
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
         final ThingPolicyId thingId = (ThingPolicyId) o;
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

     @Override
     public boolean isPlaceHolder() {
         return PLACE_HOLDER_ID.equals(this);
     }
 }
