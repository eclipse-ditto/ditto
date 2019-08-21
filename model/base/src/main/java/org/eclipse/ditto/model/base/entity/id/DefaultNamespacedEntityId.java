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
 package org.eclipse.ditto.model.base.entity.id;

 import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ENTITY_NAME_GROUP_NAME;
 import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ENTITY_NAME_PATTERN;
 import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.ID_PATTERN;
 import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.NAMESPACE_DELIMITER;
 import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.NAMESPACE_GROUP_NAME;
 import static org.eclipse.ditto.model.base.entity.id.RegexPatterns.NAMESPACE_PATTERN;

 import java.util.Objects;
 import java.util.regex.Matcher;

 import javax.annotation.concurrent.Immutable;

 /**
  * Default implementation for a validated {@link org.eclipse.ditto.model.base.entity.id.NamespacedEntityId}
  */
 @Immutable
 public final class DefaultNamespacedEntityId implements NamespacedEntityId {

     private static final NamespacedEntityId PLACE_HOLDER_ID = DefaultNamespacedEntityId.of("unknown:unknown");
     public static final String DEFAULT_NAMESPACE = "";

     private final String namespace;
     private final String name;
     private final String stringRepresentation;

     private DefaultNamespacedEntityId(final String namespace, final String name,
             final boolean shouldValidate) {
         if (name == null) {
             throw EntityNameInvalidException.forEntityName(name).build();
         }

         if (namespace == null) {
             throw EntityNamespaceInvalidException.forEntityNamespace(namespace).build();
         }

         this.namespace = shouldValidate ? validateNamespace(namespace) : namespace;
         this.name = shouldValidate ? validateName(name) : name;
         stringRepresentation = namespace + NAMESPACE_DELIMITER + name;
     }

     private DefaultNamespacedEntityId(final CharSequence entityId) {
         if (entityId == null) {
             throw EntityIdInvalidException.forNamespacedEntityId(entityId).build();
         }

         final Matcher nsMatcher = ID_PATTERN.matcher(entityId);

         if (nsMatcher.matches()) {
             this.namespace = nsMatcher.group(NAMESPACE_GROUP_NAME);
             this.name = nsMatcher.group(ENTITY_NAME_GROUP_NAME);
             stringRepresentation = namespace + NAMESPACE_DELIMITER + name;
         } else {
             throw EntityIdInvalidException.forNamespacedEntityId(entityId).build();
         }
     }

     /**
      * Returns a {@link NamespacedEntityId} based on the given entityId CharSequence. May return the same instance as
      * the parameter if the given parameter is already a DefaultNamespacedEntityId. Skips validation if the given
      * {@code entityId} is an instance of NamespacedEntityId.
      *
      * @param entityId The entity ID.
      * @return the namespaced entity ID.
      */
     public static NamespacedEntityId of(final CharSequence entityId) {
         if (entityId instanceof DefaultNamespacedEntityId) {
             return (NamespacedEntityId) entityId;
         }

         if (entityId instanceof NamespacedEntityId) {
             final String namespace = ((NamespacedEntityId) entityId).getNamespace();
             final String name = ((NamespacedEntityId) entityId).getName();
             return new DefaultNamespacedEntityId(namespace, name, false);
         }

         return new DefaultNamespacedEntityId(entityId);
     }

     /**
      * Creates {@link NamespacedEntityId} with default namespace placeholder.
      * @param entityName the name of the entity.
      * @return the created namespaced entity ID.
      */
     public static NamespacedEntityId fromName(final String entityName) {
         return of(DEFAULT_NAMESPACE, entityName);
     }

     /**
      * Creates a new {@link NamespacedEntityId} with the given namespace and name.
      * @param namespace the namespace of the entity.
      * @param name the name of the entity.
      * @return the created instance of {@link NamespacedEntityId}
      */
     public static NamespacedEntityId of(final String namespace, final String name) {
         return new DefaultNamespacedEntityId(namespace, name, true);
     }

     /**
      * Returns a dummy {@link NamespacedEntityId}. This ID should not be used. It can be identified by
      * checking {@link NamespacedEntityId#isPlaceholder()}.
      * @return the dummy ID.
      */
     public static NamespacedEntityId placeholder() {
         return PLACE_HOLDER_ID;
     }

     @Override
     public boolean isPlaceholder() {
         return PLACE_HOLDER_ID.equals(this);
     }

     @Override
     public String getName() {
         return name;
     }

     @Override
     public String getNamespace() {
         return namespace;
     }

     private static String validateNamespace(final String namespace) {
         if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
             throw EntityNamespaceInvalidException.forEntityNamespace(namespace).build();
         }

         return namespace;
     }

     private static String validateName(final String name) {
         if (!ENTITY_NAME_PATTERN.matcher(name).matches()) {
             throw EntityNameInvalidException.forEntityName(name).build();
         }

         return name;
     }

     @Override
     public boolean equals(final Object o) {
         if (this == o) {
             return true;
         }

         if (o == null || getClass() != o.getClass()) {
             return false;
         }

         final DefaultNamespacedEntityId that = (DefaultNamespacedEntityId) o;
         return Objects.equals(namespace, that.namespace) &&
                 Objects.equals(name, that.name);
     }

     @Override
     public int hashCode() {
         return Objects.hash(namespace, name);
     }


     @Override
     public String toString() {
         return stringRepresentation;
     }

 }
