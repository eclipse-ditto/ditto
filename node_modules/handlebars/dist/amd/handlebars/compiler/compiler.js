define(['exports', '../exception', '../utils', './ast', './node-types'], function (exports, _exception, _utils, _ast, _nodeTypes) {
  /* eslint-disable new-cap */

  'use strict';

  exports.__esModule = true;
  exports.Compiler = Compiler;
  exports.precompile = precompile;
  exports.compile = compile;
  // istanbul ignore next

  function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { 'default': obj }; }

  var _Exception = _interopRequireDefault(_exception);

  var _AST = _interopRequireDefault(_ast);

  var slice = [].slice;

  function Compiler() {}

  // the foundHelper register will disambiguate helper lookup from finding a
  // function in a context. This is necessary for mustache compatibility, which
  // requires that context functions in blocks are evaluated by blockHelperMissing,
  // and then proceed as if the resulting value was provided to blockHelperMissing.

  Compiler.prototype = {
    compiler: Compiler,

    equals: function equals(other) {
      var len = this.opcodes.length;
      if (other.opcodes.length !== len) {
        return false;
      }

      for (var i = 0; i < len; i++) {
        var opcode = this.opcodes[i],
            otherOpcode = other.opcodes[i];
        if (opcode.opcode !== otherOpcode.opcode || !argEquals(opcode.args, otherOpcode.args)) {
          return false;
        }
      }

      // We know that length is the same between the two arrays because they are directly tied
      // to the opcode behavior above.
      len = this.children.length;
      for (var i = 0; i < len; i++) {
        if (!this.children[i].equals(other.children[i])) {
          return false;
        }
      }

      return true;
    },

    guid: 0,

    compile: function compile(program, options) {
      this.sourceNode = [];
      this.opcodes = [];
      this.children = [];
      this.options = options;
      this.stringParams = options.stringParams;
      this.trackIds = options.trackIds;

      options.blockParams = options.blockParams || [];

      options.knownHelpers = _utils.extend(Object.create(null), {
        helperMissing: true,
        blockHelperMissing: true,
        each: true,
        'if': true,
        unless: true,
        'with': true,
        log: true,
        lookup: true
      }, options.knownHelpers);

      return this.accept(program);
    },

    compileProgram: function compileProgram(program) {
      var childCompiler = new this.compiler(),
          // eslint-disable-line new-cap
      result = childCompiler.compile(program, this.options),
          guid = this.guid++;

      this.usePartial = this.usePartial || result.usePartial;

      this.children[guid] = result;
      this.useDepths = this.useDepths || result.useDepths;

      return guid;
    },

    accept: function accept(node) {
      // Dispatching on an unknown type could call an arbitrary Compiler method.
      if (_utils.indexOf(_nodeTypes.NODE_TYPES, node.type) === -1 || !this[node.type]) {
        throw new _Exception['default']('Unknown type: ' + node.type, node);
      }

      this.sourceNode.unshift(node);
      var ret = this[node.type](node);
      this.sourceNode.shift();
      return ret;
    },

    Program: function Program(program) {
      validateBlockParams(program);
      this.options.blockParams.unshift(program.blockParams);

      var body = program.body,
          bodyLength = body.length;
      for (var i = 0; i < bodyLength; i++) {
        this.accept(body[i]);
      }

      this.options.blockParams.shift();

      this.isSimple = bodyLength === 1;
      this.blockParams = program.blockParams ? program.blockParams.length : 0;

      return this;
    },

    BlockStatement: function BlockStatement(block) {
      transformLiteralToPath(block);

      var program = block.program,
          inverse = block.inverse;

      program = program && this.compileProgram(program);
      inverse = inverse && this.compileProgram(inverse);

      var type = this.classifySexpr(block);

      if (type === 'helper') {
        this.helperSexpr(block, program, inverse);
      } else if (type === 'simple') {
        this.simpleSexpr(block);

        // now that the simple mustache is resolved, we need to
        // evaluate it by executing `blockHelperMissing`
        this.opcode('pushProgram', program);
        this.opcode('pushProgram', inverse);
        this.opcode('emptyHash');
        this.opcode('blockValue', block.path.original);
      } else {
        this.ambiguousSexpr(block, program, inverse);

        // now that the simple mustache is resolved, we need to
        // evaluate it by executing `blockHelperMissing`
        this.opcode('pushProgram', program);
        this.opcode('pushProgram', inverse);
        this.opcode('emptyHash');
        this.opcode('ambiguousBlockValue');
      }

      this.opcode('append');
    },

    DecoratorBlock: function DecoratorBlock(decorator) {
      var program = decorator.program && this.compileProgram(decorator.program);
      var params = this.setupFullMustacheParams(decorator, program, undefined),
          path = decorator.path;

      this.useDecorators = true;
      this.opcode('registerDecorator', params.length, path.original);
    },

    PartialStatement: function PartialStatement(partial) {
      this.usePartial = true;

      var program = partial.program;
      if (program) {
        program = this.compileProgram(partial.program);
      }

      var params = partial.params;
      if (params.length > 1) {
        throw new _Exception['default']('Unsupported number of partial arguments: ' + params.length, partial);
      } else if (!params.length) {
        if (this.options.explicitPartialContext) {
          this.opcode('pushLiteral', 'undefined');
        } else {
          params.push({ type: 'PathExpression', parts: [], depth: 0 });
        }
      }

      var partialName = partial.name.original,
          isDynamic = partial.name.type === 'SubExpression';
      if (isDynamic) {
        this.accept(partial.name);
      }

      this.setupFullMustacheParams(partial, program, undefined, true);

      var indent = partial.indent || '';
      if (this.options.preventIndent && indent) {
        this.opcode('appendContent', indent);
        indent = '';
      }

      this.opcode('invokePartial', isDynamic, partialName, indent);
      this.opcode('append');
    },
    PartialBlockStatement: function PartialBlockStatement(partialBlock) {
      this.PartialStatement(partialBlock);
    },

    MustacheStatement: function MustacheStatement(mustache) {
      this.SubExpression(mustache);

      if (mustache.escaped && !this.options.noEscape) {
        this.opcode('appendEscaped');
      } else {
        this.opcode('append');
      }
    },
    Decorator: function Decorator(decorator) {
      this.DecoratorBlock(decorator);
    },

    ContentStatement: function ContentStatement(content) {
      if (content.value != null && typeof content.value !== 'string') {
        throw invalidAst(content, 'value must be a string');
      }
      if (content.value) {
        this.opcode('appendContent', content.value);
      }
    },

    CommentStatement: function CommentStatement() {},

    SubExpression: function SubExpression(sexpr) {
      transformLiteralToPath(sexpr);
      var type = this.classifySexpr(sexpr);

      if (type === 'simple') {
        this.simpleSexpr(sexpr);
      } else if (type === 'helper') {
        this.helperSexpr(sexpr);
      } else {
        this.ambiguousSexpr(sexpr);
      }
    },
    ambiguousSexpr: function ambiguousSexpr(sexpr, program, inverse) {
      var path = sexpr.path,
          name = path.parts[0],
          isBlock = program != null || inverse != null;

      this.opcode('getContext', validateDepth(path));

      this.opcode('pushProgram', program);
      this.opcode('pushProgram', inverse);

      path.strict = true;
      this.accept(path);

      this.opcode('invokeAmbiguous', name, isBlock);
    },

    simpleSexpr: function simpleSexpr(sexpr) {
      var path = sexpr.path;
      path.strict = true;
      this.accept(path);
      this.opcode('resolvePossibleLambda');
    },

    helperSexpr: function helperSexpr(sexpr, program, inverse) {
      var params = this.setupFullMustacheParams(sexpr, program, inverse),
          path = sexpr.path,
          name = path.parts[0];

      if (this.options.knownHelpers[name]) {
        this.opcode('invokeKnownHelper', params.length, name);
      } else if (this.options.knownHelpersOnly) {
        throw new _Exception['default']('You specified knownHelpersOnly, but used the unknown helper ' + name, sexpr);
      } else {
        path.strict = true;
        path.falsy = true;

        this.accept(path);
        this.opcode('invokeHelper', params.length, path.original, _AST['default'].helpers.simpleId(path));
      }
    },

    PathExpression: function PathExpression(path) {
      // Validate AST values at the compiler boundary: javascript-compiler.js
      // trusts all opcode arguments to be safe.
      var depth = validateDepth(path);
      var parts = validateParts(path);

      this.addDepth(depth);
      this.opcode('getContext', depth);

      var name = parts[0],
          scoped = _AST['default'].helpers.scopedId(path),
          blockParamId = !depth && !scoped && this.blockParamIndex(name);

      if (blockParamId) {
        this.opcode('lookupBlockParam', blockParamId, parts);
      } else if (!name) {
        // Context reference, i.e. `{{foo .}}` or `{{foo ..}}`
        this.opcode('pushContext');
      } else if (path.data) {
        this.options.data = true;
        this.opcode('lookupData', depth, parts, path.strict);
      } else {
        this.opcode('lookupOnContext', parts, path.falsy, path.strict, scoped);
      }
    },

    StringLiteral: function StringLiteral(string) {
      validateLiteral(string);
      this.opcode('pushString', string.value);
    },

    NumberLiteral: function NumberLiteral(number) {
      validateLiteral(number);
      this.opcode('pushLiteral', number.value);
    },

    BooleanLiteral: function BooleanLiteral(bool) {
      validateLiteral(bool);
      this.opcode('pushLiteral', bool.value);
    },

    UndefinedLiteral: function UndefinedLiteral() {
      this.opcode('pushLiteral', 'undefined');
    },

    NullLiteral: function NullLiteral() {
      this.opcode('pushLiteral', 'null');
    },

    Hash: function Hash(hash) {
      var pairs = hash.pairs,
          i = 0,
          l = undefined;

      if (!_utils.isArray(pairs)) {
        throw invalidAst(hash, 'pairs must be an array');
      }
      l = pairs.length;
      for (var j = 0; j < l; j++) {
        if (!pairs[j] || typeof pairs[j].key !== 'string') {
          throw invalidAst(hash, 'pair keys must be strings');
        }
      }

      this.opcode('pushHash');

      for (; i < l; i++) {
        this.pushParam(pairs[i].value);
      }
      while (i--) {
        this.opcode('assignToHash', pairs[i].key);
      }
      this.opcode('popHash');
    },

    // HELPERS
    opcode: function opcode(name) {
      this.opcodes.push({
        opcode: name,
        args: slice.call(arguments, 1),
        loc: this.sourceNode[0].loc
      });
    },

    addDepth: function addDepth(depth) {
      if (!depth) {
        return;
      }

      this.useDepths = true;
    },

    classifySexpr: function classifySexpr(sexpr) {
      // The first part is used as a helper name below, e.g. for known helpers,
      // before the PathExpression handler gets to validate the parts.
      validateParts(sexpr.path);
      var isSimple = _AST['default'].helpers.simpleId(sexpr.path);

      var isBlockParam = isSimple && !!this.blockParamIndex(sexpr.path.parts[0]);

      // a mustache is an eligible helper if:
      // * its id is simple (a single part, not `this` or `..`)
      var isHelper = !isBlockParam && _AST['default'].helpers.helperExpression(sexpr);

      // if a mustache is an eligible helper but not a definite
      // helper, it is ambiguous, and will be resolved in a later
      // pass or at runtime.
      var isEligible = !isBlockParam && (isHelper || isSimple);

      // if ambiguous, we can possibly resolve the ambiguity now
      // An eligible helper is one that does not have a complex path, i.e. `this.foo`, `../foo` etc.
      if (isEligible && !isHelper) {
        var _name = sexpr.path.parts[0],
            options = this.options;
        if (options.knownHelpers[_name]) {
          isHelper = true;
        } else if (options.knownHelpersOnly) {
          isEligible = false;
        }
      }

      if (isHelper) {
        return 'helper';
      } else if (isEligible) {
        return 'ambiguous';
      } else {
        return 'simple';
      }
    },

    pushParams: function pushParams(params) {
      for (var i = 0, l = params.length; i < l; i++) {
        this.pushParam(params[i]);
      }
    },

    pushParam: function pushParam(val) {
      // Literal params are not visited by their node handlers in stringParams
      // mode, so their values must be validated here as well.
      validateLiteral(val);
      var value = val.value != null ? val.value : val.original || '';
      var depth = validateDepth(val);

      if (this.stringParams) {
        // pushStringParam emits non-string values as raw JavaScript literals.
        // Literal values are validated above, but `original` is not, so
        // anything that is not a number or boolean is stringified.
        if (typeof value !== 'number' && typeof value !== 'boolean') {
          value = String(value);
        }
        if (value.replace) {
          value = value.replace(/^(\.?\.\/)*/g, '').replace(/\//g, '.');
        }
        if (depth) {
          this.addDepth(depth);
        }
        this.opcode('getContext', depth);
        this.opcode('pushStringParam', value, val.type);

        if (val.type === 'SubExpression') {
          // SubExpressions get evaluated and passed in
          // in string params mode.
          this.accept(val);
        }
      } else {
        if (this.trackIds) {
          var blockParamIndex = undefined;
          if (val.parts && !_AST['default'].helpers.scopedId(val) && !depth) {
            // The parts are read here before the PathExpression handler,
            // invoked by accept() below, gets to validate them.
            blockParamIndex = this.blockParamIndex(validateParts(val)[0]);
          }
          if (blockParamIndex) {
            var blockParamChild = val.parts.slice(1).join('.');
            this.opcode('pushId', 'BlockParam', blockParamIndex, blockParamChild);
          } else {
            value = val.original || value;
            if (value.replace) {
              value = value.replace(/^this(?:\.|$)/, '').replace(/^\.\//, '').replace(/^\.$/, '');
            }

            this.opcode('pushId', val.type, value);
          }
        }
        this.accept(val);
      }
    },

    setupFullMustacheParams: function setupFullMustacheParams(sexpr, program, inverse, omitEmpty) {
      var params = sexpr.params;
      this.pushParams(params);

      this.opcode('pushProgram', program);
      this.opcode('pushProgram', inverse);

      if (sexpr.hash) {
        this.accept(sexpr.hash);
      } else {
        this.opcode('emptyHash', omitEmpty);
      }

      return params;
    },

    blockParamIndex: function blockParamIndex(name) {
      for (var depth = 0, len = this.options.blockParams.length; depth < len; depth++) {
        var blockParams = this.options.blockParams[depth],
            param = blockParams && _utils.indexOf(blockParams, name);
        if (blockParams && param >= 0) {
          return [depth, param];
        }
      }
    }
  };

  function precompile(input, options, env) {
    if (input == null || typeof input !== 'string' && input.type !== 'Program') {
      throw new _Exception['default']('You must pass a string or Handlebars AST to Handlebars.precompile. You passed ' + input);
    }

    options = options || {};
    if (!('data' in options)) {
      options.data = true;
    }
    if (options.compat) {
      options.useDepths = true;
    }

    var ast = env.parse(input, options),
        environment = new env.Compiler().compile(ast, options);
    return new env.JavaScriptCompiler().compile(environment, options);
  }

  function compile(input, options, env) {
    if (options === undefined) options = {};

    if (input == null || typeof input !== 'string' && input.type !== 'Program') {
      throw new _Exception['default']('You must pass a string or Handlebars AST to Handlebars.compile. You passed ' + input);
    }

    options = _utils.extend({}, options);
    if (!('data' in options)) {
      options.data = true;
    }
    if (options.compat) {
      options.useDepths = true;
    }

    var compiled = undefined;

    function compileInput() {
      var ast = env.parse(input, options),
          environment = new env.Compiler().compile(ast, options),
          templateSpec = new env.JavaScriptCompiler().compile(environment, options, undefined, true);
      return env.template(templateSpec);
    }

    // Template is only compiled on first use and cached after that point.
    function ret(context, execOptions) {
      if (!compiled) {
        compiled = compileInput();
      }
      return compiled.call(this, context, execOptions);
    }
    ret._setup = function (setupOptions) {
      if (!compiled) {
        compiled = compileInput();
      }
      return compiled._setup(setupOptions);
    };
    ret._child = function (i, data, blockParams, depths) {
      if (!compiled) {
        compiled = compileInput();
      }
      return compiled._child(i, data, blockParams, depths);
    };
    return ret;
  }

  /**
   * Create the exception thrown for an AST node that fails validation.
   *
   * @param {Object} node the offending AST node; its `type` is included in the
   *   message and its `loc`, if present, in the error location
   * @param {string} message what is wrong with the node
   * @returns {Exception} the exception to throw
   */
  function invalidAst(node, message) {
    return new _Exception['default']('Invalid AST: ' + node.type + ' ' + message, node);
  }

  /**
   * Validate the `depth` of a path or param node.
   *
   * A missing depth is treated as 0, because ASTs built by hand or by other
   * tools commonly omit it. Any depth that is present must be a non-negative
   * integer, since it is written into the generated code.
   *
   * @param {Object} node the AST node whose `depth` is validated
   * @returns {number} the validated depth, or 0 if the node has none
   * @throws {Exception} if the depth is present but not a non-negative integer
   */
  function validateDepth(node) {
    var depth = node.depth;
    if (depth == null) {
      return 0;
    }
    if (typeof depth !== 'number' || !isFinite(depth) || Math.floor(depth) !== depth || depth < 0) {
      throw invalidAst(node, 'depth must be a non-negative integer');
    }
    return depth;
  }

  /**
   * Validate the `value` of a StringLiteral, NumberLiteral or BooleanLiteral.
   *
   * Literal values are written into the generated code, so they must have
   * exactly the type their node promises. Nodes of any other type pass through
   * unchanged.
   *
   * Numbers need not be finite: the parser produces Infinity for very long
   * number literals, and Infinity and NaN are emitted as the read-only globals
   * of the same name.
   *
   * @param {Object} node the AST node to validate
   * @throws {Exception} if the value does not match the literal type
   */
  function validateLiteral(node) {
    var value = node.value;
    if (node.type === 'StringLiteral' && typeof value !== 'string') {
      throw invalidAst(node, 'value must be a string');
    } else if (node.type === 'NumberLiteral' && typeof value !== 'number') {
      throw invalidAst(node, 'value must be a number');
    } else if (node.type === 'BooleanLiteral' && typeof value !== 'boolean') {
      throw invalidAst(node, 'value must be a boolean');
    }
  }

  /**
   * Validate that a node property is an array containing only strings.
   *
   * @param {Object} node the AST node the property belongs to, for the error
   * @param {*} value the property value to validate
   * @param {string} name the property name, used in the error message
   * @returns {string[]} the validated array
   * @throws {Exception} if the value is not an array or contains non-strings
   */
  function validateStringArray(node, value, name) {
    if (!_utils.isArray(value)) {
      throw invalidAst(node, name + ' must be an array');
    }
    for (var i = 0; i < value.length; i++) {
      if (typeof value[i] !== 'string') {
        throw invalidAst(node, name + ' must only contain strings');
      }
    }
    return value;
  }

  /**
   * Validate the `parts` of a PathExpression node.
   *
   * @param {Object} path the PathExpression node
   * @returns {string[]} the validated parts
   * @throws {Exception} if `parts` is not an array of strings
   */
  function validateParts(path) {
    return validateStringArray(path, path.parts, 'parts');
  }

  /**
   * Validate the optional `blockParams` of a Program node. Its length is
   * written into the generated code, so it must be an array of strings when
   * present.
   *
   * @param {Object} program the Program node
   * @throws {Exception} if `blockParams` is present and not an array of strings
   */
  function validateBlockParams(program) {
    if (program.blockParams != null) {
      validateStringArray(program, program.blockParams, 'blockParams');
    }
  }

  function argEquals(a, b) {
    if (a === b) {
      return true;
    }

    if (_utils.isArray(a) && _utils.isArray(b) && a.length === b.length) {
      for (var i = 0; i < a.length; i++) {
        if (!argEquals(a[i], b[i])) {
          return false;
        }
      }
      return true;
    }
  }

  function transformLiteralToPath(sexpr) {
    if (!sexpr.path.parts) {
      var literal = sexpr.path;
      // Casting to string here to make false and 0 literal values play nicely with the rest
      // of the system.
      sexpr.path = {
        type: 'PathExpression',
        data: false,
        depth: 0,
        parts: [literal.original + ''],
        original: literal.original + '',
        loc: literal.loc
      };
    }
  }
});
//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJzb3VyY2VzIjpbIi4uLy4uLy4uLy4uL2xpYi9oYW5kbGViYXJzL2NvbXBpbGVyL2NvbXBpbGVyLmpzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiI7Ozs7Ozs7Ozs7Ozs7Ozs7O0FBT0EsTUFBTSxLQUFLLEdBQUcsRUFBRSxDQUFDLEtBQUssQ0FBQzs7QUFFaEIsV0FBUyxRQUFRLEdBQUcsRUFBRTs7Ozs7OztBQU83QixVQUFRLENBQUMsU0FBUyxHQUFHO0FBQ25CLFlBQVEsRUFBRSxRQUFROztBQUVsQixVQUFNLEVBQUUsZ0JBQVMsS0FBSyxFQUFFO0FBQ3RCLFVBQUksR0FBRyxHQUFHLElBQUksQ0FBQyxPQUFPLENBQUMsTUFBTSxDQUFDO0FBQzlCLFVBQUksS0FBSyxDQUFDLE9BQU8sQ0FBQyxNQUFNLEtBQUssR0FBRyxFQUFFO0FBQ2hDLGVBQU8sS0FBSyxDQUFDO09BQ2Q7O0FBRUQsV0FBSyxJQUFJLENBQUMsR0FBRyxDQUFDLEVBQUUsQ0FBQyxHQUFHLEdBQUcsRUFBRSxDQUFDLEVBQUUsRUFBRTtBQUM1QixZQUFJLE1BQU0sR0FBRyxJQUFJLENBQUMsT0FBTyxDQUFDLENBQUMsQ0FBQztZQUMxQixXQUFXLEdBQUcsS0FBSyxDQUFDLE9BQU8sQ0FBQyxDQUFDLENBQUMsQ0FBQztBQUNqQyxZQUNFLE1BQU0sQ0FBQyxNQUFNLEtBQUssV0FBVyxDQUFDLE1BQU0sSUFDcEMsQ0FBQyxTQUFTLENBQUMsTUFBTSxDQUFDLElBQUksRUFBRSxXQUFXLENBQUMsSUFBSSxDQUFDLEVBQ3pDO0FBQ0EsaUJBQU8sS0FBSyxDQUFDO1NBQ2Q7T0FDRjs7OztBQUlELFNBQUcsR0FBRyxJQUFJLENBQUMsUUFBUSxDQUFDLE1BQU0sQ0FBQztBQUMzQixXQUFLLElBQUksQ0FBQyxHQUFHLENBQUMsRUFBRSxDQUFDLEdBQUcsR0FBRyxFQUFFLENBQUMsRUFBRSxFQUFFO0FBQzVCLFlBQUksQ0FBQyxJQUFJLENBQUMsUUFBUSxDQUFDLENBQUMsQ0FBQyxDQUFDLE1BQU0sQ0FBQyxLQUFLLENB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S0FBSyxDQUFDLElBQUksQ0FBQyxLQUFLLEVBQUU7QUFDckIsVUFBSSxPQUFPLEdBQUcsS0FBSyxDQUFDLElBQUksQ0FBQzs7O0FBR3pCLFdBQUssQ0FBQyxJQUFJLEdBQUc7QUFDWCxZQUFJLEVBQUUsZ0JBQWdCO0FBQ3RCLFlBQUksRUFBRSxLQUFLO0FBQ1gsYUFBSyxFQUFFLENBQUM7QUFDUixhQUFLLEVBQUUsQ0FBQyxPQUFPLENBQUMsUUFBUSxHQUFHLEVBQUUsQ0FBQztBQUM5QixnQkFBUSxFQUFFLE9BQU8sQ0FBQyxRQUFRLEdBQUcsRUFBRTtBQUMvQixXQUFHLEVBQUUsT0FBTyxDQUFDLEdBQUc7T0FDakIsQ0FBQztLQUNIO0dBQ0YiLCJmaWxlIjoiY29tcGlsZXIuanMiLCJzb3VyY2VzQ29udGVudCI6WyIvKiBlc2xpbnQtZGlzYWJsZSBuZXctY2FwICovXG5cbmltcG9ydCBFeGNlcHRpb24gZnJvbSAnLi4vZXhjZXB0aW9uJztcbmltcG9ydCB7IGlzQXJyYXksIGluZGV4T2YsIGV4dGVuZCB9IGZyb20gJy4uL3V0aWxzJztcbmltcG9ydCBBU1QgZnJvbSAnLi9hc3QnO1xuaW1wb3J0IHsgTk9ERV9UWVBFUyB9IGZyb20gJy4vbm9kZS10eXBlcyc7XG5cbmNvbnN0IHNsaWNlID0gW10uc2xpY2U7XG5cbmV4cG9ydCBmdW5jdGlvbiBDb21waWxlcigpIHt9XG5cbi8vIHRoZSBmb3VuZEhlbHBlciByZWdpc3RlciB3aWxsIGRpc2FtYmlndWF0ZSBoZWxwZXIgbG9va3VwIGZyb20gZmluZGluZyBhXG4vLyBmdW5jdGlvbiBpbiBhIGNvbnRleHQuIFRoaXMgaXMgbmVjZXNzYXJ5IGZvciBtdXN0YWNoZSBjb21wYXRpYmlsaXR5LCB3aGljaFxuLy8gcmVxdWlyZXMgdGhhdCBjb250ZXh0IGZ1bmN0aW9ucyBpbiBibG9ja3MgYXJlIGV2YWx1YXRlZCBieSBibG9ja0hlbHBlck1pc3NpbmcsXG4vLyBhbmQgdGhlbiBwcm9jZWVkIGFzIGlmIHRoZSByZXN1bHRpbmcgdmFsdWUgd2FzIHByb3ZpZGVkIHRvIGJsb2NrSGVscGVyTWlzc2luZy5cblxuQ29tcGlsZXIucHJvdG90eXBlID0ge1xuICBjb21waWxlcjogQ29tcGlsZXIsXG5cbiAgZXF1YWxzOiBmdW5jdGlvbihvdGhlcikge1xuICAgIGxldCBsZW4gPSB0aGlzLm9wY29kZXMubGVuZ3RoO1xuICAgIGlmIChvdGhlci5vcGNvZGVzLmxlbmd0aCAhPT0gbGVuKSB7XG4gICAgICByZXR1cm4gZmFsc2U7XG4gICAgfVxuXG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPCBsZW47IGkrKykge1xuICAgICAgbGV0IG9wY29kZSA9IHRoaXMub3Bjb2Rlc1tpXSxcbiAgICAgICAgb3RoZXJPcGNvZGUgPSBvdGhlci5vcGNvZGVzW2ldO1xuICAgICAgaWYgKFxuICAgICAgICBvcGNvZGUub3Bjb2RlICE9PSBvdGhlck9wY29kZS5vcGNvZGUgfHxcbiAgICAgICAgIWFyZ0VxdWFscyhvcGNvZGUuYXJncywgb3RoZXJPcGNvZGUuYXJncylcbiAgICAgICkge1xuICAgICAgICByZXR1cm4gZmFsc2U7XG4gICAgICB9XG4gICAgfVxuXG4gICAgLy8gV2Uga25vdyB0aGF0IGxlbmd0aCBpcyB0aGUgc2FtZSBiZXR3ZWVuIHRoZSB0d28gYXJyYXlzIGJlY2F1c2UgdGhleSBhcmUgZGlyZWN0bHkgdGllZFxuICAgIC8vIHRvIHRoZSBvcGNvZGUgYmVoYXZpb3IgYWJvdmUuXG4gICAgbGVuID0gdGhpcy5jaGlsZHJlbi5sZW5ndGg7XG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPCBsZW47IGkrKykge1xuICAgICAgaWYgKCF0aGlzLmNoaWxkcmVuW2ldLmVxdWFscyhvdGhlci5jaGlsZHJlbltpXSkpIHtcbiAgICAgICAgcmV0dXJuIGZhbHNlO1xuICAgICAgfVxuICAgIH1cblxuICAgIHJldHVybiB0cnVlO1xuICB9LFxuXG4gIGd1aWQ6IDAsXG5cbiAgY29tcGlsZTogZnVuY3Rpb24ocHJvZ3JhbSwgb3B0aW9ucykge1xuICAgIHRoaXMuc291cmNlTm9kZSA9IFtdO1xuICAgIHRoaXMub3Bjb2RlcyA9IFtdO1xuICAgIHRoaXMuY2hpbGRyZW4gPSBbXTtcbiAgICB0aGlzLm9wdGlvbnMgPSBvcHRpb25zO1xuICAgIHRoaXMuc3RyaW5nUGFyYW1zID0gb3B0aW9ucy5zdHJpbmdQYXJhbXM7XG4gICAgdGhpcy50cmFja0lkcyA9IG9wdGlvbnMudHJhY2tJZHM7XG5cbiAgICBvcHRpb25zLmJsb2NrUGFyYW1zID0gb3B0aW9ucy5ibG9ja1BhcmFtcyB8fCBbXTtcblxuICAgIG9wdGlvbnMua25vd25IZWxwZXJzID0gZXh0ZW5kKFxuICAgICAgT2JqZWN0LmNyZWF0ZShudWxsKSxcbiAgICAgIHtcbiAgICAgICAgaGVscGVyTWlzc2luZzogdHJ1ZSxcbiAgICAgICAgYmxvY2tIZWxwZXJNaXNzaW5nOiB0cnVlLFxuICAgICAgICBlYWNoOiB0cnVlLFxuICAgICAgICBpZjogdHJ1ZSxcbiAgICAgICAgdW5sZXNzOiB0cnVlLFxuICAgICAgICB3aXRoOiB0cnVlLFxuICAgICAgICBsb2c6IHRydWUsXG4gICAgICAgIGxvb2t1cDogdHJ1ZVxuICAgICAgfSxcbiAgICAgIG9wdGlvbnMua25vd25IZWxwZXJzXG4gICAgKTtcblxuICAgIHJldHVybiB0aGlzLmFjY2VwdChwcm9ncmFtKTtcbiAgfSxcblxuICBjb21waWxlUHJvZ3JhbTogZnVuY3Rpb24ocHJvZ3JhbSkge1xuICAgIGxldCBjaGlsZENvbXBpbGVyID0gbmV3IHRoaXMuY29tcGlsZXIoKSwgLy8gZXNsaW50LWRpc2FibGUtbGluZSBuZXctY2FwXG4gICAgICByZXN1bHQgPSBjaGlsZENvbXBpbGVyLmNvbXBpbGUocHJvZ3JhbSwgdGhpcy5vcHRpb25zKSxcbiAgICAgIGd1aWQgPSB0aGlzLmd1aWQrKztcblxuICAgIHRoaXMudXNlUGFydGlhbCA9IHRoaXMudXNlUGFydGlhbCB8fCByZXN1bHQudXNlUGFydGlhbDtcblxuICAgIHRoaXMuY2hpbGRyZW5bZ3VpZF0gPSByZXN1bHQ7XG4gICAgdGhpcy51c2VEZXB0aHMgPSB0aGlzLnVzZURlcHRocyB8fCByZXN1bHQudXNlRGVwdGhzO1xuXG4gICAgcmV0dXJuIGd1aWQ7XG4gIH0sXG5cbiAgYWNjZXB0OiBmdW5jdGlvbihub2RlKSB7XG4gICAgLy8gRGlzcGF0Y2hpbmcgb24gYW4gdW5rbm93biB0eXBlIGNvdWxkIGNhbGwgYW4gYXJiaXRyYXJ5IENvbXBpbGVyIG1ldGhvZC5cbiAgICBpZiAoaW5kZXhPZihOT0RFX1RZUEVTLCBub2RlLnR5cGUpID09PSAtMSB8fCAhdGhpc1tub2RlLnR5cGVdKSB7XG4gICAgICB0aHJvdyBuZXcgRXhjZXB0aW9uKCdVbmtub3duIHR5cGU6ICcgKyBub2RlLnR5cGUsIG5vZGUpO1xuICAgIH1cblxuICAgIHRoaXMuc291cmNlTm9kZS51bnNoaWZ0KG5vZGUpO1xuICAgIGxldCByZXQgPSB0aGlzW25vZGUudHlwZV0obm9kZSk7XG4gICAgdGhpcy5zb3VyY2VOb2RlLnNoaWZ0KCk7XG4gICAgcmV0dXJuIHJldDtcbiAgfSxcblxuICBQcm9ncmFtOiBmdW5jdGlvbihwcm9ncmFtKSB7XG4gICAgdmFsaWRhdGVCbG9ja1BhcmFtcyhwcm9ncmFtKTtcbiAgICB0aGlzLm9wdGlvbnMuYmxvY2tQYXJhbXMudW5zaGlmdChwcm9ncmFtLmJsb2NrUGFyYW1zKTtcblxuICAgIGxldCBib2R5ID0gcHJvZ3JhbS5ib2R5LFxuICAgICAgYm9keUxlbmd0aCA9IGJvZHkubGVuZ3RoO1xuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgYm9keUxlbmd0aDsgaSsrKSB7XG4gICAgICB0aGlzLmFjY2VwdChib2R5W2ldKTtcbiAgICB9XG5cbiAgICB0aGlzLm9wdGlvbnMuYmxvY2tQYXJhbXMuc2hpZnQoKTtcblxuICAgIHRoaXMuaXNTaW1wbGUgPSBib2R5TGVuZ3RoID09PSAxO1xuICAgIHRoaXMuYmxvY2tQYXJhbXMgPSBwcm9ncmFtLmJsb2NrUGFyYW1zID8gcHJvZ3JhbS5ibG9ja1BhcmFtcy5sZW5ndGggOiAwO1xuXG4gICAgcmV0dXJuIHRoaXM7XG4gIH0sXG5cbiAgQmxvY2tTdGF0ZW1lbnQ6IGZ1bmN0aW9uKGJsb2NrKSB7XG4gICAgdHJhbnNmb3JtTGl0ZXJhbFRvUGF0aChibG9jayk7XG5cbiAgICBsZXQgcHJvZ3JhbSA9IGJsb2NrLnByb2dyYW0sXG4gICAgICBpbnZlcnNlID0gYmxvY2suaW52ZXJzZTtcblxuICAgIHByb2dyYW0gPSBwcm9ncmFtICYmIHRoaXMuY29tcGlsZVByb2dyYW0ocHJvZ3JhbSk7XG4gICAgaW52ZXJzZSA9IGludmVyc2UgJiYgdGhpcy5jb21waWxlUHJvZ3JhbShpbnZlcnNlKTtcblxuICAgIGxldCB0eXBlID0gdGhpcy5jbGFzc2lmeVNleHByKGJsb2NrKTtcblxuICAgIGlmICh0eXBlID09PSAnaGVscGVyJykge1xuICAgICAgdGhpcy5oZWxwZXJTZXhwcihibG9jaywgcHJvZ3JhbSwgaW52ZXJzZSk7XG4gICAgfSBlbHNlIGlmICh0eXBlID09PSAnc2ltcGxlJykge1xuICAgICAgdGhpcy5zaW1wbGVTZXhwcihibG9jayk7XG5cbiAgICAgIC8vIG5vdyB0aGF0IHRoZSBzaW1wbGUgbXVzdGFjaGUgaXMgcmVzb2x2ZWQsIHdlIG5lZWQgdG9cbiAgICAgIC8vIGV2YWx1YXRlIGl0IGJ5IGV4ZWN1dGluZyBgYmxvY2tIZWxwZXJNaXNzaW5nYFxuICAgICAgdGhpcy5vcGNvZGUoJ3B1c2hQcm9ncmFtJywgcHJvZ3JhbSk7XG4gICAgICB0aGlzLm9wY29kZSgncHVzaFByb2dyYW0nLCBpbnZlcnNlKTtcbiAgICAgIHRoaXMub3Bjb2RlKCdlbXB0eUhhc2gnKTtcbiAgICAgIHRoaXMub3Bjb2RlKCdibG9ja1ZhbHVlJywgYmxvY2sucGF0aC5vcmlnaW5hbCk7XG4gICAgfSBlbHNlIHtcbiAgICAgIHRoaXMuYW1iaWd1b3VzU2V4cHIoYmxvY2ssIHByb2dyYW0sIGludmVyc2UpO1xuXG4gICAgICAvLyBub3cgdGhhdCB0aGUgc2ltcGxlIG11c3RhY2hlIGlzIHJlc29sdmVkLCB3ZSBuZWVkIHRvXG4gICAgICAvLyBldmFsdWF0ZSBpdCBieSBleGVjdXRpbmcgYGJsb2NrSGVscGVyTWlzc2luZ2BcbiAgICAgIHRoaXMub3Bjb2RlKCdwdXNoUHJvZ3JhbScsIHByb2dyYW0pO1xuICAgICAgdGhpcy5vcGNvZGUoJ3B1c2hQcm9ncmFtJywgaW52ZXJzZSk7XG4gICAgICB0aGlzLm9wY29kZSgnZW1wdHlIYXNoJyk7XG4gICAgICB0aGlzLm9wY29kZSgnYW1iaWd1b3VzQmxvY2tWYWx1ZScpO1xuICAgIH1cblxuICAgIHRoaXMub3Bjb2RlKCdhcHBlbmQnKTtcbiAgfSxcblxuICBEZWNvcmF0b3JCbG9jayhkZWNvcmF0b3IpIHtcbiAgICBsZXQgcHJvZ3JhbSA9IGRlY29yYXRvci5wcm9ncmFtICYmIHRoaXMuY29tcGlsZVByb2dyYW0oZGVjb3JhdG9yLnByb2dyYW0pO1xuICAgIGxldCBwYXJhbXMgPSB0aGlzLnNldHVwRnVsbE11c3RhY2hlUGFyYW1zKGRlY29yYXRvciwgcHJvZ3JhbSwgdW5kZWZpbmVkKSxcbiAgICAgIHBhdGggPSBkZWNvcmF0b3IucGF0aDtcblxuICAgIHRoaXMudXNlRGVjb3JhdG9ycyA9IHRydWU7XG4gICAgdGhpcy5vcGNvZGUoJ3JlZ2lzdGVyRGVjb3JhdG9yJywgcGFyYW1zLmxlbmd0aCwgcGF0aC5vcmlnaW5hbCk7XG4gIH0sXG5cbiAgUGFydGlhbFN0YXRlbWVudDogZnVuY3Rpb24ocGFydGlhbCkge1xuICAgIHRoaXMudXNlUGFydGlhbCA9IHRydWU7XG5cbiAgICBsZXQgcHJvZ3JhbSA9IHBhcnRpYWwucHJvZ3JhbTtcbiAgICBpZiAocHJvZ3JhbSkge1xuICAgICAgcHJvZ3JhbSA9IHRoaXMuY29tcGlsZVByb2dyYW0ocGFydGlhbC5wcm9ncmFtKTtcbiAgICB9XG5cbiAgICBsZXQgcGFyYW1zID0gcGFydGlhbC5wYXJhbXM7XG4gICAgaWYgKHBhcmFtcy5sZW5ndGggPiAxKSB7XG4gICAgICB0aHJvdyBuZXcgRXhjZXB0aW9uKFxuICAgICAgICAnVW5zdXBwb3J0ZWQgbnVtYmVyIG9mIHBhcnRpYWwgYXJndW1lbnRzOiAnICsgcGFyYW1zLmxlbmd0aCxcbiAgICAgICAgcGFydGlhbFxuICAgICAgKTtcbiAgICB9IGVsc2UgaWYgKCFwYXJhbXMubGVuZ3RoKSB7XG4gICAgICBpZiAodGhpcy5vcHRpb25zLmV4cGxpY2l0UGFydGlhbENvbnRleHQpIHtcbiAgICAgICAgdGhpcy5vcGNvZGUoJ3B1c2hMaXRlcmFsJywgJ3VuZGVmaW5lZCcpO1xuICAgICAgfSBlbHNlIHtcbiAgICAgICAgcGFyYW1zLnB1c2goeyB0eXBlOiAnUGF0aEV4cHJlc3Npb24nLCBwYXJ0czogW10sIGRlcHRoOiAwIH0pO1xuICAgICAgfVxuICAgIH1cblxuICAgIGxldCBwYXJ0aWFsTmFtZSA9IHBhcnRpYWwubmFtZS5vcmlnaW5hbCxcbiAgICAgIGlzRHluYW1pYyA9IHBhcnRpYWwubmFtZS50eXBlID09PSAnU3ViRXhwcmVzc2lvbic7XG4gICAgaWYgKGlzRHluYW1pYykge1xuICAgICAgdGhpcy5hY2NlcHQocGFydGlhbC5uYW1lKTtcbiAgICB9XG5cbiAgICB0aGlzLnNldHVwRnVsbE11c3RhY2hlUGFyYW1zKHBhcnRpYWwsIHByb2dyYW0sIHVuZGVmaW5lZCwgdHJ1ZSk7XG5cbiAgICBsZXQgaW5kZW50ID0gcGFydGlhbC5pbmRlbnQgfHwgJyc7XG4gICAgaWYgKHRoaXMub3B0aW9ucy5wcmV2ZW50SW5kZW50ICYmIGluZGVudCkge1xuICAgICAgdGhpcy5vcGNvZGUoJ2FwcGVuZENvbnRlbnQnLCBpbmRlbnQpO1xuICAgICAgaW5kZW50ID0gJyc7XG4gICAgfVxuXG4gICAgdGhpcy5vcGNvZGUoJ2ludm9rZVBhcnRpYWwnLCBpc0R5bmFtaWMsIHBhcnRpYWxOYW1lLCBpbmRlbnQpO1xuICAgIHRoaXMub3Bjb2RlKCdhcHBlbmQnKTtcbiAgfSxcbiAgUGFydGlhbEJsb2NrU3RhdGVtZW50OiBmdW5jdGlvbihwYXJ0aWFsQmxvY2spIHtcbiAgICB0aGlzLlBhcnRpYWxTdGF0ZW1lbnQocGFydGlhbEJsb2NrKTtcbiAgfSxcblxuICBNdXN0YWNoZVN0YXRlbWVudDogZnVuY3Rpb24obXVzdGFjaGUpIHtcbiAgICB0aGlzLlN1YkV4cHJlc3Npb24obXVzdGFjaGUpO1xuXG4gICAgaWYgKG11c3RhY2hlLmVzY2FwZWQgJiYgIXRoaXMub3B0aW9ucy5ub0VzY2FwZSkge1xuICAgICAgdGhpcy5vcGNvZGUoJ2FwcGVuZEVzY2FwZWQnKTtcbiAgICB9IGVsc2Uge1xuICAgICAgdGhpcy5vcGNvZGUoJ2FwcGVuZCcpO1xuICAgIH1cbiAgfSxcbiAgRGVjb3JhdG9yKGRlY29yYXRvcikge1xuICAgIHRoaXMuRGVjb3JhdG9yQmxvY2soZGVjb3JhdG9yKTtcbiAgfSxcblxuICBDb250ZW50U3RhdGVtZW50OiBmdW5jdGlvbihjb250ZW50KSB7XG4gICAgaWYgKGNvbnRlbnQudmFsdWUgIT0gbnVsbCAmJiB0eXBlb2YgY29udGVudC52YWx1ZSAhPT0gJ3N0cmluZycpIHtcbiAgICAgIHRocm93IGludmFsaWRBc3QoY29udGVudCwgJ3ZhbHVlIG11c3QgYmUgYSBzdHJpbmcnKTtcbiAgICB9XG4gICAgaWYgKGNvbnRlbnQudmFsdWUpIHtcbiAgICAgIHRoaXMub3Bjb2RlKCdhcHBlbmRDb250ZW50JywgY29udGVudC52YWx1ZSk7XG4gICAgfVxuICB9LFxuXG4gIENvbW1lbnRTdGF0ZW1lbnQ6IGZ1bmN0aW9uKCkge30sXG5cbiAgU3ViRXhwcmVzc2lvbjogZnVuY3Rpb24oc2V4cHIpIHtcbiAgICB0cmFuc2Zvcm1MaXRlcmFsVG9QYXRoKHNleHByKTtcbiAgICBsZXQgdHlwZSA9IHRoaXMuY2xhc3NpZnlTZXhwcihzZXhwcik7XG5cbiAgICBpZiAodHlwZSA9PT0gJ3NpbXBsZScpIHtcbiAgICAgIHRoaXMuc2ltcGxlU2V4cHIoc2V4cHIpO1xuICAgIH0gZWxzZSBpZiAodHlwZSA9PT0gJ2hlbHBlcicpIHtcbiAgICAgIHRoaXMuaGVscGVyU2V4cHIoc2V4cHIpO1xuICAgIH0gZWxzZSB7XG4gICAgICB0aGlzLmFtYmlndW91c1NleHByKHNleHByKTtcbiAgICB9XG4gIH0sXG4gIGFtYmlndW91c1NleHByOiBmdW5jdGlvbihzZXhwciwgcHJvZ3JhbSwgaW52ZXJzZSkge1xuICAgIGxldCBwYXRoID0gc2V4cHIucGF0aCxcbiAgICAgIG5hbWUgPSBwYXRoLnBhcnRzWzBdLFxuICAgICAgaXNCbG9jayA9IHByb2dyYW0gIT0gbnVsbCB8fCBpbnZlcnNlICE9IG51bGw7XG5cbiAgICB0aGlzLm9wY29kZSgnZ2V0Q29udGV4dCcsIHZhbGlkYXRlRGVwdGgocGF0aCkpO1xuXG4gICAgdGhpcy5vcGNvZGUoJ3B1c2hQcm9ncmFtJywgcHJvZ3JhbSk7XG4gICAgdGhpcy5vcGNvZGUoJ3B1c2hQcm9ncmFtJywgaW52ZXJzZSk7XG5cbiAgICBwYXRoLnN0cmljdCA9IHRydWU7XG4gICAgdGhpcy5hY2NlcHQocGF0aCk7XG5cbiAgICB0aGlzLm9wY29kZSgnaW52b2tlQW1iaWd1b3VzJywgbmFtZSwgaXNCbG9jayk7XG4gIH0sXG5cbiAgc2ltcGxlU2V4cHI6IGZ1bmN0aW9uKHNleHByKSB7XG4gICAgbGV0IHBhdGggPSBzZXhwci5wYXRoO1xuICAgIHBhdGguc3RyaWN0ID0gdHJ1ZTtcbiAgICB0aGlzLmFjY2VwdChwYXRoKTtcbiAgICB0aGlzLm9wY29kZSgncmVzb2x2ZVBvc3NpYmxlTGFtYmRhJyk7XG4gIH0sXG5cbiAgaGVscGVyU2V4cHI6IGZ1bmN0aW9uKHNleHByLCBwcm9ncmFtLCBpbnZlcnNlKSB7XG4gICAgbGV0IHBhcmFtcyA9IHRoaXMuc2V0dXBGdWxsTXVzdGFjaGVQYXJhbXMoc2V4cHIsIHByb2dyYW0sIGludmVyc2UpLFxuICAgICAgcGF0aCA9IHNleHByLnBhdGgsXG4gICAgICBuYW1lID0gcGF0aC5wYXJ0c1swXTtcblxuICAgIGlmICh0aGlzLm9wdGlvbnMua25vd25IZWxwZXJzW25hbWVdKSB7XG4gICAgICB0aGlzLm9wY29kZSgnaW52b2tlS25vd25IZWxwZXInLCBwYXJhbXMubGVuZ3RoLCBuYW1lKTtcbiAgICB9IGVsc2UgaWYgKHRoaXMub3B0aW9ucy5rbm93bkhlbHBlcnNPbmx5KSB7XG4gICAgICB0aHJvdyBuZXcgRXhjZXB0aW9uKFxuICAgICAgICAnWW91IHNwZWNpZmllZCBrbm93bkhlbHBlcnNPbmx5LCBidXQgdXNlZCB0aGUgdW5rbm93biBoZWxwZXIgJyArIG5hbWUsXG4gICAgICAgIHNleHByXG4gICAgICApO1xuICAgIH0gZWxzZSB7XG4gICAgICBwYXRoLnN0cmljdCA9IHRydWU7XG4gICAgICBwYXRoLmZhbHN5ID0gdHJ1ZTtcblxuICAgICAgdGhpcy5hY2NlcHQocGF0aCk7XG4gICAgICB0aGlzLm9wY29kZShcbiAgICAgICAgJ2ludm9rZUhlbHBlcicsXG4gICAgICAgIHBhcmFtcy5sZW5ndGgsXG4gICAgICAgIHBhdGgub3JpZ2luYWwsXG4gICAgICAgIEFTVC5oZWxwZXJzLnNpbXBsZUlkKHBhdGgpXG4gICAgICApO1xuICAgIH1cbiAgfSxcblxuICBQYXRoRXhwcmVzc2lvbjogZnVuY3Rpb24ocGF0aCkge1xuICAgIC8vIFZhbGlkYXRlIEFTVCB2YWx1ZXMgYXQgdGhlIGNvbXBpbGVyIGJvdW5kYXJ5OiBqYXZhc2NyaXB0LWNvbXBpbGVyLmpzXG4gICAgLy8gdHJ1c3RzIGFsbCBvcGNvZGUgYXJndW1lbnRzIHRvIGJlIHNhZmUuXG4gICAgY29uc3QgZGVwdGggPSB2YWxpZGF0ZURlcHRoKHBhdGgpO1xuICAgIGNvbnN0IHBhcnRzID0gdmFsaWRhdGVQYXJ0cyhwYXRoKTtcblxuICAgIHRoaXMuYWRkRGVwdGgoZGVwdGgpO1xuICAgIHRoaXMub3Bjb2RlKCdnZXRDb250ZXh0JywgZGVwdGgpO1xuXG4gICAgbGV0IG5hbWUgPSBwYXJ0c1swXSxcbiAgICAgIHNjb3BlZCA9IEFTVC5oZWxwZXJzLnNjb3BlZElkKHBhdGgpLFxuICAgICAgYmxvY2tQYXJhbUlkID0gIWRlcHRoICYmICFzY29wZWQgJiYgdGhpcy5ibG9ja1BhcmFtSW5kZXgobmFtZSk7XG5cbiAgICBpZiAoYmxvY2tQYXJhbUlkKSB7XG4gICAgICB0aGlzLm9wY29kZSgnbG9va3VwQmxvY2tQYXJhbScsIGJsb2NrUGFyYW1JZCwgcGFydHMpO1xuICAgIH0gZWxzZSBpZiAoIW5hbWUpIHtcbiAgICAgIC8vIENvbnRleHQgcmVmZXJlbmNlLCBpLmUuIGB7e2ZvbyAufX1gIG9yIGB7e2ZvbyAuLn19YFxuICAgICAgdGhpcy5vcGNvZGUoJ3B1c2hDb250ZXh0Jyk7XG4gICAgfSBlbHNlIGlmIChwYXRoLmRhdGEpIHtcbiAgICAgIHRoaXMub3B0aW9ucy5kYXRhID0gdHJ1ZTtcbiAgICAgIHRoaXMub3Bjb2RlKCdsb29rdXBEYXRhJywgZGVwdGgsIHBhcnRzLCBwYXRoLnN0cmljdCk7XG4gICAgfSBlbHNlIHtcbiAgICAgIHRoaXMub3Bjb2RlKCdsb29rdXBPbkNvbnRleHQnLCBwYXJ0cywgcGF0aC5mYWxzeSwgcGF0aC5zdHJpY3QsIHNjb3BlZCk7XG4gICAgfVxuICB9LFxuXG4gIFN0cmluZ0xpdGVyYWw6IGZ1bmN0aW9uKHN0cmluZykge1xuICAgIHZhbGlkYXRlTGl0ZXJhbChzdHJpbmcpO1xuICAgIHRoaXMub3Bjb2RlKCdwdXNoU3RyaW5nJywgc3RyaW5nLnZhbHVlKTtcbiAgfSxcblxuICBOdW1iZXJMaXRlcmFsOiBmdW5jdGlvbihudW1iZXIpIHtcbiAgICB2YWxpZGF0ZUxpdGVyYWwobnVtYmVyKTtcbiAgICB0aGlzLm9wY29kZSgncHVzaExpdGVyYWwnLCBudW1iZXIudmFsdWUpO1xuICB9LFxuXG4gIEJvb2xlYW5MaXRlcmFsOiBmdW5jdGlvbihib29sKSB7XG4gICAgdmFsaWRhdGVMaXRlcmFsKGJvb2wpO1xuICAgIHRoaXMub3Bjb2RlKCdwdXNoTGl0ZXJhbCcsIGJvb2wudmFsdWUpO1xuICB9LFxuXG4gIFVuZGVmaW5lZExpdGVyYWw6IGZ1bmN0aW9uKCkge1xuICAgIHRoaXMub3Bjb2RlKCdwdXNoTGl0ZXJhbCcsICd1bmRlZmluZWQnKTtcbiAgfSxcblxuICBOdWxsTGl0ZXJhbDogZnVuY3Rpb24oKSB7XG4gICAgdGhpcy5vcGNvZGUoJ3B1c2hMaXRlcmFsJywgJ251bGwnKTtcbiAgfSxcblxuICBIYXNoOiBmdW5jdGlvbihoYXNoKSB7XG4gICAgbGV0IHBhaXJzID0gaGFzaC5wYWlycyxcbiAgICAgIGkgPSAwLFxuICAgICAgbDtcblxuICAgIGlmICghaXNBcnJheShwYWlycykpIHtcbiAgICAgIHRocm93IGludmFsaWRBc3QoaGFzaCwgJ3BhaXJzIG11c3QgYmUgYW4gYXJyYXknKTtcbiAgICB9XG4gICAgbCA9IHBhaXJzLmxlbmd0aDtcbiAgICBmb3IgKGxldCBqID0gMDsgaiA8IGw7IGorKykge1xuICAgICAgaWYgKCFwYWlyc1tqXSB8fCB0eXBlb2YgcGFpcnNbal0ua2V5ICE9PSAnc3RyaW5nJykge1xuICAgICAgICB0aHJvdyBpbnZhbGlkQXN0KGhhc2gsICdwYWlyIGtleXMgbXVzdCBiZSBzdHJpbmdzJyk7XG4gICAgICB9XG4gICAgfVxuXG4gICAgdGhpcy5vcGNvZGUoJ3B1c2hIYXNoJyk7XG5cbiAgICBmb3IgKDsgaSA8IGw7IGkrKykge1xuICAgICAgdGhpcy5wdXNoUGFyYW0ocGFpcnNbaV0udmFsdWUpO1xuICAgIH1cbiAgICB3aGlsZSAoaS0tKSB7XG4gICAgICB0aGlzLm9wY29kZSgnYXNzaWduVG9IYXNoJywgcGFpcnNbaV0ua2V5KTtcbiAgICB9XG4gICAgdGhpcy5vcGNvZGUoJ3BvcEhhc2gnKTtcbiAgfSxcblxuICAvLyBIRUxQRVJTXG4gIG9wY29kZTogZnVuY3Rpb24obmFtZSkge1xuICAgIHRoaXMub3Bjb2Rlcy5wdXNoKHtcbiAgICAgIG9wY29kZTogbmFtZSxcbiAgICAgIGFyZ3M6IHNsaWNlLmNhbGwoYXJndW1lbnRzLCAxKSxcbiAgICAgIGxvYzogdGhpcy5zb3VyY2VOb2RlWzBdLmxvY1xuICAgIH0pO1xuICB9LFxuXG4gIGFkZERlcHRoOiBmdW5jdGlvbihkZXB0aCkge1xuICAgIGlmICghZGVwdGgpIHtcbiAgICAgIHJldHVybjtcbiAgICB9XG5cbiAgICB0aGlzLnVzZURlcHRocyA9IHRydWU7XG4gIH0sXG5cbiAgY2xhc3NpZnlTZXhwcjogZnVuY3Rpb24oc2V4cHIpIHtcbiAgICAvLyBUaGUgZmlyc3QgcGFydCBpcyB1c2VkIGFzIGEgaGVscGVyIG5hbWUgYmVsb3csIGUuZy4gZm9yIGtub3duIGhlbHBlcnMsXG4gICAgLy8gYmVmb3JlIHRoZSBQYXRoRXhwcmVzc2lvbiBoYW5kbGVyIGdldHMgdG8gdmFsaWRhdGUgdGhlIHBhcnRzLlxuICAgIHZhbGlkYXRlUGFydHMoc2V4cHIucGF0aCk7XG4gICAgbGV0IGlzU2ltcGxlID0gQVNULmhlbHBlcnMuc2ltcGxlSWQoc2V4cHIucGF0aCk7XG5cbiAgICBsZXQgaXNCbG9ja1BhcmFtID0gaXNTaW1wbGUgJiYgISF0aGlzLmJsb2NrUGFyYW1JbmRleChzZXhwci5wYXRoLnBhcnRzWzBdKTtcblxuICAgIC8vIGEgbXVzdGFjaGUgaXMgYW4gZWxpZ2libGUgaGVscGVyIGlmOlxuICAgIC8vICogaXRzIGlkIGlzIHNpbXBsZSAoYSBzaW5nbGUgcGFydCwgbm90IGB0aGlzYCBvciBgLi5gKVxuICAgIGxldCBpc0hlbHBlciA9ICFpc0Jsb2NrUGFyYW0gJiYgQVNULmhlbHBlcnMuaGVscGVyRXhwcmVzc2lvbihzZXhwcik7XG5cbiAgICAvLyBpZiBhIG11c3RhY2hlIGlzIGFuIGVsaWdpYmxlIGhlbHBlciBidXQgbm90IGEgZGVmaW5pdGVcbiAgICAvLyBoZWxwZXIsIGl0IGlzIGFtYmlndW91cywgYW5kIHdpbGwgYmUgcmVzb2x2ZWQgaW4gYSBsYXRlclxuICAgIC8vIHBhc3Mgb3IgYXQgcnVudGltZS5cbiAgICBsZXQgaXNFbGlnaWJsZSA9ICFpc0Jsb2NrUGFyYW0gJiYgKGlzSGVscGVyIHx8IGlzU2ltcGxlKTtcblxuICAgIC8vIGlmIGFtYmlndW91cywgd2UgY2FuIHBvc3NpYmx5IHJlc29sdmUgdGhlIGFtYmlndWl0eSBub3dcbiAgICAvLyBBbiBlbGlnaWJsZSBoZWxwZXIgaXMgb25lIHRoYXQgZG9lcyBub3QgaGF2ZSBhIGNvbXBsZXggcGF0aCwgaS5lLiBgdGhpcy5mb29gLCBgLi4vZm9vYCBldGMuXG4gICAgaWYgKGlzRWxpZ2libGUgJiYgIWlzSGVscGVyKSB7XG4gICAgICBsZXQgbmFtZSA9IHNleHByLnBhdGgucGFydHNbMF0sXG4gICAgICAgIG9wdGlvbnMgPSB0aGlzLm9wdGlvbnM7XG4gICAgICBpZiAob3B0aW9ucy5rbm93bkhlbHBlcnNbbmFtZV0pIHtcbiAgICAgICAgaXNIZWxwZXIgPSB0cnVlO1xuICAgICAgfSBlbHNlIGlmIChvcHRpb25zLmtub3duSGVscGVyc09ubHkpIHtcbiAgICAgICAgaXNFbGlnaWJsZSA9IGZhbHNlO1xuICAgICAgfVxuICAgIH1cblxuICAgIGlmIChpc0hlbHBlcikge1xuICAgICAgcmV0dXJuICdoZWxwZXInO1xuICAgIH0gZWxzZSBpZiAoaXNFbGlnaWJsZSkge1xuICAgICAgcmV0dXJuICdhbWJpZ3VvdXMnO1xuICAgIH0gZWxzZSB7XG4gICAgICByZXR1cm4gJ3NpbXBsZSc7XG4gICAgfVxuICB9LFxuXG4gIHB1c2hQYXJhbXM6IGZ1bmN0aW9uKHBhcmFtcykge1xuICAgIGZvciAobGV0IGkgPSAwLCBsID0gcGFyYW1zLmxlbmd0aDsgaSA8IGw7IGkrKykge1xuICAgICAgdGhpcy5wdXNoUGFyYW0ocGFyYW1zW2ldKTtcbiAgICB9XG4gIH0sXG5cbiAgcHVzaFBhcmFtOiBmdW5jdGlvbih2YWwpIHtcbiAgICAvLyBMaXRlcmFsIHBhcmFtcyBhcmUgbm90IHZpc2l0ZWQgYnkgdGhlaXIgbm9kZSBoYW5kbGVycyBpbiBzdHJpbmdQYXJhbXNcbiAgICAvLyBtb2RlLCBzbyB0aGVpciB2YWx1ZXMgbXVzdCBiZSB2YWxpZGF0ZWQgaGVyZSBhcyB3ZWxsLlxuICAgIHZhbGlkYXRlTGl0ZXJhbCh2YWwpO1xuICAgIGxldCB2YWx1ZSA9IHZhbC52YWx1ZSAhPSBudWxsID8gdmFsLnZhbHVlIDogdmFsLm9yaWdpbmFsIHx8ICcnO1xuICAgIGxldCBkZXB0aCA9IHZhbGlkYXRlRGVwdGgodmFsKTtcblxuICAgIGlmICh0aGlzLnN0cmluZ1BhcmFtcykge1xuICAgICAgLy8gcHVzaFN0cmluZ1BhcmFtIGVtaXRzIG5vbi1zdHJpbmcgdmFsdWVzIGFzIHJhdyBKYXZhU2NyaXB0IGxpdGVyYWxzLlxuICAgICAgLy8gTGl0ZXJhbCB2YWx1ZXMgYXJlIHZhbGlkYXRlZCBhYm92ZSwgYnV0IGBvcmlnaW5hbGAgaXMgbm90LCBzb1xuICAgICAgLy8gYW55dGhpbmcgdGhhdCBpcyBub3QgYSBudW1iZXIgb3IgYm9vbGVhbiBpcyBzdHJpbmdpZmllZC5cbiAgICAgIGlmICh0eXBlb2YgdmFsdWUgIT09ICdudW1iZXInICYmIHR5cGVvZiB2YWx1ZSAhPT0gJ2Jvb2xlYW4nKSB7XG4gICAgICAgIHZhbHVlID0gU3RyaW5nKHZhbHVlKTtcbiAgICAgIH1cbiAgICAgIGlmICh2YWx1ZS5yZXBsYWNlKSB7XG4gICAgICAgIHZhbHVlID0gdmFsdWUucmVwbGFjZSgvXihcXC4/XFwuXFwvKSovZywgJycpLnJlcGxhY2UoL1xcLy9nLCAnLicpO1xuICAgICAgfVxuICAgICAgaWYgKGRlcHRoKSB7XG4gICAgICAgIHRoaXMuYWRkRGVwdGgoZGVwdGgpO1xuICAgICAgfVxuICAgICAgdGhpcy5vcGNvZGUoJ2dldENvbnRleHQnLCBkZXB0aCk7XG4gICAgICB0aGlzLm9wY29kZSgncHVzaFN0cmluZ1BhcmFtJywgdmFsdWUsIHZhbC50eXBlKTtcblxuICAgICAgaWYgKHZhbC50eXBlID09PSAnU3ViRXhwcmVzc2lvbicpIHtcbiAgICAgICAgLy8gU3ViRXhwcmVzc2lvbnMgZ2V0IGV2YWx1YXRlZCBhbmQgcGFzc2VkIGluXG4gICAgICAgIC8vIGluIHN0cmluZyBwYXJhbXMgbW9kZS5cbiAgICAgICAgdGhpcy5hY2NlcHQodmFsKTtcbiAgICAgIH1cbiAgICB9IGVsc2Uge1xuICAgICAgaWYgKHRoaXMudHJhY2tJZHMpIHtcbiAgICAgICAgbGV0IGJsb2NrUGFyYW1JbmRleDtcbiAgICAgICAgaWYgKHZhbC5wYXJ0cyAmJiAhQVNULmhlbHBlcnMuc2NvcGVkSWQodmFsKSAmJiAhZGVwdGgpIHtcbiAgICAgICAgICAvLyBUaGUgcGFydHMgYXJlIHJlYWQgaGVyZSBiZWZvcmUgdGhlIFBhdGhFeHByZXNzaW9uIGhhbmRsZXIsXG4gICAgICAgICAgLy8gaW52b2tlZCBieSBhY2NlcHQoKSBiZWxvdywgZ2V0cyB0byB2YWxpZGF0ZSB0aGVtLlxuICAgICAgICAgIGJsb2NrUGFyYW1JbmRleCA9IHRoaXMuYmxvY2tQYXJhbUluZGV4KHZhbGlkYXRlUGFydHModmFsKVswXSk7XG4gICAgICAgIH1cbiAgICAgICAgaWYgKGJsb2NrUGFyYW1JbmRleCkge1xuICAgICAgICAgIGxldCBibG9ja1BhcmFtQ2hpbGQgPSB2YWwucGFydHMuc2xpY2UoMSkuam9pbignLicpO1xuICAgICAgICAgIHRoaXMub3Bjb2RlKCdwdXNoSWQnLCAnQmxvY2tQYXJhbScsIGJsb2NrUGFyYW1JbmRleCwgYmxvY2tQYXJhbUNoaWxkKTtcbiAgICAgICAgfSBlbHNlIHtcbiAgICAgICAgICB2YWx1ZSA9IHZhbC5vcmlnaW5hbCB8fCB2YWx1ZTtcbiAgICAgICAgICBpZiAodmFsdWUucmVwbGFjZSkge1xuICAgICAgICAgICAgdmFsdWUgPSB2YWx1ZVxuICAgICAgICAgICAgICAucmVwbGFjZSgvXnRoaXMoPzpcXC58JCkvLCAnJylcbiAgICAgICAgICAgICAgLnJlcGxhY2UoL15cXC5cXC8vLCAnJylcbiAgICAgICAgICAgICAgLnJlcGxhY2UoL15cXC4kLywgJycpO1xuICAgICAgICAgIH1cblxuICAgICAgICAgIHRoaXMub3Bjb2RlKCdwdXNoSWQnLCB2YWwudHlwZSwgdmFsdWUpO1xuICAgICAgICB9XG4gICAgICB9XG4gICAgICB0aGlzLmFjY2VwdCh2YWwpO1xuICAgIH1cbiAgfSxcblxuICBzZXR1cEZ1bGxNdXN0YWNoZVBhcmFtczogZnVuY3Rpb24oc2V4cHIsIHByb2dyYW0sIGludmVyc2UsIG9taXRFbXB0eSkge1xuICAgIGxldCBwYXJhbXMgPSBzZXhwci5wYXJhbXM7XG4gICAgdGhpcy5wdXNoUGFyYW1zKHBhcmFtcyk7XG5cbiAgICB0aGlzLm9wY29kZSgncHVzaFByb2dyYW0nLCBwcm9ncmFtKTtcbiAgICB0aGlzLm9wY29kZSgncHVzaFByb2dyYW0nLCBpbnZlcnNlKTtcblxuICAgIGlmIChzZXhwci5oYXNoKSB7XG4gICAgICB0aGlzLmFjY2VwdChzZXhwci5oYXNoKTtcbiAgICB9IGVsc2Uge1xuICAgICAgdGhpcy5vcGNvZGUoJ2VtcHR5SGFzaCcsIG9taXRFbXB0eSk7XG4gICAgfVxuXG4gICAgcmV0dXJuIHBhcmFtcztcbiAgfSxcblxuICBibG9ja1BhcmFtSW5kZXg6IGZ1bmN0aW9uKG5hbWUpIHtcbiAgICBmb3IgKFxuICAgICAgbGV0IGRlcHRoID0gMCwgbGVuID0gdGhpcy5vcHRpb25zLmJsb2NrUGFyYW1zLmxlbmd0aDtcbiAgICAgIGRlcHRoIDwgbGVuO1xuICAgICAgZGVwdGgrK1xuICAgICkge1xuICAgICAgbGV0IGJsb2NrUGFyYW1zID0gdGhpcy5vcHRpb25zLmJsb2NrUGFyYW1zW2RlcHRoXSxcbiAgICAgICAgcGFyYW0gPSBibG9ja1BhcmFtcyAmJiBpbmRleE9mKGJsb2NrUGFyYW1zLCBuYW1lKTtcbiAgICAgIGlmIChibG9ja1BhcmFtcyAmJiBwYXJhbSA+PSAwKSB7XG4gICAgICAgIHJldHVybiBbZGVwdGgsIHBhcmFtXTtcbiAgICAgIH1cbiAgICB9XG4gIH1cbn07XG5cbmV4cG9ydCBmdW5jdGlvbiBwcmVjb21waWxlKGlucHV0LCBvcHRpb25zLCBlbnYpIHtcbiAgaWYgKFxuICAgIGlucHV0ID09IG51bGwgfHxcbiAgICAodHlwZW9mIGlucHV0ICE9PSAnc3RyaW5nJyAmJiBpbnB1dC50eXBlICE9PSAnUHJvZ3JhbScpXG4gICkge1xuICAgIHRocm93IG5ldyBFeGNlcHRpb24oXG4gICAgICAnWW91IG11c3QgcGFzcyBhIHN0cmluZyBvciBIYW5kbGViYXJzIEFTVCB0byBIYW5kbGViYXJzLnByZWNvbXBpbGUuIFlvdSBwYXNzZWQgJyArXG4gICAgICAgIGlucHV0XG4gICAgKTtcbiAgfVxuXG4gIG9wdGlvbnMgPSBvcHRpb25zIHx8IHt9O1xuICBpZiAoISgnZGF0YScgaW4gb3B0aW9ucykpIHtcbiAgICBvcHRpb25zLmRhdGEgPSB0cnVlO1xuICB9XG4gIGlmIChvcHRpb25zLmNvbXBhdCkge1xuICAgIG9wdGlvbnMudXNlRGVwdGhzID0gdHJ1ZTtcbiAgfVxuXG4gIGxldCBhc3QgPSBlbnYucGFyc2UoaW5wdXQsIG9wdGlvbnMpLFxuICAgIGVudmlyb25tZW50ID0gbmV3IGVudi5Db21waWxlcigpLmNvbXBpbGUoYXN0LCBvcHRpb25zKTtcbiAgcmV0dXJuIG5ldyBlbnYuSmF2YVNjcmlwdENvbXBpbGVyKCkuY29tcGlsZShlbnZpcm9ubWVudCwgb3B0aW9ucyk7XG59XG5cbmV4cG9ydCBmdW5jdGlvbiBjb21waWxlKGlucHV0LCBvcHRpb25zID0ge30sIGVudikge1xuICBpZiAoXG4gICAgaW5wdXQgPT0gbnVsbCB8fFxuICAgICh0eXBlb2YgaW5wdXQgIT09ICdzdHJpbmcnICYmIGlucHV0LnR5cGUgIT09ICdQcm9ncmFtJylcbiAgKSB7XG4gICAgdGhyb3cgbmV3IEV4Y2VwdGlvbihcbiAgICAgICdZb3UgbXVzdCBwYXNzIGEgc3RyaW5nIG9yIEhhbmRsZWJhcnMgQVNUIHRvIEhhbmRsZWJhcnMuY29tcGlsZS4gWW91IHBhc3NlZCAnICtcbiAgICAgICAgaW5wdXRcbiAgICApO1xuICB9XG5cbiAgb3B0aW9ucyA9IGV4dGVuZCh7fSwgb3B0aW9ucyk7XG4gIGlmICghKCdkYXRhJyBpbiBvcHRpb25zKSkge1xuICAgIG9wdGlvbnMuZGF0YSA9IHRydWU7XG4gIH1cbiAgaWYgKG9wdGlvbnMuY29tcGF0KSB7XG4gICAgb3B0aW9ucy51c2VEZXB0aHMgPSB0cnVlO1xuICB9XG5cbiAgbGV0IGNvbXBpbGVkO1xuXG4gIGZ1bmN0aW9uIGNvbXBpbGVJbnB1dCgpIHtcbiAgICBsZXQgYXN0ID0gZW52LnBhcnNlKGlucHV0LCBvcHRpb25zKSxcbiAgICAgIGVudmlyb25tZW50ID0gbmV3IGVudi5Db21waWxlcigpLmNvbXBpbGUoYXN0LCBvcHRpb25zKSxcbiAgICAgIHRlbXBsYXRlU3BlYyA9IG5ldyBlbnYuSmF2YVNjcmlwdENvbXBpbGVyKCkuY29tcGlsZShcbiAgICAgICAgZW52aXJvbm1lbnQsXG4gICAgICAgIG9wdGlvbnMsXG4gICAgICAgIHVuZGVmaW5lZCxcbiAgICAgICAgdHJ1ZVxuICAgICAgKTtcbiAgICByZXR1cm4gZW52LnRlbXBsYXRlKHRlbXBsYXRlU3BlYyk7XG4gIH1cblxuICAvLyBUZW1wbGF0ZSBpcyBvbmx5IGNvbXBpbGVkIG9uIGZpcnN0IHVzZSBhbmQgY2FjaGVkIGFmdGVyIHRoYXQgcG9pbnQuXG4gIGZ1bmN0aW9uIHJldChjb250ZXh0LCBleGVjT3B0aW9ucykge1xuICAgIGlmICghY29tcGlsZWQpIHtcbiAgICAgIGNvbXBpbGVkID0gY29tcGlsZUlucHV0KCk7XG4gICAgfVxuICAgIHJldHVybiBjb21waWxlZC5jYWxsKHRoaXMsIGNvbnRleHQsIGV4ZWNPcHRpb25zKTtcbiAgfVxuICByZXQuX3NldHVwID0gZnVuY3Rpb24oc2V0dXBPcHRpb25zKSB7XG4gICAgaWYgKCFjb21waWxlZCkge1xuICAgICAgY29tcGlsZWQgPSBjb21waWxlSW5wdXQoKTtcbiAgICB9XG4gICAgcmV0dXJuIGNvbXBpbGVkLl9zZXR1cChzZXR1cE9wdGlvbnMpO1xuICB9O1xuICByZXQuX2NoaWxkID0gZnVuY3Rpb24oaSwgZGF0YSwgYmxvY2tQYXJhbXMsIGRlcHRocykge1xuICAgIGlmICghY29tcGlsZWQpIHtcbiAgICAgIGNvbXBpbGVkID0gY29tcGlsZUlucHV0KCk7XG4gICAgfVxuICAgIHJldHVybiBjb21waWxlZC5fY2hpbGQoaSwgZGF0YSwgYmxvY2tQYXJhbXMsIGRlcHRocyk7XG4gIH07XG4gIHJldHVybiByZXQ7XG59XG5cbi8qKlxuICogQ3JlYXRlIHRoZSBleGNlcHRpb24gdGhyb3duIGZvciBhbiBBU1Qgbm9kZSB0aGF0IGZhaWxzIHZhbGlkYXRpb24uXG4gKlxuICogQHBhcmFtIHtPYmplY3R9IG5vZGUgdGhlIG9mZmVuZGluZyBBU1Qgbm9kZTsgaXRzIGB0eXBlYCBpcyBpbmNsdWRlZCBpbiB0aGVcbiAqICAgbWVzc2FnZSBhbmQgaXRzIGBsb2NgLCBpZiBwcmVzZW50LCBpbiB0aGUgZXJyb3IgbG9jYXRpb25cbiAqIEBwYXJhbSB7c3RyaW5nfSBtZXNzYWdlIHdoYXQgaXMgd3Jvbmcgd2l0aCB0aGUgbm9kZVxuICogQHJldHVybnMge0V4Y2VwdGlvbn0gdGhlIGV4Y2VwdGlvbiB0byB0aHJvd1xuICovXG5mdW5jdGlvbiBpbnZhbGlkQXN0KG5vZGUsIG1lc3NhZ2UpIHtcbiAgcmV0dXJuIG5ldyBFeGNlcHRpb24oJ0ludmFsaWQgQVNUOiAnICsgbm9kZS50eXBlICsgJyAnICsgbWVzc2FnZSwgbm9kZSk7XG59XG5cbi8qKlxuICogVmFsaWRhdGUgdGhlIGBkZXB0aGAgb2YgYSBwYXRoIG9yIHBhcmFtIG5vZGUuXG4gKlxuICogQSBtaXNzaW5nIGRlcHRoIGlzIHRyZWF0ZWQgYXMgMCwgYmVjYXVzZSBBU1RzIGJ1aWx0IGJ5IGhhbmQgb3IgYnkgb3RoZXJcbiAqIHRvb2xzIGNvbW1vbmx5IG9taXQgaXQuIEFueSBkZXB0aCB0aGF0IGlzIHByZXNlbnQgbXVzdCBiZSBhIG5vbi1uZWdhdGl2ZVxuICogaW50ZWdlciwgc2luY2UgaXQgaXMgd3JpdHRlbiBpbnRvIHRoZSBnZW5lcmF0ZWQgY29kZS5cbiAqXG4gKiBAcGFyYW0ge09iamVjdH0gbm9kZSB0aGUgQVNUIG5vZGUgd2hvc2UgYGRlcHRoYCBpcyB2YWxpZGF0ZWRcbiAqIEByZXR1cm5zIHtudW1iZXJ9IHRoZSB2YWxpZGF0ZWQgZGVwdGgsIG9yIDAgaWYgdGhlIG5vZGUgaGFzIG5vbmVcbiAqIEB0aHJvd3Mge0V4Y2VwdGlvbn0gaWYgdGhlIGRlcHRoIGlzIHByZXNlbnQgYnV0IG5vdCBhIG5vbi1uZWdhdGl2ZSBpbnRlZ2VyXG4gKi9cbmZ1bmN0aW9uIHZhbGlkYXRlRGVwdGgobm9kZSkge1xuICBsZXQgZGVwdGggPSBub2RlLmRlcHRoO1xuICBpZiAoZGVwdGggPT0gbnVsbCkge1xuICAgIHJldHVybiAwO1xuICB9XG4gIGlmIChcbiAgICB0eXBlb2YgZGVwdGggIT09ICdudW1iZXInIHx8XG4gICAgIWlzRmluaXRlKGRlcHRoKSB8fFxuICAgIE1hdGguZmxvb3IoZGVwdGgpICE9PSBkZXB0aCB8fFxuICAgIGRlcHRoIDwgMFxuICApIHtcbiAgICB0aHJvdyBpbnZhbGlkQXN0KG5vZGUsICdkZXB0aCBtdXN0IGJlIGEgbm9uLW5lZ2F0aXZlIGludGVnZXInKTtcbiAgfVxuICByZXR1cm4gZGVwdGg7XG59XG5cbi8qKlxuICogVmFsaWRhdGUgdGhlIGB2YWx1ZWAgb2YgYSBTdHJpbmdMaXRlcmFsLCBOdW1iZXJMaXRlcmFsIG9yIEJvb2xlYW5MaXRlcmFsLlxuICpcbiAqIExpdGVyYWwgdmFsdWVzIGFyZSB3cml0dGVuIGludG8gdGhlIGdlbmVyYXRlZCBjb2RlLCBzbyB0aGV5IG11c3QgaGF2ZVxuICogZXhhY3RseSB0aGUgdHlwZSB0aGVpciBub2RlIHByb21pc2VzLiBOb2RlcyBvZiBhbnkgb3RoZXIgdHlwZSBwYXNzIHRocm91Z2hcbiAqIHVuY2hhbmdlZC5cbiAqXG4gKiBOdW1iZXJzIG5lZWQgbm90IGJlIGZpbml0ZTogdGhlIHBhcnNlciBwcm9kdWNlcyBJbmZpbml0eSBmb3IgdmVyeSBsb25nXG4gKiBudW1iZXIgbGl0ZXJhbHMsIGFuZCBJbmZpbml0eSBhbmQgTmFOIGFyZSBlbWl0dGVkIGFzIHRoZSByZWFkLW9ubHkgZ2xvYmFsc1xuICogb2YgdGhlIHNhbWUgbmFtZS5cbiAqXG4gKiBAcGFyYW0ge09iamVjdH0gbm9kZSB0aGUgQVNUIG5vZGUgdG8gdmFsaWRhdGVcbiAqIEB0aHJvd3Mge0V4Y2VwdGlvbn0gaWYgdGhlIHZhbHVlIGRvZXMgbm90IG1hdGNoIHRoZSBsaXRlcmFsIHR5cGVcbiAqL1xuZnVuY3Rpb24gdmFsaWRhdGVMaXRlcmFsKG5vZGUpIHtcbiAgbGV0IHZhbHVlID0gbm9kZS52YWx1ZTtcbiAgaWYgKG5vZGUudHlwZSA9PT0gJ1N0cmluZ0xpdGVyYWwnICYmIHR5cGVvZiB2YWx1ZSAhPT0gJ3N0cmluZycpIHtcbiAgICB0aHJvdyBpbnZhbGlkQXN0KG5vZGUsICd2YWx1ZSBtdXN0IGJlIGEgc3RyaW5nJyk7XG4gIH0gZWxzZSBpZiAobm9kZS50eXBlID09PSAnTnVtYmVyTGl0ZXJhbCcgJiYgdHlwZW9mIHZhbHVlICE9PSAnbnVtYmVyJykge1xuICAgIHRocm93IGludmFsaWRBc3Qobm9kZSwgJ3ZhbHVlIG11c3QgYmUgYSBudW1iZXInKTtcbiAgfSBlbHNlIGlmIChub2RlLnR5cGUgPT09ICdCb29sZWFuTGl0ZXJhbCcgJiYgdHlwZW9mIHZhbHVlICE9PSAnYm9vbGVhbicpIHtcbiAgICB0aHJvdyBpbnZhbGlkQXN0KG5vZGUsICd2YWx1ZSBtdXN0IGJlIGEgYm9vbGVhbicpO1xuICB9XG59XG5cbi8qKlxuICogVmFsaWRhdGUgdGhhdCBhIG5vZGUgcHJvcGVydHkgaXMgYW4gYXJyYXkgY29udGFpbmluZyBvbmx5IHN0cmluZ3MuXG4gKlxuICogQHBhcmFtIHtPYmplY3R9IG5vZGUgdGhlIEFTVCBub2RlIHRoZSBwcm9wZXJ0eSBiZWxvbmdzIHRvLCBmb3IgdGhlIGVycm9yXG4gKiBAcGFyYW0geyp9IHZhbHVlIHRoZSBwcm9wZXJ0eSB2YWx1ZSB0byB2YWxpZGF0ZVxuICogQHBhcmFtIHtzdHJpbmd9IG5hbWUgdGhlIHByb3BlcnR5IG5hbWUsIHVzZWQgaW4gdGhlIGVycm9yIG1lc3NhZ2VcbiAqIEByZXR1cm5zIHtzdHJpbmdbXX0gdGhlIHZhbGlkYXRlZCBhcnJheVxuICogQHRocm93cyB7RXhjZXB0aW9ufSBpZiB0aGUgdmFsdWUgaXMgbm90IGFuIGFycmF5IG9yIGNvbnRhaW5zIG5vbi1zdHJpbmdzXG4gKi9cbmZ1bmN0aW9uIHZhbGlkYXRlU3RyaW5nQXJyYXkobm9kZSwgdmFsdWUsIG5hbWUpIHtcbiAgaWYgKCFpc0FycmF5KHZhbHVlKSkge1xuICAgIHRocm93IGludmFsaWRBc3Qobm9kZSwgbmFtZSArICcgbXVzdCBiZSBhbiBhcnJheScpO1xuICB9XG4gIGZvciAobGV0IGkgPSAwOyBpIDwgdmFsdWUubGVuZ3RoOyBpKyspIHtcbiAgICBpZiAodHlwZW9mIHZhbHVlW2ldICE9PSAnc3RyaW5nJykge1xuICAgICAgdGhyb3cgaW52YWxpZEFzdChub2RlLCBuYW1lICsgJyBtdXN0IG9ubHkgY29udGFpbiBzdHJpbmdzJyk7XG4gICAgfVxuICB9XG4gIHJldHVybiB2YWx1ZTtcbn1cblxuLyoqXG4gKiBWYWxpZGF0ZSB0aGUgYHBhcnRzYCBvZiBhIFBhdGhFeHByZXNzaW9uIG5vZGUuXG4gKlxuICogQHBhcmFtIHtPYmplY3R9IHBhdGggdGhlIFBhdGhFeHByZXNzaW9uIG5vZGVcbiAqIEByZXR1cm5zIHtzdHJpbmdbXX0gdGhlIHZhbGlkYXRlZCBwYXJ0c1xuICogQHRocm93cyB7RXhjZXB0aW9ufSBpZiBgcGFydHNgIGlzIG5vdCBhbiBhcnJheSBvZiBzdHJpbmdzXG4gKi9cbmZ1bmN0aW9uIHZhbGlkYXRlUGFydHMocGF0aCkge1xuICByZXR1cm4gdmFsaWRhdGVTdHJpbmdBcnJheShwYXRoLCBwYXRoLnBhcnRzLCAncGFydHMnKTtcbn1cblxuLyoqXG4gKiBWYWxpZGF0ZSB0aGUgb3B0aW9uYWwgYGJsb2NrUGFyYW1zYCBvZiBhIFByb2dyYW0gbm9kZS4gSXRzIGxlbmd0aCBpc1xuICogd3JpdHRlbiBpbnRvIHRoZSBnZW5lcmF0ZWQgY29kZSwgc28gaXQgbXVzdCBiZSBhbiBhcnJheSBvZiBzdHJpbmdzIHdoZW5cbiAqIHByZXNlbnQuXG4gKlxuICogQHBhcmFtIHtPYmplY3R9IHByb2dyYW0gdGhlIFByb2dyYW0gbm9kZVxuICogQHRocm93cyB7RXhjZXB0aW9ufSBpZiBgYmxvY2tQYXJhbXNgIGlzIHByZXNlbnQgYW5kIG5vdCBhbiBhcnJheSBvZiBzdHJpbmdzXG4gKi9cbmZ1bmN0aW9uIHZhbGlkYXRlQmxvY2tQYXJhbXMocHJvZ3JhbSkge1xuICBpZiAocHJvZ3JhbS5ibG9ja1BhcmFtcyAhPSBudWxsKSB7XG4gICAgdmFsaWRhdGVTdHJpbmdBcnJheShwcm9ncmFtLCBwcm9ncmFtLmJsb2NrUGFyYW1zLCAnYmxvY2tQYXJhbXMnKTtcbiAgfVxufVxuXG5mdW5jdGlvbiBhcmdFcXVhbHMoYSwgYikge1xuICBpZiAoYSA9PT0gYikge1xuICAgIHJldHVybiB0cnVlO1xuICB9XG5cbiAgaWYgKGlzQXJyYXkoYSkgJiYgaXNBcnJheShiKSAmJiBhLmxlbmd0aCA9PT0gYi5sZW5ndGgpIHtcbiAgICBmb3IgKGxldCBpID0gMDsgaSA8IGEubGVuZ3RoOyBpKyspIHtcbiAgICAgIGlmICghYXJnRXF1YWxzKGFbaV0sIGJbaV0pKSB7XG4gICAgICAgIHJldHVybiBmYWxzZTtcbiAgICAgIH1cbiAgICB9XG4gICAgcmV0dXJuIHRydWU7XG4gIH1cbn1cblxuZnVuY3Rpb24gdHJhbnNmb3JtTGl0ZXJhbFRvUGF0aChzZXhwcikge1xuICBpZiAoIXNleHByLnBhdGgucGFydHMpIHtcbiAgICBsZXQgbGl0ZXJhbCA9IHNleHByLnBhdGg7XG4gICAgLy8gQ2FzdGluZyB0byBzdHJpbmcgaGVyZSB0byBtYWtlIGZhbHNlIGFuZCAwIGxpdGVyYWwgdmFsdWVzIHBsYXkgbmljZWx5IHdpdGggdGhlIHJlc3RcbiAgICAvLyBvZiB0aGUgc3lzdGVtLlxuICAgIHNleHByLnBhdGggPSB7XG4gICAgICB0eXBlOiAnUGF0aEV4cHJlc3Npb24nLFxuICAgICAgZGF0YTogZmFsc2UsXG4gICAgICBkZXB0aDogMCxcbiAgICAgIHBhcnRzOiBbbGl0ZXJhbC5vcmlnaW5hbCArICcnXSxcbiAgICAgIG9yaWdpbmFsOiBsaXRlcmFsLm9yaWdpbmFsICsgJycsXG4gICAgICBsb2M6IGxpdGVyYWwubG9jXG4gICAgfTtcbiAgfVxufVxuIl19
