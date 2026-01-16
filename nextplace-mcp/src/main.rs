//! Nextplace MCP Server
//!
//! A thin MCP server that bridges Claude Code to the Nextplace RocksDB database
//! for event source schema generation workflow.
//!
//! Tools:
//! - list_pending_sources: List URLs queued for scraper script generation
//! - mark_source_complete: Remove a source from the pending queue
//!
//! This server does NOT:
//! - Fetch web pages
//! - Execute scraper scripts
//! - Generate schemas
//!
//! Those tasks are handled by Claude Code (analysis) and Clojure (execution).

use anyhow::{Context, Result};
use rmcp::{ServerHandler, ServiceExt, model::*, tool, tool_router, transport::stdio};
use rocksdb::{DB, IteratorMode, Options};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::RwLock;
use tracing::{info, warn};
use tracing_subscriber::{self, EnvFilter};

/// Pending source data structure (matches Clojure EDN format)
#[derive(Debug, Clone, Serialize, Deserialize)]
struct PendingSource {
    id: String,
    url: String,
    name: String,
    geographic_scope: Vec<String>,
    queued_at: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    notes: Option<String>,
}

/// Get the RocksDB path from environment or default
fn get_db_path() -> PathBuf {
    if let Ok(path) = std::env::var("NEXTPLACE_DB_PATH") {
        PathBuf::from(path)
    } else {
        // Default: relative to current directory, matching Clojure app structure
        PathBuf::from("web/data/nextplace.db")
    }
}

/// Parse EDN string to PendingSource
/// Clojure stores data as EDN, so we need to convert to JSON-compatible format
fn parse_edn_to_pending_source(edn_str: &str) -> Result<PendingSource> {
    // Simple EDN to JSON conversion for our known structure
    // EDN uses :keywords, JSON uses "strings"
    let json_str = edn_str
        .replace(":id", "\"id\"")
        .replace(":url", "\"url\"")
        .replace(":name", "\"name\"")
        .replace(":geographic-scope", "\"geographic_scope\"")
        .replace(":geographic_scope", "\"geographic_scope\"")
        .replace(":queued-at", "\"queued_at\"")
        .replace(":queued_at", "\"queued_at\"")
        .replace(":notes", "\"notes\"");

    serde_json::from_str(&json_str).context("Failed to parse EDN as JSON")
}

/// The MCP service handler
struct NextplaceMcp {
    db: Arc<RwLock<DB>>,
}

impl NextplaceMcp {
    fn new(db_path: PathBuf) -> Result<Self> {
        let mut opts = Options::default();
        opts.create_if_missing(true);

        // Open in read-write mode (same DB as Clojure app)
        // Note: RocksDB allows multiple readers but only one writer process
        // The Clojure app should not be running when this MCP server writes
        let db = DB::open(&opts, &db_path)
            .context(format!("Failed to open RocksDB at {:?}", db_path))?;

        info!("Opened RocksDB at {:?}", db_path);

        Ok(Self {
            db: Arc::new(RwLock::new(db)),
        })
    }
}

/// Response helpers
fn success_response(content: impl Serialize) -> Result<CallToolResult, rmcp::Error> {
    Ok(CallToolResult::success(vec![Content::text(
        serde_json::to_string_pretty(&content).unwrap_or_else(|_| "{}".to_string()),
    )]))
}

fn error_response(message: &str) -> Result<CallToolResult, rmcp::Error> {
    Ok(CallToolResult::error(vec![Content::text(message.to_string())]))
}

#[tool_router]
impl NextplaceMcp {
    /// List all URLs queued for scraper script generation
    #[tool(description = "List all event sources that are queued for scraper script generation. These are URLs that need Crawlee scripts to be created by Claude.")]
    async fn list_pending_sources(&self) -> Result<CallToolResult, rmcp::Error> {
        let db = self.db.read().await;

        let mut pending_sources = Vec::new();
        let prefix = b"pending_source:";

        // Scan all keys with pending_source: prefix
        let iter = db.iterator(IteratorMode::From(prefix, rocksdb::Direction::Forward));

        for item in iter {
            match item {
                Ok((key, value)) => {
                    let key_str = String::from_utf8_lossy(&key);

                    // Stop if we've passed the prefix
                    if !key_str.starts_with("pending_source:") {
                        break;
                    }

                    // Parse the EDN value
                    let value_str = String::from_utf8_lossy(&value);
                    match parse_edn_to_pending_source(&value_str) {
                        Ok(source) => pending_sources.push(source),
                        Err(e) => {
                            warn!("Failed to parse pending source {}: {}", key_str, e);
                        }
                    }
                }
                Err(e) => {
                    warn!("Error reading from RocksDB: {}", e);
                }
            }
        }

        if pending_sources.is_empty() {
            return success_response(json!({
                "message": "No pending sources. Use the Clojure console (queue-source ...) to add URLs for scraper generation.",
                "count": 0,
                "sources": []
            }));
        }

        success_response(json!({
            "count": pending_sources.len(),
            "sources": pending_sources,
            "next_step": "For each source: 1) Use WebFetch to analyze the page, 2) Generate a Crawlee script, 3) Save to scrapers/ directory, 4) Call mark_source_complete"
        }))
    }

    /// Mark a source as complete (remove from pending queue)
    #[tool(description = "Mark an event source as complete after its Crawlee script has been generated. This removes it from the pending queue.")]
    async fn mark_source_complete(
        &self,
        #[tool(description = "The ID of the pending source to mark as complete")] id: String,
    ) -> Result<CallToolResult, rmcp::Error> {
        let db = self.db.write().await;

        let key = format!("pending_source:{}", id);

        // Check if it exists first
        match db.get(key.as_bytes()) {
            Ok(Some(value)) => {
                // Parse to get the name for confirmation
                let value_str = String::from_utf8_lossy(&value);
                let source_name = parse_edn_to_pending_source(&value_str)
                    .map(|s| s.name)
                    .unwrap_or_else(|_| "unknown".to_string());

                // Delete the key
                if let Err(e) = db.delete(key.as_bytes()) {
                    return error_response(&format!("Failed to delete from RocksDB: {}", e));
                }

                success_response(json!({
                    "message": "Source marked as complete and removed from pending queue",
                    "id": id,
                    "name": source_name,
                    "reminder": "Make sure the Crawlee script was saved to the scrapers/ directory"
                }))
            }
            Ok(None) => {
                error_response(&format!("No pending source found with ID: {}", id))
            }
            Err(e) => {
                error_response(&format!("Failed to read from RocksDB: {}", e))
            }
        }
    }
}

#[rmcp::async_trait]
impl ServerHandler for NextplaceMcp {
    fn get_info(&self) -> ServerInfo {
        ServerInfo {
            protocol_version: ProtocolVersion::V_2024_11_05,
            capabilities: ServerCapabilities::builder()
                .enable_tools()
                .build(),
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
    // Initialize logging to stderr (stdout is for MCP protocol)
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .with_writer(std::io::stderr)
        .init();

    info!("Starting Nextplace MCP server");

    // Get database path
    let db_path = get_db_path();
    info!("Using RocksDB at: {:?}", db_path);

    // Create the MCP service
    let service = NextplaceMcp::new(db_path)?;

    // Run the server over stdio
    let server = service.serve(stdio()).await?;

    info!("MCP server running on stdio");

    // Wait for shutdown
    server.waiting().await?;

    info!("MCP server shutting down");
    Ok(())
}
