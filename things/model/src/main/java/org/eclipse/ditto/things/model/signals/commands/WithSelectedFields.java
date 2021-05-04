 /*
  * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.things.model.signals.commands;

 import java.util.Optional;

 import org.eclipse.ditto.json.JsonFieldSelector;

 public interface WithSelectedFields {

     /**
      * Returns the selected fields which are to be included in the JSON of the retrieved entity.
      *
      * @return the selected fields.
      */
     default Optional<JsonFieldSelector> getSelectedFields() {
         return Optional.empty();
     }

 }
