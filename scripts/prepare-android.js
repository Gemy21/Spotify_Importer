/* eslint-disable */
/**
 * prepare-android.js
 *
 * 1. Temporarily renames src/app/api → src/app/_api_disabled so Next.js
 *    static export doesn't choke on POST route handlers.
 * 2. Runs `next build` with ANDROID_BUILD=1 (triggers output: 'export').
 * 3. Restores src/app/api.
 * 4. Copies the generated out/ → android/app/src/main/assets/www/
 *    so WebViewAssetLoader can serve it directly from the APK.
 *
 * Usage (already wired into package.json as "build:android"):
 *   node scripts/prepare-android.js
 *
 * Requires: next build has NOT already been run (this script runs it internally).
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const rootDir = path.resolve(__dirname, '..');
const apiDir = path.join(rootDir, 'src', 'app', 'api');
const apiDirDisabled = path.join(rootDir, 'src', 'app', '_api_disabled');
const outDir = path.join(rootDir, 'out');
const androidWww = path.join(rootDir, 'android', 'app', 'src', 'main', 'assets', 'www');

// ── Utility ──────────────────────────────────────────────────────────────

function copyDir(src, dest) {
  if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, entry.name);
    const d = path.join(dest, entry.name);
    entry.isDirectory() ? copyDir(s, d) : fs.copyFileSync(s, d);
  }
}

function countFiles(dir) {
  let n = 0;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    n += e.isDirectory() ? countFiles(path.join(dir, e.name)) : 1;
  }
  return n;
}

// ── Step 1: Disable API routes so static export doesn't error ────────────

console.log('\n[Android Build] Step 1: Disabling API routes for static export...');
if (fs.existsSync(apiDir)) {
  fs.renameSync(apiDir, apiDirDisabled);
  console.log('[Android Build] src/app/api → src/app/_api_disabled');
} else {
  console.log('[Android Build] No api/ directory found, skipping rename.');
}

// ── Step 2: Run Next.js static export ────────────────────────────────────

let buildFailed = false;
try {
  console.log('\n[Android Build] Step 2: Running next build (static export)...');
  execSync('npx next build', {
    cwd: rootDir,
    stdio: 'inherit',
    env: { ...process.env, ANDROID_BUILD: '1' },
  });
  console.log('[Android Build] next build succeeded.');
} catch (err) {
  buildFailed = true;
  console.error('[Android Build] next build FAILED:', err.message);
}

// ── Step 3: Restore API routes regardless of build outcome ───────────────

console.log('\n[Android Build] Step 3: Restoring API routes...');
if (fs.existsSync(apiDirDisabled)) {
  fs.renameSync(apiDirDisabled, apiDir);
  console.log('[Android Build] src/app/_api_disabled → src/app/api');
}

if (buildFailed) {
  console.error('[Android Build] Build failed — API routes have been restored. Exiting.');
  process.exit(1);
}

// ── Step 4: Copy out/ → android assets ───────────────────────────────────

console.log('\n[Android Build] Step 4: Copying static export to Android assets...');

if (!fs.existsSync(outDir)) {
  console.error('[Android Build] ERROR: out/ directory not found after build!');
  console.error('[Android Build] Make sure next.config.mjs has output: "export" when ANDROID_BUILD=1.');
  process.exit(1);
}

// Clean destination
if (fs.existsSync(androidWww)) {
  fs.rmSync(androidWww, { recursive: true, force: true });
  console.log('[Android Build] Cleaned existing assets/www/');
}

copyDir(outDir, androidWww);

const total = countFiles(androidWww);
console.log(`\n[Android Build] ✅ Done! Copied ${total} files → android/app/src/main/assets/www/`);
console.log('[Android Build] APK will serve the app via WebViewAssetLoader (zero server required).');
