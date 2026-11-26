/* eslint-env node */
/* eslint-disable no-console */
'use strict';

// istanbul ignore next

function _interopRequireWildcard(obj) { if (obj && obj.__esModule) { return obj; } else { var newObj = {}; if (obj != null) { for (var key in obj) { if (Object.prototype.hasOwnProperty.call(obj, key)) newObj[key] = obj[key]; } } newObj['default'] = obj; return newObj; } }

// istanbul ignore next

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { 'default': obj }; }

var _neoAsync = require('neo-async');

var _neoAsync2 = _interopRequireDefault(_neoAsync);

var _fs = require('fs');

var _fs2 = _interopRequireDefault(_fs);

var _handlebars = require('./handlebars');

var Handlebars = _interopRequireWildcard(_handlebars);

var _handlebarsCompilerCodeGen = require('./handlebars/compiler/code-gen');

var _path = require('path');

var _sourceMap = require('source-map');

module.exports.loadTemplates = function (opts, callback) {
  loadStrings(opts, function (err, strings) {
    if (err) {
      callback(err);
    } else {
      loadFiles(opts, function (err, files) {
        if (err) {
          callback(err);
        } else {
          opts.templates = strings.concat(files);
          callback(undefined, opts);
        }
      });
    }
  });
};

function loadStrings(opts, callback) {
  var strings = arrayCast(opts.string),
      names = arrayCast(opts.name);

  if (names.length !== strings.length && strings.length > 1) {
    return callback(new Handlebars.Exception('Number of names did not match the number of string inputs'));
  }

  _neoAsync2['default'].map(strings, function (string, callback) {
    if (string !== '-') {
      callback(undefined, string);
    } else {
      (function () {
        // Load from stdin
        var buffer = '';
        process.stdin.setEncoding('utf8');

        process.stdin.on('data', function (chunk) {
          buffer += chunk;
        });
        process.stdin.on('end', function () {
          callback(undefined, buffer);
        });
      })();
    }
  }, function (err, strings) {
    strings = strings.map(function (string, index) {
      return {
        name: names[index],
        path: names[index],
        source: string
      };
    });
    callback(err, strings);
  });
}

function loadFiles(opts, callback) {
  // Build file extension pattern
  var extension = (opts.extension || 'handlebars').replace(/[\\^$*+?.():=!|{}\-[\]]/g, function (arg) {
    return '\\' + arg;
  });
  extension = new RegExp('\\.' + extension + '$');

  var ret = [],
      queue = (opts.files || []).map(function (template) {
    return { template: template, root: opts.root };
  });
  _neoAsync2['default'].whilst(function () {
    return queue.length;
  }, function (callback) {
    var _queue$shift = queue.shift();

    var path = _queue$shift.template;
    var root = _queue$shift.root;

    _fs2['default'].stat(path, function (err, stat) {
      if (err) {
        return callback(new Handlebars.Exception('Unable to open template file "' + path + '"'));
      }

      if (stat.isDirectory()) {
        opts.hasDirectory = true;

        _fs2['default'].readdir(path, function (err, children) {
          /* istanbul ignore next : Race condition that being too lazy to test */
          if (err) {
            return callback(err);
          }
          children.forEach(function (file) {
            var childPath = path + '/' + file;

            if (extension.test(childPath) || _fs2['default'].statSync(childPath).isDirectory()) {
              queue.push({ template: childPath, root: root || path });
            }
          });

          callback();
        });
      } else {
        _fs2['default'].readFile(path, 'utf8', function (err, data) {
          /* istanbul ignore next : Race condition that being too lazy to test */
          if (err) {
            return callback(err);
          }

          if (opts.bom && data.indexOf('﻿') === 0) {
            data = data.substring(1);
          }

          // Clean the template name
          var name = path;
          if (!root) {
            name = _path.basename(name);
          } else if (name.indexOf(root) === 0) {
            name = name.substring(root.length + 1);
          }
          name = name.replace(extension, '');

          ret.push({
            path: path,
            name: name,
            source: data
          });

          callback();
        });
      }
    });
  }, function (err) {
    if (err) {
      callback(err);
    } else {
      callback(undefined, ret);
    }
  });
}

module.exports.cli = function (opts) {
  if (opts.version) {
    console.log(Handlebars.VERSION);
    return;
  }

  if (!opts.templates.length && !opts.hasDirectory) {
    throw new Handlebars.Exception('Must define at least one template or directory.');
  }

  if (opts.simple && opts.min) {
    throw new Handlebars.Exception('Unable to minimize simple output');
  }

  var multiple = opts.templates.length !== 1 || opts.hasDirectory;
  if (opts.simple && multiple) {
    throw new Handlebars.Exception('Unable to output multiple templates in simple mode');
  }

  // Force simple mode if we have only one template and it's unnamed.
  if (!opts.amd && !opts.commonjs && opts.templates.length === 1 && !opts.templates[0].name) {
    opts.simple = true;
  }

  // Convert the known list into a hash
  var known = {};
  if (opts.known && !Array.isArray(opts.known)) {
    opts.known = [opts.known];
  }
  if (opts.known) {
    for (var i = 0, len = opts.known.length; i < len; i++) {
      known[opts.known[i]] = true;
    }
  }

  var objectName = opts.partial ? 'Handlebars.partials' : 'templates';

  if (opts.namespace && !isValidNamespace(opts.namespace)) {
    throw new Handlebars.Exception('Invalid namespace format');
  }

  var output = new _sourceMap.SourceNode();
  if (!opts.simple) {
    if (opts.amd) {
      var runtimeModulePath = (opts.handlebarPath || '') + 'handlebars.runtime';
      output.add('define([' + quoteForJavaScript(runtimeModulePath) + '], function(Handlebars) {\n  Handlebars = Handlebars["default"];');
    } else if (opts.commonjs) {
      output.add('var Handlebars = require(' + quoteForJavaScript(opts.commonjs) + ');');
    } else {
      output.add('(function() {\n');
    }
    output.add('  var template = Handlebars.template, templates = ');
    if (opts.namespace) {
      output.add(opts.namespace);
      output.add(' = ');
      output.add(opts.namespace);
      output.add(' || ');
    }
    output.add('{};\n');
  }

  opts.templates.forEach(function (template) {
    var options = {
      knownHelpers: known,
      knownHelpersOnly: opts.o
    };

    if (opts.map) {
      options.srcName = template.path;
    }
    if (opts.data) {
      options.data = true;
    }

    var precompiled = Handlebars.precompile(template.source, options);

    // If we are generating a source map, we have to reconstruct the SourceNode object
    if (opts.map) {
      var consumer = new _sourceMap.SourceMapConsumer(precompiled.map);
      precompiled = _sourceMap.SourceNode.fromStringWithSourceMap(precompiled.code, consumer);
    }

    if (opts.simple) {
      output.add([precompiled, '\n']);
    } else {
      if (!template.name) {
        throw new Handlebars.Exception('Name missing for template');
      }

      if (opts.amd && !multiple) {
        output.add('return ');
      }
      output.add([objectName, '[', quoteForJavaScript(template.name), '] = template(', precompiled, ');\n']);
    }
  });

  // Output the content
  if (!opts.simple) {
    if (opts.amd) {
      if (multiple) {
        output.add(['return ', objectName, ';\n']);
      }
      output.add('});');
    } else if (!opts.commonjs) {
      output.add('})();');
    }
  }

  if (opts.map) {
    output.add('\n//# sourceMappingURL=' + sanitizeSourceMapComment(opts.map) + '\n');
  }

  output = output.toStringWithSourceMap();
  output.map = output.map + '';

  if (opts.min) {
    output = minify(output, opts.map);
  }

  if (opts.map) {
    _fs2['default'].writeFileSync(opts.map, output.map, 'utf8');
  }
  output = output.code;

  if (opts.output) {
    _fs2['default'].writeFileSync(opts.output, output, 'utf8');
  } else {
    console.log(output);
  }
};

function arrayCast(value) {
  value = value != null ? value : [];
  if (!Array.isArray(value)) {
    value = [value];
  }
  return value;
}

/*
 * Safely quotes a value for embedding in generated JavaScript strings
 *
 * Uses JSON.stringify which handles all special characters, and also escapes
 * line separators and the sequences that could end or change the parsing of an
 * enclosing <script> element.
 */
function quoteForJavaScript(value) {
  return _handlebarsCompilerCodeGen.safeJsonLiteral(String(value));
}

/**
 * Validates that a namespace is a legitimate dotted JavaScript identifier
 * (e.g. "App.templates") to prevent arbitrary code injection
 */
function isValidNamespace(namespace) {
  return (/^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$/.test(namespace)
  );
}

/**
 * Strips line terminators from source map URLs to prevent injection of new
 * JavaScript lines via the sourceMappingURL comment, and percent-encodes "<"
 * so that the comment cannot end or change the parsing of an enclosing
 * <script> element.
 */
function sanitizeSourceMapComment(value) {
  return String(value).replace(/[\r\n\u2028\u2029]/g, '').replace(/</g, '%3C');
}

/**
 * Run uglify to minify the compiled template, if uglify exists in the dependencies.
 *
 * We are using `require` instead of `import` here, because es6-modules do not allow
 * dynamic imports and uglify-js is an optional dependency. Since we are inside NodeJS here, this
 * should not be a problem.
 *
 * @param {string} output the compiled template
 * @param {string} sourceMapFile the file to write the source map to.
 */
function minify(output, sourceMapFile) {
  try {
    // Try to resolve uglify-js in order to see if it does exist
    require.resolve('uglify-js');
  } catch (e) {
    if (e.code !== 'MODULE_NOT_FOUND') {
      // Something else seems to be wrong
      throw e;
    }
    // it does not exist!
    console.error('Code minimization is disabled due to missing uglify-js dependency');
    return output;
  }
  return require('uglify-js').minify(output.code, {
    sourceMap: {
      content: output.map,
      // uglify writes its own sourceMappingURL comment, so the URL has to be
      // sanitized here as well.
      url: sourceMapFile && sanitizeSourceMapComment(sourceMapFile)
    }
  });
}
//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIi4uLy4uL2xpYi9wcmVjb21waWxlci5qcyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiOzs7Ozs7Ozs7Ozs7d0JBRWtCLFdBQVc7Ozs7a0JBQ2QsSUFBSTs7OzswQkFDUyxjQUFjOztJQUE5QixVQUFVOzt5Q0FDVSxnQ0FBZ0M7O29CQUN2QyxNQUFNOzt5QkFDZSxZQUFZOztBQUUxRCxNQUFNLENBQUMsT0FBTyxDQUFDLGFBQWEsR0FBRyxVQUFTLElBQUksRUFBRSxRQUFRLEVBQUU7QUFDdEQsYUFBVyxDQUFDLElBQUksRUFBRSxVQUFTLEdBQUcsRUFBRSxPQUFPLEVBQUU7QUFDdkMsUUFBSSxHQUFHLEVBQUU7QUFDUCxjQUFRLENBQUMsR0FBRyxDQUFDLENBQUM7S0FDZixNQUFNO0FBQ0wsZUFBUyxDQUFDLElBQUksRUFBRSxVQUFTLEdBQUcsRUFBRSxLQUFLLEVBQUU7QUFDbkMsWUFBSSxHQUFHLEVBQUU7QUFDUCxrQkFBUSxDQUFDLEdBQUcsQ0FBQyxDQUFDO1NBQ2YsTUFBTTtBQUNMLGNBQUksQ0FBQyxTQUFTLEdBQUcsT0FBTyxDQUFDLE1BQU0sQ0FBQyxLQUFLLENBQUMsQ0FBQztBQUN2QyxrQkFBUSxDQUFDLFNBQVMsRUFBRSxJQUFJLENBQUMsQ0FBQztTQUMzQjtPQUNGLENBQUMsQ0FBQztLQUNKO0dBQ0YsQ0FBQyxDQUFDO0NBQ0osQ0FBQzs7QUFFRixTQUFTLFdBQVcsQ0FBQyxJQUFJLEVBQUUsUUFBUSxFQUFFO0FBQ25DLE1BQUksT0FBTyxHQUFHLFNBQVMsQ0FBQyxJQUFJLENBQUMsTUFBTSxDQUFDO01BQ2xDLEtBQUssR0FBRyxTQUFTLENBQUMsSUFBSSxDQUFDLElBQUksQ0FBQyxDQUFDOztBQUUvQixNQUFJLEtBQUssQ0FBQyxNQUFNLEtBQUssT0FBTyxDQUFDLE1BQU0sSUFBSSxPQUFPLENBQUMsTUFBTSxHQUFHLENBQUMsRUFBRTtBQUN6RCxXQUFPLFFBQVEsQ0FDYixJQUFJLFVBQVUsQ0FBQyxTQUFTLENBQ3RCLDJEQUEyRCxDQUM1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O0FBQ25DLFNBQU8sMERBQXlELENBQUMsSUFBSSxDQUNuRSxTQUFTLENBQ1Y7SUFBQztDQUNIOzs7Ozs7OztBQVFELFNBQVMsd0JBQXdCLENBQUMsS0FBSyxFQUFFO0FBQ3ZDLFNBQU8sTUFBTSxDQUFDLEtBQUssQ0FBQyxDQUNqQixPQUFPLENBQUMscUJBQXFCLEVBQUUsRUFBRSxDQUFDLENBQ2xDLE9BQU8sQ0FBQyxJQUFJLEVBQUUsS0FBSyxDQUFDLENBQUM7Q0FDekI7Ozs7Ozs7Ozs7OztBQVlELFNBQVMsTUFBTSxDQUFDLE1BQU0sRUFBRSxhQUFhLEVBQUU7QUFDckMsTUFBSTs7QUFFRixXQUFPLENBQUMsT0FBTyxDQUFDLFdBQVcsQ0FBQyxDQUFDO0dBQzlCLENBQUMsT0FBTyxDQUFDLEVBQUU7QUFDVixRQUFJLENBQUMsQ0FBQyxJQUFJLEtBQUssa0JBQWtCLEVBQUU7O0FBRWpDLFlBQU0sQ0FBQyxDQUFDO0tBQ1Q7O0FBRUQsV0FBTyxDQUFDLEtBQUssQ0FDWCxtRUFBbUUsQ0FDcEUsQ0FBQztBQUNGLFdBQU8sTUFBTSxDQUFDO0dBQ2Y7QUFDRCxTQUFPLE9BQU8sQ0FBQyxXQUFXLENBQUMsQ0FBQyxNQUFNLENBQUMsTUFBTSxDQUFDLElBQUksRUFBRTtBQUM5QyxhQUFTLEVBQUU7QUFDVCxhQUFPLEVBQUUsTUFBTSxDQUFDLEdBQUc7OztBQUduQixTQUFHLEVBQUUsYUFBYSxJQUFJLHdCQUF3QixDQUFDLGFBQWEsQ0FBQztLQUM5RDtHQUNGLENBQUMsQ0FBQztDQUNKIiwiZmlsZSI6InByZWNvbXBpbGVyLmpzIiwic291cmNlc0NvbnRlbnQiOlsiLyogZXNsaW50LWVudiBub2RlICovXG4vKiBlc2xpbnQtZGlzYWJsZSBuby1jb25zb2xlICovXG5pbXBvcnQgQXN5bmMgZnJvbSAnbmVvLWFzeW5jJztcbmltcG9ydCBmcyBmcm9tICdmcyc7XG5pbXBvcnQgKiBhcyBIYW5kbGViYXJzIGZyb20gJy4vaGFuZGxlYmFycyc7XG5pbXBvcnQgeyBzYWZlSnNvbkxpdGVyYWwgfSBmcm9tICcuL2hhbmRsZWJhcnMvY29tcGlsZXIvY29kZS1nZW4nO1xuaW1wb3J0IHsgYmFzZW5hbWUgfSBmcm9tICdwYXRoJztcbmltcG9ydCB7IFNvdXJjZU1hcENvbnN1bWVyLCBTb3VyY2VOb2RlIH0gZnJvbSAnc291cmNlLW1hcCc7XG5cbm1vZHVsZS5leHBvcnRzLmxvYWRUZW1wbGF0ZXMgPSBmdW5jdGlvbihvcHRzLCBjYWxsYmFjaykge1xuICBsb2FkU3RyaW5ncyhvcHRzLCBmdW5jdGlvbihlcnIsIHN0cmluZ3MpIHtcbiAgICBpZiAoZXJyKSB7XG4gICAgICBjYWxsYmFjayhlcnIpO1xuICAgIH0gZWxzZSB7XG4gICAgICBsb2FkRmlsZXMob3B0cywgZnVuY3Rpb24oZXJyLCBmaWxlcykge1xuICAgICAgICBpZiAoZXJyKSB7XG4gICAgICAgICAgY2FsbGJhY2soZXJyKTtcbiAgICAgICAgfSBlbHNlIHtcbiAgICAgICAgICBvcHRzLnRlbXBsYXRlcyA9IHN0cmluZ3MuY29uY2F0KGZpbGVzKTtcbiAgICAgICAgICBjYWxsYmFjayh1bmRlZmluZWQsIG9wdHMpO1xuICAgICAgICB9XG4gICAgICB9KTtcbiAgICB9XG4gIH0pO1xufTtcblxuZnVuY3Rpb24gbG9hZFN0cmluZ3Mob3B0cywgY2FsbGJhY2spIHtcbiAgbGV0IHN0cmluZ3MgPSBhcnJheUNhc3Qob3B0cy5zdHJpbmcpLFxuICAgIG5hbWVzID0gYXJyYXlDYXN0KG9wdHMubmFtZSk7XG5cbiAgaWYgKG5hbWVzLmxlbmd0aCAhPT0gc3RyaW5ncy5sZW5ndGggJiYgc3RyaW5ncy5sZW5ndGggPiAxKSB7XG4gICAgcmV0dXJuIGNhbGxiYWNrKFxuICAgICAgbmV3IEhhbmRsZWJhcnMuRXhjZXB0aW9uKFxuICAgICAgICAnTnVtYmVyIG9mIG5hbWVzIGRpZCBub3QgbWF0Y2ggdGhlIG51bWJlciBvZiBzdHJpbmcgaW5wdXRzJ1xuICAgICAgKVxuICAgICk7XG4gIH1cblxuICBBc3luYy5tYXAoXG4gICAgc3RyaW5ncyxcbiAgICBmdW5jdGlvbihzdHJpbmcsIGNhbGxiYWNrKSB7XG4gICAgICBpZiAoc3RyaW5nICE9PSAnLScpIHtcbiAgICAgICAgY2FsbGJhY2sodW5kZWZpbmVkLCBzdHJpbmcpO1xuICAgICAgfSBlbHNlIHtcbiAgICAgICAgLy8gTG9hZCBmcm9tIHN0ZGluXG4gICAgICAgIGxldCBidWZmZXIgPSAnJztcbiAgICAgICAgcHJvY2Vzcy5zdGRpbi5zZXRFbmNvZGluZygndXRmOCcpO1xuXG4gICAgICAgIHByb2Nlc3Muc3RkaW4ub24oJ2RhdGEnLCBmdW5jdGlvbihjaHVuaykge1xuICAgICAgICAgIGJ1ZmZlciArPSBjaHVuaztcbiAgICAgICAgfSk7XG4gICAgICAgIHByb2Nlc3Muc3RkaW4ub24oJ2VuZCcsIGZ1bmN0aW9uKCkge1xuICAgICAgICAgIGNhbGxiYWNrKHVuZGVmaW5lZCwgYnVmZmVyKTtcbiAgICAgICAgfSk7XG4gICAgICB9XG4gICAgfSxcbiAgICBmdW5jdGlvbihlcnIsIHN0cmluZ3MpIHtcbiAgICAgIHN0cmluZ3MgPSBzdHJpbmdzLm1hcCgoc3RyaW5nLCBpbmRleCkgPT4gKHtcbiAgICAgICAgbmFtZTogbmFtZXNbaW5kZXhdLFxuICAgICAgICBwYXRoOiBuYW1lc1tpbmRleF0sXG4gICAgICAgIHNvdXJjZTogc3RyaW5nXG4gICAgICB9KSk7XG4gICAgICBjYWxsYmFjayhlcnIsIHN0cmluZ3MpO1xuICAgIH1cbiAgKTtcbn1cblxuZnVuY3Rpb24gbG9hZEZpbGVzKG9wdHMsIGNhbGxiYWNrKSB7XG4gIC8vIEJ1aWxkIGZpbGUgZXh0ZW5zaW9uIHBhdHRlcm5cbiAgbGV0IGV4dGVuc2lvbiA9IChvcHRzLmV4dGVuc2lvbiB8fCAnaGFuZGxlYmFycycpLnJlcGxhY2UoXG4gICAgL1tcXFxcXiQqKz8uKCk6PSF8e31cXC1bXFxdXS9nLFxuICAgIGZ1bmN0aW9uKGFyZykge1xuICAgICAgcmV0dXJuICdcXFxcJyArIGFyZztcbiAgICB9XG4gICk7XG4gIGV4dGVuc2lvbiA9IG5ldyBSZWdFeHAoJ1xcXFwuJyArIGV4dGVuc2lvbiArICckJyk7XG5cbiAgbGV0IHJldCA9IFtdLFxuICAgIHF1ZXVlID0gKG9wdHMuZmlsZXMgfHwgW10pLm1hcCh0ZW1wbGF0ZSA9PiAoeyB0ZW1wbGF0ZSwgcm9vdDogb3B0cy5yb290IH0pKTtcbiAgQXN5bmMud2hpbHN0KFxuICAgICgpID0+IHF1ZXVlLmxlbmd0aCxcbiAgICBmdW5jdGlvbihjYWxsYmFjaykge1xuICAgICAgbGV0IHsgdGVtcGxhdGU6IHBhdGgsIHJvb3QgfSA9IHF1ZXVlLnNoaWZ0KCk7XG5cbiAgICAgIGZzLnN0YXQocGF0aCwgZnVuY3Rpb24oZXJyLCBzdGF0KSB7XG4gICAgICAgIGlmIChlcnIpIHtcbiAgICAgICAgICByZXR1cm4gY2FsbGJhY2soXG4gICAgICAgICAgICBuZXcgSGFuZGxlYmFycy5FeGNlcHRpb24oYFVuYWJsZSB0byBvcGVuIHRlbXBsYXRlIGZpbGUgXCIke3BhdGh9XCJgKVxuICAgICAgICAgICk7XG4gICAgICAgIH1cblxuICAgICAgICBpZiAoc3RhdC5pc0RpcmVjdG9yeSgpKSB7XG4gICAgICAgICAgb3B0cy5oYXNEaXJlY3RvcnkgPSB0cnVlO1xuXG4gICAgICAgICAgZnMucmVhZGRpcihwYXRoLCBmdW5jdGlvbihlcnIsIGNoaWxkcmVuKSB7XG4gICAgICAgICAgICAvKiBpc3RhbmJ1bCBpZ25vcmUgbmV4dCA6IFJhY2UgY29uZGl0aW9uIHRoYXQgYmVpbmcgdG9vIGxhenkgdG8gdGVzdCAqL1xuICAgICAgICAgICAgaWYgKGVycikge1xuICAgICAgICAgICAgICByZXR1cm4gY2FsbGJhY2soZXJyKTtcbiAgICAgICAgICAgIH1cbiAgICAgICAgICAgIGNoaWxkcmVuLmZvckVhY2goZnVuY3Rpb24oZmlsZSkge1xuICAgICAgICAgICAgICBsZXQgY2hpbGRQYXRoID0gcGF0aCArICcvJyArIGZpbGU7XG5cbiAgICAgICAgICAgICAgaWYgKFxuICAgICAgICAgICAgICAgIGV4dGVuc2lvbi50ZXN0KGNoaWxkUGF0aCkgfHxcbiAgICAgICAgICAgICAgICBmcy5zdGF0U3luYyhjaGlsZFBhdGgpLmlzRGlyZWN0b3J5KClcbiAgICAgICAgICAgICAgKSB7XG4gICAgICAgICAgICAgICAgcXVldWUucHVzaCh7IHRlbXBsYXRlOiBjaGlsZFBhdGgsIHJvb3Q6IHJvb3QgfHwgcGF0aCB9KTtcbiAgICAgICAgICAgICAgfVxuICAgICAgICAgICAgfSk7XG5cbiAgICAgICAgICAgIGNhbGxiYWNrKCk7XG4gICAgICAgICAgfSk7XG4gICAgICAgIH0gZWxzZSB7XG4gICAgICAgICAgZnMucmVhZEZpbGUocGF0aCwgJ3V0ZjgnLCBmdW5jdGlvbihlcnIsIGRhdGEpIHtcbiAgICAgICAgICAgIC8qIGlzdGFuYnVsIGlnbm9yZSBuZXh0IDogUmFjZSBjb25kaXRpb24gdGhhdCBiZWluZyB0b28gbGF6eSB0byB0ZXN0ICovXG4gICAgICAgICAgICBpZiAoZXJyKSB7XG4gICAgICAgICAgICAgIHJldHVybiBjYWxsYmFjayhlcnIpO1xuICAgICAgICAgICAgfVxuXG4gICAgICAgICAgICBpZiAob3B0cy5ib20gJiYgZGF0YS5pbmRleE9mKCdcXHVGRUZGJykgPT09IDApIHtcbiAgICAgICAgICAgICAgZGF0YSA9IGRhdGEuc3Vic3RyaW5nKDEpO1xuICAgICAgICAgICAgfVxuXG4gICAgICAgICAgICAvLyBDbGVhbiB0aGUgdGVtcGxhdGUgbmFtZVxuICAgICAgICAgICAgbGV0IG5hbWUgPSBwYXRoO1xuICAgICAgICAgICAgaWYgKCFyb290KSB7XG4gICAgICAgICAgICAgIG5hbWUgPSBiYXNlbmFtZShuYW1lKTtcbiAgICAgICAgICAgIH0gZWxzZSBpZiAobmFtZS5pbmRleE9mKHJvb3QpID09PSAwKSB7XG4gICAgICAgICAgICAgIG5hbWUgPSBuYW1lLnN1YnN0cmluZyhyb290Lmxlbmd0aCArIDEpO1xuICAgICAgICAgICAgfVxuICAgICAgICAgICAgbmFtZSA9IG5hbWUucmVwbGFjZShleHRlbnNpb24sICcnKTtcblxuICAgICAgICAgICAgcmV0LnB1c2goe1xuICAgICAgICAgICAgICBwYXRoOiBwYXRoLFxuICAgICAgICAgICAgICBuYW1lOiBuYW1lLFxuICAgICAgICAgICAgICBzb3VyY2U6IGRhdGFcbiAgICAgICAgICAgIH0pO1xuXG4gICAgICAgICAgICBjYWxsYmFjaygpO1xuICAgICAgICAgIH0pO1xuICAgICAgICB9XG4gICAgICB9KTtcbiAgICB9LFxuICAgIGZ1bmN0aW9uKGVycikge1xuICAgICAgaWYgKGVycikge1xuICAgICAgICBjYWxsYmFjayhlcnIpO1xuICAgICAgfSBlbHNlIHtcbiAgICAgICAgY2FsbGJhY2sodW5kZWZpbmVkLCByZXQpO1xuICAgICAgfVxuICAgIH1cbiAgKTtcbn1cblxubW9kdWxlLmV4cG9ydHMuY2xpID0gZnVuY3Rpb24ob3B0cykge1xuICBpZiAob3B0cy52ZXJzaW9uKSB7XG4gICAgY29uc29sZS5sb2coSGFuZGxlYmFycy5WRVJTSU9OKTtcbiAgICByZXR1cm47XG4gIH1cblxuICBpZiAoIW9wdHMudGVtcGxhdGVzLmxlbmd0aCAmJiAhb3B0cy5oYXNEaXJlY3RvcnkpIHtcbiAgICB0aHJvdyBuZXcgSGFuZGxlYmFycy5FeGNlcHRpb24oXG4gICAgICAnTXVzdCBkZWZpbmUgYXQgbGVhc3Qgb25lIHRlbXBsYXRlIG9yIGRpcmVjdG9yeS4nXG4gICAgKTtcbiAgfVxuXG4gIGlmIChvcHRzLnNpbXBsZSAmJiBvcHRzLm1pbikge1xuICAgIHRocm93IG5ldyBIYW5kbGViYXJzLkV4Y2VwdGlvbignVW5hYmxlIHRvIG1pbmltaXplIHNpbXBsZSBvdXRwdXQnKTtcbiAgfVxuXG4gIGNvbnN0IG11bHRpcGxlID0gb3B0cy50ZW1wbGF0ZXMubGVuZ3RoICE9PSAxIHx8IG9wdHMuaGFzRGlyZWN0b3J5O1xuICBpZiAob3B0cy5zaW1wbGUgJiYgbXVsdGlwbGUpIHtcbiAgICB0aHJvdyBuZXcgSGFuZGxlYmFycy5FeGNlcHRpb24oXG4gICAgICAnVW5hYmxlIHRvIG91dHB1dCBtdWx0aXBsZSB0ZW1wbGF0ZXMgaW4gc2ltcGxlIG1vZGUnXG4gICAgKTtcbiAgfVxuXG4gIC8vIEZvcmNlIHNpbXBsZSBtb2RlIGlmIHdlIGhhdmUgb25seSBvbmUgdGVtcGxhdGUgYW5kIGl0J3MgdW5uYW1lZC5cbiAgaWYgKFxuICAgICFvcHRzLmFtZCAmJlxuICAgICFvcHRzLmNvbW1vbmpzICYmXG4gICAgb3B0cy50ZW1wbGF0ZXMubGVuZ3RoID09PSAxICYmXG4gICAgIW9wdHMudGVtcGxhdGVzWzBdLm5hbWVcbiAgKSB7XG4gICAgb3B0cy5zaW1wbGUgPSB0cnVlO1xuICB9XG5cbiAgLy8gQ29udmVydCB0aGUga25vd24gbGlzdCBpbnRvIGEgaGFzaFxuICBsZXQga25vd24gPSB7fTtcbiAgaWYgKG9wdHMua25vd24gJiYgIUFycmF5LmlzQXJyYXkob3B0cy5rbm93bikpIHtcbiAgICBvcHRzLmtub3duID0gW29wdHMua25vd25dO1xuICB9XG4gIGlmIChvcHRzLmtub3duKSB7XG4gICAgZm9yIChsZXQgaSA9IDAsIGxlbiA9IG9wdHMua25vd24ubGVuZ3RoOyBpIDwgbGVuOyBpKyspIHtcbiAgICAgIGtub3duW29wdHMua25vd25baV1dID0gdHJ1ZTtcbiAgICB9XG4gIH1cblxuICBjb25zdCBvYmplY3ROYW1lID0gb3B0cy5wYXJ0aWFsID8gJ0hhbmRsZWJhcnMucGFydGlhbHMnIDogJ3RlbXBsYXRlcyc7XG5cbiAgaWYgKG9wdHMubmFtZXNwYWNlICYmICFpc1ZhbGlkTmFtZXNwYWNlKG9wdHMubmFtZXNwYWNlKSkge1xuICAgIHRocm93IG5ldyBIYW5kbGViYXJzLkV4Y2VwdGlvbignSW52YWxpZCBuYW1lc3BhY2UgZm9ybWF0Jyk7XG4gIH1cblxuICBsZXQgb3V0cHV0ID0gbmV3IFNvdXJjZU5vZGUoKTtcbiAgaWYgKCFvcHRzLnNpbXBsZSkge1xuICAgIGlmIChvcHRzLmFtZCkge1xuICAgICAgY29uc3QgcnVudGltZU1vZHVsZVBhdGggPVxuICAgICAgICAob3B0cy5oYW5kbGViYXJQYXRoIHx8ICcnKSArICdoYW5kbGViYXJzLnJ1bnRpbWUnO1xuICAgICAgb3V0cHV0LmFkZChcbiAgICAgICAgJ2RlZmluZShbJyArXG4gICAgICAgICAgcXVvdGVGb3JKYXZhU2NyaXB0KHJ1bnRpbWVNb2R1bGVQYXRoKSArXG4gICAgICAgICAgJ10sIGZ1bmN0aW9uKEhhbmRsZWJhcnMpIHtcXG4gIEhhbmRsZWJhcnMgPSBIYW5kbGViYXJzW1wiZGVmYXVsdFwiXTsnXG4gICAgICApO1xuICAgIH0gZWxzZSBpZiAob3B0cy5jb21tb25qcykge1xuICAgICAgb3V0cHV0LmFkZChcbiAgICAgICAgJ3ZhciBIYW5kbGViYXJzID0gcmVxdWlyZSgnICsgcXVvdGVGb3JKYXZhU2NyaXB0KG9wdHMuY29tbW9uanMpICsgJyk7J1xuICAgICAgKTtcbiAgICB9IGVsc2Uge1xuICAgICAgb3V0cHV0LmFkZCgnKGZ1bmN0aW9uKCkge1xcbicpO1xuICAgIH1cbiAgICBvdXRwdXQuYWRkKCcgIHZhciB0ZW1wbGF0ZSA9IEhhbmRsZWJhcnMudGVtcGxhdGUsIHRlbXBsYXRlcyA9ICcpO1xuICAgIGlmIChvcHRzLm5hbWVzcGFjZSkge1xuICAgICAgb3V0cHV0LmFkZChvcHRzLm5hbWVzcGFjZSk7XG4gICAgICBvdXRwdXQuYWRkKCcgPSAnKTtcbiAgICAgIG91dHB1dC5hZGQob3B0cy5uYW1lc3BhY2UpO1xuICAgICAgb3V0cHV0LmFkZCgnIHx8ICcpO1xuICAgIH1cbiAgICBvdXRwdXQuYWRkKCd7fTtcXG4nKTtcbiAgfVxuXG4gIG9wdHMudGVtcGxhdGVzLmZvckVhY2goZnVuY3Rpb24odGVtcGxhdGUpIHtcbiAgICBsZXQgb3B0aW9ucyA9IHtcbiAgICAgIGtub3duSGVscGVyczoga25vd24sXG4gICAgICBrbm93bkhlbHBlcnNPbmx5OiBvcHRzLm9cbiAgICB9O1xuXG4gICAgaWYgKG9wdHMubWFwKSB7XG4gICAgICBvcHRpb25zLnNyY05hbWUgPSB0ZW1wbGF0ZS5wYXRoO1xuICAgIH1cbiAgICBpZiAob3B0cy5kYXRhKSB7XG4gICAgICBvcHRpb25zLmRhdGEgPSB0cnVlO1xuICAgIH1cblxuICAgIGxldCBwcmVjb21waWxlZCA9IEhhbmRsZWJhcnMucHJlY29tcGlsZSh0ZW1wbGF0ZS5zb3VyY2UsIG9wdGlvbnMpO1xuXG4gICAgLy8gSWYgd2UgYXJlIGdlbmVyYXRpbmcgYSBzb3VyY2UgbWFwLCB3ZSBoYXZlIHRvIHJlY29uc3RydWN0IHRoZSBTb3VyY2VOb2RlIG9iamVjdFxuICAgIGlmIChvcHRzLm1hcCkge1xuICAgICAgbGV0IGNvbnN1bWVyID0gbmV3IFNvdXJjZU1hcENvbnN1bWVyKHByZWNvbXBpbGVkLm1hcCk7XG4gICAgICBwcmVjb21waWxlZCA9IFNvdXJjZU5vZGUuZnJvbVN0cmluZ1dpdGhTb3VyY2VNYXAoXG4gICAgICAgIHByZWNvbXBpbGVkLmNvZGUsXG4gICAgICAgIGNvbnN1bWVyXG4gICAgICApO1xuICAgIH1cblxuICAgIGlmIChvcHRzLnNpbXBsZSkge1xuICAgICAgb3V0cHV0LmFkZChbcHJlY29tcGlsZWQsICdcXG4nXSk7XG4gICAgfSBlbHNlIHtcbiAgICAgIGlmICghdGVtcGxhdGUubmFtZSkge1xuICAgICAgICB0aHJvdyBuZXcgSGFuZGxlYmFycy5FeGNlcHRpb24oJ05hbWUgbWlzc2luZyBmb3IgdGVtcGxhdGUnKTtcbiAgICAgIH1cblxuICAgICAgaWYgKG9wdHMuYW1kICYmICFtdWx0aXBsZSkge1xuICAgICAgICBvdXRwdXQuYWRkKCdyZXR1cm4gJyk7XG4gICAgICB9XG4gICAgICBvdXRwdXQuYWRkKFtcbiAgICAgICAgb2JqZWN0TmFtZSxcbiAgICAgICAgJ1snLFxuICAgICAgICBxdW90ZUZvckphdmFTY3JpcHQodGVtcGxhdGUubmFtZSksXG4gICAgICAgICddID0gdGVtcGxhdGUoJyxcbiAgICAgICAgcHJlY29tcGlsZWQsXG4gICAgICAgICcpO1xcbidcbiAgICAgIF0pO1xuICAgIH1cbiAgfSk7XG5cbiAgLy8gT3V0cHV0IHRoZSBjb250ZW50XG4gIGlmICghb3B0cy5zaW1wbGUpIHtcbiAgICBpZiAob3B0cy5hbWQpIHtcbiAgICAgIGlmIChtdWx0aXBsZSkge1xuICAgICAgICBvdXRwdXQuYWRkKFsncmV0dXJuICcsIG9iamVjdE5hbWUsICc7XFxuJ10pO1xuICAgICAgfVxuICAgICAgb3V0cHV0LmFkZCgnfSk7Jyk7XG4gICAgfSBlbHNlIGlmICghb3B0cy5jb21tb25qcykge1xuICAgICAgb3V0cHV0LmFkZCgnfSkoKTsnKTtcbiAgICB9XG4gIH1cblxuICBpZiAob3B0cy5tYXApIHtcbiAgICBvdXRwdXQuYWRkKFxuICAgICAgJ1xcbi8vIyBzb3VyY2VNYXBwaW5nVVJMPScgKyBzYW5pdGl6ZVNvdXJjZU1hcENvbW1lbnQob3B0cy5tYXApICsgJ1xcbidcbiAgICApO1xuICB9XG5cbiAgb3V0cHV0ID0gb3V0cHV0LnRvU3RyaW5nV2l0aFNvdXJjZU1hcCgpO1xuICBvdXRwdXQubWFwID0gb3V0cHV0Lm1hcCArICcnO1xuXG4gIGlmIChvcHRzLm1pbikge1xuICAgIG91dHB1dCA9IG1pbmlmeShvdXRwdXQsIG9wdHMubWFwKTtcbiAgfVxuXG4gIGlmIChvcHRzLm1hcCkge1xuICAgIGZzLndyaXRlRmlsZVN5bmMob3B0cy5tYXAsIG91dHB1dC5tYXAsICd1dGY4Jyk7XG4gIH1cbiAgb3V0cHV0ID0gb3V0cHV0LmNvZGU7XG5cbiAgaWYgKG9wdHMub3V0cHV0KSB7XG4gICAgZnMud3JpdGVGaWxlU3luYyhvcHRzLm91dHB1dCwgb3V0cHV0LCAndXRmOCcpO1xuICB9IGVsc2Uge1xuICAgIGNvbnNvbGUubG9nKG91dHB1dCk7XG4gIH1cbn07XG5cbmZ1bmN0aW9uIGFycmF5Q2FzdCh2YWx1ZSkge1xuICB2YWx1ZSA9IHZhbHVlICE9IG51bGwgPyB2YWx1ZSA6IFtdO1xuICBpZiAoIUFycmF5LmlzQXJyYXkodmFsdWUpKSB7XG4gICAgdmFsdWUgPSBbdmFsdWVdO1xuICB9XG4gIHJldHVybiB2YWx1ZTtcbn1cblxuLypcbiAqIFNhZmVseSBxdW90ZXMgYSB2YWx1ZSBmb3IgZW1iZWRkaW5nIGluIGdlbmVyYXRlZCBKYXZhU2NyaXB0IHN0cmluZ3NcbiAqXG4gKiBVc2VzIEpTT04uc3RyaW5naWZ5IHdoaWNoIGhhbmRsZXMgYWxsIHNwZWNpYWwgY2hhcmFjdGVycywgYW5kIGFsc28gZXNjYXBlc1xuICogbGluZSBzZXBhcmF0b3JzIGFuZCB0aGUgc2VxdWVuY2VzIHRoYXQgY291bGQgZW5kIG9yIGNoYW5nZSB0aGUgcGFyc2luZyBvZiBhblxuICogZW5jbG9zaW5nIDxzY3JpcHQ+IGVsZW1lbnQuXG4gKi9cbmZ1bmN0aW9uIHF1b3RlRm9ySmF2YVNjcmlwdCh2YWx1ZSkge1xuICByZXR1cm4gc2FmZUpzb25MaXRlcmFsKFN0cmluZyh2YWx1ZSkpO1xufVxuXG4vKipcbiAqIFZhbGlkYXRlcyB0aGF0IGEgbmFtZXNwYWNlIGlzIGEgbGVnaXRpbWF0ZSBkb3R0ZWQgSmF2YVNjcmlwdCBpZGVudGlmaWVyXG4gKiAoZS5nLiBcIkFwcC50ZW1wbGF0ZXNcIikgdG8gcHJldmVudCBhcmJpdHJhcnkgY29kZSBpbmplY3Rpb25cbiAqL1xuZnVuY3Rpb24gaXNWYWxpZE5hbWVzcGFjZShuYW1lc3BhY2UpIHtcbiAgcmV0dXJuIC9eW0EtWmEtel8kXVtBLVphLXowLTlfJF0qKFxcLltBLVphLXpfJF1bQS1aYS16MC05XyRdKikqJC8udGVzdChcbiAgICBuYW1lc3BhY2VcbiAgKTtcbn1cblxuLyoqXG4gKiBTdHJpcHMgbGluZSB0ZXJtaW5hdG9ycyBmcm9tIHNvdXJjZSBtYXAgVVJMcyB0byBwcmV2ZW50IGluamVjdGlvbiBvZiBuZXdcbiAqIEphdmFTY3JpcHQgbGluZXMgdmlhIHRoZSBzb3VyY2VNYXBwaW5nVVJMIGNvbW1lbnQsIGFuZCBwZXJjZW50LWVuY29kZXMgXCI8XCJcbiAqIHNvIHRoYXQgdGhlIGNvbW1lbnQgY2Fubm90IGVuZCBvciBjaGFuZ2UgdGhlIHBhcnNpbmcgb2YgYW4gZW5jbG9zaW5nXG4gKiA8c2NyaXB0PiBlbGVtZW50LlxuICovXG5mdW5jdGlvbiBzYW5pdGl6ZVNvdXJjZU1hcENvbW1lbnQodmFsdWUpIHtcbiAgcmV0dXJuIFN0cmluZyh2YWx1ZSlcbiAgICAucmVwbGFjZSgvW1xcclxcblxcdTIwMjhcXHUyMDI5XS9nLCAnJylcbiAgICAucmVwbGFjZSgvPC9nLCAnJTNDJyk7XG59XG5cbi8qKlxuICogUnVuIHVnbGlmeSB0byBtaW5pZnkgdGhlIGNvbXBpbGVkIHRlbXBsYXRlLCBpZiB1Z2xpZnkgZXhpc3RzIGluIHRoZSBkZXBlbmRlbmNpZXMuXG4gKlxuICogV2UgYXJlIHVzaW5nIGByZXF1aXJlYCBpbnN0ZWFkIG9mIGBpbXBvcnRgIGhlcmUsIGJlY2F1c2UgZXM2LW1vZHVsZXMgZG8gbm90IGFsbG93XG4gKiBkeW5hbWljIGltcG9ydHMgYW5kIHVnbGlmeS1qcyBpcyBhbiBvcHRpb25hbCBkZXBlbmRlbmN5LiBTaW5jZSB3ZSBhcmUgaW5zaWRlIE5vZGVKUyBoZXJlLCB0aGlzXG4gKiBzaG91bGQgbm90IGJlIGEgcHJvYmxlbS5cbiAqXG4gKiBAcGFyYW0ge3N0cmluZ30gb3V0cHV0IHRoZSBjb21waWxlZCB0ZW1wbGF0ZVxuICogQHBhcmFtIHtzdHJpbmd9IHNvdXJjZU1hcEZpbGUgdGhlIGZpbGUgdG8gd3JpdGUgdGhlIHNvdXJjZSBtYXAgdG8uXG4gKi9cbmZ1bmN0aW9uIG1pbmlmeShvdXRwdXQsIHNvdXJjZU1hcEZpbGUpIHtcbiAgdHJ5IHtcbiAgICAvLyBUcnkgdG8gcmVzb2x2ZSB1Z2xpZnktanMgaW4gb3JkZXIgdG8gc2VlIGlmIGl0IGRvZXMgZXhpc3RcbiAgICByZXF1aXJlLnJlc29sdmUoJ3VnbGlmeS1qcycpO1xuICB9IGNhdGNoIChlKSB7XG4gICAgaWYgKGUuY29kZSAhPT0gJ01PRFVMRV9OT1RfRk9VTkQnKSB7XG4gICAgICAvLyBTb21ldGhpbmcgZWxzZSBzZWVtcyB0byBiZSB3cm9uZ1xuICAgICAgdGhyb3cgZTtcbiAgICB9XG4gICAgLy8gaXQgZG9lcyBub3QgZXhpc3QhXG4gICAgY29uc29sZS5lcnJvcihcbiAgICAgICdDb2RlIG1pbmltaXphdGlvbiBpcyBkaXNhYmxlZCBkdWUgdG8gbWlzc2luZyB1Z2xpZnktanMgZGVwZW5kZW5jeSdcbiAgICApO1xuICAgIHJldHVybiBvdXRwdXQ7XG4gIH1cbiAgcmV0dXJuIHJlcXVpcmUoJ3VnbGlmeS1qcycpLm1pbmlmeShvdXRwdXQuY29kZSwge1xuICAgIHNvdXJjZU1hcDoge1xuICAgICAgY29udGVudDogb3V0cHV0Lm1hcCxcbiAgICAgIC8vIHVnbGlmeSB3cml0ZXMgaXRzIG93biBzb3VyY2VNYXBwaW5nVVJMIGNvbW1lbnQsIHNvIHRoZSBVUkwgaGFzIHRvIGJlXG4gICAgICAvLyBzYW5pdGl6ZWQgaGVyZSBhcyB3ZWxsLlxuICAgICAgdXJsOiBzb3VyY2VNYXBGaWxlICYmIHNhbml0aXplU291cmNlTWFwQ29tbWVudChzb3VyY2VNYXBGaWxlKVxuICAgIH1cbiAgfSk7XG59XG4iXX0=
