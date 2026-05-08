/** Cookie lida pelo middleware Edge; sincronizada no cliente após GET /v1/auth/me. */
export const CEREBRO_BILLING_BLOCKED_COOKIE = "cerebro-billing-blocked";

export function setBillingBlockedCookieClient(blocked: boolean): void {
  if (typeof document === "undefined") return;
  const maxAge = 60 * 60 * 24 * 14;
  if (blocked) {
    document.cookie = `${CEREBRO_BILLING_BLOCKED_COOKIE}=1; Path=/; Max-Age=${maxAge}; SameSite=Lax`;
  } else {
    document.cookie = `${CEREBRO_BILLING_BLOCKED_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
  }
}

export function clearBillingBlockedCookieClient(): void {
  if (typeof document === "undefined") return;
  document.cookie = `${CEREBRO_BILLING_BLOCKED_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}
