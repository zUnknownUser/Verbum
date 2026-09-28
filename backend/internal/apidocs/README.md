# API documentation assets

Swagger UI **5.33.0** is vendored from the official `swagger-ui-dist` npm package
(https://www.npmjs.com/package/swagger-ui-dist/v/5.33.0).
`assets/LICENSE` and `assets/NOTICE` preserve upstream attribution. Only the bundle
and stylesheet are shipped; development source-map comments are removed. No CDN,
remote validator, analytics or runtime npm installation is used.

`api/openapi.yaml` remains the single source of truth, shared with the mobile
contract fixtures in `api/examples`. The Docker image copies these to
`/app/contracts`; local runs from `backend` use `../api`.
`VERBUM_API_CONTRACT_DIR` overrides the directory for standalone deployments.

- `/docs/` — branded, responsive Swagger UI with PT-BR/EN introduction.
- `/swagger` — redirects to `/docs/`.
- `/openapi.yaml` — downloadable contract.
- `/examples/...json` — only the checked-in JSON examples, no directory listing.

Authorization is held only in the page's memory. Browser connections are limited
to the same origin by CSP. Try it out performs real requests and retains every API
authentication, attestation, quota and cost check. The WebSocket relay requires a
WebSocket client; Swagger UI cannot establish that connection.

On an upstream update, replace the two vendor assets, retain notices, run the Go
route-inventory tests, validate the OpenAPI 3.1 document, and check browser rendering
at desktop/mobile widths in both introductory languages. Do not edit vendor code.
