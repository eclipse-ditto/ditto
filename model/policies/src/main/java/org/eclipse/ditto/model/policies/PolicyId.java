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

 /**
  * Java representation of a policy ID.
  */
 @Immutable
 public final class PolicyId implements NamespacedEntityId {

     private static final PolicyId PLACE_HOLDER_ID = PolicyId.of(DefaultNamespacedEntityId.placeholder());

     private final NamespacedEntityId entityId;

     private PolicyId(final NamespacedEntityId entityId) {
         this.entityId = entityId;
     }


     /**
      * Returns a {@link PolicyId} based on the given policyId CharSequence. May return the same instance as
      * the parameter if the given parameter is already a PolicyId. Skips validation if the given
      * {@code policyId} is an instance of NamespacedEntityId.
      *
      * @param policyId The policy ID.
      * @return the policy ID.
      */
     public static PolicyId of(final CharSequence policyId) {

         if (policyId instanceof PolicyId) {
             return (PolicyId) policyId;
         }

         try {
             return new PolicyId(DefaultNamespacedEntityId.of(policyId));
         } catch (final EntityNameInvalidException e) {
             throw PolicyIdInvalidException.forInvalidName(policyId).cause(e).build();
         } catch (final EntityNamespaceInvalidException e) {
             throw PolicyIdInvalidException.forInvalidNamespace(policyId).cause(e).build();
         } catch (final EntityIdInvalidException e) {
             throw PolicyIdInvalidException.newBuilder(policyId).cause(e).build();
         }
     }

     /**
      * Creates a new {@link PolicyId} with the given namespace and name.
      * @param namespace the namespace of the policy.
      * @param policyName the name of the policy.
      * @return the created instance of {@link PolicyId}
      */
     public static PolicyId of(final String namespace, final String policyName) {
         try {
             return new PolicyId(DefaultNamespacedEntityId.of(namespace, policyName));
         } catch (final EntityNameInvalidException e) {
             throw PolicyIdInvalidException.forInvalidName(namespace + ":" + policyName).cause(e).build();
         } catch (final EntityNamespaceInvalidException e) {
             throw PolicyIdInvalidException.forInvalidNamespace(namespace + ":" + policyName).cause(e).build();
         } catch (final EntityIdInvalidException e) {
             throw PolicyIdInvalidException.newBuilder(namespace + ":" + policyName).cause(e).build();
         }
     }

     /**
      * Generates a policy ID with a random unique name inside the given namespace.
      * @param namespace the namespace of the policy.
      * @return The generated unique policy ID.
      */
     public static PolicyId inNamespaceWithRandomName(final String namespace) {
         return of(namespace, UUID.randomUUID().toString());
     }

     /**
      * Returns a dummy {@link PolicyId}. This ID should not be used. It can be identified by
      * checking {@link PolicyId#isPlaceholder()}.
      * @return the dummy ID.
      */
     public static PolicyId placeholder() {
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

     @Override
     public boolean isPlaceholder() {
         return PLACE_HOLDER_ID.equals(this);
     }
 }
