#!/usr/bin/env node
/**
 * Wrapper to run an npx-installed package's bin with Node explicitly.
 * Fixes "import: command not found" when the package's bin is a .js file without shebang.
 * Usage: node mcp-npx-run.js <package-name> [args...]
 * Debug: AGENTDSL_MCP_WRAPPER_DEBUG=1 node mcp-npx-run.js @pkg  (writes phase logs to /tmp/agentdsl-mcp-wrapper.log)
 *
 * Manual test (keep stdin open so MCP server does not see EOF and exit):
 *   node mcp-npx-run.js @starhawk/mcp-weather-server < /dev/tty
 * Or: script -q /dev/null node mcp-npx-run.js @starhawk/mcp-weather-server
 *
 * When run by MCP client, stdin/stdout are the protocol channel. The first spawn (npx -p install)
 * must use stdio ['ignore','pipe','pipe'] so it does NOT consume the client's initialize message.
 */
const path = require('path');
const fs = require('fs');
const cp = require('child_process');

const DEBUG = process.env.AGENTDSL_MCP_WRAPPER_DEBUG === '1' || process.env.AGENTDSL_MCP_WRAPPER_DEBUG === 'true';
const LOG = DEBUG ? (msg) => { try { fs.appendFileSync('/tmp/agentdsl-mcp-wrapper.log', `${new Date().toISOString()} ${msg}\n`); } catch (_) {} } : () => {};

const pkg = process.argv[2];
if (!pkg) {
  process.stderr.write('Usage: node mcp-npx-run.js <package-name> [args...]\n');
  process.exit(1);
}

LOG(`[wrapper] start pkg=${pkg}`);

/**
 * Resolve the bin entry-point for an npx-installed package.
 *
 * Strategy (in order):
 *   1. Use `npx -y -p <pkg> node -e "..."` to install the package AND
 *      scan the PATH env-var inside that npx sub-shell to locate the
 *      npx-cache node_modules directory. Then read package.json directly
 *      from the filesystem (avoids require.resolve which fails for ESM
 *      packages under Node ≥ 18).
 *   2. Fallback: try require.resolve (works for CJS packages).
 */
// Inner script: runs inside `npx -y -p <pkg> node -e "..."`.
// Finds the npx cache node_modules dir from PATH, reads package.json, prints bin path.
const inner = `
var path = require('path');
var fs = require('fs');
var pkg = process.argv[1];

// Strategy A: find the npx cache dir from PATH
var dirs = (process.env.PATH || '').split(':');
for (var i = 0; i < dirs.length; i++) {
  if (dirs[i].includes('_npx') && dirs[i].endsWith('node_modules/.bin')) {
    var nmDir = path.resolve(dirs[i], '..');
    var pkgJson = path.join(nmDir, pkg, 'package.json');
    if (fs.existsSync(pkgJson)) {
      var j = JSON.parse(fs.readFileSync(pkgJson, 'utf8'));
      var b = j.bin ? (typeof j.bin === 'string' ? j.bin : Object.values(j.bin)[0]) : 'index.js';
      console.log(path.join(path.dirname(pkgJson), b));
      process.exit(0);
    }
  }
}

// Strategy B: try require.resolve (works for CJS packages)
try {
  var r = require.resolve(pkg + '/package.json');
  var j2 = require(r);
  var b2 = j2.bin ? (typeof j2.bin === 'string' ? j2.bin : Object.values(j2.bin)[0]) : 'index.js';
  console.log(path.join(path.dirname(r), b2));
  process.exit(0);
} catch(_) {}

// Strategy C: find bin symlink in PATH and resolve it
for (var i = 0; i < dirs.length; i++) {
  if (dirs[i].includes('_npx') && dirs[i].endsWith('node_modules/.bin')) {
    var binFiles = [];
    try { binFiles = fs.readdirSync(dirs[i]); } catch(_) {}
    for (var k = 0; k < binFiles.length; k++) {
      try {
        var realPath = fs.realpathSync(path.join(dirs[i], binFiles[k]));
        if (realPath.endsWith('.js')) {
          console.log(realPath);
          process.exit(0);
        }
      } catch(_) {}
    }
  }
}

process.stderr.write('Could not resolve bin for: ' + pkg + '\\n');
process.exit(1);
`;

// 重要：不要继承 stdin/stdout，否则 MCP 客户端发来的 initialize 会被 npx 子进程消费，导致真正 MCP 进程收不到握手
LOG(`[wrapper] phase: npx -y -p install + resolve bin`);
const t0 = Date.now();
const out = cp.spawnSync('npx', ['-y', '-p', pkg, 'node', '-e', inner.trim(), pkg], {
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'pipe']
});
LOG(`[wrapper] npx phase done in ${Date.now() - t0}ms status=${out.status}`);
if (out.status !== 0) {
  LOG(`[wrapper] npx failed stderr=${(out.stderr || '').slice(0, 200)}`);
  process.exit(out.status || 1);
}
const binPath = out.stdout.trim();
if (!binPath) {
  process.stderr.write(`Could not resolve bin for package: ${pkg}\n`);
  process.exit(1);
}
LOG(`[wrapper] binPath=${binPath} spawning node`);
const t1 = Date.now();
const result = cp.spawnSync(process.execPath, [binPath].concat(process.argv.slice(3)), {
  stdio: 'inherit'
});
LOG(`[wrapper] server process exited in ${Date.now() - t1}ms status=${result.status}`);
process.exit(result.status || 0);
