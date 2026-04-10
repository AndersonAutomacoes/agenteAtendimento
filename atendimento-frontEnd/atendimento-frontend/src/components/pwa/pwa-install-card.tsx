"use client";

import { Download, Share2, X } from "lucide-react";
import { useTranslations } from "next-intl";
import * as React from "react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { cn } from "@/lib/utils";

type BeforeInstallPromptOutcome = "accepted" | "dismissed";

/** Chrome/Edge/Android — evento nao padronizado no TypeScript */
interface BeforeInstallPromptEvent extends Event {
  readonly platforms: string[];
  readonly userChoice: Promise<{ outcome: BeforeInstallPromptOutcome }>;
  prompt: () => Promise<void>;
}

export function PwaInstallCard() {
  const t = useTranslations("pwa");
  const [installEvent, setInstallEvent] = React.useState<BeforeInstallPromptEvent | null>(
    null,
  );
  const [dismissed, setDismissed] = React.useState(false);
  const [iosHintOpen, setIosHintOpen] = React.useState(false);

  React.useEffect(() => {
    const onBip = (e: Event) => {
      e.preventDefault();
      setInstallEvent(e as BeforeInstallPromptEvent);
    };
    window.addEventListener("beforeinstallprompt", onBip as EventListener);
    return () => window.removeEventListener("beforeinstallprompt", onBip as EventListener);
  }, []);

  const [isStandalone, setIsStandalone] = React.useState(false);
  React.useEffect(() => {
    const mq = window.matchMedia("(display-mode: standalone)");
    const nav = navigator as Navigator & { standalone?: boolean };
    setIsStandalone(mq.matches || nav.standalone === true);
  }, []);

  const isIos = React.useMemo(() => {
    if (typeof navigator === "undefined") return false;
    return /iPad|iPhone|iPod/.test(navigator.userAgent);
  }, []);

  if (dismissed || isStandalone) {
    return null;
  }

  const runInstall = async () => {
    if (!installEvent) return;
    try {
      await installEvent.prompt();
      await installEvent.userChoice;
    } finally {
      setInstallEvent(null);
    }
  };

  return (
    <Card
      className={cn(
        "rounded-2xl border-border/80 bg-card/50 shadow-md",
        "dark:border-white/10 dark:bg-card/40",
      )}
    >
      <CardHeader className="relative pb-2">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="absolute right-2 top-2 h-8 w-8 text-muted-foreground"
          aria-label={t("dismissAria")}
          onClick={() => setDismissed(true)}
        >
          <X className="h-4 w-4" />
        </Button>
        <CardTitle className="pr-8 text-base">{t("title")}</CardTitle>
        <CardDescription>{t("subtitle")}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {installEvent ? (
          <Button
            type="button"
            className="w-full touch-manipulation rounded-xl gap-2"
            size="touch"
            onClick={() => void runInstall()}
          >
            <Download className="h-4 w-4 shrink-0" />
            {t("installCta")}
          </Button>
        ) : null}

        {isIos || iosHintOpen || !installEvent ? (
          <div className="rounded-xl border border-border/60 bg-muted/30 px-3 py-2.5 text-sm text-muted-foreground dark:bg-muted/15">
            <p className="flex items-start gap-2">
              <Share2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              <span>{isIos ? t("iosInstructions") : t("fallbackHint")}</span>
            </p>
          </div>
        ) : null}

        {!installEvent && !isIos ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="touch-manipulation"
            onClick={() => setIosHintOpen((v) => !v)}
          >
            {iosHintOpen ? t("hideManual") : t("showManual")}
          </Button>
        ) : null}
      </CardContent>
    </Card>
  );
}
