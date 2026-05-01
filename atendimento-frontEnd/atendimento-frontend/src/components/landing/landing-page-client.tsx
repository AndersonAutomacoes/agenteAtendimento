"use client";

import {
  Bot,
  CalendarClock,
  ChevronRight,
  LayoutDashboard,
  Sparkles,
  Users,
  Zap,
} from "lucide-react";
import { useTranslations } from "next-intl";

import { LandingContactForm } from "@/components/landing/landing-contact-form";
import { LocaleSwitcher } from "@/components/layout/locale-switcher";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

type ProofItem = { label: string; detail: string };
type Benefit = { title: string; description: string };
type Step = { title: string; description: string };
type FaqItem = { q: string; a: string };

const benefitIcons = [Bot, CalendarClock, LayoutDashboard, Users, Sparkles];

export function LandingPageClient() {
  const t = useTranslations("landingPage");
  const proofItems = t.raw("proofItems") as ProofItem[];
  const benefits = t.raw("benefits") as Benefit[];
  const howSteps = t.raw("howSteps") as Step[];
  const faqItems = t.raw("faq") as FaqItem[];
  const contactEmail = process.env.NEXT_PUBLIC_CONTACT_EMAIL?.trim();

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

      <header className="sticky top-0 z-40 border-b border-border/80 bg-background/80 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 md:px-6">
          <span className="text-lg font-semibold tracking-tight">{t("navBrand")}</span>
          <div className="flex flex-wrap items-center justify-end gap-2">
            <LocaleSwitcher />
            <ThemeToggle />
            <Button variant="ghost" size="sm" className="hidden sm:inline-flex" asChild>
              <Link href="/login">{t("navLogin")}</Link>
            </Button>
            <Button size="sm" asChild>
              <Link href="/register">{t("navRegister")}</Link>
            </Button>
          </div>
        </div>
      </header>

      <main>
        <section className="relative overflow-hidden px-4 pb-20 pt-16 md:px-6 md:pb-28 md:pt-24">
          <div
            className="pointer-events-none absolute -left-1/4 top-0 h-[420px] w-[140%] rounded-full bg-primary/15 blur-3xl md:left-1/3 md:w-[900px]"
            aria-hidden
          />
          <div className="relative mx-auto max-w-6xl">
            <p className="mb-4 inline-flex items-center rounded-full border border-border bg-card/80 px-3 py-1 text-xs font-medium text-primary">
              <Zap className="mr-2 size-3.5" aria-hidden />
              {t("heroBadge")}
            </p>
            <h1 className="max-w-4xl text-balance text-4xl font-bold tracking-tight md:text-5xl lg:text-6xl">
              {t("heroTitle")}
            </h1>
            <p className="mt-6 max-w-2xl text-pretty text-lg text-muted-foreground md:text-xl">
              {t("heroSubtitle")}
            </p>
            <div className="mt-10 flex flex-wrap gap-3">
              <Button size="lg" asChild>
                <a href="#contato">
                  {t("ctaPrimary")}
                  <ChevronRight className="ml-1 size-4" aria-hidden />
                </a>
              </Button>
              <Button size="lg" variant="outline" asChild>
                <Link href="/login">{t("ctaSecondary")}</Link>
              </Button>
            </div>
          </div>
        </section>

        <section className="border-y border-border bg-card/40 px-4 py-12 md:px-6">
          <div className="mx-auto max-w-6xl">
            <h2 className="text-center text-sm font-semibold uppercase tracking-wider text-muted-foreground">
              {t("proofTitle")}
            </h2>
            <ul className="mt-8 grid gap-6 sm:grid-cols-3">
              {proofItems.map((item) => (
                <li
                  key={item.label}
                  className="rounded-xl border border-border bg-background/80 px-5 py-4 text-center shadow-sm"
                >
                  <p className="font-semibold text-foreground">{item.label}</p>
                  <p className="mt-1 text-sm text-muted-foreground">{item.detail}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="px-4 py-16 md:px-6 md:py-24">
          <div className="mx-auto max-w-6xl">
            <div className="mx-auto max-w-2xl text-center">
              <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{t("benefitsHeading")}</h2>
              <p className="mt-4 text-muted-foreground">{t("benefitsSub")}</p>
            </div>
            <ul className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {benefits.map((b, i) => {
                const Icon = benefitIcons[i % benefitIcons.length];
                return (
                  <li key={b.title}>
                    <Card className="h-full border-border/80 bg-card/60 transition-colors hover:border-primary/40">
                      <CardHeader>
                        <div className="mb-2 flex size-10 items-center justify-center rounded-lg bg-primary/15 text-primary">
                          <Icon className="size-5" aria-hidden />
                        </div>
                        <CardTitle className="text-lg">{b.title}</CardTitle>
                        <CardDescription className="text-base leading-relaxed">
                          {b.description}
                        </CardDescription>
                      </CardHeader>
                    </Card>
                  </li>
                );
              })}
            </ul>
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
                    <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                      {step.description}
                    </p>
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
                  <p className="mt-3 border-t border-border pt-3 text-sm leading-relaxed text-muted-foreground">
                    {item.a}
                  </p>
                </details>
              ))}
            </div>
          </div>
        </section>

        <section id="contato" className="scroll-mt-20 px-4 pb-20 pt-4 md:px-6 md:pb-28">
          <div className="mx-auto max-w-6xl">
            <div className="mx-auto max-w-xl text-center">
              <h2 className="text-3xl font-bold tracking-tight md:text-4xl">{t("contactHeading")}</h2>
              <p className="mt-4 text-muted-foreground">{t("contactSub")}</p>
            </div>
            <Card className="mx-auto mt-12 max-w-xl border-primary/25 bg-card/80 shadow-lg shadow-primary/5">
              <CardHeader>
                <CardTitle className="text-xl">{t("ctaPrimary")}</CardTitle>
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
            <Link
              href="/register"
              className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
            >
              {t("footerRegister")}
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
