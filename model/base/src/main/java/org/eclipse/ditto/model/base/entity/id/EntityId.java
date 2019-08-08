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

     @Override
     default int compareTo(final EntityId o) {
         return toString().compareTo(o.toString());
     }
 }
