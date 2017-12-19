/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akkapersistence

/**
  * Contains implicits for this package.
  */
package object mongoaddons {
  implicit class NonWrappingLongToInt(val pimped: Long) extends AnyVal {
    def toIntWithoutWrapping: Int = {
      if (pimped > Int.MaxValue) {
        Int.MaxValue
      } else {
        pimped.intValue
      }
    }
  }
}
