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
/* eslint-disable require-jsdoc */
/* eslint-disable comma-dangle */

import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Things from './things.js';

const dom = {
  tbodyAttributes: null,
  crudAttribute: null,
  badgeAttributeCount: null,
};

let eTag;
let attributeEditor;

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export function ready() {
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  dom.tbodyAttributes.onclick = onAttributeTableClick;

  dom.crudAttribute.addEventListener('onCreateClick', onCreateAttributeClick);
  dom.crudAttribute.addEventListener('onUpdateClick', onUpdateAttributeClick);
  dom.crudAttribute.addEventListener('onDeleteClick', onDeleteAttributeClick);
  dom.crudAttribute.addEventListener('onEditToggle', onEditToggle);

  attributeEditor = Utils.createAceEditor('attributeEditor', 'ace/mode/json', true);

  document.querySelector('a[data-bs-target="#tabCrudAttribute"]').addEventListener('shown.bs.tab', (event) => {
    attributeEditor.renderer.updateFull();
  });
}

function onCreateAttributeClick() {
  Utils.assert(dom.crudAttribute.idValue, 'Attribute path must not be empty', dom.crudAttribute.validationElement);
  Utils.assert(!Things.theThing['attributes'] || !Object.keys(Things.theThing.attributes).includes(dom.crudAttribute.idValue),
      `Attribute path ${dom.crudAttribute.idValue} already exists in Thing`,
      dom.crudAttribute.validationElement);

  updateAttribute('PUT', true);
}
function onUpdateAttributeClick() {
  updateAttribute('PUT');
}

function onDeleteAttributeClick() {
  Utils.confirm(`Are you sure you want to delete attribute<br>'${dom.crudAttribute.idValue}'?`, 'Delete', () => {
    updateAttribute('DELETE');
  });
}

function onAttributeTableClick(event) {
  if (event.target && event.target.nodeName === 'TD') {
    const path = event.target.parentNode.children[0].innerText;
    if (dom.crudAttribute.idValue === path) {
      refreshAttribute(null);
    } else {
      refreshAttribute(Things.theThing, path);
    }
  }
}

/**
 * Creates a onclick handler function
 * @param {String} method PUT or DELETE
 * @param {boolean} isNewAttribute if a new attribute is created. default = false
 */
function updateAttribute(method, isNewAttribute = false) {
  const attributeValue = JSON.parse(attributeEditor.getValue());
  API.callDittoREST(
      method,
      `/things/${Things.theThing.thingId}/attributes/${dom.crudAttribute.idValue}`,
      method === 'PUT' ? attributeValue : null,
      isNewAttribute ?
      {
        'If-None-Match': '*'
      } :
      {
        'If-Match': method === 'PUT' ? eTag : '*'
      },
  ).then(() => {
    if (method === 'PUT') {
      dom.crudAttribute.toggleEdit();
    }
    Things.refreshThing(Things.theThing.thingId);
  });
}

function refreshAttribute(thing, attributePath = null) {
  if (dom.crudAttribute.isEditing) {
    return;
  }

  if (thing) {
    dom.crudAttribute.idValue = attributePath;
    attributeEditor.setValue(Utils.stringifyPretty(thing.attributes[attributePath]), -1);
  } else {
    dom.crudAttribute.idValue = null;
    attributeEditor.setValue('');
  }
}

function onThingChanged(thing) {
  dom.crudAttribute.editDisabled = (thing === null);

  dom.tbodyAttributes.innerHTML = '';
  let count = 0;
  let thingHasAttribute = false;
  if (thing && thing.attributes) {
    Object.keys(Things.theThing.attributes).forEach((path) => {
      if (path === dom.crudAttribute.idValue) {
        refreshAttribute(Things.theThing, path);
        thingHasAttribute = true;
      }
      Utils.addTableRow(dom.tbodyAttributes, path, path === dom.crudAttribute.idValue, null,
          attributeToString(Things.theThing.attributes[path]));
      count++;
    });
  }
  dom.badgeAttributeCount.innerText = count > 0 ? count : '';
  if (!thingHasAttribute) {
    refreshAttribute(null);
  }
}

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

function onEditToggle(event) {
  const isEditing = event.detail.isEditing;
  if (isEditing && dom.crudAttribute.idValue && dom.crudAttribute.idValue !== '') {
    API.callDittoREST('GET', `/things/${Things.theThing.thingId}/attributes/${dom.crudAttribute.idValue}`,
        null, null, true)
        .then((response) => {
          eTag = response.headers.get('ETag');
          return response.json();
        })
        .then((attributeValue) => {
          enableDisableEditor();
          attributeEditor.setValue(Utils.stringifyPretty(attributeValue), -1);
        });
  } else {
    enableDisableEditor();
    refreshAttribute(Things.theThing, dom.crudAttribute.idValue)
    attributeEditor.setValue(
      Utils.stringifyPretty(Things.theThing.attributes[dom.crudAttribute.idValue]), -1);
  }

  function enableDisableEditor() {
    attributeEditor.setReadOnly(!isEditing);
    attributeEditor.renderer.setShowGutter(isEditing);
  }
}
