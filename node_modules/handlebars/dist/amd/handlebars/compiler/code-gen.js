define(['exports', '../utils'], function (exports, _utils) {
  /* global define, require */
  'use strict';

  exports.__esModule = true;
  exports.safeJsonLiteral = safeJsonLiteral;

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
});
//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uL2xpYi9oYW5kbGViYXJzL2NvbXBpbGVyL2NvZGUtZ2VuLmpzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiI7Ozs7Ozs7Ozs7O0FBT0EsTUFBTSxjQUFjLEdBQUcsMENBQTBDLENBQUM7Ozs7O0FBS2xFLE1BQU0saUJBQWlCLEdBQUcsc0JBQXNCLENBQUM7Ozs7Ozs7Ozs7OztBQVlqRCxXQUFTLHNCQUFzQixDQUFDLElBQUksRUFBRTtBQUNwQyxXQUFPLElBQUksQ0FBQyxPQUFPLENBQUMsaUJBQWlCLEVBQUUsU0FBUyxDQUFDLENBQUM7R0FDbkQ7Ozs7Ozs7O0FBUUQsV0FBUyxlQUFlLENBQUMsU0FBUyxFQUFFO0FBQ2xDLFdBQU8sS0FBSyxHQUFHLFNBQVMsQ0FBQyxVQUFVLENBQUMsQ0FBQyxDQUFDLENBQUMsUUFBUSxDQUFDLEVBQUUsQ0FBQyxDQUFDO0dBQ3JEOzs7Ozs7Ozs7Ozs7QUFXTSxXQUFTLGVBQWUsQ0FBQyxLQUFLLEVBQUU7QUFDckMsV0FBTyxzQkFBc0IsQ0FDM0IsSUFBSSxDQUFDLFNBQVMsQ0FBQyxLQUFLLENBQUMsQ0FDbEIsT0FBTyxDQUFDLFNBQVMsRUFBRSxTQUFTLENBQUMsQ0FDN0IsT0FBTyxDQUFDLFNBQVMsRUFBRSxTQUFTLENBQUM7OztLQUc3QixPQUFPLENBQUMsa0JBQWtCLEVBQUUsZUFBZSxDQUFDLENBQ2hELENBQUM7R0FDSDs7QUFFRCxNQUFJLFVBQVUsWUFBQSxDQUFDOztBQUVmLE1BQUk7O0FBRUYsUUFBSSxPQUFPLE1BQU0sS0FBSyxVQUFVLElBQUksQ0FBQyxNQUFNLENBQUMsR0FBRyxFQUFFOzs7QUFHL0MsVUFBSSxTQUFTLEdBQUcsT0FBTyxDQUFDLFlBQVksQ0FBQyxDQUFDO0FBQ3RDLGdCQUFVLEdBQUcsU0FBUyxDQUFDLFVBQVUsQ0FBQztLQUNuQztHQUNGLENBQUMsT0FBTyxHQUFHLEVBQUUsRUFFYjs7OztBQUFBLEFBR0QsTUFBSSxDQUFDLFVBQVUsRUFBRTtBQUNmLGNBQVUsR0FB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c0JBQVMsT0FBTyxFQUFFO0FBQzlCLFVBQUksR0FBRyxHQUFHLElBQUksQ0FBQyxLQUFLLEVBQUUsQ0FBQzs7QUFFdkIsV0FBSyxJQUFJLENBQUMsR0FBRyxDQUFDLEVBQUUsR0FBRyxHQUFHLE9BQU8sQ0FBQyxNQUFNLEVBQUUsQ0FBQyxHQUFHLEdBQUcsRUFBRSxDQUFDLEVBQUUsRUFBRTtBQUNsRCxZQUFJLENBQUMsRUFBRTtBQUNMLGFBQUcsQ0FBQyxHQUFHLENBQUMsR0FBRyxDQUFDLENBQUM7U0FDZDs7QUFFRCxXQUFHLENBQUMsR0FBRyxDQUFDLFNBQVMsQ0FBQyxPQUFPLENBQUMsQ0FBQyxDQUFDLEVBQUUsSUFBSSxDQUFDLENBQUMsQ0FBQztPQUN0Qzs7QUFFRCxhQUFPLEdBQUcsQ0FBQztLQUNaOztBQUVELGlCQUFhLEVBQUUsdUJBQVMsT0FBTyxFQUFFO0FBQy9CLFVBQUksR0FBRyxHQUFHLElBQUksQ0FBQyxZQUFZLENBQUMsT0FBTyxDQUFDLENBQUM7QUFDckMsU0FBRyxDQUFDLE9BQU8sQ0FBQyxHQUFHLENBQUMsQ0FBQztBQUNqQixTQUFHLENBQUMsR0FBRyxDQUFDLEdBQUcsQ0FBQyxDQUFDOztBQUViLGFBQU8sR0FBRyxDQUFDO0tBQ1o7R0FDRixDQUFDOzt1QkFFYSxPQUFPIiwiZmlsZSI6ImNvZGUtZ2VuLmpzIiwic291cmNlc0NvbnRlbnQiOlsiLyogZ2xvYmFsIGRlZmluZSwgcmVxdWlyZSAqL1xuaW1wb3J0IHsgaXNBcnJheSB9IGZyb20gJy4uL3V0aWxzJztcblxuLy8gTWF0Y2hlcyBhbnkgY2hhcmFjdGVyIHRoYXQgcXVvdGVkU3RyaW5nIG1heSBoYXZlIHRvIGVzY2FwZTogcXVvdGVzLFxuLy8gYmFja3NsYXNoZXMsIGNvbnRyb2wgY2hhcmFjdGVycywgbGluZSBzZXBhcmF0b3JzLCBzdXJyb2dhdGVzIGFuZCBcIjxcIi4gTW9zdFxuLy8gc3RyaW5ncywgZS5nLiBsb29rdXAgbmFtZXMsIGNvbnRhaW4gbm9uZSBvZiB0aGVtIGFuZCBjYW4gc2tpcCB0aGUgZXNjYXBpbmcuXG4vLyBlc2xpbnQtZGlzYWJsZS1uZXh0LWxpbmUgbm8tY29udHJvbC1yZWdleFxuY29uc3QgTkVFRFNfRVNDQVBJTkcgPSAvW1xceDAwLVxceDFmXFxcXFwiXFx1MjAyOFxcdTIwMjk8XFx1ZDgwMC1cXHVkZmZmXS87XG5cbi8vIFBlciBIVE1MIFwiUmVzdHJpY3Rpb25zIGZvciBjb250ZW50cyBvZiBzY3JpcHQgZWxlbWVudHNcIjogXCI8IS0tXCIsIFwiPHNjcmlwdFwiXG4vLyBhbmQgXCI8L3NjcmlwdFwiIGNhbiBlbmQgb3IgY2hhbmdlIHRoZSBwYXJzaW5nIG9mIGFuIGVuY2xvc2luZyA8c2NyaXB0PlxuLy8gZWxlbWVudC5cbmNvbnN0IFNDUklQVF9ERUxJTUlURVJTID0gLzwoPz0hLS18XFwvP3NjcmlwdCkvZ2k7XG5cbi8qKlxuICogRXNjYXBlIHRoZSBcIjxcIiBvZiBldmVyeSBzZXF1ZW5jZSB0aGF0IGNvdWxkIGVuZCBvciBjaGFuZ2UgdGhlIHBhcnNpbmcgb2ZcbiAqIGFuIGVuY2xvc2luZyA8c2NyaXB0PiBlbGVtZW50LlxuICpcbiAqIE9ubHkgcGFzcyBKYXZhU2NyaXB0IG9yIEpTT04gY29kZSBpbiB3aGljaCBcIjxcIiBjYW4gb25seSBvY2N1ciBpbnNpZGUgc3RyaW5nXG4gKiBsaXRlcmFscywgd2hlcmUgXCJcXHUwMDNDXCIgaGFzIHRoZSBzYW1lIHZhbHVlLlxuICpcbiAqIEBwYXJhbSB7c3RyaW5nfSBjb2RlIHRoZSBjb2RlIHRvIGVzY2FwZVxuICogQHJldHVybnMge3N0cmluZ30gdGhlIGVzY2FwZWQgY29kZVxuICovXG5mdW5jdGlvbiBlc2NhcGVTY3JpcHREZWxpbWl0ZXJzKGNvZGUpIHtcbiAgcmV0dXJuIGNvZGUucmVwbGFjZShTQ1JJUFRfREVMSU1JVEVSUywgJ1xcXFx1MDAzQycpO1xufVxuXG4vKipcbiAqIEVzY2FwZSBhIFVURi0xNiBzdXJyb2dhdGUgY29kZSB1bml0IGFzIGEgXCJcXHVYWFhYXCIgZXNjYXBlIHNlcXVlbmNlLlxuICpcbiAqIEBwYXJhbSB7c3RyaW5nfSBzdXJyb2dhdGUgYSBzaW5nbGUgc3Vycm9nYXRlIGNvZGUgdW5pdFxuICogQHJldHVybnMge3N0cmluZ30gdGhlIGVzY2FwZSBzZXF1ZW5jZVxuICovXG5mdW5jdGlvbiBlc2NhcGVTdXJyb2dhdGUoc3Vycm9nYXRlKSB7XG4gIHJldHVybiAnXFxcXHUnICsgc3Vycm9nYXRlLmNoYXJDb2RlQXQoMCkudG9TdHJpbmcoMTYpO1xufVxuXG4vKipcbiAqIENvbnZlcnQgYSB2YWx1ZSB0byBhIEpTT04gbGl0ZXJhbCB0aGF0IGlzIHNhZmUgdG8gd3JpdGUgaW50byBnZW5lcmF0ZWRcbiAqIGNvZGU6IHZhbGlkIGluIEVTNSwgd2hlcmUgbGluZSBhbmQgcGFyYWdyYXBoIHNlcGFyYXRvcnMgZW5kIHN0cmluZyBsaXRlcmFscyxcbiAqIGVuY29kYWJsZSBhcyBVVEYtOCBvbiBldmVyeSBlbmdpbmUsIGFuZCBzYWZlIGluc2lkZSBhbiBlbmNsb3NpbmcgPHNjcmlwdD5cbiAqIGVsZW1lbnQuXG4gKlxuICogQHBhcmFtIHsqfSB2YWx1ZSB0aGUgdmFsdWUgdG8gY29udmVydFxuICogQHJldHVybnMge3N0cmluZ30gdGhlIEphdmFTY3JpcHQgbGl0ZXJhbFxuICovXG5leHBvcnQgZnVuY3Rpb24gc2FmZUpzb25MaXRlcmFsKHZhbHVlKSB7XG4gIHJldHVybiBlc2NhcGVTY3JpcHREZWxpbWl0ZXJzKFxuICAgIEpTT04uc3RyaW5naWZ5KHZhbHVlKVxuICAgICAgLnJlcGxhY2UoL1xcdTIwMjgvZywgJ1xcXFx1MjAyOCcpXG4gICAgICAucmVwbGFjZSgvXFx1MjAyOS9nLCAnXFxcXHUyMDI5JylcbiAgICAgIC8vIEVuZ2luZXMgYmVmb3JlIEVTMjAxOSBsZWF2ZSBsb25lIHN1cnJvZ2F0ZXMgdW5lc2NhcGVkLCBhbmQgdGhvc2UgY2Fubm90XG4gICAgICAvLyBiZSBlbmNvZGVkIGFzIFVURi04LiBFc2NhcGluZyBwYWlyZWQgb25lcyBhcyB3ZWxsIGtlZXBzIHRoZSB2YWx1ZS5cbiAgICAgIC5yZXBsYWNlKC9bXFx1ZDgwMC1cXHVkZmZmXS9nLCBlc2NhcGVTdXJyb2dhdGUpXG4gICk7XG59XG5cbmxldCBTb3VyY2VOb2RlO1xuXG50cnkge1xuICAvKiBpc3RhbmJ1bCBpZ25vcmUgbmV4dCAqL1xuICBpZiAodHlwZW9mIGRlZmluZSAhPT0gJ2Z1bmN0aW9uJyB8fCAhZGVmaW5lLmFtZCkge1xuICAgIC8vIFdlIGRvbid0IHN1cHBvcnQgdGhpcyBpbiBBTUQgZW52aXJvbm1lbnRzLiBGb3IgdGhlc2UgZW52aXJvbm1lbnRzLCB3ZSBhc3N1bWUgdGhhdFxuICAgIC8vIHRoZXkgYXJlIHJ1bm5pbmcgb24gdGhlIGJyb3dzZXIgYW5kIHRodXMgaGF2ZSBubyBuZWVkIGZvciB0aGUgc291cmNlLW1hcCBsaWJyYXJ5LlxuICAgIGxldCBTb3VyY2VNYXAgPSByZXF1aXJlKCdzb3VyY2UtbWFwJyk7XG4gICAgU291cmNlTm9kZSA9IFNvdXJjZU1hcC5Tb3VyY2VOb2RlO1xuICB9XG59IGNhdGNoIChlcnIpIHtcbiAgLyogTk9QICovXG59XG5cbi8qIGlzdGFuYnVsIGlnbm9yZSBpZjogdGVzdGVkIGJ1dCBub3QgY292ZXJlZCBpbiBpc3RhbmJ1bCBkdWUgdG8gZGlzdCBidWlsZCAgKi9cbmlmICghU291cmNlTm9kZSkge1xuICBTb3VyY2VOb2RlID0gZnVuY3Rpb24obGluZSwgY29sdW1uLCBzcmNGaWxlLCBjaHVua3MpIHtcbiAgICB0aGlzLnNyYyA9ICcnO1xuICAgIGlmIChjaHVua3MpIHtcbiAgICAgIHRoaXMuYWRkKGNodW5rcyk7XG4gICAgfVxuICB9O1xuICAvKiBpc3RhbmJ1bCBpZ25vcmUgbmV4dCAqL1xuICBTb3VyY2VOb2RlLnByb3RvdHlwZSA9IHtcbiAgICBhZGQ6IGZ1bmN0aW9uKGNodW5rcykge1xuICAgICAgaWYgKGlzQXJyYXkoY2h1bmtzKSkge1xuICAgICAgICBjaHVua3MgPSBjaHVua3Muam9pbignJyk7XG4gICAgICB9XG4gICAgICB0aGlzLnNyYyArPSBjaHVua3M7XG4gICAgfSxcbiAgICBwcmVwZW5kOiBmdW5jdGlvbihjaHVua3MpIHtcbiAgICAgIGlmIChpc0FycmF5KGNodW5rcykpIHtcbiAgICAgICAgY2h1bmtzID0gY2h1bmtzLmpvaW4oJycpO1xuICAgICAgfVxuICAgICAgdGhpcy5zcmMgPSBjaHVua3MgKyB0aGlzLnNyYztcbiAgICB9LFxuICAgIHRvU3RyaW5nV2l0aFNvdXJjZU1hcDogZnVuY3Rpb24oKSB7XG4gICAgICByZXR1cm4geyBjb2RlOiB0aGlzLnRvU3RyaW5nKCkgfTtcbiAgICB9LFxuICAgIHRvU3RyaW5nOiBmdW5jdGlvbigpIHtcbiAgICAgIHJldHVybiB0aGlzLnNyYztcbiAgICB9XG4gIH07XG59XG5cbmZ1bmN0aW9uIGNhc3RDaHVuayhjaHVuaywgY29kZUdlbiwgbG9jKSB7XG4gIGlmIChpc0FycmF5KGNodW5rKSkge1xuICAgIGxldCByZXQgPSBbXTtcblxuICAgIGZvciAobGV0IGkgPSAwLCBsZW4gPSBjaHVuay5sZW5ndGg7IGkgPCBsZW47IGkrKykge1xuICAgICAgcmV0LnB1c2goY29kZUdlbi53cmFwKGNodW5rW2ldLCBsb2MpKTtcbiAgICB9XG4gICAgcmV0dXJuIHJldDtcbiAgfSBlbHNlIGlmICh0eXBlb2YgY2h1bmsgPT09ICdib29sZWFuJyB8fCB0eXBlb2YgY2h1bmsgPT09ICdudW1iZXInKSB7XG4gICAgLy8gSGFuZGxlIHByaW1pdGl2ZXMgdGhhdCB0aGUgU291cmNlTm9kZSB3aWxsIHRocm93IHVwIG9uXG4gICAgcmV0dXJuIGNodW5rICsgJyc7XG4gIH1cbiAgcmV0dXJuIGNodW5rO1xufVxuXG5mdW5jdGlvbiBDb2RlR2VuKHNyY0ZpbGUpIHtcbiAgdGhpcy5zcmNGaWxlID0gc3JjRmlsZTtcbiAgdGhpcy5zb3VyY2UgPSBbXTtcbn1cblxuQ29kZUdlbi5wcm90b3R5cGUgPSB7XG4gIGlzRW1wdHkoKSB7XG4gICAgcmV0dXJuICF0aGlzLnNvdXJjZS5sZW5ndGg7XG4gIH0sXG4gIHByZXBlbmQ6IGZ1bmN0aW9uKHNvdXJjZSwgbG9jKSB7XG4gICAgdGhpcy5zb3VyY2UudW5zaGlmdCh0aGlzLndyYXAoc291cmNlLCBsb2MpKTtcbiAgfSxcbiAgcHVzaDogZnVuY3Rpb24oc291cmNlLCBsb2MpIHtcbiAgICB0aGlzLnNvdXJjZS5wdXNoKHRoaXMud3JhcChzb3VyY2UsIGxvYykpO1xuICB9LFxuXG4gIG1lcmdlOiBmdW5jdGlvbigpIHtcbiAgICBsZXQgc291cmNlID0gdGhpcy5lbXB0eSgpO1xuICAgIHRoaXMuZWFjaChmdW5jdGlvbihsaW5lKSB7XG4gICAgICBzb3VyY2UuYWRkKFsnICAnLCBsaW5lLCAnXFxuJ10pO1xuICAgIH0pO1xuICAgIHJldHVybiBzb3VyY2U7XG4gIH0sXG5cbiAgZWFjaDogZnVuY3Rpb24oaXRlcikge1xuICAgIGZvciAobGV0IGkgPSAwLCBsZW4gPSB0aGlzLnNvdXJjZS5sZW5ndGg7IGkgPCBsZW47IGkrKykge1xuICAgICAgaXRlcih0aGlzLnNvdXJjZVtpXSk7XG4gICAgfVxuICB9LFxuXG4gIGVtcHR5OiBmdW5jdGlvbigpIHtcbiAgICBsZXQgbG9jID0gdGhpcy5jdXJyZW50TG9jYXRpb24gfHwgeyBzdGFydDoge30gfTtcbiAgICByZXR1cm4gbmV3IFNvdXJjZU5vZGUobG9jLnN0YXJ0LmxpbmUsIGxvYy5zdGFydC5jb2x1bW4sIHRoaXMuc3JjRmlsZSk7XG4gIH0sXG4gIHdyYXA6IGZ1bmN0aW9uKGNodW5rLCBsb2MgPSB0aGlzLmN1cnJlbnRMb2NhdGlvbiB8fCB7IHN0YXJ0OiB7fSB9KSB7XG4gICAgaWYgKGNodW5rIGluc3RhbmNlb2YgU291cmNlTm9kZSkge1xuICAgICAgcmV0dXJuIGNodW5rO1xuICAgIH1cblxuICAgIGNodW5rID0gY2FzdENodW5rKGNodW5rLCB0aGlzLCBsb2MpO1xuXG4gICAgcmV0dXJuIG5ldyBTb3VyY2VOb2RlKFxuICAgICAgbG9jLnN0YXJ0LmxpbmUsXG4gICAgICBsb2Muc3RhcnQuY29sdW1uLFxuICAgICAgdGhpcy5zcmNGaWxlLFxuICAgICAgY2h1bmtcbiAgICApO1xuICB9LFxuXG4gIGZ1bmN0aW9uQ2FsbDogZnVuY3Rpb24oZm4sIHR5cGUsIHBhcmFtcykge1xuICAgIHBhcmFtcyA9IHRoaXMuZ2VuZXJhdGVMaXN0KHBhcmFtcyk7XG4gICAgcmV0dXJuIHRoaXMud3JhcChbZm4sIHR5cGUgPyAnLicgKyB0eXBlICsgJygnIDogJygnLCBwYXJhbXMsICcpJ10pO1xuICB9LFxuXG4gIHF1b3RlZFN0cmluZzogZnVuY3Rpb24oc3RyKSB7XG4gICAgc3RyID0gc3RyICsgJyc7XG4gICAgLy8gRmFzdCBwYXRoOiBxdW90ZWRTdHJpbmcgcnVucyBmb3IgZXZlcnkgbG9va3VwIG5hbWUgYW5kIGNvbnRlbnQgc3RyaW5nLFxuICAgIC8vIGFuZCB0aGUgY2hhaW4gb2YgcmVwbGFjZW1lbnRzIGJlbG93IGlzIG11Y2ggc2xvd2VyIHRoYW4gYSBzaW5nbGUgdGVzdC5cbiAgICBpZiAoIU5FRURTX0VTQ0FQSU5HLnRlc3Qoc3RyKSkge1xuICAgICAgcmV0dXJuICdcIicgKyBzdHIgKyAnXCInO1xuICAgIH1cblxuICAgIC8vIEpTT04uc3RyaW5naWZ5IGVzY2FwZXMgcXVvdGVzLCBiYWNrc2xhc2hlcyBhbmQgY29udHJvbCBjaGFyYWN0ZXJzO1xuICAgIC8vIHNhZmVKc29uTGl0ZXJhbCBhZGRzIGxpbmUgc2VwYXJhdG9ycywgc3Vycm9nYXRlcyBhbmQgc2NyaXB0IGRlbGltaXRlcnMuXG4gICAgcmV0dXJuIHNhZmVKc29uTGl0ZXJhbChzdHIpO1xuICB9LFxuXG4gIG9iamVjdExpdGVyYWw6IGZ1bmN0aW9uKG9iaikge1xuICAgIGxldCBwYWlycyA9IFtdO1xuXG4gICAgT2JqZWN0LmtleXMob2JqKS5mb3JFYWNoKGtleSA9PiB7XG4gICAgICBsZXQgdmFsdWUgPSBjYXN0Q2h1bmsob2JqW2tleV0sIHRoaXMpO1xuICAgICAgaWYgKHZhbHVlICE9PSAndW5kZWZpbmVkJykge1xuICAgICAgICBwYWlycy5wdXNoKFt0aGlzLnF1b3RlZFN0cmluZyhrZXkpLCAnOicsIHZhbHVlXSk7XG4gICAgICB9XG4gICAgfSk7XG5cbiAgICBsZXQgcmV0ID0gdGhpcy5nZW5lcmF0ZUxpc3QocGFpcnMpO1xuICAgIHJldC5wcmVwZW5kKCd7Jyk7XG4gICAgcmV0LmFkZCgnfScpO1xuICAgIHJldHVybiByZXQ7XG4gIH0sXG5cbiAgZ2VuZXJhdGVMaXN0OiBmdW5jdGlvbihlbnRyaWVzKSB7XG4gICAgbGV0IHJldCA9IHRoaXMuZW1wdHkoKTtcblxuICAgIGZvciAobGV0IGkgPSAwLCBsZW4gPSBlbnRyaWVzLmxlbmd0aDsgaSA8IGxlbjsgaSsrKSB7XG4gICAgICBpZiAoaSkge1xuICAgICAgICByZXQuYWRkKCcsJyk7XG4gICAgICB9XG5cbiAgICAgIHJldC5hZGQoY2FzdENodW5rKGVudHJpZXNbaV0sIHRoaXMpKTtcbiAgICB9XG5cbiAgICByZXR1cm4gcmV0O1xuICB9LFxuXG4gIGdlbmVyYXRlQXJyYXk6IGZ1bmN0aW9uKGVudHJpZXMpIHtcbiAgICBsZXQgcmV0ID0gdGhpcy5nZW5lcmF0ZUxpc3QoZW50cmllcyk7XG4gICAgcmV0LnByZXBlbmQoJ1snKTtcbiAgICByZXQuYWRkKCddJyk7XG5cbiAgICByZXR1cm4gcmV0O1xuICB9XG59O1xuXG5leHBvcnQgZGVmYXVsdCBDb2RlR2VuO1xuIl19
