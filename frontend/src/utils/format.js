const inrFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export function formatMoney(value) {
  if (value == null) return '—';
  return inrFormatter.format(value);
}

export function formatTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString();
}

export function formatRelative(iso) {
  const diff = new Date(iso).getTime() - Date.now();
  const abs = Math.abs(diff);
  const minutes = Math.floor(abs / 60000);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  let text;
  if (days > 0) text = `${days}d ${hours % 24}h`;
  else if (hours > 0) text = `${hours}h ${minutes % 60}m`;
  else text = `${minutes}m`;
  return diff >= 0 ? `in ${text}` : `${text} ago`;
}
