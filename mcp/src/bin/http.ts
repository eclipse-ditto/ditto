import { loadConfig } from "../config/load.js";
import { createHttpApp } from "../server/http-app.js";
import { buildKnowledgeService } from "../knowledge/build.js";

const config = loadConfig(process.env.DITTO_MCP_CONFIG);
const knowledge = await buildKnowledgeService(config);
const app = createHttpApp(config, knowledge);
app.listen(config.server.http.port, config.server.http.host, () => {
  process.stderr.write(
    `[ditto-mcp] http server listening on ${config.server.http.host}:${config.server.http.port}/mcp\n`,
  );
});
