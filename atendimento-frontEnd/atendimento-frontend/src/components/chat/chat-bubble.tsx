"use client";

import type { ReactNode } from "react";
import { CheckCheck, CircleAlert, Clock } from "lucide-react";
import { useTranslations } from "next-intl";

import { cn } from "@/lib/utils";

type ChatBubbleProps = {
  role: "user" | "assistant";
  children: ReactNode;
  timeLabel?: string;
  layout?: "test" | "monitor";
  deliveryStatus?: "RECEIVED" | "SENT" | "ERROR";
  onRetry?: () => void;
  retryDisabled?: boolean;
};

export function ChatBubble({
  role,
  children,
  timeLabel,
  layout = "test",
  deliveryStatus,
  onRetry,
  retryDisabled = false,
}: ChatBubbleProps) {
  const t = useTranslations("chatBubble");
  const isUser = role === "user";
  const monitor = layout === "monitor";

  const rowAlign = monitor
    ? isUser
      ? "justify-start"
      : "justify-end"
    : isUser
      ? "justify-end"
      : "justify-start";

  const bubble = monitor
    ? isUser
      ? "rounded-2xl rounded-tl-md border border-border/70 bg-muted/95 text-foreground shadow-sm dark:border-border dark:bg-muted dark:text-foreground"
      : "rounded-2xl rounded-tr-md bg-gradient-to-br from-primary via-primary to-emerald-700/95 text-primary-foreground shadow-md dark:from-primary dark:via-primary dark:to-emerald-800 dark:text-primary-foreground"
    : isUser
      ? "rounded-tr-md bg-chat-user text-primary-foreground"
      : "rounded-tl-md border border-border/60 bg-chat-assistant text-foreground";

  const timeClass = monitor
    ? isUser
      ? "text-muted-foreground dark:text-muted-foreground"
      : "text-primary-foreground/95 dark:text-primary-foreground"
    : isUser
      ? "text-primary-foreground/80"
      : "text-muted-foreground";

  const showDelivery = monitor && !isUser && deliveryStatus != null;

  const statusIcon = (() => {
    if (!showDelivery) return null;
    switch (deliveryStatus) {
      case "RECEIVED":
        return (
          <Clock
            className="size-3.5 shrink-0 text-amber-500 dark:text-amber-400"
            aria-hidden
          />
        );
      case "SENT":
        return (
          <CheckCheck
            className="size-3.5 shrink-0 text-emerald-600 dark:text-emerald-400"
            aria-hidden
          />
        );
      case "ERROR":
        return (
          <CircleAlert
            className="size-3.5 shrink-0 text-red-600 dark:text-red-400"
            aria-hidden
          />
        );
      default:
        return null;
    }
  })();

  const statusTitle = (() => {
    if (!showDelivery) return undefined;
    switch (deliveryStatus) {
      case "RECEIVED":
        return t("statusReceived");
      case "SENT":
        return t("statusSent");
      case "ERROR":
        return t("statusError");
      default:
        return undefined;
    }
  })();

  return (
    <div className={cn("flex w-full", rowAlign)}>
      <div
        className={cn(
          "max-w-[min(100%,28rem)] rounded-2xl px-4 py-2.5 text-sm leading-relaxed",
          bubble,
        )}
      >
        <div className="whitespace-pre-wrap">{children}</div>
        {timeLabel || showDelivery ? (
          <div
            className={cn(
              "mt-1.5 flex items-end justify-between gap-2",
              showDelivery ? "min-h-[1.25rem]" : "",
            )}
          >
            {timeLabel ? (
              <p className={cn("text-[11px]", timeClass)}>{timeLabel}</p>
            ) : (
              <span />
            )}
            {showDelivery ? (
              <span
                className="flex shrink-0 items-center gap-1 self-end"
                title={statusTitle}
              >
                {statusIcon}
              </span>
            ) : null}
          </div>
        ) : null}
        {showDelivery && deliveryStatus === "ERROR" && onRetry ? (
          <button
            type="button"
            disabled={retryDisabled}
            onClick={onRetry}
            className={cn(
              "mt-1 text-[10px] font-medium tracking-wide text-primary-foreground/90 underline decoration-primary-foreground/40 underline-offset-2 hover:decoration-primary-foreground disabled:pointer-events-none disabled:opacity-50 dark:text-primary-foreground",
            )}
          >
            {t("retry")}
          </button>
        ) : null}
      </div>
    </div>
  );
}
