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

 import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

 import java.util.Objects;
 import java.util.regex.Matcher;
 import java.util.regex.Pattern;

 public final class DefaultNamespacedEntityId implements NamespacedEntityId {

     private static final String NAMESPACE_GROUP_NAME = "ns";
     private static final String NAMESPACE_REGEX =
             "(?<" + NAMESPACE_GROUP_NAME + ">(?:|(?:[a-zA-Z]\\w*+)(?:\\.[a-zA-Z]\\w*+)*+))";

     private static final String ENTITY_NAME_GROUP_NAME = "name";
     /**
      * The regex pattern for an Entity Name. Has to be conform to
      * <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC-2396</a>.
      */
     private static final String ENTITY_NAME_REGEX = "(?<" + ENTITY_NAME_GROUP_NAME +
             ">(?:[-\\w:@&=+,.!~*'_;]|%\\p{XDigit}{2})(?:[-\\w:@&=+,.!~*'$_;<>]|%\\p{XDigit}{2})*+)";

     /**
      * The regex pattern for an Entity ID.
      * Combines "namespace" pattern (java package notation + a colon) and "name" pattern.
      */
     public static final String ID_REGEX = NAMESPACE_REGEX + "\\:" + ENTITY_NAME_REGEX;

     private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);
     private static final Pattern ENTITY_NAME_PATTERN = Pattern.compile(ENTITY_NAME_REGEX);
     private static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);

     private static final String NAMESPACE_DELIMITER = ":";
     private static final String DEFAULT_NAMESPACE = "";

     private final String namespace;
     private final String name;
     private final String stringRepresentation;

     private DefaultNamespacedEntityId(final String namespace, final String name,
             final boolean shouldValidateNamespace) {
         this.namespace = shouldValidateNamespace ? validateNamespace(namespace) : namespace;
         this.name = validateName(checkNotNull(name, "entity name"));
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

     public static NamespacedEntityId of(final CharSequence entityId) {
         if (entityId instanceof DefaultNamespacedEntityId) {
             return (NamespacedEntityId) entityId;
         }

         if (entityId instanceof NamespacedEntityId) {
             return new DefaultNamespacedEntityId(((NamespacedEntityId) entityId).getNamespace(),
                     ((NamespacedEntityId) entityId).getName(), false);
         }

         return new DefaultNamespacedEntityId(entityId);
     }

     public static NamespacedEntityId fromName(final String entityName) {
         return new DefaultNamespacedEntityId(DEFAULT_NAMESPACE, entityName, false);
     }

     public static NamespacedEntityId of(final String namespace, final String name) {
         return new DefaultNamespacedEntityId(namespace, name, true);
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
         if (!NAMESPACE_PATTERN.matcher(checkNotNull(namespace, "namespace")).matches()) {
             throw EntityNamespaceInvalidException.forEntityNamespace(namespace).build();
         }

         return namespace;
     }

     private static String validateName(final String name) {
         if (!ENTITY_NAME_PATTERN.matcher(checkNotNull(name, "name")).matches()) {
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
