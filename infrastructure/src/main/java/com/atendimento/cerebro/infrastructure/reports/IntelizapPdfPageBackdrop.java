package com.atendimento.cerebro.infrastructure.reports;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/** Pinta o fundo escuro em cada página (abaixo do conteúdo). */
final class IntelizapPdfPageBackdrop extends PdfPageEventHelper {

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte under = writer.getDirectContentUnder();
        Rectangle r = document.getPageSize();
        under.saveState();
        under.setColorFill(IntelizapDashboardPdfTheme.PAGE_BG);
        under.rectangle(r.getLeft(), r.getBottom(), r.getWidth(), r.getHeight());
        under.fill();
        under.restoreState();
    }
}
