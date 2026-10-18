// Renders docs/brand/banner.html to docs/brand/banner.png (1920 x 1080).
//   node docs/brand/render.mjs
// Needs Playwright with a Chromium; point PLAYWRIGHT_MODULE / CHROMIUM at yours if they are not on the default path.
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const here = path.dirname(fileURLToPath(import.meta.url));

const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
await page.goto(pathToFileURL(path.join(here, 'banner.html')).href);
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(400);
await page.screenshot({ path: path.join(here, 'banner.png') });
await browser.close();
console.log('banner.png written');
