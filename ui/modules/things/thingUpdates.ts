/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import { FilterType, Term } from '../utils/basicFilters.js';
import { TableFilter } from '../utils/tableFilter.js';
import thingUpdatesHTML from './thingUpdates.html';
import * as Things from './things.js';
import * as ThingsSSE from './thingsSSE.js';

enum ThingUpdateContent {
  ONLY_CONTEXT = 'ONLY_CONTEXT',
  ONLY_CONTEXT_WITH_METADATA = 'ONLY_CONTEXT_WITH_METADATA',
  FULL_THING = 'FULL_THING',
  FULL_THING_WITH_CONTEXT = 'FULL_THING_WITH_CONTEXT'
}

type DomElements = {
  badgeThingUpdateCount: HTMLSpanElement,
  buttonResetThingUpdates: HTMLButtonElement,
  thingUpdatesModeLive: HTMLInputElement,
  thingUpdatesModeHistorical: HTMLInputElement,
  historicalUpdateControls: HTMLDivElement,
  historicalRangeModeRevision: HTMLInputElement,
  historicalRangeModeTimestamp: HTMLInputElement,
  historicalRevisionRange: HTMLDivElement,
  historicalTimestampRange: HTMLDivElement,
  historicalFromRevision: HTMLInputElement,
  historicalFromRevisionSlider: HTMLInputElement,
  historicalToRevision: HTMLInputElement,
  historicalToRevisionSlider: HTMLInputElement,
  buttonFromOldest: HTMLButtonElement,
  buttonToMostRecent: HTMLButtonElement,
  historicalFromTimestamp: HTMLInputElement,
  historicalToTimestamp: HTMLInputElement,
  buttonFromOldestTimestamp: HTMLButtonElement,
  buttonToMostRecentTimestamp: HTMLButtonElement,
  inputHistoricalRqlFilter: HTMLInputElement,
  buttonFetchHistorical: HTMLButtonElement,
  buttonStopHistorical: HTMLButtonElement,
  selectThingUpdateContent: HTMLSelectElement,
  tbodyThingUpdates: HTMLTableElement,
  tableFilterThingUpdates: TableFilter,
};

let dom: DomElements = {
  badgeThingUpdateCount: null,
  buttonResetThingUpdates: null,
  thingUpdatesModeLive: null,
  thingUpdatesModeHistorical: null,
  historicalUpdateControls: null,
  historicalRangeModeRevision: null,
  historicalRangeModeTimestamp: null,
  historicalRevisionRange: null,
  historicalTimestampRange: null,
  historicalFromRevision: null,
  historicalFromRevisionSlider: null,
  historicalToRevision: null,
  historicalToRevisionSlider: null,
  buttonFromOldest: null,
  buttonToMostRecent: null,
  historicalFromTimestamp: null,
  historicalToTimestamp: null,
  buttonFromOldestTimestamp: null,
  buttonToMostRecentTimestamp: null,
  inputHistoricalRqlFilter: null,
  buttonFetchHistorical: null,
  buttonStopHistorical: null,
  selectThingUpdateContent: null,
  tbodyThingUpdates: null,
  tableFilterThingUpdates: null,
};

let messages = [];
let filteredMessages = [];
let selectedRow;
let messageDetail;
let currentThingId: string | null = null;
let thingUpdateContent = ThingUpdateContent.ONLY_CONTEXT;
let historicalEventSource = null;
let isHistoricalMode = false;
let lastReceivedRevision: number | null = null;
let requestedToRevision: number | null = null;
let probedMinRevision = 1;
let probedMaxRevision = 1;
let probedOldestTimestamp: string | null = null;
let probedNewestTimestamp: string | null = null;

document.getElementById('thingUpdatesHTML').innerHTML = thingUpdatesHTML;

export function ready() {
  ThingsSSE.addChangeListener(onLiveMessage);
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  messageDetail = Utils.createAceEditor('thingUpdateDetail', 'ace/mode/json', true);

  dom.buttonResetThingUpdates.onclick = onResetClick;
  dom.selectThingUpdateContent.onchange = onSelectContentChange;
  dom.tbodyThingUpdates.addEventListener('click', onTableClick);
  dom.tableFilterThingUpdates.addEventListener('filterChange', onFilterChange);
  dom.tableFilterThingUpdates.filterOptions = createFilterOptions();

  dom.thingUpdatesModeLive.onchange = onModeChange;
  dom.thingUpdatesModeHistorical.onchange = onModeChange;
  dom.historicalRangeModeRevision.onchange = onRangeModeChange;
  dom.historicalRangeModeTimestamp.onchange = onRangeModeChange;
  dom.buttonFetchHistorical.onclick = onFetchHistorical;
  dom.buttonStopHistorical.onclick = onStopHistorical;

  dom.historicalFromRevisionSlider.oninput = () => {
    dom.historicalFromRevision.value = dom.historicalFromRevisionSlider.value;
  };
  dom.historicalFromRevision.onchange = () => {
    dom.historicalFromRevisionSlider.value = dom.historicalFromRevision.value;
  };
  dom.historicalToRevisionSlider.oninput = () => {
    dom.historicalToRevision.value = dom.historicalToRevisionSlider.value;
  };
  dom.historicalToRevision.onchange = () => {
    dom.historicalToRevisionSlider.value = dom.historicalToRevision.value;
  };

  dom.buttonFromOldest.onclick = () => {
    dom.historicalFromRevision.value = String(probedMinRevision);
    dom.historicalFromRevisionSlider.value = String(probedMinRevision);
  };
  dom.buttonToMostRecent.onclick = () => {
    dom.historicalToRevision.value = String(probedMaxRevision);
    dom.historicalToRevisionSlider.value = String(probedMaxRevision);
  };
  dom.buttonFromOldestTimestamp.onclick = () => {
    if (probedOldestTimestamp) {
      dom.historicalFromTimestamp.value = probedOldestTimestamp;
    }
  };
  dom.buttonToMostRecentTimestamp.onclick = () => {
    if (probedNewestTimestamp) {
      dom.historicalToTimestamp.value = probedNewestTimestamp;
    }
  };
}

function onTableClick(event) {
  selectedRow = event.target.parentNode.rowIndex - 1;
  updateDetailView();
  messageDetail.session.getUndoManager().reset();
}

function updateDetailView() {
  if (selectedRow === undefined || selectedRow < 0 || selectedRow >= filteredMessages.length) {
    return;
  }
  const msg = filteredMessages[selectedRow];
  switch (thingUpdateContent) {
    case ThingUpdateContent.FULL_THING_WITH_CONTEXT:
      messageDetail.setValue(Utils.stringifyPretty(msg), -1);
      break;
    case ThingUpdateContent.FULL_THING: {
      const entries = Object.entries(msg).filter(([key]) => !key.startsWith('_'));
      messageDetail.setValue(Utils.stringifyPretty(Object.fromEntries(entries)), -1);
      break;
    }
    case ThingUpdateContent.ONLY_CONTEXT_WITH_METADATA: {
      const entries = Object.entries(msg).filter(([key]) => key.startsWith('_'));
      messageDetail.setValue(Utils.stringifyPretty(Object.fromEntries(entries)), -1);
      break;
    }
    case ThingUpdateContent.ONLY_CONTEXT: {
      const entries = Object.entries(msg)
        .filter(([key]) => key.startsWith('_'))
        .filter(([key]) => key !== '_metadata');
      messageDetail.setValue(Utils.stringifyPretty(Object.fromEntries(entries)), -1);
      break;
    }
    default:
      messageDetail.setValue(Utils.stringifyPretty(msg), -1);
  }
}

function onResetClick() {
  messages = [];
  filteredMessages = [];
  dom.badgeThingUpdateCount.textContent = '';
  dom.tbodyThingUpdates.textContent = '';
  messageDetail.setValue('');
}

function onSelectContentChange() {
  thingUpdateContent = dom.selectThingUpdateContent.value as ThingUpdateContent;
  updateDetailView();
}

function onLiveMessage(messageData) {
  if (isHistoricalMode) return;

  messages.push(messageData);

  const filteredMessage = dom.tableFilterThingUpdates.filterItems([messageData]);
  if (filteredMessage.length > 0) {
    filteredMessages.push(filteredMessage[0]);
    addTableRow(filteredMessage[0]);
  }

  Utils.updateCounterBadge(dom.badgeThingUpdateCount, messages, filteredMessages);
}

function addTableRow(messageData: any) {
  let action = 'unknown';
  if (messageData['_context']?.topic) {
    action = messageData['_context'].topic.substring(messageData['_context'].topic.lastIndexOf('/') + 1);
  }

  Utils.addTableRow(
    dom.tbodyThingUpdates,
    messageData._revision, false, null,
    action,
    messageData['_context']?.path || '',
    getColumnValues(action).join('\n'),
    Utils.formatDate(messageData._modified, true)
  );

  function getColumnValues(action: string): string[] {
    if (action === 'deleted') {
      return [];
    } else if (messageData['_context']?.value) {
      return [
        ...messageData['_context'].value.features ? Object.keys(messageData['_context'].value.features) : [],
        ...messageData['_context'].value.attributes ? Object.keys(messageData['_context'].value.attributes) : [],
      ];
    } else {
      return [
        ...messageData['features'] ? Object.keys(messageData.features) : [],
        ...messageData['attributes'] ? Object.keys(messageData.attributes) : [],
      ];
    }
  }
}

function onThingChanged(thing) {
  if (!thing || thing.thingId !== currentThingId) {
    currentThingId = thing ? thing.thingId : null;
    onResetClick();
    onStopHistorical();
    dom.tableFilterThingUpdates.filterOptions = createFilterOptions(thing);

    if (thing) {
      const rev = thing._revision || 1;
      probedMinRevision = 1;
      probedMaxRevision = rev;
      probedOldestTimestamp = thing._created ? toDatetimeLocalValue(thing._created) : null;
      probedNewestTimestamp = thing._modified ? toDatetimeLocalValue(thing._modified) : null;

      updateRevisionSliderRanges();
      dom.historicalFromRevision.value = '1';
      dom.historicalFromRevisionSlider.value = '1';
      dom.historicalToRevision.value = String(rev);
      dom.historicalToRevisionSlider.value = String(rev);
      if (probedOldestTimestamp) {
        dom.historicalFromTimestamp.value = probedOldestTimestamp;
      }
      if (probedNewestTimestamp) {
        dom.historicalToTimestamp.value = probedNewestTimestamp;
      }
      probeOldestRevision(thing.thingId);
    }
  }
}

function createFilterOptions(thing?: any): [Term?] {
  let result: [Term?] = [];
  ['created', 'modified', 'merged', 'deleted'].forEach((e) => result.push(
    new Term(FilterType.PROP_LIKE, `/${e}`, '_context.topic', 'Action')));
  if (thing) {
    ['features', 'attributes'].forEach((part) => {
      if (thing[part]) {
        Object.keys(thing[part]).forEach((name) => {
          result.push(new Term(FilterType.PROP_LIKE, `/${part}/${name}`, '_context.path', 'Path'));
        });
      }
    });
  }
  return result;
}

function onFilterChange(event: CustomEvent) {
  dom.tbodyThingUpdates.textContent = '';
  filteredMessages = dom.tableFilterThingUpdates.filterItems(messages);
  filteredMessages.forEach((entry) => addTableRow(entry));
  Utils.updateCounterBadge(dom.badgeThingUpdateCount, messages, filteredMessages);
}

function onModeChange() {
  isHistoricalMode = dom.thingUpdatesModeHistorical.checked;
  dom.historicalUpdateControls.hidden = !isHistoricalMode;
  onResetClick();
  if (!isHistoricalMode) {
    onStopHistorical();
  }
}

/**
 * Switches to Historical mode with pre-filled range and auto-fetches.
 * Called from the Time Travel section to pass the current position.
 */
export function showHistoricalFromRevision(fromRevision: number, toRevision: number) {
  activateHistoricalMode();
  dom.historicalRangeModeRevision.checked = true;
  dom.historicalRangeModeTimestamp.checked = false;
  onRangeModeChange();
  dom.historicalFromRevision.value = String(fromRevision);
  dom.historicalFromRevisionSlider.value = String(fromRevision);
  dom.historicalToRevision.value = String(toRevision);
  dom.historicalToRevisionSlider.value = String(toRevision);
  onFetchHistorical();
}

export function showHistoricalFromTimestamp(fromTimestamp: string, toTimestamp: string) {
  activateHistoricalMode();
  dom.historicalRangeModeRevision.checked = false;
  dom.historicalRangeModeTimestamp.checked = true;
  onRangeModeChange();
  dom.historicalFromTimestamp.value = fromTimestamp;
  dom.historicalToTimestamp.value = toTimestamp;
  onFetchHistorical();
}

function activateHistoricalMode() {
  // Expand the Thing Updates section if collapsed
  const collapseEl = document.getElementById('collapseThingUpdates');
  if (collapseEl && !collapseEl.classList.contains('show')) {
    collapseEl.classList.add('show');
  }
  // Switch to Historical mode
  dom.thingUpdatesModeHistorical.checked = true;
  dom.thingUpdatesModeLive.checked = false;
  isHistoricalMode = true;
  dom.historicalUpdateControls.hidden = false;
  onResetClick();
  onStopHistorical();
}

function onRangeModeChange() {
  const byRevision = dom.historicalRangeModeRevision.checked;
  dom.historicalRevisionRange.hidden = !byRevision;
  dom.historicalTimestampRange.hidden = byRevision;
}

function onFetchHistorical() {
  if (!currentThingId) return;

  onStopHistorical();
  onResetClick();
  lastReceivedRevision = null;
  requestedToRevision = null;

  const fields = 'fields=thingId,policyId,definition,attributes,features,_revision,_created,_modified,_metadata,_context/topic,_context/path,_context/value' +
    '&extraFields=thingId,policyId,definition,attributes,features,_revision,_created,_modified,_metadata';

  let rangeParams = '';
  if (dom.historicalRangeModeRevision.checked) {
    const from = dom.historicalFromRevision.value;
    const to = dom.historicalToRevision.value;
    requestedToRevision = parseInt(to, 10);
    rangeParams = `from-historical-revision=${from}&to-historical-revision=${to}`;
  } else {
    const from = dom.historicalFromTimestamp.value;
    const to = dom.historicalToTimestamp.value;
    if (from) {
      rangeParams += `from-historical-timestamp=${new Date(from).toISOString()}`;
    }
    if (to) {
      rangeParams += `${rangeParams ? '&' : ''}to-historical-timestamp=${new Date(to).toISOString()}`;
    }
  }

  const rqlFilter = dom.inputHistoricalRqlFilter.value.trim();
  const filterParam = rqlFilter ? `&filter=${encodeURIComponent(rqlFilter)}` : '';

  const urlParams = `${rangeParams}&${fields}${filterParam}`;

  try {
    historicalEventSource = API.getHistoricalEventSource(currentThingId, urlParams);
    historicalEventSource.onmessage = onHistoricalMessage;
    historicalEventSource.onerror = onHistoricalError;

    dom.buttonFetchHistorical.hidden = true;
    dom.buttonStopHistorical.hidden = false;
  } catch (err) {
    Utils.showError(err);
  }
}

function onHistoricalMessage(event) {
  if (event.data && event.data !== '') {
    const messageData = JSON.parse(event.data);
    const revision = messageData._revision;

    // Stop the stream when the historical range is exhausted:
    // - duplicate revision means the backend looped back to live events
    // - revision exceeding the requested "to" means we've passed the range
    if (revision != null) {
      if (revision === lastReceivedRevision) {
        onStopHistorical();
        return;
      }
      if (requestedToRevision != null && revision > requestedToRevision) {
        onStopHistorical();
        return;
      }
    }
    lastReceivedRevision = revision;

    messages.push(messageData);

    const filteredMessage = dom.tableFilterThingUpdates.filterItems([messageData]);
    if (filteredMessage.length > 0) {
      filteredMessages.push(filteredMessage[0]);
      addTableRow(filteredMessage[0]);
    }

    Utils.updateCounterBadge(dom.badgeThingUpdateCount, messages, filteredMessages);
  }
}

function onHistoricalError(event) {
  // When the historical SSE stream ends, EventSourcePolyfill treats the
  // closed connection as a transient error and auto-reconnects, restarting
  // the stream. Always stop on error to prevent this loop.
  onStopHistorical();
}

function onStopHistorical() {
  if (historicalEventSource) {
    historicalEventSource.close();
    historicalEventSource = null;
  }
  dom.buttonFetchHistorical.hidden = false;
  dom.buttonStopHistorical.hidden = true;
}

/**
 * Probes the oldest available revision by opening a historical SSE stream
 * starting at revision 1. The backend skips cleaned-up revisions, so the
 * first event contains the oldest available revision and timestamp.
 */
function probeOldestRevision(thingId: string) {
  const urlParams = 'from-historical-revision=1&fields=_revision,_modified';
  let probeSource;
  try {
    probeSource = API.getHistoricalEventSource(thingId, urlParams);
  } catch (err) {
    return;
  }

  probeSource.onmessage = (event) => {
    probeSource.close();
    if (event.data && event.data !== '' && thingId === currentThingId) {
      try {
        const data = JSON.parse(event.data);
        if (data._revision) {
          probedMinRevision = data._revision;
          updateRevisionSliderRanges();
          dom.historicalFromRevision.value = String(probedMinRevision);
          dom.historicalFromRevisionSlider.value = String(probedMinRevision);
        }
        if (data._modified) {
          probedOldestTimestamp = toDatetimeLocalValue(data._modified);
          dom.historicalFromTimestamp.value = probedOldestTimestamp;
          dom.historicalFromTimestamp.min = probedOldestTimestamp;
        }
      } catch (e) {
        // ignore parse errors from probe
      }
    }
  };

  probeSource.onerror = () => {
    probeSource.close();
  };
}

function updateRevisionSliderRanges() {
  const min = String(probedMinRevision);
  const max = String(probedMaxRevision);
  dom.historicalFromRevision.min = min;
  dom.historicalFromRevisionSlider.min = min;
  dom.historicalFromRevisionSlider.max = max;
  dom.historicalToRevision.min = min;
  dom.historicalToRevision.max = max;
  dom.historicalToRevisionSlider.min = min;
  dom.historicalToRevisionSlider.max = max;
}

function toDatetimeLocalValue(isoString: string): string {
  if (!isoString) return '';
  return isoString.substring(0, 19);
}
