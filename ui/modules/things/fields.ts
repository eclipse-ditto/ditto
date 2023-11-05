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
import {Modal} from 'bootstrap';

import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import fieldsHTML from './fields.html';

let theFieldIndex = -1;

const dom = {
  fieldPath: null,
  fieldLabel: null,
  fieldList: null,
  fieldsModal: null,
};

document.getElementById('fieldsHTML').innerHTML = fieldsHTML;

/**
 * Create a Ditto search query parameter from the active fields
 * @return {String} The fields query parameter for Ditto search
 */
export function getQueryParameter() {
  if (!Environments.current().fieldList) {
    Environments.current().fieldList = [];
  }
  const fields = Environments.current().fieldList.filter((f) => f.active).map((f) => f.path);
  return 'fields=thingId' + (fields.length > 0 ? ',' + fields : '');
}


let bsFieldsModal = null;
/**
 * Set the fieldpath value
 * @param {String} fieldPath new value for the fieldpath
 */
export function proposeNewField(fieldPath) {
  dom.fieldPath.value = fieldPath;
  dom.fieldLabel.value = null;
  if (!bsFieldsModal) {
    bsFieldsModal = new Modal(dom.fieldsModal);
  }
  bsFieldsModal.show();
}

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  dom.fieldList.addEventListener('click', (event) => {
    if (event.target && event.target.tagName === 'TD') {
      toggleFieldSelection(event.target.parentNode.rowIndex);
    }
  });

  dom.fieldsModal.addEventListener('hide.bs.modal', () => {
    Environments.environmentsJsonChanged();
  });

  document.getElementById('fieldUpdate').onclick = () => {
    Utils.assert(theFieldIndex >= 0, 'No field selected');
    const selectedField = Environments.current().fieldList[theFieldIndex];
    const otherFields = Environments.current().fieldList.filter((elem, i) => i != theFieldIndex);
    const mapped = otherFields.map((field) => field.path);
    const cond = mapped.includes(selectedField.path);
    console.log(cond);
    Utils.assert(!Environments.current().fieldList
        .filter((elem, i) => i != theFieldIndex)
        .map((field) => field.path)
        .includes(dom.fieldPath.value), 'Changed field path already exists', dom.fieldPath);

    selectedField.path = dom.fieldPath.value;
    selectedField.label = dom.fieldLabel.value;
    updateFieldList();
  };

  document.getElementById('fieldCreate').onclick = () => {
    Utils.assert(dom.fieldPath.value, 'Field path must not be empty', dom.fieldPath);
    Utils.assert(!Environments.current().fieldList
        .map((field) => field.path)
        .includes(dom.fieldPath.value), 'Field path already exists', dom.fieldPath);

    Environments.current().fieldList.push({
      active: true,
      path: dom.fieldPath.value,
      label: dom.fieldLabel.value ? dom.fieldLabel.value : dom.fieldPath.value.split('/').slice(-1)[0],
    });
    updateFieldList();
  };

  document.getElementById('fieldDelete').onclick = () => {
    Utils.assert(theFieldIndex >= 0, 'No field selected');

    Environments.current().fieldList.splice(theFieldIndex, 1);
    updateFieldList();
    theFieldIndex = -1;
  };

  document.getElementById('fieldUp').onclick = () => {
    if (theFieldIndex <= 0) {
      return;
    }
    const movedItem = Environments.current().fieldList[theFieldIndex];
    Environments.current().fieldList.splice(theFieldIndex, 1);
    theFieldIndex--;
    Environments.current().fieldList.splice(theFieldIndex, 0, movedItem);
    updateFieldList();
  };

  document.getElementById('fieldDown').onclick = () => {
    if (theFieldIndex < 0 || theFieldIndex === Environments.current().fieldList.length - 1) {
      return;
    }
    const movedItem = Environments.current().fieldList[theFieldIndex];
    Environments.current().fieldList.splice(theFieldIndex, 1);
    theFieldIndex++;
    Environments.current().fieldList.splice(theFieldIndex, 0, movedItem);
    updateFieldList();
  };
}

/**
 * Selects or de-selects the field for editing
 * @param {integer} fieldIndex index in fieldlist of field to toggle
 */
function toggleFieldSelection(fieldIndex) {
  if (theFieldIndex === fieldIndex) {
    theFieldIndex = -1;
    dom.fieldPath.value = null;
    dom.fieldLabel.value = null;
  } else {
    theFieldIndex = fieldIndex;
    const selectedField = Environments.current().fieldList[theFieldIndex];
    dom.fieldPath.value = selectedField.path;
    dom.fieldLabel.value = selectedField['label'] ? selectedField.label : null;
  }
}

/**
 * Callback on environment change. Initializes all UI components for fields
 */
function onEnvironmentChanged() {
  if (!Environments.current()['fieldList']) {
    Environments.current().fieldList = [];
  }
  updateFieldList();
}

/**
 * (Re-)Initializes the fieldlist in the UI
 */
function updateFieldList() {
  dom.fieldList.innerHTML = '';
  theFieldIndex = -1;
  Environments.current().fieldList.forEach((field, i) => {
    const fieldSelected = dom.fieldPath.value === field.path;
    const row = dom.fieldList.insertRow();
    Utils.addCheckboxToRow(row, i, field.active, false, toggleFieldActiveEventHandler);
    row.insertCell(-1).innerHTML = field.path;
    row.insertCell(-1).innerHTML = field['label'] ? field.label : null;
    if (fieldSelected) {
      theFieldIndex = i;
      row.classList.add('table-active');
      dom.fieldLabel.value = field.label;
    }
  });
  if (theFieldIndex < 0) {
    dom.fieldPath.value = null;
    dom.fieldLabel.value = null;
  }
}

/**
 * Event handler for field active check box
 * @param {Object} evt checkbox click event
 */
function toggleFieldActiveEventHandler(evt) {
  Environments.current().fieldList[evt.target.id].active = evt.target.checked;
  updateFieldList();
}

