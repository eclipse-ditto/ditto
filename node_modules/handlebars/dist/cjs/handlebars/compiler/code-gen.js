/* global define, require */
'use strict';

exports.__esModule = true;
exports.safeJsonLiteral = safeJsonLiteral;

var _utils = require('../utils');

// Matches any character that quotedString may have to escape: quotes,
// backslashes, control characters, line separators, surrogates and "<". Most
// strings, e.g. lookup names, contain none of them and can skip the escaping.
// eslint-disable-next-line no-control-regex
var NEEDS_ESCAPING = /[\x00-\x1f\\"\u2028\u2029<\ud800-\udfff]/;

// Per HTML "Restrictions for contents of script elements": "<!--", "<script"
// and "</script" can end or change the parsing of an enclosing <script>
// element.
var SCRIPT_DELIMITERS = /<(?=!--|\/?script)/gi;

/**
 * Escape the "<" of every sequence that could end or change the parsing of
 * an enclosing <script> element.
 *
 * Only pass JavaScript or JSON code in which "<" can only occur inside string
 * literals, where "\u003C" has the same value.
 *
 * @param {string} code the code to escape
 * @returns {string} the escaped code
 */
function escapeScriptDelimiters(code) {
  return code.replace(SCRIPT_DELIMITERS, '\\u003C');
}

/**
 * Escape a UTF-16 surrogate code unit as a "\uXXXX" escape sequence.
 *
 * @param {string} surrogate a single surrogate code unit
 * @returns {string} the escape sequence
 */
function escapeSurrogate(surrogate) {
  return '\\u' + surrogate.charCodeAt(0).toString(16);
}

/**
 * Convert a value to a JSON literal that is safe to write into generated
 * code: valid in ES5, where line and paragraph separators end string literals,
 * encodable as UTF-8 on every engine, and safe inside an enclosing <script>
 * element.
 *
 * @param {*} value the value to convert
 * @returns {string} the JavaScript literal
 */

function safeJsonLiteral(value) {
  return escapeScriptDelimiters(JSON.stringify(value).replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029')
  // Engines before ES2019 leave lone surrogates unescaped, and those cannot
  // be encoded as UTF-8. Escaping paired ones as well keeps the value.
  .replace(/[\ud800-\udfff]/g, escapeSurrogate));
}

var SourceNode = undefined;

try {
  /* istanbul ignore next */
  if (typeof define !== 'function' || !define.amd) {
    // We don't support this in AMD environments. For these environments, we assume that
    // they are running on the browser and thus have no need for the source-map library.
    var SourceMap = require('source-map');
    SourceNode = SourceMap.SourceNode;
  }
} catch (err) {}
/* NOP */

/* istanbul ignore if: tested but not covered in istanbul due to dist build  */
if (!SourceNode) {
  SourceNode = function (line, column, srcFile, chunks) {
    this.src = '';
    if (chunks) {
      this.add(chunks);
    }
  };
  /* istanbul ignore next */
  SourceNode.prototype = {
    add: function add(chunks) {
      if (_utils.isArray(chunks)) {
        chunks = chunks.join('');
      }
      this.src += chunks;
    },
    prepend: function prepend(chunks) {
      if (_utils.isArray(chunks)) {
        chunks = chunks.join('');
      }
      this.src = chunks + this.src;
    },
    toStringWithSourceMap: function toStringWithSourceMap() {
      return { code: this.toString() };
    },
    toString: function toString() {
      return this.src;
    }
  };
}

function castChunk(chunk, codeGen, loc) {
  if (_utils.isArray(chunk)) {
    var ret = [];

    for (var i = 0, len = chunk.length; i < len; i++) {
      ret.push(codeGen.wrap(chunk[i], loc));
    }
    return ret;
  } else if (typeof chunk === 'boolean' || typeof chunk === 'number') {
    // Handle primitives that the SourceNode will throw up on
    return chunk + '';
  }
  return chunk;
}

function CodeGen(srcFile) {
  this.srcFile = srcFile;
  this.source = [];
}

CodeGen.prototype = {
  isEmpty: function isEmpty() {
    return !this.source.length;
  },
  prepend: function prepend(source, loc) {
    this.source.unshift(this.wrap(source, loc));
  },
  push: function push(source, loc) {
    this.source.push(this.wrap(source, loc));
  },

  merge: function merge() {
    var source = this.empty();
    this.each(function (line) {
      source.add(['  ', line, '\n']);
    });
    return source;
  },

  each: function each(iter) {
    for (var i = 0, len = this.source.length; i < len; i++) {
      iter(this.source[i]);
    }
  },

  empty: function empty() {
    var loc = this.currentLocation || { start: {} };
    return new SourceNode(loc.start.line, loc.start.column, this.srcFile);
  },
  wrap: function wrap(chunk) {
    var loc = arguments.length <= 1 || arguments[1] === undefined ? this.currentLocation || { start: {} } : arguments[1];

    if (chunk instanceof SourceNode) {
      return chunk;
    }

    chunk = castChunk(chunk, this, loc);

    return new SourceNode(loc.start.line, loc.start.column, this.srcFile, chunk);
  },

  functionCall: function functionCall(fn, type, params) {
    params = this.generateList(params);
    return this.wrap([fn, type ? '.' + type + '(' : '(', params, ')']);
  },

  quotedString: function quotedString(str) {
    str = str + '';
    // Fast path: quotedString runs for every lookup name and content string,
    // and the chain of replacements below is much slower than a single test.
    if (!NEEDS_ESCAPING.test(str)) {
      return '"' + str + '"';
    }

    // JSON.stringify escapes quotes, backslashes and control characters;
    // safeJsonLiteral adds line separators, surrogates and script delimiters.
    return safeJsonLiteral(str);
  },

  objectLiteral: function objectLiteral(obj) {
    // istanbul ignore next

    var _this = this;

    var pairs = [];

    Object.keys(obj).forEach(function (key) {
      var value = castChunk(obj[key], _this);
      if (value !== 'undefined') {
        pairs.push([_this.quotedString(key), ':', value]);
      }
    });

    var ret = this.generateList(pairs);
    ret.prepend('{');
    ret.add('}');
    return ret;
  },

  generateList: function generateList(entries) {
    var ret = this.empty();

    for (var i = 0, len = entries.length; i < len; i++) {
      if (i) {
        ret.add(',');
      }

      ret.add(castChunk(entries[i], this));
    }

    return ret;
  },

  generateArray: function generateArray(entries) {
    var ret = this.generateList(entries);
    ret.prepend('[');
    ret.add(']');

    return ret;
  }
};

exports['default'] = CodeGen;
//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uL2xpYi9oYW5kbGViYXJzL2NvbXBpbGVyL2NvZGUtZ2VuLmpzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiI7Ozs7OztxQkFDd0IsVUFBVTs7Ozs7O0FBTWxDLElBQU0sY0FBYyxHQUFHLDBDQUEwQyxDQUFDOzs7OztBQUtsRSxJQUFNLGlCQUFpQixHQUFHLHNCQUFzQixDQUFDOzs7Ozs7Ozs7Ozs7QUFZakQsU0FBUyxzQkFBc0IsQ0FBQyxJQUFJLEVBQUU7QUFDcEMsU0FBTyxJQUFJLENBQUMsT0FBTyxDQUFDLGlCQUFpQixFQUFFLFNBQVMsQ0FBQyxDQUFDO0NBQ25EOzs7Ozs7OztBQVFELFNBQVMsZUFBZSxDQUFDLFNBQVMsRUFBRTtBQUNsQyxTQUFPLEtBQUssR0FBRyxTQUFTLENBQUMsVUFBVSxDQUFDLENBQUMsQ0FBQyxDQUFDLFFBQVEsQ0FBQyxFQUFFLENBQUMsQ0FBQztDQUNyRDs7Ozs7Ozs7Ozs7O0FBV00sU0FBUyxlQUFlLENBQUMsS0FBSyxFQUFFO0FBQ3JDLFNBQU8sc0JBQXNCLENBQzNCLElBQUksQ0FBQyxTQUFTLENBQUMsS0FBSyxDQUFDLENBQ2xCLE9BQU8sQ0FBQyxTQUFTLEVBQUUsU0FBUyxDQUFDLENBQzdCLE9BQU8sQ0FBQyxTQUFTLEVBQUUsU0FBUyxDQUFDOzs7R0FHN0IsT0FBTyxDQUFDLGtCQUFrQixFQUFFLGVBQWUsQ0FBQyxDQUNoRCxDQUFDO0NBQ0g7O0FBRUQsSUFBSSxVQUFVLFlBQUEsQ0FBQzs7QUFFZixJQUFJOztBQUVGLE1BQUksT0FBTyxNQUFNLEtBQUssVUFBVSxJQUFJLENBQUMsTUFBTSxDQUFDLEdBQUcsRUFBRTs7O0FBRy9DLFFBQUksU0FBUyxHQUFHLE9BQU8sQ0FBQyxZQUFZLENBQUMsQ0FBQztBQUN0QyxjQUFVLEdBQUcsU0FBUyxDQUFDLFVBQVUsQ0FBQztHQUNuQztDQUNGLENBQUMsT0FBTyxHQUFHLEVBQUUsRUFFYjs7OztBQUFBLEFBR0QsSUFBSSxDQUFDLFVBQVUsRUFBRTtBQUNmLFlBQVUsR0FBRyxVQUFTLElBQUksRUFBRSxNQUFNLEVBQUUsT0FBTyxFQUFFLE1BQU0sRUFBRTtBQUNuRCxRQUFJLENBQUMsR0FBRyxHQUFHLEVBQUUsQ0FBQztBQUNkLFFBQUksTUFBTSxFQUFFO0FBQ1YsVUFBSSxDQUFDLEdBQUcsQ0FBQyxNQUFNLENBQUMsQ0FBQztLQUNsQjtHQUNGLENBQUM7O0FBRUYsWUFBVSxDQUFDLFNBQVMsR0FBRztBQUNyQixPQUFHLEVBQUUsYUFBUyxNQUFNLEVBQUU7QUFDcEIsVUFBSSxlQUFRLE1BQU0sQ0FBQyxFQUFFO0FBQ25CLGNBQU0sR0FBRyxNQUFNLENBQUMsSUFBSSxDQUFDLEVBQUUsQ0FBQyxDQUFDO09BQzFCO0FBQ0QsVUFBSSxDQUFDLEdBQUcsSUFBSSxNQUFNLENBQUM7S0FDcEI7QUFDRCxXQUFPLEVBQUUsaUJBQVMsTUFBTSxFQUFFO0FBQ3hCLFVBQUksZUFBUSxNQUFNLENBQUMsRUFBRTtBQUNuQixjQUFNLEdBQUcsTUFBTSxDQUFDLElBQUksQ0FBQyxFQUFFLENBQUMsQ0FBQztPQUMxQjtBQUNELFVBQUksQ0FBQyxHQUFHLEdBQUcsTUFBTSxHQUFHLElBQUksQ0FBQyxHQUFHLENBQUM7S0FDOUI7QUFDRCx5QkFBcUIsRUFBRSxpQ0FBVztBQUNoQyxhQUFPLEVBQUUsSUFBSSxFQUFFLElBQUksQ0FBQyxRQUFRLEVBQUUsRUFBRSxDQUFDO0tBQ2xDO0FBQ0QsWUFBUSxFQUFFLG9CQUFXO0FBQ25CLGFBQU8sSUFBSSxDQUFDLEdBQUcsQ0FBQztLQUNqQjtHQUNGLENBQUM7Q0FDSDs7QUFFRCxTQUFTLFNBQVMsQ0FBQyxLQUFLLEVBQUUsT0FBTyxFQUFFLEdBQUcsRUFBRTtBQUN0QyxNQUFJLGVBQVEsS0FBSyxDQUFDLEVBQUU7QUFDbEIsUUFBSSxHQUFHLEdBQUcsRUFBRSxDQUFDOztBQUViLFNBQUssSUFBSSxDQUFDLEdBQUcsQ0FBQyxFQUFFLEdBQUcsR0FBRyxLQUFLLENBQUMsTUFBTSxFQUFFLENBQUMsR0FBRyxHQUFHLEVBQUUsQ0FBQyxFQUFFLEVBQUU7QUFDaEQsU0FBRyxDQUFDLElBQUksQ0FBQyxPQUFPLENBQUMsSUFBSSxDQUFDLEtBQUssQ0FBQyxDQUFDLENBQUMsRUFBRSxHQUFHLENBQUMsQ0FBQyxDQUFDO0tBQ3ZDO0FBQ0QsV0FBTyxHQUFHLENBQUM7R0FDWixNQUFNLElBQUksT0FBTyxLQUFLLEtBQUssU0FBUyxJQUFJLE9BQU8sS0FBSyxLQUFLLFFBQVEsRUFBRTs7QUFFbEUsV0FBTyxLQUFLLEdBQUcsRUFBRSxDQUFDO0dBQ25CO0FBQ0QsU0FBTyxLQUFLLENBQUM7Q0FDZDs7QUFFRCxTQUFTLE9BQU8sQ0FBQyxPQUFPLEVBQUU7QUFDeEIsTUFBSSxDQUFDLE9BQU8sR0FBRyxPQUFPLENBQUM7QUFDdkIsTUFBSSxDQUFDLE1BQU0sR0FBRyxFQUFFLENBQUM7Q0FDbEI7O0FBRUQsT0FBTyxDQUFDLFNBQVMsR0FBRztBQUNsQixTQUFPLEVBQUEsbUJBQUc7QUFDUixXQUFPLENBQUMsSUFBSSxDQUFDLE1BQU0sQ0FBQyxNQUFNLENBQUM7R0FDNUI7QUFDRCxTQUFPLEVBQUUsaUJBQVMsTUFBTSxFQUFFLEdBQUcsRUFBRTtBQUM3QixRQUFJLENBQUMsTUFBTSxDQUFDLE9BQU8sQ0FBQyxJQUFJLENBQUMsSUFBSSxDQUFDLE1BQU0sRUFBRSxHQUFHLENBQUMsQ0FBQyxDQUFDO0dBQzdDO0FBQ0QsTUFBSSxFQUFFLGNBQVMsTUFBTSxFQUFFLEdBQUcsRUFBRTtBQUMxQixRQUFJLENBQUMsTUFBTSxDQUFDLElBQUksQ0FBQyxJQUFJLENBQUMsSUFBSSxDQUFDLE1BQU0sRUFBRSxHQUFHLENBQUMsQ0FBQyxDQUFDO0dBQzFDOztBQUVELE9BQUssRUFBRSxpQkFBVztBQUNoQixRQUFJLE1BQU0sR0FBRyxJQUFJLENBQUMsS0FBSyxFQUFFLENBQUM7QUFDMUIsUUFBSSxDQUFDLElBQUksQ0FBQyxVQUFTLElBQUksRUFBRTtBQUN2QixZQUFNLENBQUMsR0FBRyxDQUFDLENBQUMsSUFBSSxFQUFFLElBQUksRUFBRSxJQUFJLENBQUMsQ0FBQyxDQUFDO0tBQ2hDLENBQUMsQ0FBQztBQUNILFdBQU8sTUFBTSxDQUFDO0dBQ2Y7O0FBRUQsTUFBSSxFQUFFLGNBQVMsSUFBSSxFQUFFO0FBQ25CLFNBQUssSUFBSSxDQUFDLEdBQUcsQ0FBQyxFQUFFLEdBQUcsR0FBRyxJQUFJLENBQUMsTUFBTSxDQUFDLE1BQU0sRUFBRSxDQUFDLEdBQUcsR0FBRyxFQUFFLENBQUMsRUFBRSxFQUFFO0FBQ3RELFVBQUksQ0FBQyxJQUFJLENBQUMsTUFBTSxDQUFDLENBQUMsQ0FBQyxDQUFDLENBQUM7S0FDdEI7R0FDRjs7QUFFRCxPQUFLLEVBQUUsaUJBQVc7QUFDaEIsUUFBSSxHQUFHLEdBQUcsSUFBSSxDQUFDLGVBQWUsSUFBSSxFQUFFLEtBQUssRUFBRSxFQUFFLEVBQUUsQ0FBQztBQUNoRCxXQUFPLElBQUksVUFBVSxDQUFDLEdBQUcsQ0FBQyxLQUFLLENBQUMsSUFBSSxFQUFFLEdBQUcsQ0FBQyxLQUFLLENBQUMsTUFBTSxFQUFFLElBQUksQ0FBQyxPQUFPLENBQUMsQ0FBQztHQUN2RTtBQUNELE1BQUksRUFBRSxjQUFTLEtBQUssRUFBK0M7UUFBN0MsR0FBRyx5REFBRyxJQUFJLENBQUMsZUFBZSxJQUFJLEVBQUUsS0FBSyxFQUFFLEVBQUUsRUFBRTs7QUFDL0QsUUFBSSxLQUFLLFlBQVksVUFBVSxFQUFFO0FBQy9CLGFBQU8sS0FBSyxDQUFDO0tBQ2Q7O0FBRUQsU0FBSyxHQUFHLFNBQVMsQ0FBQyxLQUFLLEVBQUUsSUFBSSxFQUFFLEdBQUcsQ0FBQyxDQUFDOztBQUVwQyxXQUFPLElBQUksVUFBVSxDQUNuQixHQUFHLENBQUMsS0FBSyxDQUFDLElBQUksRUFDZCxHQUFHLENBQUMsS0FBSyxDQUFDLE1BQU0sRUFDaEIsSUFBSSxDQUFDLE9BQU8sRUFDWixLQUFLLENBQ04sQ0FBQztHQUNIOztBQUVELGNBQVksRUFBRSxzQkFBUyxFQUFFLEVBQUUsSUFBSSxFQUFFLE1BQU0sRUFBRTtBQUN2QyxVQUFNLEdBQUcsSUFBSSxDQUFDLFlBQVksQ0FBQyxNQUFNLENBQUMsQ0FBQztBQUNuQyxXQUFPLElBQUksQ0FBQyxJQUFJLENBQUMsQ0FBQyxFQUFFLEVBQUUsSUFBSSxHQUFHLEdBQUcsR0FBRyxJQUFJLEdBQUcsR0FBRyxHQUFHLEdBQUcsRUFBRSxNQUFNLEVBQUUsR0FBRyxDQUFDLENBQUMsQ0FBQztHQUNwRTs7QUFFRCxjQUFZLEVBQUUsc0JBQVMsR0FBRyxFQUFFO0FBQzFCLE9BQUcsR0FBRyxHQUFHLEdBQUcsRUFBRSxDQUFDOzs7QUFHZixRQUFJLENBQUMsY0FBYyxDQUFDLElBQUksQ0FBQyxHQUFHLENBQUMsRUFBRTtBQUM3QixhQUFPLEdBQUcsR0FBRyxHQUFHLEdBQUcsR0FBRyxDQUFDO0tBQ3hCOzs7O0FBSUQsV0FBTyxlQUFlLENBQUMsR0FBRyxDQUFDLENBQUM7R0FDN0I7O0FBRUQsZUFBYSxFQUFFLHVCQUFTLEdBQUcsRUFBRTs7Ozs7QUFDM0IsUUFBSSxLQUFLLEdBQUcsRUFBRSxDQUFDOztBQUVmLFVBQU0sQ0FBQyxJQUFJLENBQUMsR0FBRyxDQUFDLENBQUMsT0FBTyxDQUFDLFVBQUEsR0FBRyxFQUFJO0FBQzlCLFVBQUksS0FBSyxHQUFHLFNBQVMsQ0FBQyxHQUFHLENBQUMsR0FBRyxDQUFDLFFBQU8sQ0FBQztBQUN0QyxVQUFJLEtBQUssS0FBSyxXQUFXLEVBQUU7QUFDekIsYUFBSyxDQUFDLElBQUksQ0FBQyxDQUFDLE1BQUssWUFBWSxDQUFDLEdBQUcsQ0FBQyxFQUFFLEdBQUcsRUFBRSxLQUFLLENBQUMsQ0FBQyxDQUFDO09BQ2xEO0tBQ0YsQ0FBQyxDQUFDOztBQUVILFFBQUksR0FBRyxHQUFHLElBQUksQ0FBQyxZQUFZLENBQUMsS0FBSyxDQUFDLENBQUM7QUFDbkMsT0FBRyxDQUFDLE9BQU8sQ0FBQyxHQUFHLENBQUMsQ0FBQztBQUNqQixPQUFHLENBQUMsR0FBRyxDQUFDLEdBQUcsQ0FBQyxDQUFDO0FBQ2IsV0FBTyxHQUFHLENBQUM7R0FDWjs7QUFFRCxjQUFZLEVBQUUsc0JBQVMsT0FBTyxFQUFFO0FBQzlCLFFBQUksR0FBRyxHQUFHLElBQUksQ0FBQyxLQUFLLEVBQUUsQ0FBQzs7QUFFdkIsU0FBSyxJQUFJLENBQUMsR0FBRyxDQUFDLEVBQUUsR0FBRyxHQUFHLE9BQU8sQ0FBQyxNQUFNLEVBQUUsQ0FBQyxHQUFHLEdBQUcsRUFBRSxDQUFDLEVBQUUsRUFBRTtBQUNsRCxVQUFJLENBQUMsRUFBRTtBQUNMLFdBQUcsQ0FBQyxHQUFHLENBQUMsR0FBRyxDQUFDLENBQUM7T0FDZDs7QUFFRCxTQUFHLENBQUMsR0FBRyxDQUFDLFNBQVMsQ0FBQyxPQUFPLENBQUMsQ0FBQyxDQUFDLEVBQUUsSUFBSSxDQUFDLENBQUMsQ0FBQztLQUN0Qzs7QUFFRCxXQUFPLEdBQUcsQ0FBQztHQUNaOztBQUVELGVBQWEsRUFBRSx1QkFBUyxPQUFPLEVBQUU7QUFDL0IsUUFBSSxHQUFHLEdBQUcsSUFBSSxDQUFDLFlBQVksQ0FBQyxPQUFPLENBQUMsQ0FBQztBQUNyQyxPQUFHLENBQUMsT0FBTyxDQUFDLEdBQUcsQ0FBQyxDQUFDO0FBQ2pCLE9BQUcsQ0FBQyxHQUFHLENBQUMsR0FBRyxDQUFDLENBQUM7O0FBRWIsV0FBTyxHQUFHLENBQUM7R0FDWjtDQUNGLENBQUM7O3FCQUVhLE9BQU8iLCJmaWxlIjoiY29kZS1nZW4uanMiLCJzb3VyY2VzQ29udGVudCI6WyIvKiBnbG9iYWwgZGVmaW5lLCByZXF1aXJlICovXG5pbXBvcnQgeyBpc0FycmF5IH0gZnJvbSAnLi4vdXRpbHMnO1xuXG4vLyBNYXRjaGVzIGFueSBjaGFyYWN0ZXIgdGhhdCBxdW90ZWRTdHJpbmcgbWF5IGhhdmUgdG8gZXNjYXBlOiBxdW90ZXMsXG4vLyBiYWNrc2xhc2hlcywgY29udHJvbCBjaGFyYWN0ZXJzLCBsaW5lIHNlcGFyYXRvcnMsIHN1cnJvZ2F0ZXMgYW5kIFwiPFwiLiBNb3N0XG4vLyBzdHJpbmdzLCBlLmcuIGxvb2t1cCBuYW1lcywgY29udGFpbiBub25lIG9mIHRoZW0gYW5kIGNhbiBza2lwIHRoZSBlc2NhcGluZy5cbi8vIGVzbGludC1kaXNhYmxlLW5leHQtbGluZSBuby1jb250cm9sLXJlZ2V4XG5jb25zdCBORUVEU19FU0NBUElORyA9IC9bXFx4MDAtXFx4MWZcXFxcXCJcXHUyMDI4XFx1MjAyOTxcXHVkODAwLVxcdWRmZmZdLztcblxuLy8gUGVyIEhUTUwgXCJSZXN0cmljdGlvbnMgZm9yIGNvbnRlbnRzIG9mIHNjcmlwdCBlbGVtZW50c1wiOiBcIjwhLS1cIiwgXCI8c2NyaXB0XCJcbi8vIGFuZCBcIjwvc2NyaXB0XCIgY2FuIGVuZCBvciBjaGFuZ2UgdGhlIHBhcnNpbmcgb2YgYW4gZW5jbG9zaW5nIDxzY3JpcHQ+XG4vLyBlbGVtZW50LlxuY29uc3QgU0NSSVBUX0RFTElNSVRFUlMgPSAvPCg/PSEtLXxcXC8/c2NyaXB0KS9naTtcblxuLyoqXG4gKiBFc2NhcGUgdGhlIFwiPFwiIG9mIGV2ZXJ5IHNlcXVlbmNlIHRoYXQgY291bGQgZW5kIG9yIGNoYW5nZSB0aGUgcGFyc2luZyBvZlxuICogYW4gZW5jbG9zaW5nIDxzY3JpcHQ+IGVsZW1lbnQuXG4gKlxuICogT25seSBwYXNzIEphdmFTY3JpcHQgb3IgSlNPTiBjb2RlIGluIHdoaWNoIFwiPFwiIGNhbiBvbmx5IG9jY3VyIGluc2lkZSBzdHJpbmdcbiAqIGxpdGVyYWxzLCB3aGVyZSBcIlxcdTAwM0NcIiBoYXMgdGhlIHNhbWUgdmFsdWUuXG4gKlxuICogQHBhcmFtIHtzdHJpbmd9IGNvZGUgdGhlIGNvZGUgdG8gZXNjYXBlXG4gKiBAcmV0dXJucyB7c3RyaW5nfSB0aGUgZXNjYXBlZCBjb2RlXG4gKi9cbmZ1bmN0aW9uIGVzY2FwZVNjcmlwdERlbGltaXRlcnMoY29kZSkge1xuICByZXR1cm4gY29kZS5yZXBsYWNlKFNDUklQVF9ERUxJTUlURVJTLCAnXFxcXHUwMDNDJyk7XG59XG5cbi8qKlxuICogRXNjYXBlIGEgVVRGLTE2IHN1cnJvZ2F0ZSBjb2RlIHVuaXQgYXMgYSBcIlxcdVhYWFhcIiBlc2NhcGUgc2VxdWVuY2UuXG4gKlxuICogQHBhcmFtIHtzdHJpbmd9IHN1cnJvZ2F0ZSBhIHNpbmdsZSBzdXJyb2dhdGUgY29kZSB1bml0XG4gKiBAcmV0dXJucyB7c3RyaW5nfSB0aGUgZXNjYXBlIHNlcXVlbmNlXG4gKi9cbmZ1bmN0aW9uIGVzY2FwZVN1cnJvZ2F0ZShzdXJyb2dhdGUpIHtcbiAgcmV0dXJuICdcXFxcdScgKyBzdXJyb2dhdGUuY2hhckNvZGVBdCgwKS50b1N0cmluZygxNik7XG59XG5cbi8qKlxuICogQ29udmVydCBhIHZhbHVlIHRvIGEgSlNPTiBsaXRlcmFsIHRoYXQgaXMgc2FmZSB0byB3cml0ZSBpbnRvIGdlbmVyYXRlZFxuICogY29kZTogdmFsaWQgaW4gRVM1LCB3aGVyZSBsaW5lIGFuZCBwYXJhZ3JhcGggc2VwYXJhdG9ycyBlbmQgc3RyaW5nIGxpdGVyYWxzLFxuICogZW5jb2RhYmxlIGFzIFVURi04IG9uIGV2ZXJ5IGVuZ2luZSwgYW5kIHNhZmUgaW5zaWRlIGFuIGVuY2xvc2luZyA8c2NyaXB0PlxuICogZWxlbWVudC5cbiAqXG4gKiBAcGFyYW0geyp9IHZhbHVlIHRoZSB2YWx1ZSB0byBjb252ZXJ0XG4gKiBAcmV0dXJucyB7c3RyaW5nfSB0aGUgSmF2YVNjcmlwdCBsaXRlcmFsXG4gKi9cbmV4cG9ydCBmdW5jdGlvbiBzYWZlSnNvbkxpdGVyYWwodmFsdWUpIHtcbiAgcmV0dXJuIGVzY2FwZVNjcmlwdERlbGltaXRlcnMoXG4gICAgSlNPTi5zdHJpbmdpZnkodmFsdWUpXG4gICAgICAucmVwbGFjZSgvXFx1MjAyOC9nLCAnXFxcXHUyMDI4JylcbiAgICAgIC5yZXBsYWNlKC9cXHUyMDI5L2csICdcXFxcdTIwMjknKVxuICAgICAgLy8gRW5naW5lcyBiZWZvcmUgRVMyMDE5IGxlYXZlIGxvbmUgc3Vycm9nYXRlcyB1bmVzY2FwZWQsIGFuZCB0aG9zZSBjYW5ub3RcbiAgICAgIC8vIGJlIGVuY29kZWQgYXMgVVRGLTguIEVzY2FwaW5nIHBhaXJlZCBvbmVzIGFzIHdlbGwga2VlcHMgdGhlIHZhbHVlLlxuICAgICAgLnJlcGxhY2UoL1tcXHVkODAwLVxcdWRmZmZdL2csIGVzY2FwZVN1cnJvZ2F0ZSlcbiAgKTtcbn1cblxubGV0IFNvdXJjZU5vZGU7XG5cbnRyeSB7XG4gIC8qIGlzdGFuYnVsIGlnbm9yZSBuZXh0ICovXG4gIGlmICh0eXBlb2YgZGVmaW5lICE9PSAnZnVuY3Rpb24nIHx8ICFkZWZpbmUuYW1kKSB7XG4gICAgLy8gV2UgZG9uJ3Qgc3VwcG9ydCB0aGlzIGluIEFNRCBlbnZpcm9ubWVudHMuIEZvciB0aGVzZSBlbnZpcm9ubWVudHMsIHdlIGFzc3VtZSB0aGF0XG4gICAgLy8gdGhleSBhcmUgcnVubmluZyBvbiB0aGUgYnJvd3NlciBhbmQgdGh1cyBoYXZlIG5vIG5lZWQgZm9yIHRoZSBzb3VyY2UtbWFwIGxpYnJhcnkuXG4gICAgbGV0IFNvdXJjZU1hcCA9IHJlcXVpcmUoJ3NvdXJjZS1tYXAnKTtcbiAgICBTb3VyY2VOb2RlID0gU291cmNlTWFwLlNvdXJjZU5vZGU7XG4gIH1cbn0gY2F0Y2ggKGVycikge1xuICAvKiBOT1AgKi9cbn1cblxuLyogaXN0YW5idWwgaWdub3JlIGlmOiB0ZXN0ZWQgYnV0IG5vdCBjb3ZlcmVkIGluIGlzdGFuYnVsIGR1ZSB0byBkaXN0IGJ1aWxkICAqL1xuaWYgKCFTb3VyY2VOb2RlKSB7XG4gIFNvdXJjZU5vZGUgPSBmdW5jdGlvbihsaW5lLCBjb2x1bW4sIHNyY0ZpbGUsIGNodW5rcykge1xuICAgIHRoaXMuc3JjID0gJyc7XG4gICAgaWYgKGNodW5rcykge1xuICAgICAgdGhpcy5hZGQoY2h1bmtzKTtcbiAgICB9XG4gIH07XG4gIC8qIGlzdGFuYnVsIGlnbm9yZSBuZXh0ICovXG4gIFNvdXJjZU5vZGUucHJvdG90eXBlID0ge1xuICAgIGFkZDogZnVuY3Rpb24oY2h1bmtzKSB7XG4gICAgICBpZiAoaXNBcnJheShjaHVua3MpKSB7XG4gICAgICAgIGNodW5rcyA9IGNodW5rcy5qb2luKCcnKTtcbiAgICAgIH1cbiAgICAgIHRoaXMuc3JjICs9IGNodW5rcztcbiAgICB9LFxuICAgIHByZXBlbmQ6IGZ1bmN0aW9uKGNodW5rcykge1xuICAgICAgaWYgKGlzQXJyYXkoY2h1bmtzKSkge1xuICAgICAgICBjaHVua3MgPSBjaHVua3Muam9pbignJyk7XG4gICAgICB9XG4gICAgICB0aGlzLnNyYyA9IGNodW5rcyArIHRoaXMuc3JjO1xuICAgIH0sXG4gICAgdG9TdHJpbmdXaXRoU291cmNlTWFwOiBmdW5jdGlvbigpIHtcbiAgICAgIHJldHVybiB7IGNvZGU6IHRoaXMudG9TdHJpbmcoKSB9O1xuICAgIH0sXG4gICAgdG9TdHJpbmc6IGZ1bmN0aW9uKCkge1xuICAgICAgcmV0dXJuIHRoaXMuc3JjO1xuICAgIH1cbiAgfTtcbn1cblxuZnVuY3Rpb24gY2FzdENodW5rKGNodW5rLCBjb2RlR2VuLCBsb2MpIHtcbiAgaWYgKGlzQXJyYXkoY2h1bmspKSB7XG4gICAgbGV0IHJldCA9IFtdO1xuXG4gICAgZm9yIChsZXQgaSA9IDAsIGxlbiA9IGNodW5rLmxlbmd0aDsgaSA8IGxlbjsgaSsrKSB7XG4gICAgICByZXQucHVzaChjb2RlR2VuLndyYXAoY2h1bmtbaV0sIGxvYykpO1xuICAgIH1cbiAgICByZXR1cm4gcmV0O1xuICB9IGVsc2UgaWYgKHR5cGVvZiBjaHVuayA9PT0gJ2Jvb2xlYW4nIHx8IHR5cGVvZiBjaHVuayA9PT0gJ251bWJlcicpIHtcbiAgICAvLyBIYW5kbGUgcHJpbWl0aXZlcyB0aGF0IHRoZSBTb3VyY2VOb2RlIHdpbGwgdGhyb3cgdXAgb25cbiAgICByZXR1cm4gY2h1bmsgKyAnJztcbiAgfVxuICByZXR1cm4gY2h1bms7XG59XG5cbmZ1bmN0aW9uIENvZGVHZW4oc3JjRmlsZSkge1xuICB0aGlzLnNyY0ZpbGUgPSBzcmNGaWxlO1xuICB0aGlzLnNvdXJjZSA9IFtdO1xufVxuXG5Db2RlR2VuLnByb3RvdHlwZSA9IHtcbiAgaXNFbXB0eSgpIHtcbiAgICByZXR1cm4gIXRoaXMuc291cmNlLmxlbmd0aDtcbiAgfSxcbiAgcHJlcGVuZDogZnVuY3Rpb24oc291cmNlLCBsb2MpIHtcbiAgICB0aGlzLnNvdXJjZS51bnNoaWZ0KHRoaXMud3JhcChzb3VyY2UsIGxvYykpO1xuICB9LFxuICBwdXNoOiBmdW5jdGlvbihzb3VyY2UsIGxvYykge1xuICAgIHRoaXMuc291cmNlLnB1c2godGhpcy53cmFwKHNvdXJjZSwgbG9jKSk7XG4gIH0sXG5cbiAgbWVyZ2U6IGZ1bmN0aW9uKCkge1xuICAgIGxldCBzb3VyY2UgPSB0aGlzLmVtcHR5KCk7XG4gICAgdGhpcy5lYWNoKGZ1bmN0aW9uKGxpbmUpIHtcbiAgICAgIHNvdXJjZS5hZGQoWycgICcsIGxpbmUsICdcXG4nXSk7XG4gICAgfSk7XG4gICAgcmV0dXJuIHNvdXJjZTtcbiAgfSxcblxuICBlYWNoOiBmdW5jdGlvbihpdGVyKSB7XG4gICAgZm9yIChsZXQgaSA9IDAsIGxlbiA9IHRoaXMuc291cmNlLmxlbmd0aDsgaSA8IGxlbjsgaSsrKSB7XG4gICAgICBpdGVyKHRoaXMuc291cmNlW2ldKTtcbiAgICB9XG4gIH0sXG5cbiAgZW1wdHk6IGZ1bmN0aW9uKCkge1xuICAgIGxldCBsb2MgPSB0aGlzLmN1cnJlbnRMb2NhdGlvbiB8fCB7IHN0YXJ0OiB7fSB9O1xuICAgIHJldHVybiBuZXcgU291cmNlTm9kZShsb2Muc3RhcnQubGluZSwgbG9jLnN0YXJ0LmNvbHVtbiwgdGhpcy5zcmNGaWxlKTtcbiAgfSxcbiAgd3JhcDogZnVuY3Rpb24oY2h1bmssIGxvYyA9IHRoaXMuY3VycmVudExvY2F0aW9uIHx8IHsgc3RhcnQ6IHt9IH0pIHtcbiAgICBpZiAoY2h1bmsgaW5zdGFuY2VvZiBTb3VyY2VOb2RlKSB7XG4gICAgICByZXR1cm4gY2h1bms7XG4gICAgfVxuXG4gICAgY2h1bmsgPSBjYXN0Q2h1bmsoY2h1bmssIHRoaXMsIGxvYyk7XG5cbiAgICByZXR1cm4gbmV3IFNvdXJjZU5vZGUoXG4gICAgICBsb2Muc3RhcnQubGluZSxcbiAgICAgIGxvYy5zdGFydC5jb2x1bW4sXG4gICAgICB0aGlzLnNyY0ZpbGUsXG4gICAgICBjaHVua1xuICAgICk7XG4gIH0sXG5cbiAgZnVuY3Rpb25DYWxsOiBmdW5jdGlvbihmbiwgdHlwZSwgcGFyYW1zKSB7XG4gICAgcGFyYW1zID0gdGhpcy5nZW5lcmF0ZUxpc3QocGFyYW1zKTtcbiAgICByZXR1cm4gdGhpcy53cmFwKFtmbiwgdHlwZSA/ICcuJyArIHR5cGUgKyAnKCcgOiAnKCcsIHBhcmFtcywgJyknXSk7XG4gIH0sXG5cbiAgcXVvdGVkU3RyaW5nOiBmdW5jdGlvbihzdHIpIHtcbiAgICBzdHIgPSBzdHIgKyAnJztcbiAgICAvLyBGYXN0IHBhdGg6IHF1b3RlZFN0cmluZyBydW5zIGZvciBldmVyeSBsb29rdXAgbmFtZSBhbmQgY29udGVudCBzdHJpbmcsXG4gICAgLy8gYW5kIHRoZSBjaGFpbiBvZiByZXBsYWNlbWVudHMgYmVsb3cgaXMgbXVjaCBzbG93ZXIgdGhhbiBhIHNpbmdsZSB0ZXN0LlxuICAgIGlmICghTkVFRFNfRVNDQVBJTkcudGVzdChzdHIpKSB7XG4gICAgICByZXR1cm4gJ1wiJyArIHN0ciArICdcIic7XG4gICAgfVxuXG4gICAgLy8gSlNPTi5zdHJpbmdpZnkgZXNjYXBlcyBxdW90ZXMsIGJhY2tzbGFzaGVzIGFuZCBjb250cm9sIGNoYXJhY3RlcnM7XG4gICAgLy8gc2FmZUpzb25MaXRlcmFsIGFkZHMgbGluZSBzZXBhcmF0b3JzLCBzdXJyb2dhdGVzIGFuZCBzY3JpcHQgZGVsaW1pdGVycy5cbiAgICByZXR1cm4gc2FmZUpzb25MaXRlcmFsKHN0cik7XG4gIH0sXG5cbiAgb2JqZWN0TGl0ZXJhbDogZnVuY3Rpb24ob2JqKSB7XG4gICAgbGV0IHBhaXJzID0gW107XG5cbiAgICBPYmplY3Qua2V5cyhvYmopLmZvckVhY2goa2V5ID0+IHtcbiAgICAgIGxldCB2YWx1ZSA9IGNhc3RDaHVuayhvYmpba2V5XSwgdGhpcyk7XG4gICAgICBpZiAodmFsdWUgIT09ICd1bmRlZmluZWQnKSB7XG4gICAgICAgIHBhaXJzLnB1c2goW3RoaXMucXVvdGVkU3RyaW5nKGtleSksICc6JywgdmFsdWVdKTtcbiAgICAgIH1cbiAgICB9KTtcblxuICAgIGxldCByZXQgPSB0aGlzLmdlbmVyYXRlTGlzdChwYWlycyk7XG4gICAgcmV0LnByZXBlbmQoJ3snKTtcbiAgICByZXQuYWRkKCd9Jyk7XG4gICAgcmV0dXJuIHJldDtcbiAgfSxcblxuICBnZW5lcmF0ZUxpc3Q6IGZ1bmN0aW9uKGVudHJpZXMpIHtcbiAgICBsZXQgcmV0ID0gdGhpcy5lbXB0eSgpO1xuXG4gICAgZm9yIChsZXQgaSA9IDAsIGxlbiA9IGVudHJpZXMubGVuZ3RoOyBpIDwgbGVuOyBpKyspIHtcbiAgICAgIGlmIChpKSB7XG4gICAgICAgIHJldC5hZGQoJywnKTtcbiAgICAgIH1cblxuICAgICAgcmV0LmFkZChjYXN0Q2h1bmsoZW50cmllc1tpXSwgdGhpcykpO1xuICAgIH1cblxuICAgIHJldHVybiByZXQ7XG4gIH0sXG5cbiAgZ2VuZXJhdGVBcnJheTogZnVuY3Rpb24oZW50cmllcykge1xuICAgIGxldCByZXQgPSB0aGlzLmdlbmVyYXRlTGlzdChlbnRyaWVzKTtcbiAgICByZXQucHJlcGVuZCgnWycpO1xuICAgIHJldC5hZGQoJ10nKTtcblxuICAgIHJldHVybiByZXQ7XG4gIH1cbn07XG5cbmV4cG9ydCBkZWZhdWx0IENvZGVHZW47XG4iXX0=
