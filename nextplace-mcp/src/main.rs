//! Nextplace MCP Server
//!
//! A thin MCP server that bridges Claude Code to the Nextplace Clojure application
//! for event source schema generation workflow.
//!
//! Tools:
//! - list_pending_sources: List URLs queued for scraper script generation
//! - mark_source_complete: Mark a source as complete (via HTTP to Clojure app)
//!
//! Communication:
//! - Uses HTTP to communicate with the Clojure app
//! - No direct database access (Clojure owns the database)
//! - Gracefully handles Clojure app being unavailable
//! - Can start before or after the Clojure app

use anyhow::Result;
use rmcp::{
    handler::server::router::tool::ToolRouter, model::*, schemars::JsonSchema, tool, tool_handler,
    tool_router, ServerHandler, ServiceExt, transport::stdio,
};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::future::Future;
use std::sync::Arc;
use tracing::info;
use tracing_subscriber::EnvFilter;

const DEFAULT_API_BASE: &str = "http://localhost:8888";

/// Pending source data structure (matches Clojure API response)
#[derive(Debug, Clone, Serialize, Deserialize)]
struct PendingSource {
    id: String,
    url: String,
    name: String,
    #[serde(default)]
    geographic_scope: Vec<String>,
    queued_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    notes: Option<String>,
}

/// Request to mark a source as complete
#[derive(Debug, Clone, Serialize, Deserialize, JsonSchema)]
struct MarkSourceCompleteRequest {
    /// The ID of the pending source to mark as complete
    #[schemars(description = "The ID of the pending source to mark as complete")]
    id: String,
}

/// Get the API base URL from environment or default
fn get_api_base() -> String {
    std::env::var("NEXTPLACE_API_URL").unwrap_or_else(|_| DEFAULT_API_BASE.to_string())
}

/// The MCP service handler
#[derive(Debug, Clone)]
struct NextplaceMcp {
    http_client: Arc<reqwest::Client>,
    api_base: String,
    tool_router: ToolRouter<Self>,
}

impl NextplaceMcp {
    fn new() -> Self {
        let api_base = get_api_base();
        info!("Using Clojure API at: {}", api_base);

        Self {
            http_client: Arc::new(reqwest::Client::new()),
            api_base,
            tool_router: Self::tool_router(),
        }
    }
}

#[tool_router]
impl NextplaceMcp {
    /// List all URLs queued for scraper script generation
    #[tool(description = "List all event sources that are queued for scraper script generation. These are URLs that need Crawlee scripts to be created by Claude.")]
    async fn list_pending_sources(&self) -> String {
        let url = format!("{}/api/pending-sources", self.api_base);

        match self.http_client.get(&url).send().await {
            Ok(response) => {
                if response.status().is_success() {
                    match response.json::<Vec<PendingSource>>().await {
                        Ok(sources) => {
                            if sources.is_empty() {
                                serde_json::to_string_pretty(&json!({
                                    "message": "No pending sources. Use the Clojure console (queue-source ...) to add URLs for scraper generation.",
                                    "count": 0,
                                    "sources": []
                                }))
                                .unwrap_or_default()
                            } else {
                                serde_json::to_string_pretty(&json!({
                                    "count": sources.len(),
                                    "sources": sources,
                                    "next_step": "For each source: 1) Use WebFetch to analyze the page, 2) Generate a Crawlee script, 3) Save to scrapers/ directory, 4) Call mark_source_complete"
                                }))
                                .unwrap_or_default()
                            }
                        }
                        Err(e) => serde_json::to_string_pretty(&json!({
                            "error": format!("Failed to parse response: {}", e),
                            "hint": "The Clojure API may have returned an unexpected format"
                        }))
                        .unwrap_or_default(),
                    }
                } else {
                    serde_json::to_string_pretty(&json!({
                        "error": format!("API returned status {}", response.status()),
                        "hint": "Check if the Clojure app is running correctly"
                    }))
                    .unwrap_or_default()
                }
            }
            Err(e) => {
                if e.is_connect() {
                    serde_json::to_string_pretty(&json!({
                        "error": "Cannot connect to Clojure app",
                        "api_url": self.api_base,
                        "hint": "Start the Clojure app with 'make web/run' or check NEXTPLACE_API_URL environment variable"
                    }))
                    .unwrap_or_default()
                } else {
                    serde_json::to_string_pretty(&json!({
                        "error": format!("HTTP request failed: {}", e)
                    }))
                    .unwrap_or_default()
                }
            }
        }
    }

    /// Mark a source as complete (remove from pending queue)
    #[tool(description = "Mark an event source as complete after its Crawlee script has been generated. This removes it from the pending queue.")]
    async fn mark_source_complete(
        &self,
        rmcp::handler::server::tool::Parameters(req): rmcp::handler::server::tool::Parameters<
            MarkSourceCompleteRequest,
        >,
    ) -> String {
        let url = format!("{}/api/pending-sources/{}/complete", self.api_base, req.id);

        match self.http_client.post(&url).send().await {
            Ok(response) => {
                if response.status().is_success() {
                    serde_json::to_string_pretty(&json!({
                        "message": "Source marked as complete and removed from pending queue",
                        "id": req.id,
                        "reminder": "Make sure the Crawlee script was saved to the scrapers/ directory"
                    }))
                    .unwrap_or_default()
                } else if response.status() == reqwest::StatusCode::NOT_FOUND {
                    serde_json::to_string_pretty(&json!({
                        "error": format!("No pending source found with ID: {}", req.id)
                    }))
                    .unwrap_or_default()
                } else {
                    serde_json::to_string_pretty(&json!({
                        "error": format!("API returned status {}", response.status())
                    }))
                    .unwrap_or_default()
                }
            }
            Err(e) => {
                if e.is_connect() {
                    serde_json::to_string_pretty(&json!({
                        "error": "Cannot connect to Clojure app",
                        "api_url": self.api_base,
                        "hint": "Start the Clojure app with 'make web/run' or check NEXTPLACE_API_URL environment variable"
                    }))
                    .unwrap_or_default()
                } else {
                    serde_json::to_string_pretty(&json!({
                        "error": format!("HTTP request failed: {}", e)
                    }))
                    .unwrap_or_default()
                }
            }
        }
    }
}

#[tool_handler]
impl ServerHandler for NextplaceMcp {
    fn get_info(&self) -> ServerInfo {
        ServerInfo {
            protocol_version: ProtocolVersion::V_2024_11_05,
            capabilities: ServerCapabilities::builder().enable_tools().build(),
            server_info: Implementation {
                name: "nextplace-mcp".to_string(),
                version: env!("CARGO_PKG_VERSION").to_string(),
            },
            instructions: Some(
                "Nextplace MCP server for event source scraper workflow.\n\n\
                 Workflow:\n\
                 1. Call list_pending_sources to see URLs that need scraper scripts\n\
                 2. For each URL, use WebFetch to analyze the page structure\n\
                 3. Generate a Crawlee (Node.js) script that extracts events\n\
                 4. Save the script to the scrapers/ directory\n\
                 5. Call mark_source_complete to remove from the queue\n\n\
                 The Clojure app will run all scripts in scrapers/ on a schedule."
                    .to_string(),
            ),
        }
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .with_writer(std::io::stderr)
        .init();

    info!("Starting Nextplace MCP server");

    let service = NextplaceMcp::new();
    let server = service.serve(stdio()).await?;

    info!("MCP server running on stdio");

    server.waiting().await?;

    info!("MCP server shutting down");
    Ok(())
}
