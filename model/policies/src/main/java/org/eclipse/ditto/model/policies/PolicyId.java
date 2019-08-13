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
 package org.eclipse.ditto.model.policies;

 import java.util.Objects;
 import java.util.UUID;

 import org.eclipse.ditto.model.base.entity.id.DefaultNamespacedEntityId;
 import org.eclipse.ditto.model.base.entity.id.EntityIdInvalidException;
 import org.eclipse.ditto.model.base.entity.id.EntityNameInvalidException;
 import org.eclipse.ditto.model.base.entity.id.EntityNamespaceInvalidException;
 import org.eclipse.ditto.model.base.entity.id.NamespacedEntityId;

 import jdk.nashorn.internal.ir.annotations.Immutable;

 @Immutable
 public final class PolicyId implements NamespacedEntityId {

     public static final PolicyId UNKNOWN = PolicyId.of("unknown", "unknown");

     private final NamespacedEntityId entityId;


     private PolicyId(final NamespacedEntityId entityId) {
         this.entityId = entityId;
     }

     public static PolicyId of(final CharSequence policyId) {
         try {
             return new PolicyId(DefaultNamespacedEntityId.fromCharSequence(policyId));
         } catch (final EntityNameInvalidException e) {
             throw PolicyIdInvalidException.forInvalidName(policyId).cause(e).build();
         } catch (final EntityNamespaceInvalidException e) {
             throw PolicyIdInvalidException.forInvalidNamespace(policyId).cause(e).build();
         } catch (final EntityIdInvalidException e) {
             throw PolicyIdInvalidException.newBuilder(policyId).cause(e).build();
         }
     }

     public static PolicyId of(final String namespace, final String policyName) {
         return new PolicyId(DefaultNamespacedEntityId.of(namespace, policyName));
     }

     public static PolicyId of(final NamespacedEntityId namespacedEntityId) {
         return of(namespacedEntityId.getNameSpace(), namespacedEntityId.getName());
     }

     public static PolicyId inNamespaceWithRandomName(final String namespace) {
         return of(namespace, UUID.randomUUID().toString());
     }

     public static PolicyId asPolicyId(final CharSequence charSequence) {
         return charSequence instanceof PolicyId ? (PolicyId) charSequence : PolicyId.of(charSequence);
     }

     @Override
     public String getName() {
         return entityId.getName();
     }

     @Override
     public String getNameSpace() {
         return entityId.getNameSpace();
     }

     @Override
     public boolean equals(final Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         final PolicyId thingId = (PolicyId) o;
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
