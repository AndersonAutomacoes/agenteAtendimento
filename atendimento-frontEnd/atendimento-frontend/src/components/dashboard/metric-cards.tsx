import { Activity, FileStack, MessageSquare } from "lucide-react";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { cn } from "@/lib/utils";

type MetricCardsProps = {
  totalMessages: number;
  documentsIngested: number;
  systemStatus: "online" | "degraded" | "offline";
};

const cardClass =
  "rounded-2xl border-border/70 shadow-md shadow-black/5 transition-shadow hover:shadow-lg dark:shadow-black/25";

function statusLabel(status: MetricCardsProps["systemStatus"]) {
  switch (status) {
    case "online":
      return "Operacional";
    case "degraded":
      return "Degradado";
    case "offline":
      return "Indisponível";
    default:
      return "—";
  }
}

function statusColor(status: MetricCardsProps["systemStatus"]) {
  switch (status) {
    case "online":
      return "text-emerald-500 dark:text-emerald-400";
    case "degraded":
      return "text-amber-500 dark:text-amber-400";
    case "offline":
      return "text-red-500 dark:text-red-400";
    default:
      return "text-muted-foreground";
  }
}

export function MetricCards({
  totalMessages,
  documentsIngested,
  systemStatus,
}: MetricCardsProps) {
  return (
    <div className="grid gap-5 md:grid-cols-3">
      <Card className={cn(cardClass)}>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">
            Total de mensagens
          </CardTitle>
          <MessageSquare className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-3xl font-bold tabular-nums tracking-tight">
            {totalMessages.toLocaleString("pt-BR")}
          </div>
          <CardDescription>Últimos 30 dias (mock)</CardDescription>
        </CardContent>
      </Card>
      <Card className={cn(cardClass)}>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">
            Documentos ingeridos
          </CardTitle>
          <FileStack className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-3xl font-bold tabular-nums tracking-tight">
            {documentsIngested.toLocaleString("pt-BR")}
          </div>
          <CardDescription>Na base de conhecimento (mock)</CardDescription>
        </CardContent>
      </Card>
      <Card className={cn(cardClass)}>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium">
            Status do sistema
          </CardTitle>
          <Activity className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div
            className={`text-2xl font-semibold tracking-tight ${statusColor(systemStatus)}`}
          >
            {statusLabel(systemStatus)}
          </div>
          <CardDescription>Monitorização simplificada (mock)</CardDescription>
        </CardContent>
      </Card>
    </div>
  );
}
