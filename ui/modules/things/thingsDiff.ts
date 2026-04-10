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

import * as ace from 'ace-builds/src-noconflict/ace';
import AceDiff from 'ace-diff';
import * as API from '../api.js';
import * as Utils from '../utils.js';
import thingsDiffHTML from './thingsDiff.html';
import * as Things from './things.js';

const HISTORY_FIELDS = 'fields=thingId%2CpolicyId%2Cdefinition%2Cattributes%2Cfeatures%2C_created%2C_modified%2C_revision%2C_metadata';

const SYSTEM_FIELDS = ['_revision', '_created', '_modified', '_context', '_metadata'];

export function ThingsDiff(targetTab) {
  let diffInstance: AceDiff | null = null;
  let tabLink: HTMLAnchorElement | null = null;
  let viewDirty = false;
  let currentThingId: string | null = null;
  let currentMinRevision = 1;
  let currentMaxRevision = 1;
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;

  const dom = {
    diffLeftRevision: null as HTMLInputElement,
    diffLeftSlider: null as HTMLInputElement,
    diffLeftMin: null as HTMLSpanElement,
    diffLeftMax: null as HTMLSpanElement,
    diffLeftPrev: null as HTMLButtonElement,
    diffLeftNext: null as HTMLButtonElement,
    diffLeftCurrent: null as HTMLButtonElement,
    diffLeftTimeTravel: null as HTMLButtonElement,
    diffRightRevision: null as HTMLInputElement,
    diffRightSlider: null as HTMLInputElement,
    diffRightMin: null as HTMLSpanElement,
    diffRightMax: null as HTMLSpanElement,
    diffRightPrev: null as HTMLButtonElement,
    diffRightNext: null as HTMLButtonElement,
    diffRightCurrent: null as HTMLButtonElement,
    diffContainer: null as HTMLDivElement,
  };

  const ready = async () => {
    const tabId = Utils.addTab(
      document.getElementById(targetTab.itemsId),
      document.getElementById(targetTab.contentId),
      '<i class="bi bi-file-diff"></i> Diff',
      thingsDiffHTML,
      'Compare two revisions of this Thing side-by-side'
    );

    tabLink = document.querySelector(`a[data-bs-target="#${tabId}"]`);
    tabLink.addEventListener('shown.bs.tab', onTabActivated);

    Utils.getAllElementsById(dom);

    // Left revision controls
    dom.diffLeftSlider.oninput = () => {
      dom.diffLeftRevision.value = dom.diffLeftSlider.value;
      debouncedFetchAndDiff();
    };
    dom.diffLeftRevision.onchange = () => {
      dom.diffLeftSlider.value = dom.diffLeftRevision.value;
      debouncedFetchAndDiff();
    };
    dom.diffLeftPrev.onclick = () => stepRevision('left', -1);
    dom.diffLeftNext.onclick = () => stepRevision('left', 1);
    dom.diffLeftCurrent.onclick = () => setRevision('left', currentMaxRevision);
    dom.diffLeftTimeTravel.onclick = () => {
      const slider = document.getElementById('historyRevisionSlider') as HTMLInputElement;
      if (slider) {
        setRevision('left', parseInt(slider.value, 10));
      }
    };

    // Right revision controls
    dom.diffRightSlider.oninput = () => {
      dom.diffRightRevision.value = dom.diffRightSlider.value;
      debouncedFetchAndDiff();
    };
    dom.diffRightRevision.onchange = () => {
      dom.diffRightSlider.value = dom.diffRightRevision.value;
      debouncedFetchAndDiff();
    };
    dom.diffRightPrev.onclick = () => stepRevision('right', -1);
    dom.diffRightNext.onclick = () => stepRevision('right', 1);
    dom.diffRightCurrent.onclick = () => setRevision('right', currentMaxRevision);

  };

  const onThingChanged = (thing, isNewThingId: boolean) => {
    if (!thing) {
      currentThingId = null;
      currentMaxRevision = 1;
      if (diffInstance) {
        diffInstance.getEditors().left.setValue('');
        diffInstance.getEditors().right.setValue('');
        diffInstance.diff();
      }
      return;
    }

    // When in history mode and it's the same thing, don't reset
    if (Things.historyModeActive && !isNewThingId) {
      return;
    }

    currentThingId = thing.thingId;
    currentMinRevision = 1;
    currentMaxRevision = thing._revision || 1;
    updateSliderRanges();

    const leftRev = Math.max(currentMinRevision, currentMaxRevision - 1);
    setRevisionSilent('left', leftRev);
    setRevisionSilent('right', currentMaxRevision);

    probeOldestRevision(thing.thingId);

    if (tabLink && tabLink.classList.contains('active')) {
      fetchAndDiff();
    } else {
      viewDirty = true;
    }
  };

  const onHistoryModeChanged = (active: boolean) => {
    dom.diffLeftTimeTravel.hidden = !active;
  };

  return {
    ready,
    onThingChanged,
    onHistoryModeChanged,
  };

  function onTabActivated() {
    if (viewDirty) {
      fetchAndDiff();
      viewDirty = false;
    }
    // Resize editors to fit the container
    if (diffInstance) {
      const editors = diffInstance.getEditors();
      editors.left.resize();
      editors.right.resize();
    }
  }

  function setRevision(side: 'left' | 'right', value: number) {
    setRevisionSilent(side, value);
    debouncedFetchAndDiff();
  }

  function setRevisionSilent(side: 'left' | 'right', value: number) {
    const input = side === 'left' ? dom.diffLeftRevision : dom.diffRightRevision;
    const slider = side === 'left' ? dom.diffLeftSlider : dom.diffRightSlider;
    input.value = String(value);
    slider.value = String(value);
  }

  function stepRevision(side: 'left' | 'right', delta: number) {
    const input = side === 'left' ? dom.diffLeftRevision : dom.diffRightRevision;
    let val = parseInt(input.value, 10) + delta;
    val = Math.max(currentMinRevision, Math.min(val, currentMaxRevision));
    setRevision(side, val);
  }

  function updateSliderRanges() {
    const min = String(currentMinRevision);
    const max = String(currentMaxRevision);
    dom.diffLeftSlider.min = min;
    dom.diffLeftSlider.max = max;
    dom.diffLeftRevision.min = min;
    dom.diffLeftRevision.max = max;
    dom.diffLeftMin.textContent = min;
    dom.diffLeftMax.textContent = max;
    dom.diffRightSlider.min = min;
    dom.diffRightSlider.max = max;
    dom.diffRightRevision.min = min;
    dom.diffRightRevision.max = max;
    dom.diffRightMin.textContent = min;
    dom.diffRightMax.textContent = max;
  }

  function probeOldestRevision(thingId: string) {
    const urlParams = 'from-historical-revision=1&fields=_revision';
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
            currentMinRevision = data._revision;
            updateSliderRanges();
            // Clamp left slider if below available minimum
            const leftVal = parseInt(dom.diffLeftRevision.value, 10);
            if (leftVal < currentMinRevision) {
              setRevisionSilent('left', currentMinRevision);
            }
          }
        } catch (e) {
          // ignore
        }
      }
    };
    probeSource.onerror = () => probeSource.close();
  }

  function debouncedFetchAndDiff() {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }
    debounceTimer = setTimeout(() => {
      fetchAndDiff();
      debounceTimer = null;
    }, 300);
  }

  function fetchAndDiff() {
    if (!currentThingId) return;

    const leftRev = parseInt(dom.diffLeftRevision.value, 10);
    const rightRev = parseInt(dom.diffRightRevision.value, 10);

    if (isNaN(leftRev) || isNaN(rightRev) || leftRev < 1 || rightRev < 1) return;

    Promise.all([
      fetchRevision(currentThingId, leftRev),
      fetchRevision(currentThingId, rightRev),
    ]).then(([leftJson, rightJson]) => {
      const leftStr = Utils.stringifyPretty(leftJson);
      const rightStr = Utils.stringifyPretty(rightJson);
      createOrUpdateDiff(leftStr, rightStr);
    }).catch((err) => {
      console.error('Failed to fetch revisions for diff', err);
    });
  }

  function fetchRevision(thingId: string, revision: number): Promise<object> {
    return API.callDittoREST('GET',
      `/things/${thingId}?${HISTORY_FIELDS}`,
      null,
      {'at-historical-revision': String(revision)}
    ).then((thing) => {
      if (thing) {
        const copy = JSON.parse(JSON.stringify(thing));
        SYSTEM_FIELDS.forEach((f) => delete copy[f]);
        return copy;
      }
      return {};
    });
  }

  function createOrUpdateDiff(leftContent: string, rightContent: string) {
    if (diffInstance) {
      const editors = diffInstance.getEditors();
      editors.left.setValue(leftContent, -1);
      editors.right.setValue(rightContent, -1);
      diffInstance.diff();
      renderChangeOverview(diffInstance.diffs);
    } else {
      diffInstance = new AceDiff({
        ace: ace as any,
        element: dom.diffContainer,
        mode: 'ace/mode/json',
        theme: null,
        diffGranularity: 'specific',
        lockScrolling: true,
        showDiffs: true,
        showConnectors: true,
        charDiffs: true,
        maxDiffs: 5000,
        left: {
          content: leftContent,
          editable: false,
          copyLinkEnabled: false,
        },
        right: {
          content: rightContent,
          editable: false,
          copyLinkEnabled: false,
        },
        onDiffReady: renderChangeOverview,
      });
    }
  }

  /**
   * Renders a change overview minimap in the ace-diff gutter.
   * Each diff region is shown as a colored rectangle positioned proportionally
   * to its line number, giving a scrollbar-like overview of all changes.
   * Clicking a marker scrolls both editors to that change.
   */
  function renderChangeOverview(diffs) {
    if (!diffInstance) return;

    const gutterEl = dom.diffContainer.querySelector('.acediff__gutter') as HTMLElement;
    if (!gutterEl) return;

    // Remove previous overview if any
    const existing = gutterEl.querySelector('.diff-overview');
    if (existing) existing.remove();

    const editors = diffInstance.getEditors();
    const leftLineCount = editors.left.session.getLength();
    const rightLineCount = editors.right.session.getLength();
    const totalLines = Math.max(leftLineCount, rightLineCount, 1);

    const gutterHeight = gutterEl.clientHeight;
    if (gutterHeight === 0) return;

    // Create the overview SVG
    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg');
    svg.classList.add('diff-overview');
    svg.setAttribute('width', '100%');
    svg.setAttribute('height', String(gutterHeight));
    svg.style.position = 'absolute';
    svg.style.top = '0';
    svg.style.left = '0';
    svg.style.right = '0';
    svg.style.pointerEvents = 'auto';
    svg.style.zIndex = '10';

    for (const diff of diffs) {
      const startLine = Math.min(diff.leftStartLine, diff.rightStartLine);
      const endLine = Math.max(diff.leftEndLine, diff.rightEndLine);

      const y = (startLine / totalLines) * gutterHeight;
      const h = Math.max(((endLine - startLine + 1) / totalLines) * gutterHeight, 3);

      const rect = document.createElementNS(svgNS, 'rect');
      rect.setAttribute('x', '15%');
      rect.setAttribute('width', '70%');
      rect.setAttribute('y', String(Math.round(y)));
      rect.setAttribute('height', String(Math.round(h)));
      rect.setAttribute('rx', '2');
      rect.style.fill = 'rgba(58, 140, 154, 0.6)';
      rect.style.cursor = 'pointer';

      rect.addEventListener('click', () => {
        editors.left.scrollToLine(diff.leftStartLine, true, true, () => {});
        editors.right.scrollToLine(diff.rightStartLine, true, true, () => {});
      });
      rect.addEventListener('mouseenter', () => {
        rect.style.fill = 'rgba(58, 140, 154, 0.9)';
      });
      rect.addEventListener('mouseleave', () => {
        rect.style.fill = 'rgba(58, 140, 154, 0.6)';
      });

      svg.appendChild(rect);
    }

    gutterEl.style.position = 'relative';
    gutterEl.appendChild(svg);
  }
}
