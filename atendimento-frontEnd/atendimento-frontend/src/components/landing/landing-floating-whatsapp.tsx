"use client";

import { MessageCircle } from "lucide-react";
import { useTranslations } from "next-intl";

import { cn } from "@/lib/utils";

export function getLandingWhatsAppHref(): string | null {
  const raw = process.env.NEXT_PUBLIC_LANDING_WHATSAPP_PHONE?.replace(/\D/g, "") ?? "";
  if (!raw) return null;
  const prefill = process.env.NEXT_PUBLIC_LANDING_WHATSAPP_PREFILL?.trim();
  const q = prefill ? `?text=${encodeURIComponent(prefill)}` : "";
  return `https://wa.me/${raw}${q}`;
}

export function LandingFloatingWhatsApp({ className }: { className?: string }) {
  const t = useTranslations("landingPage");
  const href = getLandingWhatsAppHref();

  if (!href) return null;

  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className={cn(
        "fixed bottom-5 right-5 z-50 flex size-14 items-center justify-center rounded-full bg-[#25D366] text-white shadow-lg shadow-black/30 transition-transform hover:scale-105 hover:bg-[#20BD5A] active:scale-95 md:bottom-8 md:right-8 md:size-16",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#25D366] focus-visible:ring-offset-2 focus-visible:ring-offset-background",
        className,
      )}
      aria-label={t("floatWhatsAppAria")}
    >
      <MessageCircle className="size-7 md:size-8" strokeWidth={2} aria-hidden />
    </a>
  );
}
