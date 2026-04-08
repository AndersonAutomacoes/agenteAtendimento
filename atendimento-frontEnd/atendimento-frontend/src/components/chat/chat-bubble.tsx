import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

type ChatBubbleProps = {
  role: "user" | "assistant";
  children: ReactNode;
  timeLabel?: string;
};

export function ChatBubble({ role, children, timeLabel }: ChatBubbleProps) {
  const isUser = role === "user";
  return (
    <div
      className={cn(
        "flex w-full",
        isUser ? "justify-end" : "justify-start",
      )}
    >
      <div
        className={cn(
          "max-w-[min(100%,28rem)] rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm",
          isUser
            ? "rounded-tr-md bg-chat-user text-primary-foreground"
            : "rounded-tl-md border border-border/60 bg-chat-assistant text-foreground",
        )}
      >
        <div className="whitespace-pre-wrap">{children}</div>
        {timeLabel ? (
          <p
            className={cn(
              "mt-1.5 text-[11px]",
              isUser ? "text-primary-foreground/80" : "text-muted-foreground",
            )}
          >
            {timeLabel}
          </p>
        ) : null}
      </div>
    </div>
  );
}
