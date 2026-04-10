/** Preset alinhado ao backend (UTC): 24h rolante, 7 dias (início dia UTC−6), mês civil (dia 1 UTC). */

export type DashboardPeriodPreset = "today" | "last7" | "thisMonth" | "custom";

export type DashboardPeriodRange = {
  startDate: string;
  endDate: string;
};

export function computeDashboardPeriodRange(
  preset: DashboardPeriodPreset,
  custom?: { from: string; to: string } | null,
): DashboardPeriodRange | null {
  const end = new Date();
  const endIso = end.toISOString();

  if (preset === "custom") {
    if (!custom?.from?.trim() || !custom?.to?.trim()) return null;
    const s = new Date(custom.from);
    const e = new Date(custom.to);
    if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime()) || e.getTime() <= s.getTime()) {
      return null;
    }
    return { startDate: s.toISOString(), endDate: e.toISOString() };
  }

  if (preset === "today") {
    const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
    return { startDate: start.toISOString(), endDate: endIso };
  }

  if (preset === "last7") {
    const todayUtcMidnight = Date.UTC(
      end.getUTCFullYear(),
      end.getUTCMonth(),
      end.getUTCDate(),
    );
    const startMs = todayUtcMidnight - 6 * 86400 * 1000;
    return { startDate: new Date(startMs).toISOString(), endDate: endIso };
  }

  // thisMonth — primeiro dia do mês civil UTC
  const startMs = Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), 1);
  return { startDate: new Date(startMs).toISOString(), endDate: endIso };
}

export function periodSpanHours(startIso: string, endIso: string): number {
  const a = new Date(startIso).getTime();
  const b = new Date(endIso).getTime();
  return Math.max(0, (b - a) / (3600 * 1000));
}
