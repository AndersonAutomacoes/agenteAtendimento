/**
 * UUID v4 compatível com ambientes sem `crypto.randomUUID` (HTTP, WebViews antigos, alguns mobile).
 */
export function randomUuid(): string {
  const c = globalThis.crypto as Crypto | undefined;
  if (c != null && typeof c.randomUUID === "function") {
    return c.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    const v = ch === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
