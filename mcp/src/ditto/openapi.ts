export interface OpParam {
  name: string;
  in: "path" | "query";
  required: boolean;
  type: "string" | "number" | "boolean";
}

export interface BodyProp {
  name: string;
  type: "string" | "number" | "boolean" | "object" | "array" | "unknown";
  required: boolean;
}

export interface BodySchema {
  props: BodyProp[];
}

export interface DittoOperation {
  operationId: string;
  method: string;
  path: string;
  summary: string;
  description: string;
  params: OpParam[];
  hasBody: boolean;
  bodySchema?: BodySchema;
  securitySchemes: string[];
}

const METHODS = ["get", "put", "post", "delete", "patch"];

interface RawParam { name?: string; in?: string; required?: boolean; schema?: unknown; $ref?: string }

function paramType(schema: unknown): OpParam["type"] {
  const t = (schema as { type?: string } | undefined)?.type;
  return t === "number" || t === "integer" ? "number" : t === "boolean" ? "boolean" : "string";
}

function bodyPropType(schema: unknown): BodyProp["type"] {
  const s = schema as { type?: string; $ref?: string; allOf?: unknown; oneOf?: unknown; anyOf?: unknown } | undefined;
  // If the schema is a $ref or has composition keywords (allOf/oneOf/anyOf), or has no recognizable type, return "unknown"
  if (s?.$ref || s?.allOf || s?.oneOf || s?.anyOf) return "unknown";
  const t = s?.type;
  if (t === "integer" || t === "number") return "number";
  if (t === "boolean") return "boolean";
  if (t === "object") return "object";
  if (t === "array") return "array";
  if (t === "string") return "string";
  return "unknown"; // no recognizable primitive type
}

function resolveSchemaRef(s: SpecShape, schema: unknown): unknown {
  const ref = (schema as { $ref?: string } | undefined)?.$ref;
  if (ref) return (s.components?.schemas as Record<string, unknown> | undefined)?.[ref.split("/").pop() ?? ""];
  return schema;
}

function bodySchemaOf(s: SpecShape, op: { requestBody?: unknown }): BodySchema | undefined {
  const schemaRef = (op.requestBody as { content?: Record<string, { schema?: unknown }> } | undefined)
    ?.content?.["application/json"]?.schema;
  const schema = resolveSchemaRef(s, schemaRef) as
    | { type?: string; properties?: Record<string, unknown>; required?: string[] } | undefined;
  if (!schema || schema.type !== "object" || !schema.properties) return undefined;
  const required = new Set(schema.required ?? []);
  const props: BodyProp[] = Object.entries(schema.properties).map(([name, p]) => ({
    name,
    type: bodyPropType(p), // pass the full property schema
    required: required.has(name),
  }));
  return props.length ? { props } : undefined;
}

/** Resolve a `{$ref: '#/components/parameters/Name'}` param against the spec; pass others through. */
function resolveParam(spec: SpecShape, p: RawParam): RawParam {
  if (p.$ref) {
    const name = p.$ref.split("/").pop() ?? "";
    return (spec.components?.parameters?.[name] as RawParam | undefined) ?? {};
  }
  return p;
}

interface SpecShape {
  paths?: Record<string, Record<string, unknown>>;
  components?: { parameters?: Record<string, unknown>; schemas?: Record<string, unknown> };
  security?: Array<Record<string, unknown>>;
}

export function parseOperations(spec: unknown): DittoOperation[] {
  const s = (spec ?? {}) as SpecShape;
  const paths = s.paths ?? {};
  const ops: DittoOperation[] = [];
  for (const [path, item] of Object.entries(paths)) {
    // Path-item-level parameters apply to every operation under this path.
    const pathParams = ((item.parameters as RawParam[] | undefined) ?? []).map((p) => resolveParam(s, p));
    for (const method of METHODS) {
      const op = item[method] as
        | { operationId?: string; summary?: string; description?: string; parameters?: RawParam[]; requestBody?: unknown; security?: Array<Record<string, unknown>> }
        | undefined;
      if (!op) continue;
      const opParams = (op.parameters ?? []).map((p) => resolveParam(s, p));
      const seen = new Set<string>();
      const params: OpParam[] = [...opParams, ...pathParams] // op-level wins on name clash
        .filter((p) => p.in === "path" || p.in === "query")
        .filter((p) => (seen.has(`${p.in}:${p.name}`) ? false : seen.add(`${p.in}:${p.name}`)))
        .map((p) => ({
          name: String(p.name),
          in: p.in as "path" | "query",
          required: p.required === true || p.in === "path",
          type: paramType(p.schema),
        }));
      // Effective security = op.security ?? spec.security ?? []; flatten to scheme names
      const effectiveSecurity = op.security ?? s.security ?? [];
      const securitySchemes = Array.from(
        new Set(effectiveSecurity.flatMap((req) => Object.keys(req)))
      );
      const hasBody = op.requestBody !== undefined;
      ops.push({
        operationId: op.operationId ?? `${method.toUpperCase()}_${path}`,
        method: method.toUpperCase(),
        path,
        summary: op.summary ?? "",
        description: op.description ?? op.summary ?? "",
        params,
        hasBody,
        bodySchema: hasBody ? bodySchemaOf(s, op) : undefined,
        securitySchemes,
      });
    }
  }
  return ops;
}
