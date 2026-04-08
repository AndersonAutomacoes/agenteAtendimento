import { cn } from "@/lib/utils";

export function ChatSkeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "max-w-[85%] space-y-2 rounded-2xl rounded-tl-md border border-border/50 bg-chat-assistant/40 px-4 py-3 shadow-sm",
        className,
      )}
    >
      <div className="h-3 w-24 animate-pulse rounded-md bg-muted-foreground/20" />
      <div className="h-3 w-full animate-pulse rounded-md bg-muted-foreground/15" />
      <div className="h-3 w-[90%] animate-pulse rounded-md bg-muted-foreground/15" />
      <div className="h-3 w-[70%] animate-pulse rounded-md bg-muted-foreground/10" />
    </div>
  );
}
