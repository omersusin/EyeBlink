// Renders a page of docs/brand to a picture. With no arguments: banner.html -> banner.png (1920 x 1080).
//   node docs/brand/render.mjs
//   node docs/brand/render.mjs tokens.html tokens.png 1600 1200     (page, picture, width, height - the page's own size if it fits)
// Needs Playwright with a Chromium; point PLAYWRIGHT_MODULE / CHROMIUM at yours if they are not on the default path.
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const here = path.dirname(fileURLToPath(import.meta.url));

const [htmlName = 'banner.html', pngName = 'banner.png', w = '1920', h = '1080'] = process.argv.slice(2);
const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const page = await browser.newPage({ viewport: { width: Number(w), height: Number(h) }, deviceScaleFactor: 1 });
await page.goto(pathToFileURL(path.join(here, htmlName)).href);
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(400);
// The banner is a fixed 16:9 canvas; any other page is shot whole, as tall as it turns out to be.
await page.screenshot({ path: path.join(here, pngName), fullPage: htmlName !== 'banner.html' });
await browser.close();
console.log(pngName + ' written');
