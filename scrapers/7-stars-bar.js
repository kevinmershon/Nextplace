/**
 * 7 Stars Bar and Grill Events Scraper
 * https://www.7starsbarandgrill.com/
 *
 * Geographic scope: Campbell, CA
 *
 * This scraper generates events from known recurring schedules:
 * - Karaoke: Friday & Saturday 8pm
 * - Trivia Night: Monday 7pm
 * - Craft Night: Wednesday evenings
 * - Bottomless Mimosas: Saturday & Sunday until 3pm
 *
 * Limited to events within the next 3 days.
 */

const DAYS_AHEAD = 3;
const VENUE = '7 Stars Bar and Grill, 400 E Campbell Ave, Campbell, CA 95008';
const ORGANIZATION = '7 Stars Bar and Grill';
const GEOGRAPHIC_SCOPE = 'Campbell, CA';
const BASE_URL = 'https://www.7starsbarandgrill.com/events';

const now = new Date();
const cutoffDate = new Date(now.getTime() + DAYS_AHEAD * 24 * 60 * 60 * 1000);
cutoffDate.setHours(23, 59, 59, 999);

const events = [];

function formatDate(date) {
  return date.toISOString().split('T')[0];
}

function addDays(date, days) {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

for (let d = new Date(now); d <= cutoffDate; d = addDays(d, 1)) {
  const dayOfWeek = d.getDay();
  const dateStr = formatDate(d);

  if (dayOfWeek === 1) {
    events.push({
      title: 'Trivia Night',
      date: dateStr,
      time: '7:00 PM',
      location: VENUE,
      description: 'Weekly trivia night at the sci-fi themed bar. Test your knowledge across various categories.',
      url: BASE_URL,
      organization: ORGANIZATION,
      category: 'entertainment',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free to play',
      image: null,
    });
  }

  if (dayOfWeek === 3) {
    events.push({
      title: 'Arts & Crafts Night',
      date: dateStr,
      time: '7:00 PM',
      location: VENUE,
      description: 'Weekly craft night at 7 Stars. Bring your projects or join in on group activities.',
      url: BASE_URL,
      organization: ORGANIZATION,
      category: 'arts',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free',
      image: null,
    });
  }

  if (dayOfWeek === 5) {
    events.push({
      title: 'Karaoke Night',
      date: dateStr,
      time: '8:00 PM',
      location: VENUE,
      description: 'Friday karaoke at the sci-fi fan bar. Sing your favorites in a fun, nerdy atmosphere.',
      url: BASE_URL,
      organization: ORGANIZATION,
      category: 'music',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free',
      image: null,
    });
  }

  if (dayOfWeek === 6) {
    events.push({
      title: 'Karaoke Night',
      date: dateStr,
      time: '8:00 PM',
      location: VENUE,
      description: 'Saturday karaoke at the sci-fi fan bar. Sing your favorites in a fun, nerdy atmosphere.',
      url: BASE_URL,
      organization: ORGANIZATION,
      category: 'music',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free',
      image: null,
    });

    events.push({
      title: 'Bottomless Mimosas',
      date: dateStr,
      time: '11:00 AM - 3:00 PM',
      location: VENUE,
      description: 'Saturday bottomless mimosas brunch special.',
      url: BASE_URL,
      organization: ORGANIZATION,
      category: 'food',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'See venue for pricing',
      image: null,
    });
  }

  if (dayOfWeek === 0) {
    events.push({
      title: 'Bottomless Mimosas',
      date: dateStr,
      time: '11:00 AM - 3:00 PM',
      location: VENUE,
      description: 'Sunday bottomless mimosas brunch special.',
      url: BASE_URL,
      organization: ORGANIZATION,
      category: 'food',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'See venue for pricing',
      image: null,
    });
  }
}

console.log(JSON.stringify(events, null, 2));
