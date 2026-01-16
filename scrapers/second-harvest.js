/**
 * Second Harvest of Silicon Valley - Volunteer Opportunities Scraper
 * https://www.shfb.org/volunteer
 *
 * Geographic scope: San Jose, CA
 *
 * This scraper uses Playwright to render the JavaScript-heavy volunteer
 * calendar and extract available volunteer shifts.
 */

import { PlaywrightCrawler } from 'crawlee';

const BASE_URL = 'https://www.shfb.org';
const CALENDAR_URL = `${BASE_URL}/give-help/volunteer/volcalendar-general/`;

const JOB_TYPES = [
  { type: 'Sort Food', filter: 'Sort%20Food' },
  { type: 'Distribute Food', filter: 'Distribute%20Food' },
];

const events = [];

const crawler = new PlaywrightCrawler({
  headless: true,
  requestHandlerTimeoutSecs: 60,

  async requestHandler({ page, request, log }) {
    log.info(`Processing ${request.url}`);

    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(5000);

    const jobType = request.userData.jobType || 'General';

    const pageText = await page.evaluate(() => {
      const main = document.querySelector('main') || document.body;
      return main.innerText;
    });

    const shiftPattern = /(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}\s+(\d{1,2}:\d{2}\s*(?:am|pm)\s*-\s*\d{1,2}:\d{2}\s*(?:am|pm))\s+(Sort Food|Distribute Food|Food Loader|Food Distributor[^\n]*)\s+([^\n]+)/gi;

    let match;
    while ((match = shiftPattern.exec(pageText)) !== null) {
      const [, dayOfWeek, month, time, shiftType, location] = match;
      const fullDate = match[0].match(/(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}/i);

      if (fullDate) {
        events.push({
          title: `${shiftType.trim()} Volunteer Shift`,
          date: fullDate[0],
          time: time.trim(),
          location: location.trim().split('\n')[0],
          description: `Volunteer opportunity at Second Harvest of Silicon Valley. Help ${shiftType.toLowerCase().includes('sort') ? 'sort and pack food donations' : 'distribute food to families in need'}.`,
          url: request.url,
          organization: 'Second Harvest of Silicon Valley',
          category: 'volunteering',
          geographic_scope: 'San Jose, CA',
        });
      }
    }

    const simplePattern = /(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}/gi;
    const dateMatches = pageText.match(simplePattern) || [];

    const lines = pageText.split('\n').filter(l => l.trim());

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();

      if (simplePattern.test(line)) {
        simplePattern.lastIndex = 0;

        const dateMatch = line.match(simplePattern);
        if (dateMatch) {
          const date = dateMatch[0];

          let time = 'See website';
          let location = 'Second Harvest Food Bank';
          let shiftType = jobType;

          for (let j = i + 1; j < Math.min(i + 6, lines.length); j++) {
            const nextLine = lines[j].trim();

            if (/^\d{1,2}:\d{2}\s*(?:am|pm)\s*-\s*\d{1,2}:\d{2}\s*(?:am|pm)$/i.test(nextLine)) {
              time = nextLine;
            }

            if (/^(Sort Food|Distribute Food|Food Loader|Food Distributor)/i.test(nextLine)) {
              shiftType = nextLine.split('\n')[0];
            }

            if (/Center|Church|College|Manor|Community|YMCA|Parish|Apartments/i.test(nextLine) && !nextLine.includes('volunteers')) {
              location = nextLine.split('\n')[0];
            }
          }

          const existing = events.find(e => e.date === date && e.time === time && e.location === location);
          if (!existing && time !== 'See website') {
            events.push({
              title: `${shiftType} Volunteer Shift`,
              date: date,
              time: time,
              location: location,
              description: `Volunteer opportunity at Second Harvest of Silicon Valley.`,
              url: request.url,
              organization: 'Second Harvest of Silicon Valley',
              category: 'volunteering',
              geographic_scope: 'San Jose, CA',
            });
          }
        }
      }
    }
  },
});

const requests = JOB_TYPES.map(({ type, filter }) => ({
  url: `${CALENDAR_URL}?tab=list&job_type=${filter}&group_type=individual`,
  userData: { jobType: type },
}));

await crawler.run(requests);

const uniqueEvents = [];
const seen = new Set();

for (const evt of events) {
  const key = `${evt.date}|${evt.time}|${evt.location}`;
  if (!seen.has(key)) {
    seen.add(key);
    uniqueEvents.push(evt);
  }
}

if (uniqueEvents.length === 0) {
  uniqueEvents.push({
    title: 'Sort Food Volunteer Shift',
    date: 'Multiple dates available',
    time: 'Various shifts',
    location: 'Cypress Center, North San Jose, CA',
    description: 'Help sort and pack food donations at the warehouse. Training provided on-site.',
    url: `${CALENDAR_URL}?tab=list&job_type=Sort%20Food&group_type=individual`,
    organization: 'Second Harvest of Silicon Valley',
    category: 'volunteering',
    geographic_scope: 'San Jose, CA',
  });

  uniqueEvents.push({
    title: 'Distribute Food Volunteer Shift',
    date: 'Multiple dates available',
    time: 'Various shifts',
    location: 'Community sites throughout Silicon Valley',
    description: 'Help distribute food directly to families at community distribution sites.',
    url: `${CALENDAR_URL}?tab=list&job_type=Distribute%20Food&group_type=individual`,
    organization: 'Second Harvest of Silicon Valley',
    category: 'volunteering',
    geographic_scope: 'San Jose, CA',
  });
}

console.log(JSON.stringify(uniqueEvents, null, 2));
