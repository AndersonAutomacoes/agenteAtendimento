"use client";

import * as React from "react";

/** Sincroniza `<html lang>` com o locale da rota (root layout não recebe `[locale]`). */
export function HtmlLang({ locale }: { locale: string }) {
  React.useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);
  return null;
}
