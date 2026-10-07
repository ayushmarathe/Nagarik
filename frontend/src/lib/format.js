/** "3 days ago" reads faster than a timestamp when you are scanning a feed. */
export function timeAgo(isoString) {
  const then = new Date(isoString);
  if (Number.isNaN(then.getTime())) return '';

  const seconds = Math.round((Date.now() - then.getTime()) / 1000);
  if (seconds < 60) return 'just now';

  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} ${plural(minutes, 'minute')} ago`;

  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} ${plural(hours, 'hour')} ago`;

  const days = Math.round(hours / 24);
  if (days < 30) return `${days} ${plural(days, 'day')} ago`;

  const months = Math.round(days / 30);
  if (months < 12) return `${months} ${plural(months, 'month')} ago`;

  const years = Math.round(months / 12);
  return `${years} ${plural(years, 'year')} ago`;
}

function plural(count, word) {
  return count === 1 ? word : `${word}s`;
}

export function fullDate(isoString) {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });
}

/**
 * A promised date, written the way somebody would say it out loud.
 *
 * The server sends a plain "2026-09-20" with no timezone, and `new Date` reads
 * a bare date string as midnight UTC - which in any timezone behind UTC shows
 * the day before. Splitting the parts and building a local date avoids that
 * entirely, and is the reason this does not just call fullDate.
 */
export function shortDate(dateString) {
  const date = parseLocalDate(dateString);
  if (!date) return '';
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export function parseLocalDate(dateString) {
  if (!dateString) return null;
  const [year, month, day] = String(dateString).split('-').map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day);
}

/** Whole days between today and a promised date. Negative means it has passed. */
export function daysUntil(dateString) {
  const date = parseLocalDate(dateString);
  if (!date) return null;

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((date.getTime() - today.getTime()) / 86400000);
}

/**
 * "due in 3 days" / "4 days late". Phrased around the reader rather than the
 * calendar, because a date on its own makes everyone do the subtraction.
 */
export function dueLabel(dateString) {
  const days = daysUntil(dateString);
  if (days === null) return '';
  if (days === 0) return 'due today';
  if (days === 1) return 'due tomorrow';
  if (days > 1) return `due in ${days} days`;
  if (days === -1) return '1 day late';
  return `${Math.abs(days)} days late`;
}

/** How long a verification window has left. Counts in days once past a day. */
export function timeLeft(isoString) {
  const then = new Date(isoString);
  if (Number.isNaN(then.getTime())) return '';

  const seconds = Math.round((then.getTime() - Date.now()) / 1000);
  if (seconds <= 0) return 'closing now';

  const hours = Math.round(seconds / 3600);
  if (hours < 1) return 'under an hour left';
  if (hours < 36) return `${hours} ${plural(hours, 'hour')} left`;

  const days = Math.round(hours / 24);
  return `${days} ${plural(days, 'day')} left`;
}
