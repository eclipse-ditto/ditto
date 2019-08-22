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

 import javax.annotation.concurrent.Immutable;

 /**
  * Java representation of an Entity ID.
  */
 @Immutable
 public interface EntityId extends CharSequence, Comparable<EntityId> {

     @Override
     default int length() {
         return toString().length();
     }

     @Override
     default char charAt(final int index) {
         return toString().charAt(index);
     }

     @Override
     default CharSequence subSequence(final int start, final int end) {
         return toString().subSequence(start, end);
     }

     /**
      * Compares the entity IDs based on their String representation.
      *
      * @param o the other entity ID.
      * @return a negative integer, zero, or a positive integer as this object
      * is less than, equal to, or greater than the specified object.
      */
     @Override
     default int compareTo(final EntityId o) {
         return toString().compareTo(o.toString());
     }

     /**
      * Checks whether this ID acts as dummy ID. Dummy IDs should not be used as ID for any entity.
      *
      * @return true if this ID is a dummy ID, otherwise false.
      */
     boolean isDummy();
 }
