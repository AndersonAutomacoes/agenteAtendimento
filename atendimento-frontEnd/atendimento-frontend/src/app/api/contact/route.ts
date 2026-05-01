import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { NextResponse } from "next/server";

import { getFirestoreAdmin } from "@/lib/firebase-admin";

const COLLECTION = "contact_leads";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function sanitizeUa(ua: string | null): string | undefined {
  if (!ua || ua.length > 512) return undefined;
  return ua.replace(/[^\x20-\x7E]/g, "").slice(0, 512);
}

function validateBody(raw: unknown): {
  name: string;
  email: string;
  whatsapp: string;
  message: string;
  locale: string;
} | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const honeypot =
    typeof o.company_website === "string" ? o.company_website.trim() : "";
  if (honeypot.length > 0) return null;

  const name = typeof o.name === "string" ? o.name.trim() : "";
  const email = typeof o.email === "string" ? o.email.trim().toLowerCase() : "";
  const whatsapp = typeof o.whatsapp === "string" ? o.whatsapp.trim() : "";
  const message = typeof o.message === "string" ? o.message.trim() : "";
  const localeRaw = typeof o.locale === "string" ? o.locale.trim() : "pt-BR";
  const locale = localeRaw.slice(0, 16);

  if (o.consent !== true) return null;

  if (name.length < 2 || name.length > 120) return null;
  if (!EMAIL_RE.test(email) || email.length > 254) return null;
  if (whatsapp.length < 8 || whatsapp.length > 32) return null;
  if (!/^[\d\s+()-]+$/.test(whatsapp)) return null;
  if (message.length < 10 || message.length > 2000) return null;

  return { name, email, whatsapp, message, locale };
}

async function persistLead(
  db: Firestore,
  body: { name: string; email: string; whatsapp: string; message: string; locale: string },
  userAgent: string | undefined,
) {
  await db.collection(COLLECTION).add({
    name: body.name,
    email: body.email,
    whatsapp: body.whatsapp,
    message: body.message,
    locale: body.locale,
    source: "landing",
    createdAt: FieldValue.serverTimestamp(),
    ...(userAgent ? { userAgent } : {}),
  });
}

/** Honeypot filled — pretend success for bots. */
const SILENT_OK = new NextResponse(null, { status: 204 });

export async function POST(req: Request) {
  let parsed: unknown;
  try {
    parsed = await req.json();
  } catch {
    return NextResponse.json({ error: "invalid_json" }, { status: 400 });
  }

  const validated = validateBody(parsed);
  if (validated === null) {
    const o = parsed && typeof parsed === "object" ? (parsed as Record<string, unknown>) : {};
    const hp =
      typeof o.company_website === "string" ? o.company_website.trim() : "";
    if (hp.length > 0) return SILENT_OK;
    return NextResponse.json({ error: "validation" }, { status: 400 });
  }

  let db: Firestore;
  try {
    db = getFirestoreAdmin();
  } catch {
    return NextResponse.json({ error: "unavailable" }, { status: 503 });
  }

  try {
    await persistLead(db, validated, sanitizeUa(req.headers.get("user-agent")));
  } catch {
    return NextResponse.json({ error: "unavailable" }, { status: 503 });
  }

  return NextResponse.json({ ok: true }, { status: 201 });
}
