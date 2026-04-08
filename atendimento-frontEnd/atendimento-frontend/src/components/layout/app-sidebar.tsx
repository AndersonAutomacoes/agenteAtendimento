"use client";

import type { LucideIcon } from "lucide-react";
import {
  BookOpen,
  Bot,
  Brain,
  LayoutDashboard,
  MessageSquareText,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

const items: {
  href: string;
  label: string;
  sub?: string;
  icon: LucideIcon;
}[] = [
  {
    href: "/",
    label: "Dashboard",
    icon: LayoutDashboard,
  },
  {
    href: "/knowledge",
    label: "Base de Conhecimento",
    sub: "Upload",
    icon: BookOpen,
  },
  {
    href: "/test-chat",
    label: "Chat de Teste",
    icon: MessageSquareText,
  },
  {
    href: "/bot-settings",
    label: "Configurações",
    icon: Bot,
  },
];

export function AppSidebar() {
  const pathname = usePathname();

  return (
    <aside className="flex h-screen w-64 shrink-0 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground shadow-[4px_0_24px_-8px_rgba(0,0,0,0.35)]">
      <div className="flex h-16 items-center gap-2 border-b border-sidebar-border px-4">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/15 text-primary">
          <Brain className="h-5 w-5" aria-hidden />
        </div>
        <div className="min-w-0">
          <Link href="/" className="block truncate font-semibold tracking-tight">
            Cérebro
          </Link>
          <p className="truncate text-[11px] text-muted-foreground">
            Gestão IA
          </p>
        </div>
      </div>
      <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto p-3">
        {items.map(({ href, label, sub, icon: Icon }) => {
          const active =
            href === "/"
              ? pathname === "/"
              : pathname === href || pathname.startsWith(`${href}/`);
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors",
                active
                  ? "bg-primary/15 text-foreground shadow-sm ring-1 ring-primary/25"
                  : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
              )}
            >
              <Icon className="h-4 w-4 shrink-0" />
              <span className="flex min-w-0 flex-col leading-tight">
                <span>{label}</span>
                {sub ? (
                  <span className="text-[11px] font-normal text-muted-foreground">
                    {sub}
                  </span>
                ) : null}
              </span>
            </Link>
          );
        })}
      </nav>
      <Separator className="bg-sidebar-border" />
      <p className="p-4 text-xs leading-relaxed text-muted-foreground">
        Plataforma pensada para pequenas equipas — simples de usar.
      </p>
    </aside>
  );
}
