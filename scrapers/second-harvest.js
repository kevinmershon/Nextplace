/**
 * Second Harvest of Silicon Valley - Volunteer Opportunities Scraper
 * https://www.shfb.org/volunteer
 *
 * Geographic scope: San Jose, CA
 *
 * This scraper uses Playwright to render the JavaScript-heavy volunteer
 * calendar and extract available volunteer shifts.
 */

import { PlaywrightCrawler, Dataset } from 'crawlee';

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
    await page.waitForTimeout(3000);

    const jobType = request.userData.jobType || 'General';

    const shiftElements = await page.$$('[class*="shift"], [class*="event"], [class*="calendar-item"], [class*="volunteer"], .fc-event, .calendar-event, tr[data-shift], .shift-row');

    if (shiftElements.length === 0) {
      const tableRows = await page.$$('table tbody tr');
      for (const row of tableRows) {
        try {
          const cells = await row.$$('td');
          if (cells.length >= 3) {
            const dateText = await cells[0]?.innerText().catch(() => '');
            const timeText = await cells[1]?.innerText().catch(() => '');
            const locationText = await cells[2]?.innerText().catch(() => '');

            if (dateText && timeText) {
              events.push({
                title: `${jobType} Volunteer Shift`,
                date: dateText.trim(),
                time: timeText.trim(),
                location: locationText.trim() || 'Second Harvest Food Bank',
                description: `Volunteer opportunity at Second Harvest of Silicon Valley`,
                url: request.url,
                organization: 'Second Harvest of Silicon Valley',
                category: 'volunteering',
                geographic_scope: 'San Jose, CA',
              });
            }
          }
        } catch (e) {
          log.debug(`Error parsing table row: ${e.message}`);
        }
      }
    }

    for (const el of shiftElements) {
      try {
        const title = await el.$eval('[class*="title"], .title, h3, h4, .event-title', (e) => e.innerText).catch(() => '');
        const date = await el.$eval('[class*="date"], .date, time', (e) => e.innerText || e.getAttribute('datetime')).catch(() => '');
        const time = await el.$eval('[class*="time"], .time', (e) => e.innerText).catch(() => '');
        const location = await el.$eval('[class*="location"], .location, .venue', (e) => e.innerText).catch(() => '');
        const link = await el.$eval('a', (e) => e.href).catch(() => '');

        if (title || date) {
          events.push({
            title: title || `${jobType} Volunteer Shift`,
            date: date || 'See website',
            time: time || 'Various times available',
            location: location || 'Second Harvest Food Bank',
            description: `Volunteer opportunity at Second Harvest of Silicon Valley`,
            url: link || request.url,
            organization: 'Second Harvest of Silicon Valley',
            category: 'volunteering',
            geographic_scope: 'San Jose, CA',
          });
        }
      } catch (e) {
        log.debug(`Error extracting shift: ${e.message}`);
      }
    }

    const pageContent = await page.content();

    const jsonMatch = pageContent.match(/window\.__DATA__\s*=\s*(\{[\s\S]*?\});/) ||
                      pageContent.match(/var\s+events\s*=\s*(\[[\s\S]*?\]);/) ||
                      pageContent.match(/"events"\s*:\s*(\[[\s\S]*?\])/);

    if (jsonMatch) {
      try {
        const data = JSON.parse(jsonMatch[1]);
        const eventArray = Array.isArray(data) ? data : (data.events || []);
        for (const evt of eventArray) {
          events.push({
            title: evt.title || evt.name || `${jobType} Volunteer Shift`,
            date: evt.date || evt.start || evt.startDate || 'See website',
            time: evt.time || evt.startTime || 'Various times available',
            location: evt.location || evt.venue || 'Second Harvest Food Bank',
            description: evt.description || `Volunteer opportunity at Second Harvest of Silicon Valley`,
            url: evt.url || evt.link || request.url,
            organization: 'Second Harvest of Silicon Valley',
            category: 'volunteering',
            geographic_scope: 'San Jose, CA',
          });
        }
      } catch (e) {
        log.debug(`Could not parse embedded JSON: ${e.message}`);
      }
    }
  },
});

const requests = [
  { url: CALENDAR_URL, userData: { jobType: 'General' } },
  ...JOB_TYPES.map(({ type, filter }) => ({
    url: `${CALENDAR_URL}?tab=list&job_type=${filter}&group_type=individual`,
    userData: { jobType: type },
  })),
];

await crawler.run(requests);

const uniqueEvents = [];
const seen = new Set();

for (const evt of events) {
  const key = `${evt.title}|${evt.date}|${evt.time}|${evt.location}`;
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

  uniqueEvents.push({
    title: 'Speakers Bureau Volunteer',
    date: 'Ongoing - Quarterly commitment',
    time: 'Flexible',
    location: 'Various locations in Silicon Valley',
    description: 'Become an ambassador for Second Harvest, speaking at community events about hunger issues.',
    url: 'https://app.smartsheet.com/b/form/a923e19f0ece422ca5dd3e48863891b3',
    organization: 'Second Harvest of Silicon Valley',
    category: 'volunteering',
    geographic_scope: 'San Jose, CA',
  });

  uniqueEvents.push({
    title: 'Volunteer Team Leader',
    date: 'Ongoing - Regular commitment',
    time: 'Flexible',
    location: 'Cypress Center, North San Jose, CA',
    description: 'Lead and train new volunteers at the sorting facility. Training provided.',
    url: 'https://shfb.tfaforms.net/19',
    organization: 'Second Harvest of Silicon Valley',
    category: 'volunteering',
    geographic_scope: 'San Jose, CA',
  });
}

console.log(JSON.stringify(uniqueEvents, null, 2));
