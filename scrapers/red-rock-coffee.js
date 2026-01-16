/**
 * Red Rock Coffee Events Scraper
 * https://redrockcoffee.com
 *
 * Geographic scope: Mountain View, CA
 *
 * This scraper generates events from known recurring schedules:
 * - Open Mic Monday: Every Monday 6-9:30pm
 * - Book Club: First Sunday of month 4-5:30pm
 * - Artists MV: First Wednesday of month 5-7pm
 * - Matchbox Teen Open Mic: Fridays 6-8pm (2nd floor)
 *
 * Limited to events within the next 3 days.
 */

const DAYS_AHEAD = 3;
const VENUE = 'Red Rock Coffee, 201 Castro Street, Mountain View, CA 94041';
const ORGANIZATION = 'Red Rock Coffee';
const GEOGRAPHIC_SCOPE = 'Mountain View, CA';

const now = new Date();
const cutoffDate = new Date(now.getTime() + DAYS_AHEAD * 24 * 60 * 60 * 1000);
cutoffDate.setHours(23, 59, 59, 999);

const events = [];

function formatDate(date) {
  return date.toISOString().split('T')[0];
}

function isFirstOfMonth(date, dayOfWeek) {
  if (date.getDay() !== dayOfWeek) return false;
  return date.getDate() <= 7;
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
      title: 'Open Mic Monday',
      date: dateStr,
      time: '6:00 PM - 9:30 PM',
      location: VENUE,
      description: 'Weekly open mic night. Lottery sign-ups 5:45-8pm, 5-minute sets. All ages, all genres welcome. First performer at 6pm.',
      url: 'https://redrockcoffee.com/openmic',
      organization: ORGANIZATION,
      category: 'music',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free',
      image: null,
    });
  }

  if (isFirstOfMonth(d, 0)) {
    events.push({
      title: 'Red Rock Book Club',
      date: dateStr,
      time: '4:00 PM - 5:30 PM',
      location: VENUE,
      description: 'Monthly book club meeting on the first Sunday of each month.',
      url: 'https://redrockcoffee.com',
      organization: ORGANIZATION,
      category: 'community',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free',
      image: null,
    });
  }

  if (isFirstOfMonth(d, 3)) {
    events.push({
      title: 'Artists MV',
      date: dateStr,
      time: '5:00 PM - 7:00 PM',
      location: VENUE,
      description: 'Monthly gathering for Mountain View artists on the first Wednesday of each month.',
      url: 'https://redrockcoffee.com',
      organization: ORGANIZATION,
      category: 'arts',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free',
      image: null,
    });
  }

  if (dayOfWeek === 5) {
    events.push({
      title: 'Matchbox Teen Open Mic',
      date: dateStr,
      time: '6:00 PM - 8:00 PM',
      location: VENUE + ' (2nd Floor)',
      description: 'Open mic night for teens. Located on the 2nd floor.',
      url: 'https://redrockcoffee.com',
      organization: ORGANIZATION,
      category: 'music',
      geographic_scope: GEOGRAPHIC_SCOPE,
      cost: 'Free',
      image: null,
    });
  }
}

console.log(JSON.stringify(events, null, 2));
