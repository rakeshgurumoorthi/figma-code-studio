# Figma Code Studio

A Spring Boot web app (server-rendered with Thymeleaf) that:

1. Signs users in with their **Bitbucket Data Center** username and password.
2. Shows whether the user's **Figma remote MCP** connection (`https://mcp.figma.com/mcp`) is working, and lets them connect with Figma OAuth.
3. Takes a pasted **Figma frame link**, calls the MCP tool **`get_design_context`**, and shows the React + Tailwind code in a **Monaco editor**.
4. Renders the code in a **sandboxed live preview** that updates as you edit, and **downloads** it as a ready-to-run Vite project.

There is also a **mock mode** that simulates Bitbucket and Figma, so every screen can be tried on a laptop with no credentials.

## Requirements

- Java 21
- Maven 3.9+ (or use the included `./mvnw`, which downloads Maven on first run)
- Network access to Maven Central (or your Artifactory/Nexus mirror) for the first build. All front-end libraries (Monaco, React, Babel, Tailwind, htmx) come as WebJars, so the running app needs **no CDN**.

## 1. Try it in mock mode (laptop)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
# or
./mvnw -DskipTests package && java -jar target/figma-code-studio-0.1.0-SNAPSHOT.jar --spring.profiles.active=mock
```

Open http://localhost:8080. A striped banner shows you are in mock mode.

| To see | Do this |
| --- | --- |
| Successful sign-in | Any username, any password |
| Wrong password error | Password `wrong` |
| Bitbucket CAPTCHA error | Username `captcha` |
| Bitbucket unreachable | Username `offline` |
| Figma consent screen | Click **Connect Figma**, then **Allow** (or **Deny**) |
| Code generation | Paste any `https://www.figma.com/design/<key>/<name>?node-id=12-345` |
| Figma tool error | Use `node-id=0-0` |

Mock data is kept in memory and is gone when the app stops.

## 2. Run it for real (private environment)

Set these environment variables (or copy `src/main/resources/application-local.example.yml` to `config/application-local.yml` next to the jar and run with `--spring.profiles.active=local`):

| Variable | What it is |
| --- | --- |
| `BITBUCKET_BASE_URL` | Your Bitbucket Data Center URL, e.g. `https://bitbucket.yourcompany.com` |
| `FIGMA_CLIENT_ID` | **Client id Figma issued for your whitelisted app** |
| `FIGMA_CLIENT_SECRET` | **Client secret Figma issued for your whitelisted app** |
| `FIGMA_REDIRECT_URI` | The redirect URI registered with Figma, e.g. `https://studio.yourcompany.com/figma/callback`. If blank, the app uses `{its own URL}/figma/callback`. |
| `TOKEN_ENCRYPTION_KEY` | 32 random bytes, base64: `openssl rand -base64 32`. Encrypts Figma tokens in the database. If blank, a random key is used and users must reconnect Figma after every restart. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Optional. Defaults to an H2 file at `./data/figmacode`. For PostgreSQL: `DB_URL=jdbc:postgresql://host:5432/figmacode`. Tables are created automatically. |
| `SESSION_COOKIE_SECURE` | Set to `true` when the app is served over HTTPS. |
| `PORT` | HTTP port, default 8080. |

Optional Figma settings (only if Figma gave you different values):

| Variable | Default |
| --- | --- |
| `FIGMA_MCP_URL` | `https://mcp.figma.com/mcp` |
| `FIGMA_AUTHORIZATION_URI`, `FIGMA_TOKEN_URI` | Blank: discovered from the MCP server's OAuth metadata (`/.well-known/oauth-protected-resource`, then the authorization server's metadata). Set both if discovery doesn't work in your network. |
| `FIGMA_SCOPES` | Blank: whatever the server advertises |
| `FIGMA_CLIENT_AUTH_METHOD` | `client_secret_basic` (HTTP Basic). Use `client_secret_post` if Figma expects the secret in the form body. |

Then:

```bash
./mvnw -DskipTests package
export BITBUCKET_BASE_URL=https://bitbucket.yourcompany.com
export FIGMA_CLIENT_ID=...
export FIGMA_CLIENT_SECRET=...
export TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
java -jar target/figma-code-studio-0.1.0-SNAPSHOT.jar
```

### Corporate network notes

- **Proxy:** the app uses the JVM's proxy settings, e.g. `java -Dhttps.proxyHost=proxy.corp -Dhttps.proxyPort=8080 -jar ...`.
- **Internal CA for Bitbucket:** import the CA into a trust store and start with `-Djavax.net.ssl.trustStore=/path/to/truststore.jks -Djavax.net.ssl.trustStorePassword=...`. Never disable TLS checks.
- **Behind a load balancer:** `X-Forwarded-*` headers are honoured, so the Figma redirect URI is built with the public host name.

## How each part works

### Bitbucket login
`RestBitbucketAuthenticator` makes one call, `GET {BITBUCKET_BASE_URL}/rest/api/1.0/users?filter={username}`, with HTTP Basic auth.

- 200 with an `X-AUSERNAME` header: signed in (display name and email are read from the response).
- 401: wrong username or password.
- 403 with `X-Authentication-Denied-Reason: CAPTCHA...`: Bitbucket wants a CAPTCHA after repeated failures; the user must sign in on the Bitbucket website once.
- Other 403: basic auth is blocked for that account or for the REST API. If your admins have disabled basic auth, password login cannot work and the alternative is Bitbucket's OAuth 2.0 application link.

The password is never stored. After 5 failed attempts for a username within 5 minutes the app stops forwarding attempts to Bitbucket, so the form cannot be used to lock accounts.

### Figma connection and status
- **Connect Figma** starts OAuth 2.1 with PKCE (`/figma/connect`, back to `/figma/callback`). It includes the `resource` parameter the MCP spec requires.
- Tokens are stored per Bitbucket user, AES-GCM encrypted, and refreshed automatically before they expire.
- The status card (`/figma/status`, refreshed every 60 seconds with htmx) actually opens an MCP session: `initialize`, `tools/list`, and `whoami` when offered. It shows **Connected** only when `get_design_context` is listed. Other states: Not connected, Expired (reconnect), Tool missing, Unreachable.

### Code generation
- `FigmaLinkParser` accepts `figma.com/design/...`, `/file/...`, branch links and `node-id=12-345` or `12:345`.
- `get_design_context` is called with `fileKey`, `nodeId`, `clientLanguages=typescript,html,css`, `clientFrameworks=react,tailwind`. `get_screenshot` is called too, for the "Figma original" tab.
- Figma's asset URLs expire, so images are downloaded once, stored with the generation, and the code is rewritten to `/assets/<name>`.
- The MCP client is a small built-in Streamable HTTP client (`McpHttpClient`) that handles both JSON and event-stream responses.

### Editor, preview and download
- Monaco editor with Save (Ctrl/Cmd+S), Format, Copy, Revert, and a read-only "Figma notes" tab.
- The preview runs in `<iframe sandbox="allow-scripts">`. It has no access to the app's cookies or pages. Babel compiles the TSX in the browser, React 18 renders it, and Tailwind's browser build styles it. Edits are sent to the preview half a second after you stop typing. Compile and runtime errors show as an overlay.
- **Download project** saves first if needed, then streams a zip: `package.json`, `vite.config.ts`, `index.html`, `src/main.tsx`, `src/<Component>.tsx`, `public/assets/*`, and Figma's notes. Run it with `npm install && npm run dev`.

## Pages and routes

| Route | Purpose |
| --- | --- |
| `/login` | Bitbucket sign-in |
| `/` | Dashboard: Figma status, link form, recent generations |
| `/figma/connect`, `/figma/callback`, `/figma/disconnect` | Figma OAuth |
| `/figma/status` | Status card fragment |
| `POST /generations` | Generate from a Figma link |
| `/generations/{id}` | Editor and preview |
| `/generations/{id}/download` | Project zip |
| `/preview/{id}` | The page loaded inside the sandboxed iframe |

## Tests

```bash
./mvnw test
```

Covers the link parser, code extraction, the MCP client against a fake Streamable HTTP server, the Bitbucket authenticator against a fake Bitbucket, and the whole mock-mode journey (sign in, connect, generate, edit, download, preview).

## Project layout

```
src/main/java/com/figmacode
  auth/        Bitbucket authenticator (real + mock), Spring Security provider, login rate limit
  figma/       OAuth (real + mock), token storage, MCP gateway (real + mock), status, link parser
  mcp/         Minimal MCP Streamable HTTP client
  generation/  Generation flow, code extraction, zip export, controller
  preview/     Sandboxed preview page
  web/         Dashboard and login controllers
  config/      Settings, security, HTTP client
src/main/resources
  templates/   Thymeleaf pages
  static/      CSS and JS (workspace.js = editor page, preview-runtime.js = inside the iframe)
  mock/        Canned Figma response used in mock mode
```
