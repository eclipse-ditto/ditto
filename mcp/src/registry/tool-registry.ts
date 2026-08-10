import type { ToolDef } from "../core/types.js";

export class ToolRegistry {
  private readonly tools = new Map<string, ToolDef>();

  register(def: ToolDef): void {
    if (this.tools.has(def.name)) {
      throw new Error(`duplicate tool: ${def.name}`);
    }
    this.tools.set(def.name, def);
  }

  list(): ToolDef[] {
    return [...this.tools.values()];
  }

  get(name: string): ToolDef | undefined {
    return this.tools.get(name);
  }
}
