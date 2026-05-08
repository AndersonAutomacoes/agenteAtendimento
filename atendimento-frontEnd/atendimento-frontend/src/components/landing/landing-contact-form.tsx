"use client";

import { useLocale, useTranslations } from "next-intl";
import * as React from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export function LandingContactForm() {
  const t = useTranslations("landingForm");
  const locale = useLocale();
  const honeypotRef = React.useRef<HTMLInputElement>(null);
  const [name, setName] = React.useState("");
  const [whatsapp, setWhatsapp] = React.useState("");
  const [consent, setConsent] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!consent) {
      toast.error(t("errorValidation"));
      return;
    }
    setSubmitting(true);
    try {
      const res = await fetch("/api/contact", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: name.trim(),
          whatsapp: whatsapp.trim(),
          locale,
          consent: true,
          company_website: honeypotRef.current?.value?.trim() ?? "",
        }),
      });
      if (res.status === 204 || res.status === 201) {
        toast.success(t("success"));
        setName("");
        setWhatsapp("");
        setConsent(false);
        return;
      }
      if (res.status === 400) {
        toast.error(t("errorValidation"));
        return;
      }
      if (res.status === 503) {
        toast.error(t("errorUnavailable"));
        return;
      }
      toast.error(t("errorGeneric"));
    } catch {
      toast.error(t("errorGeneric"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={onSubmit} className="relative space-y-4">
      <input
        ref={honeypotRef}
        type="text"
        name="company_website"
        tabIndex={-1}
        autoComplete="off"
        className="absolute left-[-10000px] size-px opacity-0"
        aria-hidden
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="landing-name">{t("name")}</Label>
          <Input
            id="landing-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoComplete="name"
            required
            maxLength={120}
          />
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="landing-wa">{t("whatsapp")}</Label>
        <Input
          id="landing-wa"
          type="tel"
          inputMode="tel"
          placeholder={t("whatsappHint")}
          value={whatsapp}
          onChange={(e) => setWhatsapp(e.target.value)}
          autoComplete="tel"
          required
          maxLength={32}
        />
        <p className="text-xs text-muted-foreground">{t("whatsappHint")}</p>
      </div>
      <label className="flex cursor-pointer items-start gap-3 text-sm text-muted-foreground">
        <input
          type="checkbox"
          checked={consent}
          onChange={(e) => setConsent(e.target.checked)}
          className="mt-1 size-4 shrink-0 rounded border-input"
          required
        />
        <span>{t("consent")}</span>
      </label>
      <Button type="submit" size="lg" className="w-full sm:w-auto" disabled={submitting}>
        {t("submitStrong")}
      </Button>
    </form>
  );
}
