/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import * as Utils from '../utils.js';
import {TabHandler} from '../utils/tabHandler.js';
import servicesLoggingHTML from './servicesLogging.html';

let dom = {
  buttonLoadAllLogLevels: null,
  divLoggers: null,
  templateLogger: null,
  tabOperations: null,
  collapseOperations: null,
};

document.getElementById('servicesLoggingHTML').innerHTML = servicesLoggingHTML;

export async function ready() {
  Utils.getAllElementsById(dom);
  TabHandler(dom.tabOperations, dom.collapseOperations, loadAllLogLevels, 'disableOperations');

  dom.buttonLoadAllLogLevels.onclick = loadAllLogLevels;
}

function onUpdateLoggingClick(service, logConfig) {
  API.callDittoREST('PUT', '/devops/logging/' + service, logConfig, null, false, true)
      .then(() => {
        loadAllLogLevels();
      });
}

function loadAllLogLevels() {
  API.callDittoREST('GET', '/devops/logging', null, null, false, true)
      .then((result) => createLoggerView(result))
      .catch((error) => {
        dom.divLoggers.innerHTML = '';
      });
}

function createLoggerView(allLogLevels) {
  dom.divLoggers.innerHTML = '';

  type LogLevel = {
    loggerConfigs?: object[]
  }

  Object.keys(allLogLevels).sort().forEach((service, i) => {
    addHeading(service, i);
    (Object.values(allLogLevels[service]) as LogLevel[])[0].loggerConfigs.forEach((logConfig) => {
      addLoggerRowForConfig(service, logConfig);
    });
    addLoggerRowForNew(service);
  });

  function addHeading(service, i) {
    let heading = document.createElement('h6');
    heading.innerText = service;
    if (i > 0) {
      heading.classList.add('mt-3');
    }
    dom.divLoggers.append(heading);
  }

  function addLoggerRowForConfig(service, logConfig) {
    let row = document.createElement('div');
    row.attachShadow({mode: 'open'});
    row.shadowRoot.append(dom.templateLogger.content.cloneNode(true));
    (row.shadowRoot.getElementById('inputLogger') as HTMLInputElement).value = logConfig.logger;
    row.shadowRoot.getElementById(logConfig.level).setAttribute('checked', '');
    Array.from(row.shadowRoot.querySelectorAll('.btn-check')).forEach((btn) => {
      btn.addEventListener('click', (event) => onUpdateLoggingClick(service, {
        logger: logConfig.logger,
        level: (event.target as Element).id,
      }));
    });
    dom.divLoggers.append(row);
  }

  function addLoggerRowForNew(service) {
    let newLoggerRow = document.createElement('div');
    newLoggerRow.attachShadow({mode: 'open'});
    newLoggerRow.shadowRoot.append(dom.templateLogger.content.cloneNode(true));
    let inputLoggerElement = newLoggerRow.shadowRoot.getElementById('inputLogger') as HTMLInputElement;
    inputLoggerElement.disabled = false;
    inputLoggerElement.placeholder = 'Add new logger name and choose log level';
    inputLoggerElement.addEventListener('change', (event) => {
      (event.target as HTMLElement).classList.remove('is-invalid');
    });
    Array.from(newLoggerRow.shadowRoot.querySelectorAll('.btn-check')).forEach((btn) => {
      btn.addEventListener('click', (event) => {
        Utils.assert((inputLoggerElement.value && inputLoggerElement.value.trim() !== '') ,
            'Logger name must not be empty',
            inputLoggerElement);
        onUpdateLoggingClick(service, {
          logger: inputLoggerElement.value,
          level: (event.target as Element).id,
        });
      });
    });
    dom.divLoggers.append(newLoggerRow);
  }
}
