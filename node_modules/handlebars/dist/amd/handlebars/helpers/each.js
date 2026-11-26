define(['exports', 'module', '../utils', '../exception'], function (exports, module, _utils, _exception) {
  'use strict';

  // istanbul ignore next

  function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { 'default': obj }; }

  var _Exception = _interopRequireDefault(_exception);

  /**
   * Close an unfinished iterator after the block body threw, like for...of does,
   * so that generators can run their cleanup code.
   *
   * Errors thrown while closing, including by a `return` getter, are ignored:
   * the block's error is the cause and is the one the caller must see.
   *
   * @param {Iterator} iterator the iterator that was being consumed
   */
  function closeIterator(iterator) {
    try {
      var _close = iterator['return'];
      if (_utils.isFunction(_close)) {
        _close.call(iterator);
      }
    } catch (closeError) {
      // NOP
    }
  }

  module.exports = function (instance) {
    instance.registerHelper('each', function (context, options) {
      if (!options) {
        throw new _Exception['default']('Must pass iterator to #each');
      }

      var fn = options.fn,
          inverse = options.inverse,
          i = 0,
          ret = '',
          data = undefined,
          contextPath = undefined;

      if (options.data && options.ids) {
        contextPath = _utils.appendContextPath(options.data.contextPath, options.ids[0]) + '.';
      }

      if (_utils.isFunction(context)) {
        context = context.call(this);
      }

      if (options.data) {
        data = _utils.createFrame(options.data);
      }

      function execIteration(field, index, last, value) {
        if (data) {
          data.key = field;
          data.index = index;
          data.first = index === 0;
          data.last = !!last;

          if (contextPath) {
            data.contextPath = contextPath + field;
          }
        }

        ret = ret + fn(value, {
          data: data,
          blockParams: _utils.blockParams([value, field], [contextPath + field, null])
        });
      }

      if (context && typeof context === 'object') {
        if (_utils.isArray(context)) {
          for (var j = context.length; i < j; i++) {
            if (i in context) {
              execIteration(i, i, i === context.length - 1, context[i]);
            }
          }
        } else if (typeof Symbol === 'function' && context[Symbol.iterator]) {
          var iterator = context[Symbol.iterator]();
          var current = iterator.next();
          while (!current.done) {
            var next = iterator.next();
            try {
              execIteration(i, i, next.done, current.value);
            } catch (e) {
              if (!next.done) {
                closeIterator(iterator);
              }
              throw e;
            }
            current = next;
            i++;
          }
        } else {
          (function () {
            var priorKey = undefined;

            Object.keys(context).forEach(function (key) {
              // We're running the iterations one step out of sync so we can detect
              // the last iteration without have to scan the object twice and create
              // an itermediate keys array.
              if (priorKey !== undefined) {
                execIteration(priorKey, i - 1, false, context[priorKey]);
              }
              priorKey = key;
              i++;
            });
            if (priorKey !== undefined) {
              execIteration(priorKey, i - 1, true, context[priorKey]);
            }
          })();
        }
      }

      if (i === 0) {
        ret = inverse(this);
      }

      return ret;
    });
  };
});
//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uL2xpYi9oYW5kbGViYXJzL2hlbHBlcnMvZWFjaC5qcyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiOzs7Ozs7Ozs7Ozs7Ozs7Ozs7QUFrQkEsV0FBUyxhQUFhLENBQUMsUUFBUSxFQUFFO0FBQy9CLFFBQUk7QUFDRixVQUFJLE1BQUssR0FBRyxRQUFRLFVBQU8sQ0FBQztBQUM1QixVQUFJLE9BaEJOLFVBQVUsQ0FnQk8sTUFBSyxDQUFDLEVBQUU7QUFDckIsY0FBSyxDQUFDLElBQUksQ0FBQyxRQUFRLENBQUMsQ0FBQztPQUN0QjtLQUNGLENBQUMsT0FBTyxVQUFVLEVBQUU7O0tBRXBCO0dBQ0Y7O21CQUVjLFVBQVMsUUFBUSxFQUFFO0FBQ2hDLFlBQVEsQ0FBQyxjQUFjLENBQUMsTUFBTSxFQUFFLFVBQVMsT0FBTyxFQUFFLE9BQU8sRUFBRTtBQUN6RCxVQUFJLENBQUMsT0FBTyxFQUFFO0FBQ1osY0FBTSwwQkFBYyw2QkFBNkIsQ0FBQyxDQUFDO09BQ3BEOztBQUVELFVBQUksRUFBRSxHQUFHLE9BQU8sQ0FBQyxFQUFFO1VBQ2pCLE9BQU8sR0FBRyxPQUFPLENBQUMsT0FBTztVQUN6QixDQUFDLEdBQUcsQ0FBQztVQUNMLEdBQUcsR0FBRyxFQUFFO1VBQ1IsSUFBSSxZQUFBO1VBQ0osV0FBVyxZQUFBLENBQUM7O0FBRWQsVUFBSSxPQUFPLENBQUMsSUFBSSxJQUFJLE9BQU8sQ0FBQyxHQUFHLEVBQUU7QUFDL0IsbUJBQVcsR0FDVCxPQTNDTixpQkFBaUIsQ0EyQ08sT0FBTyxDQUFDLElBQUksQ0FBQyxXQUFXLEVBQUUsT0FBTyxDQUFDLEdBQUcsQ0FBQyxDQUFDLENBQUMsQ0FBQyxHQUFHLEdBQUcsQ0FBQztPQUNyRTs7QUFFRCxVQUFJLE9BMUNOLFVBQVUsQ0EwQ08sT0FBTyxDQUFDLEVBQUU7QUFDdkIsZUFBTyxHQUFHLE9BQU8sQ0FBQyxJQUFJLENBQUMsSUFBSSxDQUFDLENBQUM7T0FDOUI7O0FBRUQsVUFBSSxPQUFPLENBQUMs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LEdBQUcsQ0FBQyxFQUFFLElBQUksRUFBRSxPQUFPLENBQUMsUUFBUSxDQUFDLENBQUMsQ0FBQzthQUN6RDs7U0FDRjtPQUNGOztBQUVELFVBQUksQ0FBQyxLQUFLLENBQUMsRUFBRTtBQUNYLFdBQUcsR0FBRyxPQUFPLENBQUMsSUFBSSxDQUFDLENBQUM7T0FDckI7O0FBRUQsYUFBTyxHQUFHLENBQUM7S0FDWixDQUFDLENBQUM7R0FDSiIsImZpbGUiOiJlYWNoLmpzIiwic291cmNlc0NvbnRlbnQiOlsiaW1wb3J0IHtcbiAgYXBwZW5kQ29udGV4dFBhdGgsXG4gIGJsb2NrUGFyYW1zLFxuICBjcmVhdGVGcmFtZSxcbiAgaXNBcnJheSxcbiAgaXNGdW5jdGlvblxufSBmcm9tICcuLi91dGlscyc7XG5pbXBvcnQgRXhjZXB0aW9uIGZyb20gJy4uL2V4Y2VwdGlvbic7XG5cbi8qKlxuICogQ2xvc2UgYW4gdW5maW5pc2hlZCBpdGVyYXRvciBhZnRlciB0aGUgYmxvY2sgYm9keSB0aHJldywgbGlrZSBmb3IuLi5vZiBkb2VzLFxuICogc28gdGhhdCBnZW5lcmF0b3JzIGNhbiBydW4gdGhlaXIgY2xlYW51cCBjb2RlLlxuICpcbiAqIEVycm9ycyB0aHJvd24gd2hpbGUgY2xvc2luZywgaW5jbHVkaW5nIGJ5IGEgYHJldHVybmAgZ2V0dGVyLCBhcmUgaWdub3JlZDpcbiAqIHRoZSBibG9jaydzIGVycm9yIGlzIHRoZSBjYXVzZSBhbmQgaXMgdGhlIG9uZSB0aGUgY2FsbGVyIG11c3Qgc2VlLlxuICpcbiAqIEBwYXJhbSB7SXRlcmF0b3J9IGl0ZXJhdG9yIHRoZSBpdGVyYXRvciB0aGF0IHdhcyBiZWluZyBjb25zdW1lZFxuICovXG5mdW5jdGlvbiBjbG9zZUl0ZXJhdG9yKGl0ZXJhdG9yKSB7XG4gIHRyeSB7XG4gICAgbGV0IGNsb3NlID0gaXRlcmF0b3IucmV0dXJuO1xuICAgIGlmIChpc0Z1bmN0aW9uKGNsb3NlKSkge1xuICAgICAgY2xvc2UuY2FsbChpdGVyYXRvcik7XG4gICAgfVxuICB9IGNhdGNoIChjbG9zZUVycm9yKSB7XG4gICAgLy8gTk9QXG4gIH1cbn1cblxuZXhwb3J0IGRlZmF1bHQgZnVuY3Rpb24oaW5zdGFuY2UpIHtcbiAgaW5zdGFuY2UucmVnaXN0ZXJIZWxwZXIoJ2VhY2gnLCBmdW5jdGlvbihjb250ZXh0LCBvcHRpb25zKSB7XG4gICAgaWYgKCFvcHRpb25zKSB7XG4gICAgICB0aHJvdyBuZXcgRXhjZXB0aW9uKCdNdXN0IHBhc3MgaXRlcmF0b3IgdG8gI2VhY2gnKTtcbiAgICB9XG5cbiAgICBsZXQgZm4gPSBvcHRpb25zLmZuLFxuICAgICAgaW52ZXJzZSA9IG9wdGlvbnMuaW52ZXJzZSxcbiAgICAgIGkgPSAwLFxuICAgICAgcmV0ID0gJycsXG4gICAgICBkYXRhLFxuICAgICAgY29udGV4dFBhdGg7XG5cbiAgICBpZiAob3B0aW9ucy5kYXRhICYmIG9wdGlvbnMuaWRzKSB7XG4gICAgICBjb250ZXh0UGF0aCA9XG4gICAgICAgIGFwcGVuZENvbnRleHRQYXRoKG9wdGlvbnMuZGF0YS5jb250ZXh0UGF0aCwgb3B0aW9ucy5pZHNbMF0pICsgJy4nO1xuICAgIH1cblxuICAgIGlmIChpc0Z1bmN0aW9uKGNvbnRleHQpKSB7XG4gICAgICBjb250ZXh0ID0gY29udGV4dC5jYWxsKHRoaXMpO1xuICAgIH1cblxuICAgIGlmIChvcHRpb25zLmRhdGEpIHtcbiAgICAgIGRhdGEgPSBjcmVhdGVGcmFtZShvcHRpb25zLmRhdGEpO1xuICAgIH1cblxuICAgIGZ1bmN0aW9uIGV4ZWNJdGVyYXRpb24oZmllbGQsIGluZGV4LCBsYXN0LCB2YWx1ZSkge1xuICAgICAgaWYgKGRhdGEpIHtcbiAgICAgICAgZGF0YS5rZXkgPSBmaWVsZDtcbiAgICAgICAgZGF0YS5pbmRleCA9IGluZGV4O1xuICAgICAgICBkYXRhLmZpcnN0ID0gaW5kZXggPT09IDA7XG4gICAgICAgIGRhdGEubGFzdCA9ICEhbGFzdDtcblxuICAgICAgICBpZiAoY29udGV4dFBhdGgpIHtcbiAgICAgICAgICBkYXRhLmNvbnRleHRQYXRoID0gY29udGV4dFBhdGggKyBmaWVsZDtcbiAgICAgICAgfVxuICAgICAgfVxuXG4gICAgICByZXQgPVxuICAgICAgICByZXQgK1xuICAgICAgICBmbih2YWx1ZSwge1xuICAgICAgICAgIGRhdGE6IGRhdGEsXG4gICAgICAgICAgYmxvY2tQYXJhbXM6IGJsb2NrUGFyYW1zKFt2YWx1ZSwgZmllbGRdLCBbY29udGV4dFBhdGggKyBmaWVsZCwgbnVsbF0pXG4gICAgICAgIH0pO1xuICAgIH1cblxuICAgIGlmIChjb250ZXh0ICYmIHR5cGVvZiBjb250ZXh0ID09PSAnb2JqZWN0Jykge1xuICAgICAgaWYgKGlzQXJyYXkoY29udGV4dCkpIHtcbiAgICAgICAgZm9yIChsZXQgaiA9IGNvbnRleHQubGVuZ3RoOyBpIDwgajsgaSsrKSB7XG4gICAgICAgICAgaWYgKGkgaW4gY29udGV4dCkge1xuICAgICAgICAgICAgZXhlY0l0ZXJhdGlvbihpLCBpLCBpID09PSBjb250ZXh0Lmxlbmd0aCAtIDEsIGNvbnRleHRbaV0pO1xuICAgICAgICAgIH1cbiAgICAgICAgfVxuICAgICAgfSBlbHNlIGlmICh0eXBlb2YgU3ltYm9sID09PSAnZnVuY3Rpb24nICYmIGNvbnRleHRbU3ltYm9sLml0ZXJhdG9yXSkge1xuICAgICAgICBjb25zdCBpdGVyYXRvciA9IGNvbnRleHRbU3ltYm9sLml0ZXJhdG9yXSgpO1xuICAgICAgICBsZXQgY3VycmVudCA9IGl0ZXJhdG9yLm5leHQoKTtcbiAgICAgICAgd2hpbGUgKCFjdXJyZW50LmRvbmUpIHtcbiAgICAgICAgICBsZXQgbmV4dCA9IGl0ZXJhdG9yLm5leHQoKTtcbiAgICAgICAgICB0cnkge1xuICAgICAgICAgICAgZXhlY0l0ZXJhdGlvbihpLCBpLCBuZXh0LmRvbmUsIGN1cnJlbnQudmFsdWUpO1xuICAgICAgICAgIH0gY2F0Y2ggKGUpIHtcbiAgICAgICAgICAgIGlmICghbmV4dC5kb25lKSB7XG4gICAgICAgICAgICAgIGNsb3NlSXRlcmF0b3IoaXRlcmF0b3IpO1xuICAgICAgICAgICAgfVxuICAgICAgICAgICAgdGhyb3cgZTtcbiAgICAgICAgICB9XG4gICAgICAgICAgY3VycmVudCA9IG5leHQ7XG4gICAgICAgICAgaSsrO1xuICAgICAgICB9XG4gICAgICB9IGVsc2Uge1xuICAgICAgICBsZXQgcHJpb3JLZXk7XG5cbiAgICAgICAgT2JqZWN0LmtleXMoY29udGV4dCkuZm9yRWFjaChrZXkgPT4ge1xuICAgICAgICAgIC8vIFdlJ3JlIHJ1bm5pbmcgdGhlIGl0ZXJhdGlvbnMgb25lIHN0ZXAgb3V0IG9mIHN5bmMgc28gd2UgY2FuIGRldGVjdFxuICAgICAgICAgIC8vIHRoZSBsYXN0IGl0ZXJhdGlvbiB3aXRob3V0IGhhdmUgdG8gc2NhbiB0aGUgb2JqZWN0IHR3aWNlIGFuZCBjcmVhdGVcbiAgICAgICAgICAvLyBhbiBpdGVybWVkaWF0ZSBrZXlzIGFycmF5LlxuICAgICAgICAgIGlmIChwcmlvcktleSAhPT0gdW5kZWZpbmVkKSB7XG4gICAgICAgICAgICBleGVjSXRlcmF0aW9uKHByaW9yS2V5LCBpIC0gMSwgZmFsc2UsIGNvbnRleHRbcHJpb3JLZXldKTtcbiAgICAgICAgICB9XG4gICAgICAgICAgcHJpb3JLZXkgPSBrZXk7XG4gICAgICAgICAgaSsrO1xuICAgICAgICB9KTtcbiAgICAgICAgaWYgKHByaW9yS2V5ICE9PSB1bmRlZmluZWQpIHtcbiAgICAgICAgICBleGVjSXRlcmF0aW9uKHByaW9yS2V5LCBpIC0gMSwgdHJ1ZSwgY29udGV4dFtwcmlvcktleV0pO1xuICAgICAgICB9XG4gICAgICB9XG4gICAgfVxuXG4gICAgaWYgKGkgPT09IDApIHtcbiAgICAgIHJldCA9IGludmVyc2UodGhpcyk7XG4gICAgfVxuXG4gICAgcmV0dXJuIHJldDtcbiAgfSk7XG59XG4iXX0=
