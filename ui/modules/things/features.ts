/* eslint-disable require-jsdoc */
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

import {JSONPath} from 'jsonpath-plus';
import * as API from '../api.js';
/* eslint-disable comma-dangle */
/* eslint-disable new-cap */
import * as Utils from '../utils.js';
import * as Fields from './fields.js';
import * as Things from './things.js';
import featuresHTML from './features.html';

const observers = [];

document.getElementById('featuresHTML').innerHTML = featuresHTML;


export function addChangeListener(observer) {
  observers.push(observer);
}

function notifyAll() {
  observers.forEach((observer) => observer.call(null, dom.crudFeature.idValue));
}

let featurePropertiesEditor;
let featureDesiredPropertiesEditor;

let eTag;

const dom = {
  crudFeature: null,
  inputFeatureDefinition: null,
  badgeFeatureCount: null,
  tbodyFeatures: null,
  tableValidationFeature: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export function ready() {
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  Utils.addValidatorToTable(dom.tbodyFeatures, dom.tableValidationFeature);

  dom.tbodyFeatures.onclick = onFeaturesTableClick;

  dom.crudFeature.editDisabled = true;
  dom.crudFeature.addEventListener('onCreateClick', onCreateFeatureClick);
  dom.crudFeature.addEventListener('onUpdateClick', onUpdateFeatureClick);
  dom.crudFeature.addEventListener('onDeleteClick', onDeleteFeatureClick);
  dom.crudFeature.addEventListener('onEditToggle', onEditToggle);

  featurePropertiesEditor = Utils.createAceEditor('featurePropertiesEditor', 'ace/mode/json', true);
  featureDesiredPropertiesEditor = Utils.createAceEditor('featureDesiredPropertiesEditor', 'ace/mode/json', true);

  featurePropertiesEditor.on('dblclick', onFeaturePropertiesDblClick);

  document.querySelector('a[data-bs-target="#tabCrudFeature"]').addEventListener('shown.bs.tab', (event) => {
    featurePropertiesEditor.renderer.updateFull();
    featureDesiredPropertiesEditor.renderer.updateFull();
  });
}

function onUpdateFeatureClick() {
  updateFeature('PUT');
}

function onDeleteFeatureClick() {
  Utils.confirm(`Are you sure you want to delete feature<br>'${dom.crudFeature.idValue}'?`, 'Delete', () => {
    updateFeature('DELETE');
  });
}

function onFeaturePropertiesDblClick(event) {
  if (!event.domEvent.shiftKey) {
    return;
  }

  setTimeout(() => {
    const token = featurePropertiesEditor.getSelectedText();
    if (token) {
      const path = '$..' + token.replace(/['"]+/g, '').trim();
      const res = JSONPath({
        json: JSON.parse(featurePropertiesEditor.getValue()),
        path: path,
        resultType: 'pointer',
      });
      Fields.proposeNewField('features/' + dom.crudFeature.idValue + '/properties' + res);
    }
  }, 10);
}

function onCreateFeatureClick() {
  Utils.assert(dom.crudFeature.idValue, 'Feature ID must not be empty', dom.crudFeature.validationElement);
  Utils.assert(!Things.theThing['features'] || !Object.keys(Things.theThing.features).includes(dom.crudFeature.idValue),
      `Feature ID ${dom.crudFeature.idValue} already exists in Thing`,
      dom.crudFeature.validationElement);
  updateFeature('PUT', true);
}

function onFeaturesTableClick(event) {
  if (event.target && event.target.nodeName === 'TD') {
    if (dom.crudFeature.idValue === event.target.textContent) {
      refreshFeature(null);
    } else {
      refreshFeature(Things.theThing, event.target.textContent);
    }
  }
}

/**
 * Triggers a feature update in Ditto according to UI contents
 * @param {String} method Either PUT to update the feature or DELETE to delete the feature
 * @param {boolean} isNewFeature indicates if a new feature should be created. (default: false)
 */
function updateFeature(method, isNewFeature = false) {
  Utils.assert(Things.theThing, 'No Thing selected');
  Utils.assert(dom.crudFeature.idValue, 'No Feature selected');

  type Feature = {
    definition?: string[];
    properties?: Object;
    desiredProperties?: Object;
  };

  const featureObject: Feature = {};
  const featureProperties = featurePropertiesEditor.getValue();
  const featureDesiredProperties = featureDesiredPropertiesEditor.getValue();
  if (dom.inputFeatureDefinition.value) {
    featureObject.definition = dom.inputFeatureDefinition.value.split(',');
  }
  if (featureProperties) {
    featureObject.properties = JSON.parse(featureProperties);
  }
  if (featureDesiredProperties) {
    featureObject.desiredProperties = JSON.parse(featureDesiredProperties);
  }

  API.callDittoREST(
      method,
      '/things/' + Things.theThing.thingId + '/features/' + dom.crudFeature.idValue,
      method === 'PUT' ? featureObject : null,
      isNewFeature ?
      {
        'If-None-Match': '*'
      } :
      {
        'If-Match': method === 'PUT' ? eTag : '*'
      }
  ).then(() => {
    if (method === 'PUT') {
      dom.crudFeature.toggleEdit();
    }
    Things.refreshThing(Things.theThing.thingId);
  }).catch(
      // nothing to clean-up if featureUpdate failed
  );
}

function updateFeatureEditors(featureJson) {
  if (featureJson) {
    dom.inputFeatureDefinition.value = featureJson['definition'] ? featureJson.definition : null;
    if (featureJson['properties']) {
      featurePropertiesEditor.setValue(Utils.stringifyPretty(featureJson.properties), -1);
    } else {
      featurePropertiesEditor.setValue('');
    }
    if (featureJson['desiredProperties']) {
      featureDesiredPropertiesEditor.setValue(Utils.stringifyPretty(featureJson.desiredProperties), -1);
    } else {
      featureDesiredPropertiesEditor.setValue('');
    }
  } else {
    dom.inputFeatureDefinition.value = null;
    featurePropertiesEditor.setValue('');
    featureDesiredPropertiesEditor.setValue('');
  }
  featurePropertiesEditor.session.getUndoManager().reset();
  featureDesiredPropertiesEditor.session.getUndoManager().reset();
}

/**
 * Initializes all UI components for the given single feature of the given thing, if no thing is given the UI is cleared
 * @param {Object} thing thing the feature values are taken from
 * @param {String} featureId FeatureId to be refreshed
 */
function refreshFeature(thing, featureId = null) {
  if (!dom.crudFeature.isEditing) {
    if (thing && thing['features'] && featureId) {
      dom.crudFeature.idValue = featureId;
      updateFeatureEditors(thing.features[featureId]);
    } else {
      dom.crudFeature.idValue = null;
      updateFeatureEditors(null);
    }
    notifyAll();
  }
}

/**
 * Initializes all feature UI components for the given thing
 * @param {Object} thing UI is initialized for the features of the given thing
 */
function onThingChanged(thing) {
  dom.crudFeature.editDisabled = (thing === null);
  // Update features table
  dom.tbodyFeatures.innerHTML = '';
  let count = 0;
  let thingHasFeature = false;
  if (thing && thing.features) {
    for (const key of Object.keys(thing.features)) {
      if (key === dom.crudFeature.idValue) {
        refreshFeature(thing, key);
        thingHasFeature = true;
      }
      Utils.addTableRow(dom.tbodyFeatures, key, key === dom.crudFeature.idValue);
      count++;
    }
  }
  dom.badgeFeatureCount.textContent = count > 0 ? count : '';
  if (!thingHasFeature) {
    refreshFeature(thing, null);
  }
}

function onEditToggle(event) {
  const isEditing = event.detail.isEditing;
  if (isEditing && dom.crudFeature.idValue && dom.crudFeature.idValue !== '') {
    API.callDittoREST('GET', `/things/${Things.theThing.thingId}/features/${dom.crudFeature.idValue}`, null, null, true)
        .then((response) => {
          eTag = response.headers.get('ETag').replace('W/', '');
          return response.json();
        })
        .then((featureJson) => {
          enableDisableEditors();
          updateFeatureEditors(featureJson);
        });
  } else {
    enableDisableEditors();
    resetEditors();
    dom.crudFeature.validationElement.classList.remove('is-invalid');
  }

  function enableDisableEditors() {
    dom.inputFeatureDefinition.disabled = !isEditing;
    featurePropertiesEditor.setReadOnly(!isEditing);
    featurePropertiesEditor.renderer.setShowGutter(isEditing);
    featureDesiredPropertiesEditor.setReadOnly(!isEditing);
    featureDesiredPropertiesEditor.renderer.setShowGutter(isEditing);
  }

  function resetEditors() {
    if (dom.crudFeature.idValue) {
      refreshFeature(Things.theThing, dom.crudFeature.idValue);
    } else {
      refreshFeature(null);
    }
  }
}

