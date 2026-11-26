"use strict";
Object.defineProperty(exports, "__esModule", { value: !0 });
function cloneDiff(diff2) {
  const [type, patch] = diff2;
  return [type, patch];
}
function getCommonOverlap(textA, textB) {
  let text1 = textA, text2 = textB;
  const text1Length = text1.length, text2Length = text2.length;
  if (text1Length === 0 || text2Length === 0)
    return 0;
  text1Length > text2Length ? text1 = text1.substring(text1Length - text2Length) : text1Length < text2Length && (text2 = text2.substring(0, text1Length));
  const textLength = Math.min(text1Length, text2Length);
  if (text1 === text2)
    return textLength;
  let best = 0, length = 1;
  for (let found = 0; found !== -1; ) {
    const pattern = text1.substring(textLength - length);
    if (found = text2.indexOf(pattern), found === -1)
      return best;
    length += found, (found === 0 || text1.substring(textLength - length) === text2.substring(0, length)) && (best = length, length++);
  }
  return best;
}
function getCommonPrefix(text1, text2) {
  if (!text1 || !text2 || text1[0] !== text2[0])
    return 0;
  let pointerMin = 0, pointerMax = Math.min(text1.length, text2.length), pointerMid = pointerMax, pointerStart = 0;
  for (; pointerMin < pointerMid; )
    text1.substring(pointerStart, pointerMid) === text2.substring(pointerStart, pointerMid) ? (pointerMin = pointerMid, pointerStart = pointerMin) : pointerMax = pointerMid, pointerMid = Math.floor((pointerMax - pointerMin) / 2 + pointerMin);
  return pointerMid;
}
function getCommonSuffix(text1, text2) {
  if (!text1 || !text2 || text1[text1.length - 1] !== text2[text2.length - 1])
    return 0;
  let pointerMin = 0, pointerMax = Math.min(text1.length, text2.length), pointerMid = pointerMax, pointerEnd = 0;
  for (; pointerMin < pointerMid; )
    text1.substring(text1.length - pointerMid, text1.length - pointerEnd) === text2.substring(text2.length - pointerMid, text2.length - pointerEnd) ? (pointerMin = pointerMid, pointerEnd = pointerMin) : pointerMax = pointerMid, pointerMid = Math.floor((pointerMax - pointerMin) / 2 + pointerMin);
  return pointerMid;
}
function isHighSurrogate(char) {
  const charCode = char.charCodeAt(0);
  return charCode >= 55296 && charCode <= 56319;
}
function isLowSurrogate(char) {
  const charCode = char.charCodeAt(0);
  return charCode >= 56320 && charCode <= 57343;
}
function bisect(text1, text2, deadline) {
  const text1Length = text1.length, text2Length = text2.length, maxD = Math.ceil((text1Length + text2Length) / 2), vOffset = maxD, vLength = 2 * maxD, v1 = new Array(vLength), v2 = new Array(vLength);
  for (let x = 0; x < vLength; x++)
    v1[x] = -1, v2[x] = -1;
  v1[vOffset + 1] = 0, v2[vOffset + 1] = 0;
  const delta = text1Length - text2Length, front = delta % 2 !== 0;
  let k1start = 0, k1end = 0, k2start = 0, k2end = 0;
  for (let d = 0; d < maxD && !(Date.now() > deadline); d++) {
    for (let k1 = -d + k1start; k1 <= d - k1end; k1 += 2) {
      const k1Offset = vOffset + k1;
      let x1;
      k1 === -d || k1 !== d && v1[k1Offset - 1] < v1[k1Offset + 1] ? x1 = v1[k1Offset + 1] : x1 = v1[k1Offset - 1] + 1;
      let y1 = x1 - k1;
      for (; x1 < text1Length && y1 < text2Length && text1.charAt(x1) === text2.charAt(y1); )
        x1++, y1++;
      if (v1[k1Offset] = x1, x1 > text1Length)
        k1end += 2;
      else if (y1 > text2Length)
        k1start += 2;
      else if (front) {
        const k2Offset = vOffset + delta - k1;
        if (k2Offset >= 0 && k2Offset < vLength && v2[k2Offset] !== -1) {
          const x2 = text1Length - v2[k2Offset];
          if (x1 >= x2)
            return bisectSplit(text1, text2, x1, y1, deadline);
        }
      }
    }
    for (let k2 = -d + k2start; k2 <= d - k2end; k2 += 2) {
      const k2Offset = vOffset + k2;
      let x2;
      k2 === -d || k2 !== d && v2[k2Offset - 1] < v2[k2Offset + 1] ? x2 = v2[k2Offset + 1] : x2 = v2[k2Offset - 1] + 1;
      let y2 = x2 - k2;
      for (; x2 < text1Length && y2 < text2Length && text1.charAt(text1Length - x2 - 1) === text2.charAt(text2Length - y2 - 1); )
        x2++, y2++;
      if (v2[k2Offset] = x2, x2 > text1Length)
        k2end += 2;
      else if (y2 > text2Length)
        k2start += 2;
      else if (!front) {
        const k1Offset = vOffset + delta - k2;
        if (k1Offset >= 0 && k1Offset < vLength && v1[k1Offset] !== -1) {
          const x1 = v1[k1Offset], y1 = vOffset + x1 - k1Offset;
          if (x2 = text1Length - x2, x1 >= x2)
            return bisectSplit(text1, text2, x1, y1, deadline);
        }
      }
    }
  }
  return [
    [DIFF_DELETE, text1],
    [DIFF_INSERT, text2]
  ];
}
function bisectSplit(text1, text2, x, y, deadline) {
  const text1a = text1.substring(0, x), text2a = text2.substring(0, y), text1b = text1.substring(x), text2b = text2.substring(y), diffs = doDiff(text1a, text2a, { checkLines: !1, deadline }), diffsb = doDiff(text1b, text2b, { checkLines: !1, deadline });
  return diffs.concat(diffsb);
}
function findHalfMatch(text1, text2, timeout = 1) {
  if (timeout <= 0)
    return null;
  const longText = text1.length > text2.length ? text1 : text2, shortText = text1.length > text2.length ? text2 : text1;
  if (longText.length < 4 || shortText.length * 2 < longText.length)
    return null;
  const halfMatch1 = halfMatchI(longText, shortText, Math.ceil(longText.length / 4)), halfMatch2 = halfMatchI(longText, shortText, Math.ceil(longText.length / 2));
  let halfMatch;
  if (halfMatch1 && halfMatch2)
    halfMatch = halfMatch1[4].length > halfMatch2[4].length ? halfMatch1 : halfMatch2;
  else {
    if (!halfMatch1 && !halfMatch2)
      return null;
    halfMatch2 ? halfMatch1 || (halfMatch = halfMatch2) : halfMatch = halfMatch1;
  }
  if (!halfMatch)
    throw new Error("Unable to find a half match.");
  let text1A, text1B, text2A, text2B;
  text1.length > text2.length ? (text1A = halfMatch[0], text1B = halfMatch[1], text2A = halfMatch[2], text2B = halfMatch[3]) : (text2A = halfMatch[0], text2B = halfMatch[1], text1A = halfMatch[2], text1B = halfMatch[3]);
  const midCommon = halfMatch[4];
  return [text1A, text1B, text2A, text2B, midCommon];
}
function halfMatchI(longText, shortText, i) {
  const seed = longText.slice(i, i + Math.floor(longText.length / 4));
  let j = -1, bestCommon = "", bestLongTextA, bestLongTextB, bestShortTextA, bestShortTextB;
  for (; (j = shortText.indexOf(seed, j + 1)) !== -1; ) {
    const prefixLength = getCommonPrefix(longText.slice(i), shortText.slice(j)), suffixLength = getCommonSuffix(longText.slice(0, i), shortText.slice(0, j));
    bestCommon.length < suffixLength + prefixLength && (bestCommon = shortText.slice(j - suffixLength, j) + shortText.slice(j, j + prefixLength), bestLongTextA = longText.slice(0, i - suffixLength), bestLongTextB = longText.slice(i + prefixLength), bestShortTextA = shortText.slice(0, j - suffixLength), bestShortTextB = shortText.slice(j + prefixLength));
  }
  return bestCommon.length * 2 >= longText.length ? [
    bestLongTextA || "",
    bestLongTextB || "",
    bestShortTextA || "",
    bestShortTextB || "",
    bestCommon || ""
  ] : null;
}
function charsToLines(diffs, lineArray) {
  for (let x = 0; x < diffs.length; x++) {
    const chars = diffs[x][1], text = [];
    for (let y = 0; y < chars.length; y++)
      text[y] = lineArray[chars.charCodeAt(y)];
    diffs[x][1] = text.join("");
  }
}
function linesToChars(textA, textB) {
  const lineArray = [], lineHash = {};
  lineArray[0] = "";
  function diffLinesToMunge(text) {
    let chars = "", lineStart = 0, lineEnd = -1, lineArrayLength = lineArray.length;
    for (; lineEnd < text.length - 1; ) {
      lineEnd = text.indexOf(`
`, lineStart), lineEnd === -1 && (lineEnd = text.length - 1);
      let line = text.slice(lineStart, lineEnd + 1);
      (lineHash.hasOwnProperty ? lineHash.hasOwnProperty(line) : lineHash[line] !== void 0) ? chars += String.fromCharCode(lineHash[line]) : (lineArrayLength === maxLines && (line = text.slice(lineStart), lineEnd = text.length), chars += String.fromCharCode(lineArrayLength), lineHash[line] = lineArrayLength, lineArray[lineArrayLength++] = line), lineStart = lineEnd + 1;
    }
    return chars;
  }
  let maxLines = 4e4;
  const chars1 = diffLinesToMunge(textA);
  maxLines = 65535;
  const chars2 = diffLinesToMunge(textB);
  return { chars1, chars2, lineArray };
}
function doLineModeDiff(textA, textB, opts) {
  let text1 = textA, text2 = textB;
  const a = linesToChars(text1, text2);
  text1 = a.chars1, text2 = a.chars2;
  const linearray = a.lineArray;
  let diffs = doDiff(text1, text2, {
    checkLines: !1,
    deadline: opts.deadline
  });
  charsToLines(diffs, linearray), diffs = cleanupSemantic(diffs), diffs.push([DIFF_EQUAL, ""]);
  let pointer = 0, countDelete = 0, countInsert = 0, textDelete = "", textInsert = "";
  for (; pointer < diffs.length; ) {
    switch (diffs[pointer][0]) {
      case DIFF_INSERT:
        countInsert++, textInsert += diffs[pointer][1];
        break;
      case DIFF_DELETE:
        countDelete++, textDelete += diffs[pointer][1];
        break;
      case DIFF_EQUAL:
        if (countDelete >= 1 && countInsert >= 1) {
          diffs.splice(pointer - countDelete - countInsert, countDelete + countInsert), pointer = pointer - countDelete - countInsert;
          const aa = doDiff(textDelete, textInsert, {
            checkLines: !1,
            deadline: opts.deadline
          });
          for (let j = aa.length - 1; j >= 0; j--)
            diffs.splice(pointer, 0, aa[j]);
          pointer += aa.length;
        }
        countInsert = 0, countDelete = 0, textDelete = "", textInsert = "";
        break;
      default:
        throw new Error("Unknown diff operation.");
    }
    pointer++;
  }
  return diffs.pop(), diffs;
}
function computeDiff(text1, text2, opts) {
  let diffs;
  if (!text1)
    return [[DIFF_INSERT, text2]];
  if (!text2)
    return [[DIFF_DELETE, text1]];
  const longtext = text1.length > text2.length ? text1 : text2, shorttext = text1.length > text2.length ? text2 : text1, i = longtext.indexOf(shorttext);
  if (i !== -1)
    return diffs = [
      [DIFF_INSERT, longtext.substring(0, i)],
      [DIFF_EQUAL, shorttext],
      [DIFF_INSERT, longtext.substring(i + shorttext.length)]
    ], text1.length > text2.length && (diffs[0][0] = DIFF_DELETE, diffs[2][0] = DIFF_DELETE), diffs;
  if (shorttext.length === 1)
    return [
      [DIFF_DELETE, text1],
      [DIFF_INSERT, text2]
    ];
  const halfMatch = findHalfMatch(text1, text2);
  if (halfMatch) {
    const text1A = halfMatch[0], text1B = halfMatch[1], text2A = halfMatch[2], text2B = halfMatch[3], midCommon = halfMatch[4], diffsA = doDiff(text1A, text2A, opts), diffsB = doDiff(text1B, text2B, opts);
    return diffsA.concat([[DIFF_EQUAL, midCommon]], diffsB);
  }
  return opts.checkLines && text1.length > 100 && text2.length > 100 ? doLineModeDiff(text1, text2, opts) : bisect(text1, text2, opts.deadline);
}
var __defProp$2 = Object.defineProperty, __getOwnPropSymbols$2 = Object.getOwnPropertySymbols, __hasOwnProp$2 = Object.prototype.hasOwnProperty, __propIsEnum$2 = Object.prototype.propertyIsEnumerable, __defNormalProp$2 = (obj, key, value) => key in obj ? __defProp$2(obj, key, { enumerable: !0, configurable: !0, writable: !0, value }) : obj[key] = value, __spreadValues$2 = (a, b) => {
  for (var prop in b || (b = {}))
    __hasOwnProp$2.call(b, prop) && __defNormalProp$2(a, prop, b[prop]);
  if (__getOwnPropSymbols$2)
    for (var prop of __getOwnPropSymbols$2(b))
      __propIsEnum$2.call(b, prop) && __defNormalProp$2(a, prop, b[prop]);
  return a;
};
const DIFF_DELETE = -1, DIFF_INSERT = 1, DIFF_EQUAL = 0;
function diff(textA, textB, opts) {
  if (textA === null || textB === null)
    throw new Error("Null input. (diff)");
  const diffs = doDiff(textA, textB, createInternalOpts(opts || {}));
  return adjustDiffForSurrogatePairs(diffs), diffs;
}
function doDiff(textA, textB, options) {
  let text1 = textA, text2 = textB;
  if (text1 === text2)
    return text1 ? [[DIFF_EQUAL, text1]] : [];
  let commonlength = getCommonPrefix(text1, text2);
  const commonprefix = text1.substring(0, commonlength);
  text1 = text1.substring(commonlength), text2 = text2.substring(commonlength), commonlength = getCommonSuffix(text1, text2);
  const commonsuffix = text1.substring(text1.length - commonlength);
  text1 = text1.substring(0, text1.length - commonlength), text2 = text2.substring(0, text2.length - commonlength);
  let diffs = computeDiff(text1, text2, options);
  return commonprefix && diffs.unshift([DIFF_EQUAL, commonprefix]), commonsuffix && diffs.push([DIFF_EQUAL, commonsuffix]), diffs = cleanupMerge(diffs), diffs;
}
function createDeadLine(timeout) {
  let t = 1;
  return typeof timeout < "u" && (t = timeout <= 0 ? Number.MAX_VALUE : timeout), Date.now() + t * 1e3;
}
function createInternalOpts(opts) {
  return __spreadValues$2({
    checkLines: !0,
    deadline: createDeadLine(opts.timeout || 1)
  }, opts);
}
function combineChar(data, char, dir) {
  return dir === 1 ? data + char : char + data;
}
function splitChar(data, dir) {
  return dir === 1 ? [data.substring(0, data.length - 1), data[data.length - 1]] : [data.substring(1), data[0]];
}
function hasSharedChar(diffs, i, j, dir) {
  return dir === 1 ? diffs[i][1][diffs[i][1].length - 1] === diffs[j][1][diffs[j][1].length - 1] : diffs[i][1][0] === diffs[j][1][0];
}
function deisolateChar(diffs, i, dir) {
  const inv = dir === 1 ? -1 : 1;
  let insertIdx = null, deleteIdx = null, j = i + dir;
  for (; j >= 0 && j < diffs.length && (insertIdx === null || deleteIdx === null); j += dir) {
    const [op, text2] = diffs[j];
    if (text2.length !== 0) {
      if (op === DIFF_INSERT) {
        insertIdx === null && (insertIdx = j);
        continue;
      } else if (op === DIFF_DELETE) {
        deleteIdx === null && (deleteIdx = j);
        continue;
      } else if (op === DIFF_EQUAL) {
        if (insertIdx === null && deleteIdx === null) {
          const [rest, char2] = splitChar(diffs[i][1], dir);
          diffs[i][1] = rest, diffs[j][1] = combineChar(diffs[j][1], char2, inv);
          return;
        }
        break;
      }
    }
  }
  if (insertIdx !== null && deleteIdx !== null && hasSharedChar(diffs, insertIdx, deleteIdx, dir)) {
    const [insertText, insertChar] = splitChar(diffs[insertIdx][1], inv), [deleteText] = splitChar(diffs[deleteIdx][1], inv);
    diffs[insertIdx][1] = insertText, diffs[deleteIdx][1] = deleteText, diffs[i][1] = combineChar(diffs[i][1], insertChar, dir);
    return;
  }
  const [text, char] = splitChar(diffs[i][1], dir);
  diffs[i][1] = text, insertIdx === null ? (diffs.splice(j, 0, [DIFF_INSERT, char]), deleteIdx !== null && deleteIdx >= j && deleteIdx++) : diffs[insertIdx][1] = combineChar(diffs[insertIdx][1], char, inv), deleteIdx === null ? diffs.splice(j, 0, [DIFF_DELETE, char]) : diffs[deleteIdx][1] = combineChar(diffs[deleteIdx][1], char, inv);
}
function adjustDiffForSurrogatePairs(diffs) {
  for (let i = 0; i < diffs.length; i++) {
    const [diffType, diffText] = diffs[i];
    if (diffText.length === 0) continue;
    const firstChar = diffText[0], lastChar = diffText[diffText.length - 1];
    isHighSurrogate(lastChar) && diffType === DIFF_EQUAL && deisolateChar(diffs, i, 1), isLowSurrogate(firstChar) && diffType === DIFF_EQUAL && deisolateChar(diffs, i, -1);
  }
  for (let i = 0; i < diffs.length; i++)
    diffs[i][1].length === 0 && diffs.splice(i, 1);
}
function cleanupSemantic(rawDiffs) {
  let diffs = rawDiffs.map((diff2) => cloneDiff(diff2)), hasChanges = !1;
  const equalities = [];
  let equalitiesLength = 0, lastEquality = null, pointer = 0, lengthInsertions1 = 0, lengthDeletions1 = 0, lengthInsertions2 = 0, lengthDeletions2 = 0;
  for (; pointer < diffs.length; )
    diffs[pointer][0] === DIFF_EQUAL ? (equalities[equalitiesLength++] = pointer, lengthInsertions1 = lengthInsertions2, lengthDeletions1 = lengthDeletions2, lengthInsertions2 = 0, lengthDeletions2 = 0, lastEquality = diffs[pointer][1]) : (diffs[pointer][0] === DIFF_INSERT ? lengthInsertions2 += diffs[pointer][1].length : lengthDeletions2 += diffs[pointer][1].length, lastEquality && lastEquality.length <= Math.max(lengthInsertions1, lengthDeletions1) && lastEquality.length <= Math.max(lengthInsertions2, lengthDeletions2) && (diffs.splice(equalities[equalitiesLength - 1], 0, [DIFF_DELETE, lastEquality]), diffs[equalities[equalitiesLength - 1] + 1][0] = DIFF_INSERT, equalitiesLength--, equalitiesLength--, pointer = equalitiesLength > 0 ? equalities[equalitiesLength - 1] : -1, lengthInsertions1 = 0, lengthDeletions1 = 0, lengthInsertions2 = 0, lengthDeletions2 = 0, lastEquality = null, hasChanges = !0)), pointer++;
  for (hasChanges && (diffs = cleanupMerge(diffs)), diffs = cleanupSemanticLossless(diffs), pointer = 1; pointer < diffs.length; ) {
    if (diffs[pointer - 1][0] === DIFF_DELETE && diffs[pointer][0] === DIFF_INSERT) {
      const deletion = diffs[pointer - 1][1], insertion = diffs[pointer][1], overlapLength1 = getCommonOverlap(deletion, insertion), overlapLength2 = getCommonOverlap(insertion, deletion);
      overlapLength1 >= overlapLength2 ? (overlapLength1 >= deletion.length / 2 || overlapLength1 >= insertion.length / 2) && (diffs.splice(pointer, 0, [DIFF_EQUAL, insertion.substring(0, overlapLength1)]), diffs[pointer - 1][1] = deletion.substring(0, deletion.length - overlapLength1), diffs[pointer + 1][1] = insertion.substring(overlapLength1), pointer++) : (overlapLength2 >= deletion.length / 2 || overlapLength2 >= insertion.length / 2) && (diffs.splice(pointer, 0, [DIFF_EQUAL, deletion.substring(0, overlapLength2)]), diffs[pointer - 1][0] = DIFF_INSERT, diffs[pointer - 1][1] = insertion.substring(0, insertion.length - overlapLength2), diffs[pointer + 1][0] = DIFF_DELETE, diffs[pointer + 1][1] = deletion.substring(overlapLength2), pointer++), pointer++;
    }
    pointer++;
  }
  return diffs;
}
const nonAlphaNumericRegex = /[^a-zA-Z0-9]/, whitespaceRegex = /\s/, linebreakRegex = /[\r\n]/, blanklineEndRegex = /\n\r?\n$/, blanklineStartRegex = /^\r?\n\r?\n/;
function cleanupSemanticLossless(rawDiffs) {
  const diffs = rawDiffs.map((diff2) => cloneDiff(diff2));
  function diffCleanupSemanticScore(one, two) {
    if (!one || !two)
      return 6;
    const char1 = one.charAt(one.length - 1), char2 = two.charAt(0), nonAlphaNumeric1 = char1.match(nonAlphaNumericRegex), nonAlphaNumeric2 = char2.match(nonAlphaNumericRegex), whitespace1 = nonAlphaNumeric1 && char1.match(whitespaceRegex), whitespace2 = nonAlphaNumeric2 && char2.match(whitespaceRegex), lineBreak1 = whitespace1 && char1.match(linebreakRegex), lineBreak2 = whitespace2 && char2.match(linebreakRegex), blankLine1 = lineBreak1 && one.match(blanklineEndRegex), blankLine2 = lineBreak2 && two.match(blanklineStartRegex);
    return blankLine1 || blankLine2 ? 5 : lineBreak1 || lineBreak2 ? 4 : nonAlphaNumeric1 && !whitespace1 && whitespace2 ? 3 : whitespace1 || whitespace2 ? 2 : nonAlphaNumeric1 || nonAlphaNumeric2 ? 1 : 0;
  }
  let pointer = 1;
  for (; pointer < diffs.length - 1; ) {
    if (diffs[pointer - 1][0] === DIFF_EQUAL && diffs[pointer + 1][0] === DIFF_EQUAL) {
      let equality1 = diffs[pointer - 1][1], edit = diffs[pointer][1], equality2 = diffs[pointer + 1][1];
      const commonOffset = getCommonSuffix(equality1, edit);
      if (commonOffset) {
        const commonString = edit.substring(edit.length - commonOffset);
        equality1 = equality1.substring(0, equality1.length - commonOffset), edit = commonString + edit.substring(0, edit.length - commonOffset), equality2 = commonString + equality2;
      }
      let bestEquality1 = equality1, bestEdit = edit, bestEquality2 = equality2, bestScore = diffCleanupSemanticScore(equality1, edit) + diffCleanupSemanticScore(edit, equality2);
      for (; edit.charAt(0) === equality2.charAt(0); ) {
        equality1 += edit.charAt(0), edit = edit.substring(1) + equality2.charAt(0), equality2 = equality2.substring(1);
        const score = diffCleanupSemanticScore(equality1, edit) + diffCleanupSemanticScore(edit, equality2);
        score >= bestScore && (bestScore = score, bestEquality1 = equality1, bestEdit = edit, bestEquality2 = equality2);
      }
      diffs[pointer - 1][1] !== bestEquality1 && (bestEquality1 ? diffs[pointer - 1][1] = bestEquality1 : (diffs.splice(pointer - 1, 1), pointer--), diffs[pointer][1] = bestEdit, bestEquality2 ? diffs[pointer + 1][1] = bestEquality2 : (diffs.splice(pointer + 1, 1), pointer--));
    }
    pointer++;
  }
  return diffs;
}
function cleanupMerge(rawDiffs) {
  let diffs = rawDiffs.map((diff2) => cloneDiff(diff2));
  diffs.push([DIFF_EQUAL, ""]);
  let pointer = 0, countDelete = 0, countInsert = 0, textDelete = "", textInsert = "", commonlength;
  for (; pointer < diffs.length; )
    switch (diffs[pointer][0]) {
      case DIFF_INSERT:
        countInsert++, textInsert += diffs[pointer][1], pointer++;
        break;
      case DIFF_DELETE:
        countDelete++, textDelete += diffs[pointer][1], pointer++;
        break;
      case DIFF_EQUAL:
        countDelete + countInsert > 1 ? (countDelete !== 0 && countInsert !== 0 && (commonlength = getCommonPrefix(textInsert, textDelete), commonlength !== 0 && (pointer - countDelete - countInsert > 0 && diffs[pointer - countDelete - countInsert - 1][0] === DIFF_EQUAL ? diffs[pointer - countDelete - countInsert - 1][1] += textInsert.substring(
          0,
          commonlength
        ) : (diffs.splice(0, 0, [DIFF_EQUAL, textInsert.substring(0, commonlength)]), pointer++), textInsert = textInsert.substring(commonlength), textDelete = textDelete.substring(commonlength)), commonlength = getCommonSuffix(textInsert, textDelete), commonlength !== 0 && (diffs[pointer][1] = textInsert.substring(textInsert.length - commonlength) + diffs[pointer][1], textInsert = textInsert.substring(0, textInsert.length - commonlength), textDelete = textDelete.substring(0, textDelete.length - commonlength))), pointer -= countDelete + countInsert, diffs.splice(pointer, countDelete + countInsert), textDelete.length && (diffs.splice(pointer, 0, [DIFF_DELETE, textDelete]), pointer++), textInsert.length && (diffs.splice(pointer, 0, [DIFF_INSERT, textInsert]), pointer++), pointer++) : pointer !== 0 && diffs[pointer - 1][0] === DIFF_EQUAL ? (diffs[pointer - 1][1] += diffs[pointer][1], diffs.splice(pointer, 1)) : pointer++, countInsert = 0, countDelete = 0, textDelete = "", textInsert = "";
        break;
      default:
        throw new Error("Unknown diff operation");
    }
  diffs[diffs.length - 1][1] === "" && diffs.pop();
  let hasChanges = !1;
  for (pointer = 1; pointer < diffs.length - 1; )
    diffs[pointer - 1][0] === DIFF_EQUAL && diffs[pointer + 1][0] === DIFF_EQUAL && (diffs[pointer][1].substring(diffs[pointer][1].length - diffs[pointer - 1][1].length) === diffs[pointer - 1][1] ? (diffs[pointer][1] = diffs[pointer - 1][1] + diffs[pointer][1].substring(0, diffs[pointer][1].length - diffs[pointer - 1][1].length), diffs[pointer + 1][1] = diffs[pointer - 1][1] + diffs[pointer + 1][1], diffs.splice(pointer - 1, 1), hasChanges = !0) : diffs[pointer][1].substring(0, diffs[pointer + 1][1].length) === diffs[pointer + 1][1] && (diffs[pointer - 1][1] += diffs[pointer + 1][1], diffs[pointer][1] = diffs[pointer][1].substring(diffs[pointer + 1][1].length) + diffs[pointer + 1][1], diffs.splice(pointer + 1, 1), hasChanges = !0)), pointer++;
  return hasChanges && (diffs = cleanupMerge(diffs)), diffs;
}
function trueCount(...args) {
  return args.reduce((n, bool) => n + (bool ? 1 : 0), 0);
}
function cleanupEfficiency(rawDiffs, editCost = 4) {
  let diffs = rawDiffs.map((diff2) => cloneDiff(diff2)), hasChanges = !1;
  const equalities = [];
  let equalitiesLength = 0, lastEquality = null, pointer = 0, preIns = !1, preDel = !1, postIns = !1, postDel = !1;
  for (; pointer < diffs.length; )
    diffs[pointer][0] === DIFF_EQUAL ? (diffs[pointer][1].length < editCost && (postIns || postDel) ? (equalities[equalitiesLength++] = pointer, preIns = postIns, preDel = postDel, lastEquality = diffs[pointer][1]) : (equalitiesLength = 0, lastEquality = null), postIns = !1, postDel = !1) : (diffs[pointer][0] === DIFF_DELETE ? postDel = !0 : postIns = !0, lastEquality && (preIns && preDel && postIns && postDel || lastEquality.length < editCost / 2 && trueCount(preIns, preDel, postIns, postDel) === 3) && (diffs.splice(equalities[equalitiesLength - 1], 0, [DIFF_DELETE, lastEquality]), diffs[equalities[equalitiesLength - 1] + 1][0] = DIFF_INSERT, equalitiesLength--, lastEquality = null, preIns && preDel ? (postIns = !0, postDel = !0, equalitiesLength = 0) : (equalitiesLength--, pointer = equalitiesLength > 0 ? equalities[equalitiesLength - 1] : -1, postIns = !1, postDel = !1), hasChanges = !0)), pointer++;
  return hasChanges && (diffs = cleanupMerge(diffs)), diffs;
}
var __defProp$1 = Object.defineProperty, __getOwnPropSymbols$1 = Object.getOwnPropertySymbols, __hasOwnProp$1 = Object.prototype.hasOwnProperty, __propIsEnum$1 = Object.prototype.propertyIsEnumerable, __defNormalProp$1 = (obj, key, value) => key in obj ? __defProp$1(obj, key, { enumerable: !0, configurable: !0, writable: !0, value }) : obj[key] = value, __spreadValues$1 = (a, b) => {
  for (var prop in b || (b = {}))
    __hasOwnProp$1.call(b, prop) && __defNormalProp$1(a, prop, b[prop]);
  if (__getOwnPropSymbols$1)
    for (var prop of __getOwnPropSymbols$1(b))
      __propIsEnum$1.call(b, prop) && __defNormalProp$1(a, prop, b[prop]);
  return a;
};
const DEFAULT_OPTIONS = {
  /**
   * At what point is no match declared (0.0 = perfection, 1.0 = very loose).
   */
  threshold: 0.5,
  /**
   * How far to search for a match (0 = exact location, 1000+ = broad match).
   * A match this many characters away from the expected location will add
   * 1.0 to the score (0.0 is a perfect match).
   */
  distance: 1e3
};
function applyDefaults(options) {
  return __spreadValues$1(__spreadValues$1({}, DEFAULT_OPTIONS), options);
}
const MAX_BITS$1 = 32;
function bitap(text, pattern, loc, opts = {}) {
  if (pattern.length > MAX_BITS$1)
    throw new Error("Pattern too long for this browser.");
  const options = applyDefaults(opts), s = getAlphabetFromPattern(pattern);
  function getBitapScore(e, x) {
    const accuracy = e / pattern.length, proximity = Math.abs(loc - x);
    return options.distance ? accuracy + proximity / options.distance : proximity ? 1 : accuracy;
  }
  let scoreThreshold = options.threshold, bestLoc = text.indexOf(pattern, loc);
  bestLoc !== -1 && (scoreThreshold = Math.min(getBitapScore(0, bestLoc), scoreThreshold), bestLoc = text.lastIndexOf(pattern, loc + pattern.length), bestLoc !== -1 && (scoreThreshold = Math.min(getBitapScore(0, bestLoc), scoreThreshold)));
  const matchmask = 1 << pattern.length - 1;
  bestLoc = -1;
  let binMin, binMid, binMax = pattern.length + text.length, lastRd = [];
  for (let d = 0; d < pattern.length; d++) {
    for (binMin = 0, binMid = binMax; binMin < binMid; )
      getBitapScore(d, loc + binMid) <= scoreThreshold ? binMin = binMid : binMax = binMid, binMid = Math.floor((binMax - binMin) / 2 + binMin);
    binMax = binMid;
    let start = Math.max(1, loc - binMid + 1);
    const finish = Math.min(loc + binMid, text.length) + pattern.length, rd = new Array(finish + 2);
    rd[finish + 1] = (1 << d) - 1;
    for (let j = finish; j >= start; j--) {
      const charMatch = s[text.charAt(j - 1)];
      if (d === 0 ? rd[j] = (rd[j + 1] << 1 | 1) & charMatch : rd[j] = (rd[j + 1] << 1 | 1) & charMatch | ((lastRd[j + 1] | lastRd[j]) << 1 | 1) | lastRd[j + 1], rd[j] & matchmask) {
        const score = getBitapScore(d, j - 1);
        if (score <= scoreThreshold)
          if (scoreThreshold = score, bestLoc = j - 1, bestLoc > loc)
            start = Math.max(1, 2 * loc - bestLoc);
          else
            break;
      }
    }
    if (getBitapScore(d + 1, loc) > scoreThreshold)
      break;
    lastRd = rd;
  }
  return bestLoc;
}
function getAlphabetFromPattern(pattern) {
  const s = {};
  for (let i = 0; i < pattern.length; i++)
    s[pattern.charAt(i)] = 0;
  for (let i = 0; i < pattern.length; i++)
    s[pattern.charAt(i)] |= 1 << pattern.length - i - 1;
  return s;
}
function match(text, pattern, searchLocation, options = {}) {
  if (text === null || pattern === null || searchLocation === null)
    throw new Error("Null input. (match())");
  const loc = Math.max(0, Math.min(searchLocation, text.length));
  if (text === pattern)
    return 0;
  if (text.length) {
    if (text.substring(loc, loc + pattern.length) === pattern)
      return loc;
  } else return -1;
  return bitap(text, pattern, loc, options);
}
function diffText1(diffs) {
  const text = [];
  for (let x = 0; x < diffs.length; x++)
    diffs[x][0] !== DIFF_INSERT && (text[x] = diffs[x][1]);
  return text.join("");
}
function diffText2(diffs) {
  const text = [];
  for (let x = 0; x < diffs.length; x++)
    diffs[x][0] !== DIFF_DELETE && (text[x] = diffs[x][1]);
  return text.join("");
}
function levenshtein(diffs) {
  let leven = 0, insertions = 0, deletions = 0;
  for (let x = 0; x < diffs.length; x++) {
    const op = diffs[x][0], data = diffs[x][1];
    switch (op) {
      case DIFF_INSERT:
        insertions += data.length;
        break;
      case DIFF_DELETE:
        deletions += data.length;
        break;
      case DIFF_EQUAL:
        leven += Math.max(insertions, deletions), insertions = 0, deletions = 0;
        break;
      default:
        throw new Error("Unknown diff operation.");
    }
  }
  return leven += Math.max(insertions, deletions), leven;
}
function xIndex(diffs, location) {
  let chars1 = 0, chars2 = 0, lastChars1 = 0, lastChars2 = 0, x;
  for (x = 0; x < diffs.length && (diffs[x][0] !== DIFF_INSERT && (chars1 += diffs[x][1].length), diffs[x][0] !== DIFF_DELETE && (chars2 += diffs[x][1].length), !(chars1 > location)); x++)
    lastChars1 = chars1, lastChars2 = chars2;
  return diffs.length !== x && diffs[x][0] === DIFF_DELETE ? lastChars2 : lastChars2 + (location - lastChars1);
}
function countUtf8Bytes(str) {
  let bytes = 0;
  for (let i = 0; i < str.length; i++) {
    const codePoint = str.codePointAt(i);
    if (typeof codePoint > "u")
      throw new Error("Failed to get codepoint");
    bytes += utf8len(codePoint);
  }
  return bytes;
}
function adjustIndiciesToUcs2(patches, base, options = {}) {
  let byteOffset = 0, idx = 0;
  function advanceTo(target) {
    for (; byteOffset < target; ) {
      const codePoint = base.codePointAt(idx);
      if (typeof codePoint > "u")
        return idx;
      byteOffset += utf8len(codePoint), codePoint > 65535 ? idx += 2 : idx += 1;
    }
    if (!options.allowExceedingIndices && byteOffset !== target)
      throw new Error("Failed to determine byte offset");
    return idx;
  }
  const adjusted = [];
  for (const patch of patches)
    adjusted.push({
      diffs: patch.diffs.map((diff2) => cloneDiff(diff2)),
      start1: advanceTo(patch.start1),
      start2: advanceTo(patch.start2),
      utf8Start1: patch.utf8Start1,
      utf8Start2: patch.utf8Start2,
      length1: patch.length1,
      length2: patch.length2,
      utf8Length1: patch.utf8Length1,
      utf8Length2: patch.utf8Length2
    });
  return adjusted;
}
function utf8len(codePoint) {
  return codePoint <= 127 ? 1 : codePoint <= 2047 ? 2 : codePoint <= 65535 ? 3 : 4;
}
const MAX_BITS = 32, DEFAULT_MARGIN = 4;
function addPadding(patches, margin = DEFAULT_MARGIN) {
  const paddingLength = margin;
  let nullPadding = "";
  for (let x = 1; x <= paddingLength; x++)
    nullPadding += String.fromCharCode(x);
  for (const p of patches)
    p.start1 += paddingLength, p.start2 += paddingLength, p.utf8Start1 += paddingLength, p.utf8Start2 += paddingLength;
  let patch = patches[0], diffs = patch.diffs;
  if (diffs.length === 0 || diffs[0][0] !== DIFF_EQUAL)
    diffs.unshift([DIFF_EQUAL, nullPadding]), patch.start1 -= paddingLength, patch.start2 -= paddingLength, patch.utf8Start1 -= paddingLength, patch.utf8Start2 -= paddingLength, patch.length1 += paddingLength, patch.length2 += paddingLength, patch.utf8Length1 += paddingLength, patch.utf8Length2 += paddingLength;
  else if (paddingLength > diffs[0][1].length) {
    const firstDiffLength = diffs[0][1].length, extraLength = paddingLength - firstDiffLength;
    diffs[0][1] = nullPadding.substring(firstDiffLength) + diffs[0][1], patch.start1 -= extraLength, patch.start2 -= extraLength, patch.utf8Start1 -= extraLength, patch.utf8Start2 -= extraLength, patch.length1 += extraLength, patch.length2 += extraLength, patch.utf8Length1 += extraLength, patch.utf8Length2 += extraLength;
  }
  if (patch = patches[patches.length - 1], diffs = patch.diffs, diffs.length === 0 || diffs[diffs.length - 1][0] !== DIFF_EQUAL)
    diffs.push([DIFF_EQUAL, nullPadding]), patch.length1 += paddingLength, patch.length2 += paddingLength, patch.utf8Length1 += paddingLength, patch.utf8Length2 += paddingLength;
  else if (paddingLength > diffs[diffs.length - 1][1].length) {
    const extraLength = paddingLength - diffs[diffs.length - 1][1].length;
    diffs[diffs.length - 1][1] += nullPadding.substring(0, extraLength), patch.length1 += extraLength, patch.length2 += extraLength, patch.utf8Length1 += extraLength, patch.utf8Length2 += extraLength;
  }
  return nullPadding;
}
function createPatchObject(start1, start2) {
  return {
    diffs: [],
    start1,
    start2,
    utf8Start1: start1,
    utf8Start2: start2,
    length1: 0,
    length2: 0,
    utf8Length1: 0,
    utf8Length2: 0
  };
}
function splitMax(patches, margin = DEFAULT_MARGIN) {
  const patchSize = MAX_BITS;
  for (let x = 0; x < patches.length; x++) {
    if (patches[x].length1 <= patchSize)
      continue;
    const bigpatch = patches[x];
    patches.splice(x--, 1);
    let start1 = bigpatch.start1, start2 = bigpatch.start2, preContext = "";
    for (; bigpatch.diffs.length !== 0; ) {
      const patch = createPatchObject(start1 - preContext.length, start2 - preContext.length);
      let empty = !0;
      if (preContext !== "") {
        const precontextByteCount = countUtf8Bytes(preContext);
        patch.length1 = preContext.length, patch.utf8Length1 = precontextByteCount, patch.length2 = preContext.length, patch.utf8Length2 = precontextByteCount, patch.diffs.push([DIFF_EQUAL, preContext]);
      }
      for (; bigpatch.diffs.length !== 0 && patch.length1 < patchSize - margin; ) {
        const diffType = bigpatch.diffs[0][0];
        let diffText = bigpatch.diffs[0][1], diffTextByteCount = countUtf8Bytes(diffText);
        if (diffType === DIFF_INSERT) {
          patch.length2 += diffText.length, patch.utf8Length2 += diffTextByteCount, start2 += diffText.length;
          const diff2 = bigpatch.diffs.shift();
          diff2 && patch.diffs.push(diff2), empty = !1;
        } else diffType === DIFF_DELETE && patch.diffs.length === 1 && patch.diffs[0][0] === DIFF_EQUAL && diffText.length > 2 * patchSize ? (patch.length1 += diffText.length, patch.utf8Length1 += diffTextByteCount, start1 += diffText.length, empty = !1, patch.diffs.push([diffType, diffText]), bigpatch.diffs.shift()) : (diffText = diffText.substring(0, patchSize - patch.length1 - margin), diffTextByteCount = countUtf8Bytes(diffText), patch.length1 += diffText.length, patch.utf8Length1 += diffTextByteCount, start1 += diffText.length, diffType === DIFF_EQUAL ? (patch.length2 += diffText.length, patch.utf8Length2 += diffTextByteCount, start2 += diffText.length) : empty = !1, patch.diffs.push([diffType, diffText]), diffText === bigpatch.diffs[0][1] ? bigpatch.diffs.shift() : bigpatch.diffs[0][1] = bigpatch.diffs[0][1].substring(diffText.length));
      }
      preContext = diffText2(patch.diffs), preContext = preContext.substring(preContext.length - margin);
      const postContext = diffText1(bigpatch.diffs).substring(0, margin), postContextByteCount = countUtf8Bytes(postContext);
      postContext !== "" && (patch.length1 += postContext.length, patch.length2 += postContext.length, patch.utf8Length1 += postContextByteCount, patch.utf8Length2 += postContextByteCount, patch.diffs.length !== 0 && patch.diffs[patch.diffs.length - 1][0] === DIFF_EQUAL ? patch.diffs[patch.diffs.length - 1][1] += postContext : patch.diffs.push([DIFF_EQUAL, postContext])), empty || patches.splice(++x, 0, patch);
    }
  }
}
function apply(patches, originalText, opts = {}) {
  if (typeof patches == "string")
    throw new Error("Patches must be an array - pass the patch to `parsePatch()` first");
  let text = originalText;
  if (patches.length === 0)
    return [text, []];
  const parsed = adjustIndiciesToUcs2(patches, text, {
    allowExceedingIndices: opts.allowExceedingIndices
  }), margin = opts.margin || DEFAULT_MARGIN, deleteThreshold = opts.deleteThreshold || 0.4, nullPadding = addPadding(parsed, margin);
  text = nullPadding + text + nullPadding, splitMax(parsed, margin);
  let delta = 0;
  const results = [];
  for (let x = 0; x < parsed.length; x++) {
    const expectedLoc = parsed[x].start2 + delta, text1 = diffText1(parsed[x].diffs);
    let startLoc, endLoc = -1;
    if (text1.length > MAX_BITS ? (startLoc = match(text, text1.substring(0, MAX_BITS), expectedLoc), startLoc !== -1 && (endLoc = match(
      text,
      text1.substring(text1.length - MAX_BITS),
      expectedLoc + text1.length - MAX_BITS
    ), (endLoc === -1 || startLoc >= endLoc) && (startLoc = -1))) : startLoc = match(text, text1, expectedLoc), startLoc === -1)
      results[x] = !1, delta -= parsed[x].length2 - parsed[x].length1;
    else {
      results[x] = !0, delta = startLoc - expectedLoc;
      let text2;
      if (endLoc === -1 ? text2 = text.substring(startLoc, startLoc + text1.length) : text2 = text.substring(startLoc, endLoc + MAX_BITS), text1 === text2)
        text = text.substring(0, startLoc) + diffText2(parsed[x].diffs) + text.substring(startLoc + text1.length);
      else {
        let diffs = diff(text1, text2, { checkLines: !1 });
        if (text1.length > MAX_BITS && levenshtein(diffs) / text1.length > deleteThreshold)
          results[x] = !1;
        else {
          diffs = cleanupSemanticLossless(diffs);
          let index1 = 0, index2 = 0;
          for (let y = 0; y < parsed[x].diffs.length; y++) {
            const mod = parsed[x].diffs[y];
            mod[0] !== DIFF_EQUAL && (index2 = xIndex(diffs, index1)), mod[0] === DIFF_INSERT ? text = text.substring(0, startLoc + index2) + mod[1] + text.substring(startLoc + index2) : mod[0] === DIFF_DELETE && (text = text.substring(0, startLoc + index2) + text.substring(startLoc + xIndex(diffs, index1 + mod[1].length))), mod[0] !== DIFF_DELETE && (index1 += mod[1].length);
          }
        }
      }
    }
  }
  return text = text.substring(nullPadding.length, text.length - nullPadding.length), [text, results];
}
var __defProp = Object.defineProperty, __getOwnPropSymbols = Object.getOwnPropertySymbols, __hasOwnProp = Object.prototype.hasOwnProperty, __propIsEnum = Object.prototype.propertyIsEnumerable, __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: !0, configurable: !0, writable: !0, value }) : obj[key] = value, __spreadValues = (a, b) => {
  for (var prop in b || (b = {}))
    __hasOwnProp.call(b, prop) && __defNormalProp(a, prop, b[prop]);
  if (__getOwnPropSymbols)
    for (var prop of __getOwnPropSymbols(b))
      __propIsEnum.call(b, prop) && __defNormalProp(a, prop, b[prop]);
  return a;
};
const DEFAULT_OPTS = {
  margin: 4
};
function getDefaultOpts(opts = {}) {
  return __spreadValues(__spreadValues({}, DEFAULT_OPTS), opts);
}
function make(a, b, options) {
  if (typeof a == "string" && typeof b == "string") {
    let diffs = diff(a, b, { checkLines: !0 });
    return diffs.length > 2 && (diffs = cleanupSemantic(diffs), diffs = cleanupEfficiency(diffs)), _make(a, diffs, getDefaultOpts(options));
  }
  if (a && Array.isArray(a) && typeof b > "u")
    return _make(diffText1(a), a, getDefaultOpts(options));
  if (typeof a == "string" && b && Array.isArray(b))
    return _make(a, b, getDefaultOpts(options));
  throw new Error("Unknown call format to make()");
}
function _make(textA, diffs, options) {
  if (diffs.length === 0)
    return [];
  const patches = [];
  let patch = createPatchObject(0, 0), patchDiffLength = 0, charCount1 = 0, charCount2 = 0, utf8Count1 = 0, utf8Count2 = 0, prepatchText = textA, postpatchText = textA;
  for (let x = 0; x < diffs.length; x++) {
    const currentDiff = diffs[x], [diffType, diffText] = currentDiff, diffTextLength = diffText.length, diffByteLength = countUtf8Bytes(diffText);
    switch (!patchDiffLength && diffType !== DIFF_EQUAL && (patch.start1 = charCount1, patch.start2 = charCount2, patch.utf8Start1 = utf8Count1, patch.utf8Start2 = utf8Count2), diffType) {
      case DIFF_INSERT:
        patch.diffs[patchDiffLength++] = currentDiff, patch.length2 += diffTextLength, patch.utf8Length2 += diffByteLength, postpatchText = postpatchText.substring(0, charCount2) + diffText + postpatchText.substring(charCount2);
        break;
      case DIFF_DELETE:
        patch.length1 += diffTextLength, patch.utf8Length1 += diffByteLength, patch.diffs[patchDiffLength++] = currentDiff, postpatchText = postpatchText.substring(0, charCount2) + postpatchText.substring(charCount2 + diffTextLength);
        break;
      case DIFF_EQUAL:
        diffTextLength <= 2 * options.margin && patchDiffLength && diffs.length !== x + 1 ? (patch.diffs[patchDiffLength++] = currentDiff, patch.length1 += diffTextLength, patch.length2 += diffTextLength, patch.utf8Length1 += diffByteLength, patch.utf8Length2 += diffByteLength) : diffTextLength >= 2 * options.margin && patchDiffLength && (addContext(patch, prepatchText, options), patches.push(patch), patch = createPatchObject(-1, -1), patchDiffLength = 0, prepatchText = postpatchText, charCount1 = charCount2, utf8Count1 = utf8Count2);
        break;
      default:
        throw new Error("Unknown diff type");
    }
    diffType !== DIFF_INSERT && (charCount1 += diffTextLength, utf8Count1 += diffByteLength), diffType !== DIFF_DELETE && (charCount2 += diffTextLength, utf8Count2 += diffByteLength);
  }
  return patchDiffLength && (addContext(patch, prepatchText, options), patches.push(patch)), patches;
}
function addContext(patch, text, opts) {
  if (text.length === 0)
    return;
  let pattern = text.substring(patch.start2, patch.start2 + patch.length1), padding = 0;
  for (; text.indexOf(pattern) !== text.lastIndexOf(pattern) && pattern.length < MAX_BITS - opts.margin - opts.margin; )
    padding += opts.margin, pattern = text.substring(patch.start2 - padding, patch.start2 + patch.length1 + padding);
  padding += opts.margin;
  let prefixStart = patch.start2 - padding;
  prefixStart >= 1 && isLowSurrogate(text[prefixStart]) && prefixStart--;
  const prefix = text.substring(prefixStart, patch.start2);
  prefix && patch.diffs.unshift([DIFF_EQUAL, prefix]);
  const prefixLength = prefix.length, prefixUtf8Length = countUtf8Bytes(prefix);
  let suffixEnd = patch.start2 + patch.length1 + padding;
  suffixEnd < text.length && isLowSurrogate(text[suffixEnd]) && suffixEnd++;
  const suffix = text.substring(patch.start2 + patch.length1, suffixEnd);
  suffix && patch.diffs.push([DIFF_EQUAL, suffix]);
  const suffixLength = suffix.length, suffixUtf8Length = countUtf8Bytes(suffix);
  patch.start1 -= prefixLength, patch.start2 -= prefixLength, patch.utf8Start1 -= prefixUtf8Length, patch.utf8Start2 -= prefixUtf8Length, patch.length1 += prefixLength + suffixLength, patch.length2 += prefixLength + suffixLength, patch.utf8Length1 += prefixUtf8Length + suffixUtf8Length, patch.utf8Length2 += prefixUtf8Length + suffixUtf8Length;
}
const patchHeader = /^@@ -(\d+),?(\d*) \+(\d+),?(\d*) @@$/;
function parse(textline) {
  if (!textline)
    return [];
  const patches = [], lines = textline.split(`
`);
  let textPointer = 0;
  for (; textPointer < lines.length; ) {
    const m = lines[textPointer].match(patchHeader);
    if (!m)
      throw new Error(`Invalid patch string: ${lines[textPointer]}`);
    const patch = createPatchObject(toInt(m[1]), toInt(m[3]));
    for (patches.push(patch), m[2] === "" ? (patch.start1--, patch.utf8Start1--, patch.length1 = 1, patch.utf8Length1 = 1) : m[2] === "0" ? (patch.length1 = 0, patch.utf8Length1 = 0) : (patch.start1--, patch.utf8Start1--, patch.utf8Length1 = toInt(m[2]), patch.length1 = patch.utf8Length1), m[4] === "" ? (patch.start2--, patch.utf8Start2--, patch.length2 = 1, patch.utf8Length2 = 1) : m[4] === "0" ? (patch.length2 = 0, patch.utf8Length2 = 0) : (patch.start2--, patch.utf8Start2--, patch.utf8Length2 = toInt(m[4]), patch.length2 = patch.utf8Length2), textPointer++; textPointer < lines.length; ) {
      const currentLine = lines[textPointer], sign = currentLine.charAt(0);
      if (sign === "@")
        break;
      if (sign === "") {
        textPointer++;
        continue;
      }
      let line;
      try {
        line = decodeURI(currentLine.slice(1));
      } catch (ex) {
        throw new Error(`Illegal escape in parse: ${currentLine}`);
      }
      const utf8Diff = countUtf8Bytes(line) - line.length;
      if (sign === "-")
        patch.diffs.push([DIFF_DELETE, line]), patch.length1 -= utf8Diff;
      else if (sign === "+")
        patch.diffs.push([DIFF_INSERT, line]), patch.length2 -= utf8Diff;
      else if (sign === " ")
        patch.diffs.push([DIFF_EQUAL, line]), patch.length1 -= utf8Diff, patch.length2 -= utf8Diff;
      else
        throw new Error(`Invalid patch mode "${sign}" in: ${line}`);
      textPointer++;
    }
  }
  return patches;
}
function toInt(num) {
  return parseInt(num, 10);
}
function stringify(patches) {
  return patches.map(stringifyPatch).join("");
}
function stringifyPatch(patch) {
  const { utf8Length1, utf8Length2, utf8Start1, utf8Start2, diffs } = patch;
  let coords1;
  utf8Length1 === 0 ? coords1 = `${utf8Start1},0` : utf8Length1 === 1 ? coords1 = `${utf8Start1 + 1}` : coords1 = `${utf8Start1 + 1},${utf8Length1}`;
  let coords2;
  utf8Length2 === 0 ? coords2 = `${utf8Start2},0` : utf8Length2 === 1 ? coords2 = `${utf8Start2 + 1}` : coords2 = `${utf8Start2 + 1},${utf8Length2}`;
  const text = [`@@ -${coords1} +${coords2} @@
`];
  let op;
  for (let x = 0; x < diffs.length; x++) {
    switch (diffs[x][0]) {
      case DIFF_INSERT:
        op = "+";
        break;
      case DIFF_DELETE:
        op = "-";
        break;
      case DIFF_EQUAL:
        op = " ";
        break;
      default:
        throw new Error("Unknown patch operation.");
    }
    text[x + 1] = `${op + encodeURI(diffs[x][1])}
`;
  }
  return text.join("").replace(/%20/g, " ");
}
exports.DIFF_DELETE = DIFF_DELETE;
exports.DIFF_EQUAL = DIFF_EQUAL;
exports.DIFF_INSERT = DIFF_INSERT;
exports.adjustIndiciesToUcs2 = adjustIndiciesToUcs2;
exports.applyPatches = apply;
exports.cleanupEfficiency = cleanupEfficiency;
exports.cleanupSemantic = cleanupSemantic;
exports.makeDiff = diff;
exports.makePatches = make;
exports.match = match;
exports.parsePatch = parse;
exports.stringifyPatch = stringifyPatch;
exports.stringifyPatches = stringify;
exports.xIndex = xIndex;
//# sourceMappingURL=index.cjs.map
