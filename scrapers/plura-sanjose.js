/**
 * Plura Events - San Jose Scraper
 * https://plra.io/events/city/San%20Jose_CA
 *
 * Geographic scope: San Jose, CA
 *
 * This scraper extracts events from Plura's Next.js embedded JSON data.
 * Limited to events within the next 3 days.
 */

import { PlaywrightCrawler } from 'crawlee';

const BASE_URL = 'https://plra.io/events/city/San%20Jose_CA';
const DAYS_AHEAD = 3;

const now = new Date();
const cutoffDate = new Date(now.getTime() + DAYS_AHEAD * 24 * 60 * 60 * 1000);
cutoffDate.setHours(23, 59, 59, 999);

const events = [];
let stopFetching = false;

const crawler = new PlaywrightCrawler({
  headless: true,
  requestHandlerTimeoutSecs: 30,

  async requestHandler({ page, request, log }) {
    if (stopFetching) return;

    log.info(`Processing ${request.url}`);

    await page.waitForLoadState('networkidle');

    const pageData = await page.evaluate(() => {
      const scriptTag = document.getElementById('__NEXT_DATA__');
      if (scriptTag) {
        try {
          return JSON.parse(scriptTag.textContent);
        } catch (e) {
          return null;
        }
      }
      return null;
    });

    if (pageData?.props?.pageProps?.events) {
      const pageEvents = pageData.props.pageProps.events;

      for (const evt of pageEvents) {
        const eventDate = new Date(evt.startsAt);

        if (eventDate > cutoffDate) {
          log.info(`Event "${evt.name}" on ${evt.startsAt} is beyond ${DAYS_AHEAD}-day window, stopping.`);
          stopFetching = true;
          break;
        }

        events.push({
          title: evt.name || 'Untitled Event',
          date: formatDate(evt.startsAt),
          time: formatTime(evt.startsAt, evt.endsAt, evt.timezone),
          location: formatLocation(evt.location),
          description: `Event on Plura with ${evt.guestCount?.accepted || 0} attending, ${evt.guestCount?.interested || 0} interested.`,
          url: evt.url || `https://plra.io/events/${evt.id}`,
          organization: 'Plura',
          category: 'social',
          geographic_scope: 'San Jose, CA',
          attendance_type: evt.attendance || 'IN_PERSON',
          guest_count: evt.guestCount?.accepted || 0,
          image: evt.image || null,
        });
      }

      const currentPage = request.userData.page || 1;
      if (!stopFetching && pageData.props.pageProps.nextPage) {
        const nextPageUrl = `${BASE_URL}?page=${currentPage + 1}`;
        await crawler.addRequests([{
          url: nextPageUrl,
          userData: { page: currentPage + 1 },
        }]);
      }
    }
  },
});

function formatDate(isoString) {
  if (!isoString) return 'See website';

  const date = new Date(isoString);
  const options = { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' };
  return date.toLocaleDateString('en-US', options);
}

function formatTime(startIso, endIso, timezone) {
  if (!startIso) return 'See website';

  const start = new Date(startIso);
  const timeOptions = { hour: 'numeric', minute: '2-digit', hour12: true };

  const startTime = start.toLocaleTimeString('en-US', timeOptions);

  if (endIso) {
    const end = new Date(endIso);
    const endTime = end.toLocaleTimeString('en-US', timeOptions);
    return `${startTime} - ${endTime}`;
  }

  return startTime;
}

function formatLocation(loc) {
  if (!loc) return 'San Jose, CA';

  const parts = [];
  if (loc.name) parts.push(loc.name);
  if (loc.address1) parts.push(loc.address1);
  if (loc.city && loc.region) {
    parts.push(`${loc.city}, ${loc.region}`);
  } else if (loc.city) {
    parts.push(loc.city);
  }
  if (loc.postalCode) parts.push(loc.postalCode);

  return parts.length > 0 ? parts.join(', ') : 'San Jose, CA';
}

await crawler.run([{
  url: BASE_URL,
  userData: { page: 1 },
}]);

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
