/**
 * San Jose Downtown Events Scraper
 * https://sjdowntown.com/whats-going-on/
 *
 * Geographic scope: San Jose, CA
 *
 * This scraper uses the Tribe Events REST API to fetch events.
 * Limited to events within the next 3 days.
 */

import { CheerioCrawler } from 'crawlee';

const API_URL = 'https://sjdowntown.com/wp-json/tribe/events/v1/events';
const DAYS_AHEAD = 3;

const now = new Date();
const cutoffDate = new Date(now.getTime() + DAYS_AHEAD * 24 * 60 * 60 * 1000);
cutoffDate.setHours(23, 59, 59, 999);

const events = [];
let stopFetching = false;

const crawler = new CheerioCrawler({
  async requestHandler({ request, json, log }) {
    if (stopFetching) return;

    log.info(`Processing ${request.url}`);

    const data = json;

    if (data && data.events && Array.isArray(data.events)) {
      for (const evt of data.events) {
        const eventDate = new Date(evt.start_date);

        if (eventDate > cutoffDate) {
          log.info(`Event "${evt.title}" on ${evt.start_date} is beyond ${DAYS_AHEAD}-day window, stopping.`);
          stopFetching = true;
          break;
        }

        const venue = evt.venue || {};

        events.push({
          title: evt.title || 'Untitled Event',
          date: evt.start_date || 'See website',
          time: extractTime(evt.start_date_details, evt.end_date_details),
          location: formatLocation(venue),
          description: stripHtml(evt.description || evt.excerpt || ''),
          url: evt.url || evt.website || 'https://sjdowntown.com/whats-going-on/',
          organization: 'San Jose Downtown Association',
          category: determineCategory(evt),
          geographic_scope: 'San Jose, CA',
          cost: evt.cost || 'See website',
          image: evt.image?.url || null,
        });
      }

      if (!stopFetching && data.next_rest_url) {
        await crawler.addRequests([{
          url: data.next_rest_url,
          userData: { page: request.userData.page + 1 },
        }]);
      }
    }
  },
});

function extractTime(start, end) {
  if (!start) return 'See website';

  const startTime = formatTimeFromDetails(start);
  const endTime = end ? formatTimeFromDetails(end) : null;

  if (startTime === '12:00 AM' && (!endTime || endTime === '11:59 PM')) {
    return 'All Day';
  }

  return endTime ? `${startTime} - ${endTime}` : startTime;
}

function formatTimeFromDetails(details) {
  if (!details || !details.hour) return null;

  const hour = parseInt(details.hour, 10);
  const minutes = details.minutes || '00';
  const ampm = hour >= 12 ? 'PM' : 'AM';
  const hour12 = hour % 12 || 12;

  return `${hour12}:${minutes} ${ampm}`;
}

function formatLocation(venue) {
  if (!venue || !venue.venue) return 'Downtown San Jose, CA';

  const parts = [venue.venue];
  if (venue.address) parts.push(venue.address);
  if (venue.city) parts.push(venue.city);
  if (venue.state) parts.push(venue.state);
  if (venue.zip) parts.push(venue.zip);

  return parts.join(', ');
}

function stripHtml(html) {
  return html
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 500);
}

function determineCategory(evt) {
  const title = (evt.title || '').toLowerCase();
  const desc = (evt.description || '').toLowerCase();
  const categories = (evt.categories || []).map(c => c.name?.toLowerCase() || '');

  if (categories.includes('music') || title.includes('concert') || title.includes('live music')) {
    return 'music';
  }
  if (categories.includes('arts') || title.includes('art') || title.includes('gallery')) {
    return 'arts';
  }
  if (title.includes('film') || title.includes('movie') || title.includes('cinema')) {
    return 'film';
  }
  if (title.includes('food') || title.includes('taste') || title.includes('dining')) {
    return 'food';
  }
  if (title.includes('clean') || title.includes('volunteer') || desc.includes('community')) {
    return 'community';
  }

  return 'events';
}

await crawler.run([{
  url: `${API_URL}?per_page=50`,
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
