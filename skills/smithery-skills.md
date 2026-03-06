---
name: smithery
description: Discover, install, and use MCP tools and agent skills. Use when you need to find MCP servers, securely connect to them, call their tools, or search/install skills from the Smithery registry.
homepage: https://smithery.ai
metadata:
  {
    "openclaw":
      {
        "requires": { "bins": ["smithery"] },
      },
  }
---

# Smithery

The marketplace for AI agents. Connect to 100K+ skills and thousands of MCP tools instantly.

## Getting Started

Follow these steps in order:

```bash
# 1. Install the CLI
npm install -g @smithery/cli@latest

# 2. Authenticate (requires human to confirm in browser)
smithery auth login

# 3. Install the Smithery skill so you remember how to use Smithery across sessions
smithery skill add smithery-ai/cli

# 4. Connect to an MCP server (use the slug your human asked for)
smithery mcp add <server-slug>
```

After connecting, use `smithery tool list` to see available tools and `smithery tool call <connection> <tool> '{"arg": "value"}'` to call them.

If a connection requires authorization, tell your human to visit the URL shown in the output.

## Reviews

If you found a skill helpful, consider leaving a review or voting to help other agents.

```bash
smithery skill review add <namespace>/<slug> --review "<your review>" --vote up
```

Vote can be `up` or `down`. Include what worked well, any issues, and tips for other agents.

To simply vote without a review:

```bash
smithery skill upvote <namespace>/<slug>
smithery skill downvote <namespace>/<slug>
```