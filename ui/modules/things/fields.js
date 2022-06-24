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

import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';

let theFieldIndex = -1;

const dom = {
  fieldPath: null,
  fieldList: null,
};

/**
 * Create a Ditto search query parameter from the active fields
 * @return {String} The fields query parameter for Ditto search
 */
export function getQueryParameter() {
  const fields = Environments.current().fieldList.filter((f) => f.active).map((f) => f.path);
  return 'fields=thingId' + (fields !== '' ? ',' + fields : '');
};

/**
 * Set the fieldpath value
 * @param {String} fieldPath new value for the fieldpath
 */
export function setFieldPath(fieldPath) {
  dom.fieldPath.value = fieldPath;
}

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.addTab(
      document.getElementById('thingsTabsItems'),
      document.getElementById('thingsTabsContent'),
      'Fields',
      await( await fetch('modules/things/fields.html')).text(),
  );

  Utils.getAllElementsById(dom);

  dom.fieldList.addEventListener('click', (event) => {
    if (event.target && event.target.tagName === 'TD') {
      if (theFieldIndex === event.target.parentNode.rowIndex) {
        theFieldIndex = -1;
        dom.fieldPath.value = null;
      } else {
        theFieldIndex = event.target.parentNode.rowIndex;
        dom.fieldPath.value = Environments.current().fieldList[theFieldIndex].path;
      }
    }
  });

  document.getElementById('fieldUpdate').onclick = () => {
    if (!dom.fieldPath.value) {
      return;
    };
    const fieldExists = Environments.current().fieldList.map((field) => field.path).includes(dom.fieldPath.value);
    if (theFieldIndex < 0 && !fieldExists) {
      Environments.current().fieldList.push({
        active: true,
        path: dom.fieldPath.value,
      });
      theFieldIndex = Environments.current().fieldList.length - 1;
    } else {
      Environments.current().fieldList[theFieldIndex].path = dom.fieldPath.value;
    }
    Environments.environmentsJsonChanged();
  };

  document.getElementById('fieldDelete').onclick = () => {
    if (theFieldIndex < 0) {
      return;
    }
    Environments.current().fieldList.splice(theFieldIndex, 1);
    Environments.environmentsJsonChanged();
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
    Environments.environmentsJsonChanged();
  };

  document.getElementById('fieldDown').onclick = () => {
    if (theFieldIndex < 0 || theFieldIndex === Environments.current().fieldList.length - 1) {
      return;
    }
    const movedItem = Environments.current().fieldList[theFieldIndex];
    Environments.current().fieldList.splice(theFieldIndex, 1);
    theFieldIndex++;
    Environments.current().fieldList.splice(theFieldIndex, 0, movedItem);
    Environments.environmentsJsonChanged();
  };
};

/**
 * Callback on environment change. Initializes all UI components for fields
 */
function onEnvironmentChanged() {
  if (!Environments.current()['fieldList']) {
    Environments.current().fieldList = [];
  };
  updateFieldList();
};

/**
 * (Re-)Initializes the fieldlist in the UI
 */
function updateFieldList() {
  dom.fieldList.innerHTML = '';
  theFieldIndex = -1;
  Environments.current().fieldList.forEach((field, i) => {
    const fieldSelected = dom.fieldPath.value === field.path;
    const row = dom.fieldList.insertRow();
    Utils.addCheckboxToRow(row, i, field.active, toggleFieldActiveEventHandler);
    row.insertCell(-1).innerHTML = field.path;
    Utils.addClipboardCopyToRow(row);
    if (fieldSelected) {
      theFieldIndex = i;
      row.classList.add('table-active');
    }
  });
  if (theFieldIndex < 0) {
    dom.fieldPath.value = null;
  }
};

/**
 * Event handler for field active check box
 * @param {Object} evt checkbox click event
 */
function toggleFieldActiveEventHandler(evt) {
  Environments.current().fieldList[evt.target.id].active = evt.target.checked;
  Environments.environmentsJsonChanged();
};

