/** Basic client-side check; server/Firebase remain authoritative. */
export function isLikelyEmail(value: string): boolean {
  const s = value.trim();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s);
}

export function readFirebaseErrorCode(err: unknown): string {
  if (err && typeof err === "object" && "code" in err) {
    const c = (err as { code?: unknown }).code;
    return typeof c === "string" ? c : "";
  }
  return "";
}
