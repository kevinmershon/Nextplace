/**
 * The Hub RWC Events Scraper
 * https://www.thehubrwc.com/music
 *
 * Geographic scope: Redwood City, CA
 *
 * This scraper uses Playwright to extract events from The Hub's Wix website.
 * Scrapes the /music page for live music events.
 * Limited to events within the next 3 days.
 */

import { PlaywrightCrawler } from 'crawlee';

const MUSIC_URL = 'https://www.thehubrwc.com/music';
const DAYS_AHEAD = 3;
const VENUE = 'The Hub RWC, 2650 Broadway St, Redwood City, CA 94063';
const ORGANIZATION = 'The Hub RWC';
const GEOGRAPHIC_SCOPE = 'Redwood City, CA';

const now = new Date();
const cutoffDate = new Date(now.getTime() + DAYS_AHEAD * 24 * 60 * 60 * 1000);
cutoffDate.setHours(23, 59, 59, 999);

const events = [];

const crawler = new PlaywrightCrawler({
  headless: true,
  requestHandlerTimeoutSecs: 60,

  async requestHandler({ page, request, log }) {
    log.info(`Processing ${request.url}`);

    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);

    const pageText = await page.evaluate(() => document.body.innerText);

    const lines = pageText.split('\n').map(l => l.trim()).filter(l => l);

    const eventPattern = /^(january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{1,2}),?\s+(.+)/i;

    for (const line of lines) {
      const match = line.match(eventPattern);
      if (match) {
        const monthName = match[1].toLowerCase();
        const day = parseInt(match[2], 10);
        const details = match[3];

        const months = {
          january: 0, february: 1, march: 2, april: 3, may: 4, june: 5,
          july: 6, august: 7, september: 8, october: 9, november: 10, december: 11
        };
        const month = months[monthName];

        let year = now.getFullYear();
        let eventDate = new Date(year, month, day);

        if (eventDate < new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)) {
          eventDate = new Date(year + 1, month, day);
        }

        if (eventDate <= cutoffDate) {
          const timeMatch = details.match(/(\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)?\s*-?\s*\d{0,2}:?\d{0,2}\s*(?:a\.?m\.?|p\.?m\.?|late)?)/i);
          const time = timeMatch ? timeMatch[1].trim() : 'See website';

          let title = details;
          if (timeMatch) {
            title = details.replace(timeMatch[0], '').replace(/^[:\s]+/, '').trim();
          }

          if (title) {
            events.push({
              title: title,
              date: formatDate(eventDate),
              time: time,
              location: VENUE,
              description: `Live music at The Hub RWC: ${title}`,
              url: MUSIC_URL,
              organization: ORGANIZATION,
              category: 'music',
              geographic_scope: GEOGRAPHIC_SCOPE,
              cost: 'See website',
              image: null,
            });

            log.info(`Found event: ${title} on ${formatDate(eventDate)}`);
          }
        }
      }
    }
  },
});

function formatDate(date) {
  return date.toISOString().split('T')[0];
}

await crawler.run([{ url: MUSIC_URL }]);

const uniqueEvents = [];
const seen = new Set();

for (const evt of events) {
  const key = `${evt.title}|${evt.date}|${evt.location}`;
  if (!seen.has(key)) {
    seen.add(key);
    uniqueEvents.push(evt);
  }
}

console.log(JSON.stringify(uniqueEvents, null, 2));
