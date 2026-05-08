import createMiddleware from "next-intl/middleware";
import { type NextRequest, NextResponse } from "next/server";

import { CEREBRO_BILLING_BLOCKED_COOKIE } from "@/lib/billing-cookie";
import { routing, type AppLocale } from "@/i18n/routing";

const intlMiddleware = createMiddleware(routing);

function isAppLocale(s: string): s is AppLocale {
  return routing.locales.includes(s as AppLocale);
}

function stripLocale(pathname: string): { locale: AppLocale; pathWithoutLocale: string } {
  const normalized = pathname.startsWith("/") ? pathname : `/${pathname}`;
  const parts = normalized.split("/").filter(Boolean);
  if (parts.length === 0) {
    return { locale: routing.defaultLocale, pathWithoutLocale: "/" };
  }
  if (isAppLocale(parts[0])) {
    const rest = parts.slice(1);
    return {
      locale: parts[0],
      pathWithoutLocale: rest.length ? `/${rest.join("/")}` : "/",
    };
  }
  return { locale: routing.defaultLocale, pathWithoutLocale: normalized };
}

function firstSegment(pathWithoutLocale: string): string {
  const p = pathWithoutLocale.replace(/\/$/, "") || "/";
  return p === "/" ? "" : (p.split("/").filter(Boolean)[0] ?? "");
}

function isBillingExempt(pathWithoutLocale: string): boolean {
  const seg = firstSegment(pathWithoutLocale);
  if (!seg) return true;
  const exempt = new Set([
    "login",
    "register",
    "forgot-password",
    "landing",
    "pricing",
    "billing",
  ]);
  return exempt.has(seg);
}

/**
 * Next.js 16+ — `proxy.ts` substitui `middleware.ts`. Inclui negociação de locale (next-intl) e
 * redirecionamento para `/billing/suspended` quando o cookie de bloqueio Stripe está activo (§12.3).
 */
export function proxy(request: NextRequest) {
  const pathname = request.nextUrl.pathname;

  const blocked = request.cookies.get(CEREBRO_BILLING_BLOCKED_COOKIE)?.value === "1";
  if (blocked) {
    const { locale, pathWithoutLocale } = stripLocale(pathname);
    if (!isBillingExempt(pathWithoutLocale)) {
      const url = request.nextUrl.clone();
      url.pathname =
        locale === routing.defaultLocale ? "/billing/suspended" : `/${locale}/billing/suspended`;
      return NextResponse.redirect(url);
    }
  }

  return intlMiddleware(request);
}

/**
 * Sem este matcher, o proxy corre também em `/_next/*` e os chunks/CSS devolvem 404
 * (página sem estilos). Alinhar com a documentação do next-intl.
 * @see https://next-intl.dev/docs/routing/middleware
 */
export const config = {
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
};
