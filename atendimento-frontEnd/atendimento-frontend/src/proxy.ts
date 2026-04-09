import createMiddleware from "next-intl/middleware";
import type { NextRequest } from "next/server";

import { routing } from "./i18n/routing";

const intlMiddleware = createMiddleware(routing);

/** Next.js 16+ — substitui `middleware.ts`; delega ao negociador de locale do next-intl. */
export function proxy(request: NextRequest) {
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
