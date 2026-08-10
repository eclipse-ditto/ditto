import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { Client } from "pg";
import { startPgVector } from "./pg-testcontainer.js";

let pg: Awaited<ReturnType<typeof startPgVector>>;
beforeAll(async () => { pg = await startPgVector(); }, 120000);
afterAll(async () => { await pg?.stop(); });

describe("pgvector container", () => {
  it("has the vector extension available", async () => {
    const client = new Client({ connectionString: pg.connectionString });
    await client.connect();
    await client.query("CREATE EXTENSION IF NOT EXISTS vector");
    const r = await client.query("SELECT '[1,2,3]'::vector AS v");
    expect(r.rows[0].v).toBeDefined();
    await client.end();
  });
});
