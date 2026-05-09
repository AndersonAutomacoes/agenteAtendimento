#!/usr/bin/env node
/**
 * Runs UI/UX Pro Max BM25 search (search.py) when UI_UX_PRO_MAX_ROOT points at a skill clone.
 *
 * Usage (from atendimento-frontend):
 *   set UI_UX_PRO_MAX_ROOT=D:\tools\ui-ux-pro-max-skill
 *   npm run design:search -- "SaaS dashboard charts" --domain ux --stack nextjs
 *
 * Domains: style, prompt, color, chart, landing, product, ux, typography
 * Stacks: html-tailwind, react, nextjs, vue, nuxtjs, nuxt-ui, svelte, swiftui, react-native, flutter
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.env.UI_UX_PRO_MAX_ROOT?.trim();
if (!root) {
  console.error(
    "Missing UI_UX_PRO_MAX_ROOT. Set it to the folder that contains scripts/search.py (your ui-ux-pro-max-skill clone).\n" +
      "PowerShell:  [Environment]::SetEnvironmentVariable('UI_UX_PRO_MAX_ROOT','D:\\\\tools\\\\ui-ux-pro-max-skill','User')\n" +
      "Then restart the terminal.",
  );
  process.exit(1);
}

const script = path.join(root, "scripts", "search.py");
if (!fs.existsSync(script)) {
  console.error(`search.py not found:\n  ${script}\nCheck UI_UX_PRO_MAX_ROOT.`);
  process.exit(1);
}

const queryAndFlags = process.argv.slice(2);
if (queryAndFlags.length === 0) {
  console.error(
    'Pass a query and optional flags, e.g.\n  npm run design:search -- "dashboard accessibility" --domain ux --stack nextjs',
  );
  process.exit(1);
}

const isWin = process.platform === "win32";
const cmd = isWin ? "py" : "python3";
const prefixArgs = isWin ? ["-3"] : [];
const result = spawnSync(cmd, [...prefixArgs, script, ...queryAndFlags], {
  stdio: "inherit",
  shell: false,
});

process.exit(result.status ?? 1);
