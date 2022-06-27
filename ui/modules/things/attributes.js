/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Things from './things.js';

const dom = {
  attributesTable: null,
  attributePath: null,
  attributeValue: null,
  attributeCount: null,
  putAttribute: null,
  deleteAttribute: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export function ready() {
  Utils.getAllElementsById(dom);

  dom.attributesTable.onclick = (event) => {
    if (event.target && event.target.nodeName === 'TD') {
      const path = event.target.parentNode.children[0].innerText;
      dom.attributePath.value = path;
      dom.attributeValue.value = attributeToString(Things.theThing.attributes[path]);
    }
  };

  dom.putAttribute.onclick = clickAttribute('PUT');
  dom.deleteAttribute.onclick = clickAttribute('DELETE');

  Things.addChangeListener(updateAttributesTable);
};

/**
 * Creates a onclick handler function
 * @param {String} method PUT or DELETE
 * @return {function} Click handler function to PUT or DELETE Ditto attribute
 */
function clickAttribute(method) {
  return function() {
    Utils.assert(Things.theThing, 'No Thing selected');
    Utils.assert(dom.attributePath.value, 'Attribute path is empty');
    Utils.assert(method !== 'PUT' || dom.attributeValue.value, 'Attribute value is empty');
    API.callDittoREST(
        method,
        `/things/${Things.theThing.thingId}/attributes/${dom.attributePath.value}`,
        method === 'PUT' ? attributeFromString(dom.attributeValue.value) : null,
    ).then(() => Things.refreshThing(Things.theThing.thingId));
  };
};

/**
 * Updates UI compoents for attributes
 */
function updateAttributesTable() {
  dom.attributesTable.innerHTML = '';
  let count = 0;
  let thingHasAttribute = false;
  if (Things.theThing.attributes) {
    Object.keys(Things.theThing.attributes).forEach((path) => {
      if (path === dom.attributePath.value) {
        dom.attributeValue.value = attributeToString(Things.theThing.attributes[path]);
        thingHasAttribute = true;
      };
      Utils.addTableRow(dom.attributesTable,
          path,
          attributeToString(Things.theThing.attributes[path]),
          path === dom.attributePath.value);
      count++;
    });
  };
  dom.attributeCount.innerText = count > 0 ? count : '';
  if (!thingHasAttribute) {
    dom.attributePath.value = null;
    dom.attributeValue.value = null;
  };
};

/**
 * checks if the attribute is an array or json and returns a parsed string
 * @param {object} attribute
 * @return {String} parsed json for objects or toString for json values
 */
function attributeToString(attribute) {
  return typeof attribute === 'object' ?
      JSON.stringify(attribute) :
      attribute.toString();
}

/**
 * Converts a String into a json value for a Ditto attribute
 * @param {String} attribute Ditto attribute as a String
 * @return {Object} object in case it could be parsed, else the orignal String
 */
function attributeFromString(attribute) {
  try {
    return JSON.parse(attribute);
  } catch (err) {
    return attribute;
  }
}
