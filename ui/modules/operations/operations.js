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

/* eslint-disable arrow-parens */
/* eslint-disable prefer-const */
/* eslint-disable require-jsdoc */
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';

let opsLoggingEditor;

let allLogLevels;

let dom = {
  buttonLoadAllLogLevels: null,
  tableValidationOpsLogging: null,
  tbodyOpsLogging: null,
  crudOpsLoggingJson: null,
  tabOperations: null,
  collapseOperations: null,
};

export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  Utils.addValidatorToTable(dom.tbodyOpsLogging, dom.tableValidationOpsLogging);

  dom.tabOperations.onclick = onTabActivated;

  opsLoggingEditor = Utils.createAceEditor('opsLoggingEditor', 'ace/mode/json', true);

  dom.buttonLoadAllLogLevels.onclick = loadAllLogLevels;
  dom.tbodyOpsLogging.addEventListener('click', onLoggingTableClick);

  dom.crudOpsLoggingJson.editDisabled = true;
  dom.crudOpsLoggingJson.createDisabled = true;
  dom.crudOpsLoggingJson.deleteDisabled = true;

  dom.crudOpsLoggingJson.addEventListener('onEditToggle', onEditToggle);
  dom.crudOpsLoggingJson.addEventListener('onUpdateClick', onUpdateLoggingClick);
}

function onLoggingTableClick(event) {
  if (event.target && event.target.tagName === 'TD') {
    if (isSelected(event.target.parentNode.id, event.target.parentNode.logLevel)) {
      updateLogEditor();
    } else {
      updateLogEditor(event.target.parentNode.id, event.target.parentNode.logLevel);
    }
  }
}

function updateLogEditor(logService, logLevel) {
  if (logService) {
    dom.crudOpsLoggingJson.idValue = logService;
    dom.crudOpsLoggingJson.editDisabled = false;
    opsLoggingEditor.setValue(JSON.stringify(logLevel, null, 2), -1);
  } else {
    dom.crudOpsLoggingJson.idValue = null;
    dom.crudOpsLoggingJson.editDisabled = true;
    opsLoggingEditor.setValue('');
  }
}

let undoLogLevel;

function onEditToggle(event) {
  opsLoggingEditor.setReadOnly(!event.detail.isEditing);
  opsLoggingEditor.renderer.setShowGutter(event.detail.isEditing);
  if (event.detail.isEditing) {
    undoLogLevel = JSON.parse(opsLoggingEditor.getValue());
  } else if (event.detail.isCancel) {
    updateLogEditor(dom.crudOpsLoggingJson.idValue, undoLogLevel);
  }
}

function onUpdateLoggingClick() {
  API.callDittoREST('PUT', '/devops/logging/' + dom.crudOpsLoggingJson.idValue,
      JSON.parse(opsLoggingEditor.getValue()), null, false, true)
      .then(() => {
        loadAllLogLevels();
        dom.crudOpsLoggingJson.toggleEdit();
      });
}

let viewDirty = false;

function onTabActivated() {
  if (viewDirty) {
    loadAllLogLevels();
    viewDirty = false;
  }
}

function onEnvironmentChanged() {
  if (dom.collapseOperations.classList.contains('show')) {
    dom.tbodyOpsLogging.innerHTML = '';
    updateLogEditor();
    loadAllLogLevels();
  } else {
    viewDirty = true;
  }
}

function loadAllLogLevels() {
  API.callDittoREST('GET', '/devops/logging', null, null, false, true)
      .then((result) => {
        allLogLevels = result;
        dom.tbodyOpsLogging.innerHTML = '';
        Object.keys(allLogLevels).forEach((service) => {
          Object.values(allLogLevels[service])[0].loggerConfigs.forEach((logConfig) => {
            const row = Utils.addTableRow(dom.tbodyOpsLogging, service,
                isSelected(service, logConfig),
                false,
                logConfig.level,
                logConfig.logger);
            row.logLevel = logConfig;
          });
        });
      })
      .catch((error) => {
      });
}

function isSelected(logService, logConfig) {
  const selectedService = dom.crudOpsLoggingJson.idValue;
  const selectedLogLevel = opsLoggingEditor.getValue() ? JSON.parse(opsLoggingEditor.getValue()) : null;
  return (selectedLogLevel && logService === selectedService && logConfig.level === selectedLogLevel.level && logConfig.logger === selectedLogLevel.logger)
}

