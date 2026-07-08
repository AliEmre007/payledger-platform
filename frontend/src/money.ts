export function parseMajorToMinor(value: string): number {
  const normalized = value.trim().replace(",", ".");

  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    throw new Error("Amount must use up to two decimal places.");
  }

  const [major, minor = ""] = normalized.split(".");
  const amountMinor = Number(`${major}${minor.padEnd(2, "0")}`);

  if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) {
    throw new Error("Amount must be greater than zero.");
  }

  return amountMinor;
}

export function formatMinor(amountMinor: number, currency: string): string {
  const sign = amountMinor < 0 ? "-" : "";
  const absolute = Math.abs(amountMinor);
  const major = Math.floor(absolute / 100);
  const minor = String(absolute % 100).padStart(2, "0");

  return `${sign}${major.toLocaleString()}.${minor} ${currency}`;
}
