import { MetricCards } from "@/components/dashboard/metric-cards";

export default function DashboardPage() {
  return (
    <div className="space-y-8">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="max-w-2xl text-muted-foreground">
          Resumo rápido para acompanhar o uso da plataforma — valores de
          demonstração até ligar métricas reais.
        </p>
      </div>
      <MetricCards
        totalMessages={12840}
        documentsIngested={42}
        systemStatus="online"
      />
    </div>
  );
}
