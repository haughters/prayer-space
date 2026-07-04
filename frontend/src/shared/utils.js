export function formatDate(dateString) {
  if (!dateString) return '';
  try {
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  } catch (e) {
    return '';
  }
}

export function validatePrayerText(text) {
  if (!text) return false;
  const trimmed = text.trim();
  return trimmed.length >= 10 && trimmed.length <= 2000;
}
