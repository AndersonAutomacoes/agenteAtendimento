"use client";

import { CalendarClock, ChevronRight, Clock, Quote, TrendingUp } from "lucide-react";
import { useTranslations } from "next-intl";

import { LandingChatMockup, type ChatBubble } from "@/components/landing/landing-chat-mockup";
import { LandingContactForm } from "@/components/landing/landing-contact-form";
import { getLandingWhatsAppHref, LandingFloatingWhatsApp } from "@/components/landing/landing-floating-whatsapp";
import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

type Benefit = { title: string; description: string };
type Step = { title: string; description: string };
type FaqItem = { q: string; a: string };

const benefitIcons = [Clock, TrendingUp, CalendarClock] as const;

/** Demo externo: defina NEXT_PUBLIC_LANDING_DEMO_URL (https://...). Se vazio, o CTA secundário do Hero usa #contato. */
function getLandingDemoHref(): string | null {
  const raw = process.env.NEXT_PUBLIC_LANDING_DEMO_URL?.trim();
  if (!raw) return null;
  if (raw.startsWith("https://") || raw.startsWith("http://")) return raw;
  return null;
}

export function LandingPageClient() {
  const t = useTranslations("landingPage");
  const benefits = t.raw("benefits") as Benefit[];
  const howSteps = t.raw("howSteps") as Step[];
  const faqItems = t.raw("faq") as FaqItem[];
  const heroChatBubbles = t.raw("heroChatBubbles") as ChatBubble[];
  const contactEmail = process.env.NEXT_PUBLIC_CONTACT_EMAIL?.trim();
  const waHref = getLandingWhatsAppHref();
  const demoHref = getLandingDemoHref();

  const trialHref = waHref ?? "#contato";
  const demoTargetHref = demoHref ?? "#contato";

  return (
    <div className="relative min-h-full bg-background">
      <a
        href="#contato"
        className={cn(
          "sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50",
          "focus:rounded-lg focus:bg-primary focus:px-4 focus:py-2 focus:text-primary-foreground",
        )}
      >
        {t("skipToForm")}
      </a>

      <header className="sticky top-0 z-40 border-b border-white/5 bg-zinc-950/75 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 md:px-6">
          <span className="text-lg font-semibold tracking-tight text-white">{t("navBrand")}</span>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <LocaleSwitcher />
            <ThemeToggle />
            <Button variant="ghost" size="sm" className="hidden text-zinc-200 hover:bg-white/10 hover:text-white sm:inline-flex" asChild>
              <Link href="/login">{t("navLogin")}</Link>
            </Button>
            <Button size="sm" className="bg-white text-zinc-950 hover:bg-zinc-100" asChild>
              <Link href="/register">{t("navRegister")}</Link>
            </Button>
          </div>
        </div>
      </header>

      <main>
        <section className="relative overflow-hidden border-b border-white/5 bg-gradient-to-br from-zinc-950 via-slate-950 to-zinc-900 px-4 pb-16 pt-12 md:px-6 md:pb-24 md:pt-16">
          <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_100%_80%_at_50%_-40%,rgba(59,130,246,0.22),transparent)]" aria-hidden />
          <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_bottom,rgba(24,24,27,0.3)_0%,transparent_35%,rgba(9,9,11,0.85)_100%)]" aria-hidden />
          <div className="pointer-events-none absolute -right-20 top-1/4 size-[520px] rounded-full bg-violet-600/10 blur-[100px] md:right-0" aria-hidden />

          <div className="relative mx-auto grid max-w-6xl gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(280px,400px)] lg:items-center lg:gap-16">
            <div className="max-w-xl lg:max-w-none">
              <p className="mb-4 inline-flex items-center rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-medium text-emerald-300/90 backdrop-blur-sm">
                {t("heroBadge")}
              </p>
              <h1 className="text-balance text-3xl font-bold tracking-tight text-white md:text-4xl lg:text-[2.65rem] lg:leading-[1.12]">
                {t("heroTitle")}
              </h1>
              <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-zinc-400 md:text-lg">
                {t("heroSubtitle")}
              </p>
              <div className="mt-9 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
                <Button
                  size="lg"
                  className={cn(
                    "relative h-12 border-0 px-8 text-base font-semibold text-white shadow-xl shadow-violet-950/30 ring-2 ring-white/15",
                    "bg-gradient-to-r from-violet-600 via-emerald-500 to-teal-500",
                    "hover:from-violet-500 hover:via-emerald-400 hover:to-teal-400 hover:text-white hover:ring-white/25",
                  )}
                  asChild
                >
                  <a
                    href={trialHref}
                    target={waHref ? "_blank" : undefined}
                    rel={waHref ? "noopener noreferrer" : undefined}
                    aria-label={t("heroCtaTrialAria")}
                  >
                    {t("heroCtaTrial")}
                    <ChevronRight className="ml-1 size-5 opacity-90" aria-hidden />
                  </a>
                </Button>
                <Button
                  size="lg"
                  variant="outline"
                  className="h-12 border-white/20 bg-transparent text-white hover:bg-white/10 hover:text-white"
                  asChild
                >
                  <a
                    href={demoTargetHref}
                    target={demoHref ? "_blank" : undefined}
                    rel={demoHref ? "noopener noreferrer" : undefined}
                    aria-label={t("heroCtaDemoAria")}
                  >
                    {t("heroCtaDemo")}
                  </a>
                </Button>
              </div>
            </div>

            <div className="flex justify-center lg:justify-end">
              <LandingChatMockup
                headerTitle={t("heroChatMockHeader")}
                headerSub={t("heroChatMockSub")}
                bubbles={heroChatBubbles}
                className="lg:translate-y-2"
              />
            </div>
          </div>
        </section>

        <section className="px-4 py-14 md:px-6 md:py-20">
          <div className="mx-auto max-w-6xl">
            <div className="mx-auto max-w-2xl text-center">
              <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{t("benefitsHeading")}</h2>
              <p className="mt-4 text-muted-foreground">{t("benefitsSub")}</p>
            </div>
            <ul className="mt-10 grid grid-cols-1 gap-8 md:grid-cols-3">
              {benefits.slice(0, 3).map((b, i) => {
                const Icon = benefitIcons[i] ?? Clock;
                return (
                  <li key={b.title}>
                    <Card className="h-full border-border/80 bg-card/80 transition-colors hover:border-primary/35 hover:shadow-md hover:shadow-primary/5">
                      <CardHeader className="pb-2">
                        <div className="mb-3 flex size-12 items-center justify-center rounded-xl bg-gradient-to-br from-primary/20 to-emerald-600/15 text-primary ring-1 ring-white/10">
                          <Icon className="size-6" aria-hidden strokeWidth={1.75} />
                        </div>
                        <CardTitle className="text-lg leading-snug">{b.title}</CardTitle>
                        <CardDescription className="text-base leading-relaxed">{b.description}</CardDescription>
                      </CardHeader>
                    </Card>
                  </li>
                );
              })}
            </ul>
          </div>
        </section>

        <section className="border-y border-border bg-muted/10 px-4 py-12 md:px-6 md:py-16">
          <div className="mx-auto max-w-3xl">
            <Card className="border-primary/20 bg-card/70 shadow-sm">
              <CardContent className="flex flex-col gap-6 p-8 md:flex-row md:items-start md:gap-8 md:p-10">
                <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary">
                  <Quote className="size-6" aria-hidden />
                </div>
                <figure className="min-w-0 flex-1 space-y-4">
                  <blockquote className="text-lg font-medium leading-relaxed text-foreground md:text-xl">
                    &ldquo;{t("socialProofQuote")}&rdquo;
                  </blockquote>
                  <figcaption className="text-sm font-medium text-muted-foreground">{t("socialProofAttribution")}</figcaption>
                </figure>
              </CardContent>
            </Card>
          </div>
        </section>

        <section className="border-y border-border bg-muted/15 px-4 py-12 md:px-6 md:py-16">
          <div className="mx-auto flex max-w-6xl flex-col gap-6 rounded-2xl border border-border/80 bg-card/60 p-6 shadow-sm backdrop-blur-sm md:flex-row md:items-center md:justify-between md:p-8 lg:p-10">
            <div className="min-w-0 flex-1 space-y-2">
              <h2 className="text-xl font-bold tracking-tight md:text-2xl">{t("tryAgentHeading")}</h2>
              <p className="max-w-prose text-sm leading-relaxed text-muted-foreground md:text-base">{t("tryAgentSub")}</p>
              {!waHref ? (
                <p className="text-xs text-muted-foreground/90 md:text-sm">{t("tryAgentUnavailableHint")}</p>
              ) : null}
            </div>
            <div className="flex w-full shrink-0 flex-col gap-3 sm:flex-row sm:justify-end md:w-auto md:flex-col lg:flex-row">
              {waHref ? (
                <Button
                  size="lg"
                  className="w-full bg-[#25D366] font-semibold text-white hover:bg-[#20BD5A] sm:w-auto md:w-full lg:w-auto"
                  asChild
                >
                  <a href={waHref} target="_blank" rel="noopener noreferrer">
                    {t("tryAgentCta")}
                  </a>
                </Button>
              ) : null}
              <Button size="lg" variant={waHref ? "outline" : "default"} className="w-full sm:w-auto md:w-full lg:w-auto" asChild>
                <a href="#contato">{t("tryAgentFallbackCta")}</a>
              </Button>
            </div>
          </div>
        </section>

        <section className="border-t border-border bg-muted/20 px-4 py-16 md:px-6 md:py-24">
          <div className="mx-auto max-w-6xl">
            <div className="mx-auto max-w-2xl text-center">
              <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{t("howHeading")}</h2>
              <p className="mt-4 text-muted-foreground">{t("howSub")}</p>
            </div>
            <ol className="mt-12 grid gap-8 md:grid-cols-3">
              {howSteps.map((step, index) => (
                <li key={step.title} className="relative flex gap-4">
                  <span
                    className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary font-semibold text-primary-foreground"
                    aria-hidden
                  >
                    {index + 1}
                  </span>
                  <div>
                    <h3 className="font-semibold">{step.title}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{step.description}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="px-4 py-16 md:px-6 md:py-24">
          <div className="mx-auto max-w-3xl">
            <div className="text-center">
              <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{t("faqHeading")}</h2>
              <p className="mt-4 text-muted-foreground">{t("faqSub")}</p>
            </div>
            <div className="mt-10 space-y-3">
              {faqItems.map((item) => (
                <details
                  key={item.q}
                  className="group rounded-xl border border-border bg-card/60 px-4 py-3 [&_summary::-webkit-details-marker]:hidden"
                >
                  <summary className="cursor-pointer list-none font-medium outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring">
                    <span className="flex items-center justify-between gap-2">
                      {item.q}
                      <ChevronRight
                        className="size-4 shrink-0 transition-transform group-open:rotate-90"
                        aria-hidden
                      />
                    </span>
                  </summary>
                  <p className="mt-3 border-t border-border pt-3 text-sm leading-relaxed text-muted-foreground">{item.a}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        <section id="contato" className="scroll-mt-20 px-4 pb-28 pt-4 md:px-6 md:pb-36">
          <div className="mx-auto max-w-6xl">
            <div className="mx-auto max-w-2xl text-center">
              <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{t("contactHeading")}</h2>
              <p className="mt-4 text-muted-foreground">{t("contactSub")}</p>
              <div className="mt-8 flex w-full flex-col justify-center gap-3 sm:mx-auto sm:max-w-lg sm:flex-row sm:flex-wrap">
                <Button
                  size="lg"
                  className={cn(
                    "w-full px-6 font-semibold text-white shadow-lg ring-2 ring-primary/25 sm:flex-1",
                    "bg-gradient-to-r from-violet-600 via-emerald-600 to-teal-600 hover:from-violet-500 hover:via-emerald-500 hover:to-teal-500",
                  )}
                  asChild
                >
                  <a href="#landing-form" aria-label={t("finalCtaExpertAria")}>
                    {t("contactFormCardTitle")}
                  </a>
                </Button>
                <Button size="lg" variant="outline" className="w-full sm:flex-1" asChild>
                  <Link href="/register">{t("finalCtaRegister")}</Link>
                </Button>
              </div>
            </div>
            <Card
              id="landing-form"
              className="mx-auto mt-12 max-w-xl scroll-mt-24 border-primary/25 bg-card/80 shadow-lg shadow-primary/5"
            >
              <CardHeader>
                <CardTitle className="text-xl">{t("contactFormCardTitle")}</CardTitle>
              </CardHeader>
              <CardContent>
                <LandingContactForm />
              </CardContent>
            </Card>
          </div>
        </section>
      </main>

      <footer className="border-t border-border bg-card/30 px-4 py-10 md:px-6">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="font-semibold">{t("footerTagline")}</p>
            <p className="mt-1 text-sm text-muted-foreground">{t("footerRights")}</p>
            {contactEmail ? (
              <a
                href={`mailto:${contactEmail}`}
                className="mt-2 inline-block text-sm text-primary underline-offset-4 hover:underline"
              >
                {contactEmail}
              </a>
            ) : null}
          </div>
          <div className="flex flex-wrap gap-4 text-sm">
            <Link href="/login" className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
              {t("footerLogin")}
            </Link>
            <Link href="/register" className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
              {t("footerRegister")}
            </Link>
          </div>
        </div>
      </footer>

      <LandingFloatingWhatsApp />
    </div>
  );
}
