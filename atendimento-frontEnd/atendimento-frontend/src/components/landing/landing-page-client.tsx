"use client";

import { Brain, CalendarClock, CalendarDays, ChevronRight, Clock, Quote, Star, TrendingUp, UserCircle2 } from "lucide-react";
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
const integrations = [
  { key: "whatsapp", label: "WhatsApp", icon: MessageCircleIcon, colorClass: "group-hover:text-[#25D366]" },
  { key: "googleCalendar", label: "Google Calendar", icon: CalendarDays, colorClass: "group-hover:text-[#4285F4]" },
  { key: "postgresql", label: "PostgreSQL", icon: DatabaseIcon, colorClass: "group-hover:text-[#336791]" },
] as const;

export type LandingPageClientProps = {
  /** Resolvido no servidor (ex.: {@code LANDING_WHATSAPP_PHONE} no Docker em runtime). */
  whatsappHref?: string | null;
};

export function LandingPageClient({ whatsappHref: whatsappHrefFromServer }: LandingPageClientProps = {}) {
  const t = useTranslations("landingPage");
  const benefits = t.raw("benefits") as Benefit[];
  const howSteps = t.raw("howSteps") as Step[];
  const faqItems = t.raw("faq") as FaqItem[];
  const heroChatBubbles = t.raw("heroChatBubbles") as ChatBubble[];
  const contactEmail = process.env.NEXT_PUBLIC_CONTACT_EMAIL?.trim();
  const waHref = whatsappHrefFromServer ?? getLandingWhatsAppHref();
  const primaryCtaHref = waHref ?? "#contato";

  return (
    <div className="landing-mesh-bg dark relative min-h-full bg-background text-foreground">
      <a
        href="#contato"
        className="bg-primary text-primary-foreground shadow-md outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring absolute top-4 start-[10000px] z-[100] rounded-lg px-4 py-2 text-sm font-medium focus-visible:start-4"
      >
        {t("skipToForm")}
      </a>

      <header
        className={cn(
          "sticky top-0 z-40 border-b border-border/80 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80",
        )}
      >
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 md:px-6">
          <Link
            href="/landing"
            className="flex min-w-0 max-w-[min(100%,14rem)] items-center gap-2 rounded-lg outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring sm:max-w-none"
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary">
              <Brain className="h-5 w-5" aria-hidden />
            </span>
            <span className="truncate font-landing-brand text-lg font-semibold tracking-tight text-foreground">
              {t("navBrand")}
            </span>
          </Link>
          <div className="flex flex-wrap items-center justify-end gap-1.5 sm:gap-2">
            <LocaleSwitcher />
            <ThemeToggle />
            <Button variant="ghost" size="sm" className="touch-manipulation" asChild>
              <Link href="/login">{t("navLogin")}</Link>
            </Button>
          </div>
        </div>
      </header>

      <main
        id="landing-main-content"
        tabIndex={-1}
        className="outline-none ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        <section className="relative overflow-hidden border-b border-border/60 bg-gradient-to-br from-background via-card to-muted/30 px-4 pb-16 pt-12 md:px-6 md:pb-24 md:pt-16">
          <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_100%_80%_at_50%_-40%,rgba(59,130,246,0.22),transparent)]" aria-hidden />
          <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_bottom,rgba(24,24,27,0.3)_0%,transparent_35%,rgba(9,9,11,0.85)_100%)]" aria-hidden />
          <div className="pointer-events-none absolute -right-20 top-1/4 size-[520px] rounded-full bg-violet-600/10 blur-[100px] md:right-0" aria-hidden />

          <div className="relative mx-auto grid max-w-6xl gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(280px,400px)] lg:items-center lg:gap-16">
            <div className="max-w-xl lg:max-w-none">
              <p className="mb-4 inline-flex items-center rounded-full border border-border/80 bg-muted/40 px-3 py-1 text-xs font-medium text-success backdrop-blur-sm">
                {t("heroBadge")}
              </p>
              <h1 className="text-balance text-3xl font-bold tracking-tight text-foreground md:text-4xl lg:text-[2.65rem] lg:leading-[1.12]">
                {t("heroTitle")}
              </h1>
              <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-muted-foreground md:text-lg">
                {t("heroSubtitle")}
              </p>
              <div className="mt-9 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
                <Button
                  size="lg"
                  className={cn(
                    "relative h-12 border-0 px-8 text-base font-semibold text-white shadow-xl shadow-violet-950/30 ring-2 ring-white/15",
                    "bg-gradient-to-r from-violet-600 via-success to-teal-500",
                    "hover:from-violet-500 hover:via-success/90 hover:to-teal-400 hover:text-white hover:ring-white/25",
                  )}
                  asChild
                >
                  <a
                    href={primaryCtaHref}
                    target={waHref ? "_blank" : undefined}
                    rel={waHref ? "noopener noreferrer" : undefined}
                    aria-label={t("heroCtaTrialAria")}
                  >
                    {t("heroCtaTrial")}
                    <ChevronRight className="ml-1 size-5 opacity-90" aria-hidden />
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
                    <Card className="landing-benefit-card h-full border-border/80 bg-card/80 transition-colors hover:border-primary/35 hover:shadow-md hover:shadow-primary/5">
                      <CardHeader className="pb-2">
                        <div className="mb-3 flex size-12 items-center justify-center rounded-xl bg-gradient-to-br from-primary/20 to-success/15 text-primary ring-1 ring-border/60">
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
            <Card className="mx-auto max-w-2xl border-primary/25 bg-card/80 shadow-lg shadow-primary/10">
              <CardContent className="flex flex-col items-center gap-6 p-8 text-center md:p-10">
                <div className="flex size-16 items-center justify-center rounded-full bg-primary/15 text-primary ring-1 ring-primary/20">
                  <UserCircle2 className="size-10" aria-hidden />
                </div>
                <div className="flex items-center gap-1 text-warning" aria-label={t("socialProofStarsAria")}>
                  {Array.from({ length: 5 }).map((_, index) => (
                    <Star key={index} className="size-4 fill-current" aria-hidden />
                  ))}
                </div>
                <figure className="min-w-0 space-y-4">
                  <Quote className="mx-auto size-6 text-primary/70" aria-hidden />
                  <blockquote className="text-lg font-medium leading-relaxed text-foreground md:text-xl">
                    &ldquo;{t("socialProofQuote")}&rdquo;
                  </blockquote>
                  <figcaption className="text-sm font-medium text-muted-foreground">{t("socialProofAttribution")}</figcaption>
                </figure>
              </CardContent>
            </Card>
          </div>
        </section>

        <section className="border-b border-border bg-muted/10 px-4 py-12 md:px-6 md:py-16">
          <div className="mx-auto max-w-6xl">
            <div className="mx-auto max-w-2xl text-center">
              <h2 className="text-2xl font-bold tracking-tight md:text-3xl">{t("integrationsHeading")}</h2>
              <p className="mt-3 text-muted-foreground">{t("integrationsSub")}</p>
            </div>
            <ul className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
              {integrations.map((integration) => {
                const Icon = integration.icon;
                return (
                  <li key={integration.key}>
                    <div className="group flex h-full items-center justify-center gap-3 rounded-xl border border-border/80 bg-card/60 px-4 py-5 text-muted-foreground transition-colors hover:border-primary/35 hover:bg-card/80">
                      <Icon className={cn("size-5 transition-colors", integration.colorClass)} aria-hidden />
                      <span className={cn("font-medium transition-colors", integration.colorClass)}>{integration.label}</span>
                    </div>
                  </li>
                );
              })}
            </ul>
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
              <p className="mx-auto mt-3 max-w-lg text-pretty text-sm leading-relaxed text-muted-foreground/95 md:text-base">
                {t("contactInviteNote")}
              </p>
              <div className="mt-8 flex w-full justify-center">
                <Button
                  size="lg"
                  className="w-full max-w-md gap-1 px-6 font-semibold shadow-md touch-manipulation sm:w-auto"
                  asChild
                >
                  <a href="#landing-form" aria-label={t("finalCtaExpertAria")}>
                    {t("contactFormCardTitle")}
                    <ChevronRight className="size-5 opacity-90" aria-hidden />
                  </a>
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
          </div>
        </div>
      </footer>

      <LandingFloatingWhatsApp hrefOverride={waHref} />
    </div>
  );
}

function MessageCircleIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden>
      <path d="M12 2C6.478 2 2 6.03 2 11c0 2.145.845 4.113 2.253 5.664L3 22l5.594-1.477A10.88 10.88 0 0 0 12 21c5.522 0 10-4.03 10-10S17.522 2 12 2Zm0 17a8.7 8.7 0 0 1-3.145-.582l-.545-.207-3.318.876.84-3.243-.354-.518A7.744 7.744 0 0 1 4 11c0-4.019 3.589-7.286 8-7.286s8 3.267 8 7.286S16.411 19 12 19Z" />
    </svg>
  );
}

function DatabaseIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden>
      <path d="M12 2C7.03 2 3 3.79 3 6v12c0 2.21 4.03 4 9 4s9-1.79 9-4V6c0-2.21-4.03-4-9-4Zm0 2c4.418 0 7 .99 7 2s-2.582 2-7 2-7-.99-7-2 2.582-2 7-2Zm0 16c-4.418 0-7-.99-7-2v-2.224C6.636 16.56 9.037 17 12 17s5.364-.44 7-1.224V18c0 1.01-2.582 2-7 2Zm0-5c-4.418 0-7-.99-7-2v-2.224C6.636 11.56 9.037 12 12 12s5.364-.44 7-1.224V13c0 1.01-2.582 2-7 2Z" />
    </svg>
  );
}
