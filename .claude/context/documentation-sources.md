# Documentation sources

Authoritative in-repo references, for when behaviour or intent isn't clear from the code.

| What                          | Where                                                             | Notes                                                                     |
|-------------------------------|-------------------------------------------------------------------|---------------------------------------------------------------------------|
| User documentation            | `documentation/src/main/resources/pages/ditto/`                   | Jekyll site published at https://www.eclipse.dev/ditto/                   |
| HTTP API (OpenAPI 3)          | `documentation/src/main/resources/openapi/sources/`               | `ditto-api-2.yml` next to it is generated — `npm run build` in `sources/` |
| JSON schemas                  | `documentation/src/main/resources/jsonschema/`                    | thing, policy, connection, protocol envelope, error                       |
| Architecture decisions (ADRs) | `documentation/src/main/resources/architecture/DADR-*`            | e.g. 0004 signal enrichment, 0007 concierge removal, 0008 search index    |
| WoT Ditto ontology + examples | `documentation/src/main/resources/wot/`                           | `ditto-extension.jsonld`, `example-models/`                               |
| Release notes                 | `documentation/src/main/resources/pages/ditto/release_notes_*.md` | per version; format used by the `release-notes` skill                     |
| Helm chart changelog          | `deployment/helm/ditto/CHANGELOG.md`                              | chart versions, independent of Ditto versions                             |

Page naming in `pages/ditto/` follows the topic: `basic-*` (concepts: thing, policy, feature,
acknowledgements, placeholders, enrichment, …), `protocol-*` (Ditto Protocol), `httpapi-*`,
`connectivity-*`, `architecture-*`, `installation-*`, `operating-*`. A new user-facing feature
needs a page or section there — undocumented features don't get found.
