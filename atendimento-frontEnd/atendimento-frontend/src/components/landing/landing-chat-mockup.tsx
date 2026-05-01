"use client";

import { Bot } from "lucide-react";

import { cn } from "@/lib/utils";

export type ChatBubble = { role: "user" | "assistant"; text: string };

type LandingChatMockupProps = {
  className?: string;
  headerTitle: string;
  headerSub: string;
  bubbles: ChatBubble[];
};

export function LandingChatMockup({
  className,
  headerTitle,
  headerSub,
  bubbles,
}: LandingChatMockupProps) {
  return (
    <div
      className={cn(
        "relative mx-auto w-full max-w-[340px] overflow-hidden rounded-[2rem] border border-border/80 bg-gradient-to-b from-card to-background shadow-[0_24px_80px_-12px_rgba(0,0,0,0.45)] ring-1 ring-border/40 md:max-w-[380px]",
        className,
      )}
    >
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_80%_50%_at_50%_-20%,rgba(59,130,246,0.18),transparent)]"
        aria-hidden
      />
      <div className="relative border-b border-border/60 bg-primary/15 px-4 py-3 backdrop-blur-sm">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-inner shadow-black/15">
            <Bot className="size-5" aria-hidden />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-foreground">{headerTitle}</p>
            <p className="truncate text-xs text-muted-foreground">{headerSub}</p>
          </div>
          <span className="size-2 shrink-0 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]" aria-hidden />
        </div>
      </div>
      <div className="relative space-y-3 px-3 py-5 md:px-4 md:py-6">
        {bubbles.map((b, i) => (
          <div
            key={`${b.role}-${i}`}
            className={cn(
              "flex max-w-[92%]",
              b.role === "user" ? "ml-auto justify-end" : "mr-auto justify-start",
            )}
          >
            <div
              className={cn(
                "rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed shadow-md",
                b.role === "user"
                  ? "rounded-br-md bg-primary text-primary-foreground"
                  : "rounded-bl-md border border-border/80 bg-muted/90 text-foreground",
              )}
            >
              {b.text}
            </div>
          </div>
        ))}
        <div className="flex gap-1 pt-1 pl-2">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className="size-1.5 animate-pulse rounded-full bg-muted-foreground/50"
              style={{ animationDelay: `${i * 160}ms` }}
              aria-hidden
            />
          ))}
        </div>
      </div>
    </div>
  );
}
