# Nextplace Web

Clojure backend with Lacinia GraphQL and Preact frontend.

## Development Setup

### MCP Server

Add the Clojure MCP server to this project:

```bash
claude mcp add clojure "/bin/bash" -- -c "exec clojure -X:mcp"
```

### Running the Server

Production server:
```bash
make clj/server
```

MCP server:
```bash
make clj/mcp
```

### Code Formatting

```bash
make clj/format
```

### Build

```bash
make clj/build
```

### Clean

```bash
make clj/clean
```

## Project Structure

- `src/nextplace/` - GraphQL server and resolvers
- `src/mcp.clj` - MCP server integration (dev only)
- `resources/schema.edn` - GraphQL schema definition
- `resources/public/` - Frontend files (HTML, JS, CSS)

## Server Endpoints

- `http://localhost:8888/` - Frontend
- `http://localhost:8888/graphql` - GraphQL API
- `http://localhost:8888/graphiql` - GraphQL IDE
