import { getTranslations } from "next-intl/server";

import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";

type Props = {
  params: Promise<{ locale: string }>;
};

export default async function BillingSuccessPage({ params }: Props) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "billing" });

  return (
    <div className="flex min-h-full flex-col items-center justify-center bg-background px-4 py-12">
      <div className="w-full max-w-md space-y-4 rounded-2xl border border-border bg-card p-8 text-center shadow-lg">
        <h1 className="text-xl font-semibold">{t("successTitle")}</h1>
        <p className="text-sm text-muted-foreground leading-relaxed">{t("successBody")}</p>
        <div className="flex flex-col gap-2 pt-2">
          <Button asChild className="w-full">
            <Link href="/">{t("successCtaApp")}</Link>
          </Button>
          <Button asChild variant="outline" className="w-full">
            <Link href="/pricing">{t("successCtaPricing")}</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
