import { PostgreSqlContainer } from "@testcontainers/postgresql";

export async function startPgVector(): Promise<{ connectionString: string; stop: () => Promise<void> }> {
  const container = await new PostgreSqlContainer("pgvector/pgvector:pg16").start();
  return {
    connectionString: container.getConnectionUri(),
    stop: () => container.stop().then(() => undefined),
  };
}
