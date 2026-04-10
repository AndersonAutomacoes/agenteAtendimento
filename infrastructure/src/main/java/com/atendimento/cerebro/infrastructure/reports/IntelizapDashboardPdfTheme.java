package com.atendimento.cerebro.infrastructure.reports;

import java.awt.Color;

/**
 * Paleta alinhada ao Dashboard web em modo escuro ({@code globals.css} .dark) e às cores dos gráficos
 * (ciano #22d3ee, esmeralda #34d399, etc.) — identidade InteliZap / Cérebro.
 */
public final class IntelizapDashboardPdfTheme {

    /** Fundo da página — ~oklch(0.19 0.02 260). */
    public static final Color PAGE_BG = new Color(0x14, 0x16, 0x1f);

    /** Superfície “card” — ~oklch(0.24 0.025 260). */
    public static final Color CARD_BG = new Color(0x22, 0x25, 0x2e);

    /** Texto principal — ~foreground dark. */
    public static final Color FG = new Color(0xf4, 0xf4, 0xf7);

    /** Texto secundário — ~muted-foreground. */
    public static final Color FG_MUTED = new Color(0x9c, 0xa3, 0xb8);

    /** Acento primário (gráficos / títulos) — cyan-400 do dashboard. */
    public static final Color ACCENT_CYAN = new Color(0x22, 0xd3, 0xee);

    /** Acento secundário — emerald-400. */
    public static final Color ACCENT_EMERALD = new Color(0x34, 0xd3, 0x99);

    /** Bordas — ~border dark. */
    public static final Color BORDER = new Color(0x3d, 0x45, 0x54);

    /** Cabeçalho de tabela. */
    public static final Color TABLE_HEADER_BG = new Color(0x1a, 0x1f, 0x2e);

    /** Linha alternada (zebra). */
    public static final Color TABLE_ROW_ALT = new Color(0x19, 0x1c, 0x26);

    /** Mesma sequência que {@code PIE_COLORS} no dashboard-panel (Recharts). */
    public static final Color[] PIE_SLICES = {
        ACCENT_CYAN,
        ACCENT_EMERALD,
        new Color(0xa7, 0x8b, 0xfa),
        new Color(0xfb, 0x71, 0x85),
        new Color(0xfb, 0xbf, 0x24),
    };

    private IntelizapDashboardPdfTheme() {}
}
