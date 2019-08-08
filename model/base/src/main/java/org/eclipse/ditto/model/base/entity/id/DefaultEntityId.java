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

 import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;

 import java.util.Objects;
 import java.util.UUID;

 public final class DefaultEntityId implements EntityId {

     public static final String NONE = "none";
     public static final EntityId NONE_ID = DefaultEntityId.of(NONE);

     private final String id;

     private DefaultEntityId(final CharSequence id) {
         this.id = argumentNotEmpty(id, "ID").toString();
     }

     public static EntityId of(final CharSequence entityId) {
         return new DefaultEntityId(entityId);
     }

     public static EntityId generateRandom() {
         return new DefaultEntityId(UUID.randomUUID().toString());
     }

     @Override
     public boolean equals(final Object o) {
         if (this == o) {
             return true;
         }

         if (o == null || getClass() != o.getClass()) {
             return false;
         }

         final DefaultEntityId that = (DefaultEntityId) o;
         return Objects.equals(id, that.id);
     }

     @Override
     public int hashCode() {
         return Objects.hash(id);
     }


     @Override
     public String toString() {
         return id;
     }
 }
