/**
 * Gera PNGs a partir de public/icons/icon.svg (requer sharp, dependência do Next).
 */
import { readFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, "..");
const svgPath = join(root, "public/icons/icon.svg");
const outDir = join(root, "public/icons");

async function main() {
  if (!existsSync(svgPath)) {
    console.error("Missing", svgPath);
    process.exit(1);
  }
  mkdirSync(outDir, { recursive: true });
  const svg = readFileSync(svgPath);
  const out = [
    ["icon-192.png", 192],
    ["icon-512.png", 512],
    ["apple-touch-icon.png", 180],
  ];
  for (const [name, size] of out) {
    await sharp(svg).resize(size, size).png().toFile(join(outDir, name));
    console.log("Wrote", name);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
