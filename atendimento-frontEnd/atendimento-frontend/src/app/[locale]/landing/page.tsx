import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { LandingPageClient } from "@/components/landing/landing-page-client";
import { resolveLandingWhatsAppHrefForServer } from "@/lib/landing-whatsapp";

type Props = {
  params: Promise<{ locale: string }>;
};

/** Sem isto, o Next pré-renderiza no `build` (CI sem telefone) e o `whatsappHref` fica null para sempre. */
export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "landingPage" });
  const title = t("metaTitle");
  const description = t("metaDescription");
  return {
    title,
    description,
    openGraph: {
      title,
      description,
      type: "website",
      locale,
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
    },
  };
}

export default function LandingPage() {
  const whatsappHref = resolveLandingWhatsAppHrefForServer();
  return <LandingPageClient whatsappHref={whatsappHref} />;
}
