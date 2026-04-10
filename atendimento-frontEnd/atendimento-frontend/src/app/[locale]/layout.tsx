import type { Metadata, Viewport } from "next";
import { Geist_Mono, Inter } from "next/font/google";
import { hasLocale, NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";

import { PlanProvider } from "@/components/plan/plan-provider";
import { Providers } from "@/components/providers";
import { routing } from "@/i18n/routing";

import "../globals.css";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

type Props = {
  children: ReactNode;
  params: Promise<{ locale: string }>;
};

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

/** Tema PWA / barra de status alinhados ao modo escuro da app. */
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f4f6f9" },
    { media: "(prefers-color-scheme: dark)", color: "#1e2836" },
  ],
  colorScheme: "dark light",
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "meta" });
  const tPwa = await getTranslations({ locale, namespace: "pwa" });
  return {
    title: t("title"),
    description: t("description"),
    applicationName: "InteliZap",
    appleWebApp: {
      capable: true,
      title: "InteliZap",
      statusBarStyle: "black-translucent",
    },
    formatDetection: {
      telephone: false,
    },
    icons: {
      icon: [
        { url: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
        { url: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
      ],
      apple: [{ url: "/icons/apple-touch-icon.png", sizes: "180x180", type: "image/png" }],
    },
    openGraph: {
      siteName: "InteliZap",
      type: "website",
      locale,
      title: t("title"),
      description: t("description"),
    },
    twitter: {
      card: "summary",
      title: tPwa("socialTitle"),
      description: t("description"),
    },
  };
}

export default async function LocaleLayout({ children, params }: Props) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }

  setRequestLocale(locale);
  const messages = await getMessages();

  return (
    <html
      lang={locale}
      className={`${inter.variable} ${geistMono.variable} dark h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full font-sans">
        <Providers>
          <NextIntlClientProvider locale={locale} messages={messages}>
            <PlanProvider>{children}</PlanProvider>
          </NextIntlClientProvider>
        </Providers>
      </body>
    </html>
  );
}
