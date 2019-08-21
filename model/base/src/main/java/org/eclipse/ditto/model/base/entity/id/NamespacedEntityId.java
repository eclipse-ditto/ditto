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

 import jdk.nashorn.internal.ir.annotations.Immutable;

 /**
  * Interface for all entity IDs that contain a namespace in their string representation.
  * Every implementation of this interface needs to ensure that name and namespace are valid according to
  * {@link org.eclipse.ditto.model.base.entity.id.RegexPatterns#ENTITY_NAME_REGEX} and
  * {@link org.eclipse.ditto.model.base.entity.id.RegexPatterns#NAMESPACE_REGEX}.
  * Every implementation must ensure immutability.
  */
 @Immutable
 public interface NamespacedEntityId extends EntityId {

     /**
      * Gets the name part of this entity ID.
      *
      * @return the name if the entity.
      */
     String getName();

     /**
      * Gets the namespace part of this entity ID.
      *
      * @return the namespace o the entity.
      */
     String getNamespace();

 }
