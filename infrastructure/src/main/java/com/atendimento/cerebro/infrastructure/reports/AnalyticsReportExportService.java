package com.atendimento.cerebro.infrastructure.reports;

import com.atendimento.cerebro.application.analytics.PrimaryIntentCategory;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCount;
import com.atendimento.cerebro.application.dto.DashboardSeriesPoint;
import com.atendimento.cerebro.application.dto.DashboardSummary;
import com.atendimento.cerebro.application.port.out.AnalyticsIntentsRepository;
import com.atendimento.cerebro.application.port.out.DashboardSummaryPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.out.persistence.JdbcAnalyticsExportRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsReportExportService {

    /** Pré-visualização da primeira mensagem na tabela do PDF (evita URLs longas). */
    private static final int PDF_FIRST_MESSAGE_PREVIEW_CHARS = 50;

    private final DashboardSummaryPort dashboardSummaryPort;
    private final AnalyticsIntentsRepository analyticsIntentsRepository;
    private final JdbcAnalyticsExportRepository exportRepository;

    public AnalyticsReportExportService(
            DashboardSummaryPort dashboardSummaryPort,
            AnalyticsIntentsRepository analyticsIntentsRepository,
            JdbcAnalyticsExportRepository exportRepository) {
        this.dashboardSummaryPort = dashboardSummaryPort;
        this.analyticsIntentsRepository = analyticsIntentsRepository;
        this.exportRepository = exportRepository;
    }

    public byte[] buildCsv(TenantId tenant, Instant start, Instant end, Locale locale) {
        ReportI18n i18n = new ReportI18n(locale);
        List<AnalyticsExportDetailRow> rows = exportRepository.listIntentRowsInRange(tenant, start, end);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(csvLine(
                i18n.get("csv.header.datetime"),
                i18n.get("csv.header.phone"),
                i18n.get("csv.header.intent"),
                i18n.get("csv.header.sentiment"),
                i18n.get("csv.header.firstMessage")));
        for (AnalyticsExportDetailRow row : rows) {
            String first = row.firstUserMessage() != null ? row.firstUserMessage() : "";
            sb.append(csvLine(
                    i18n.formatInstantFriendly(row.classifiedAt()),
                    row.phone() != null ? row.phone() : "",
                    i18n.intentLabel(row.primaryIntent()),
                    i18n.sentimentLabel(row.sentiment()),
                    first));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] buildPdf(TenantId tenant, Instant start, Instant end, Locale locale) throws DocumentException {
        ReportI18n i18n = new ReportI18n(locale);
        DashboardSummary summary = dashboardSummaryPort.loadForPeriod(tenant, start, end);
        EnumMap<PrimaryIntentCategory, Long> perCat = newZeroIntentCounts();
        for (PrimaryIntentCount row :
                analyticsIntentsRepository.countByCategoryInRange(tenant, start, end)) {
            perCat.merge(row.category(), row.count(), Long::sum);
        }
        List<AnalyticsExportDetailRow> tableRows =
                exportRepository.listLatestDistinctConversations(tenant, start, end, 20);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        writer.setPageEvent(new IntelizapPdfPageBackdrop());
        document.open();

        BaseFont baseFont = loadHelveticaWinAnsi();

        Color fg = IntelizapDashboardPdfTheme.FG;
        Color fgMuted = IntelizapDashboardPdfTheme.FG_MUTED;
        Color cyan = IntelizapDashboardPdfTheme.ACCENT_CYAN;
        Color emerald = IntelizapDashboardPdfTheme.ACCENT_EMERALD;
        Color card = IntelizapDashboardPdfTheme.CARD_BG;
        Color border = IntelizapDashboardPdfTheme.BORDER;

        Font titleFont = new Font(baseFont, 18, Font.BOLD, cyan);
        Font brandFont = new Font(baseFont, 12, Font.BOLD, emerald);
        Font sectionFont = new Font(baseFont, 12, Font.BOLD, cyan);
        Font normalFont = new Font(baseFont, 11, Font.NORMAL, fg);
        Font mutedFont = new Font(baseFont, 10, Font.NORMAL, fgMuted);
        Font tableHeaderFont = new Font(baseFont, 9, Font.BOLD, IntelizapDashboardPdfTheme.FG);

        document.add(new Paragraph(i18n.get("report.title"), titleFont));
        document.add(new Paragraph(i18n.get("report.brand"), brandFont));
        document.add(accentBarTable(38f));
        document.add(new Paragraph(
                i18n.get("report.period")
                        + ": "
                        + i18n.formatInstantFriendly(start)
                        + " — "
                        + i18n.formatInstantFriendly(end),
                mutedFont));
        document.add(new Paragraph(" ", normalFont));

        document.add(executiveSummaryBlock(i18n, summary, sectionFont, normalFont, card, border));
        document.add(new Paragraph(" ", normalFont));

        JFreeChart pie = buildPieChart(i18n, perCat);
        if (pie != null) {
            document.add(new Paragraph(i18n.get("report.chart.intents"), sectionFont));
            document.add(imageFromChart(pie, 420, 260));
            document.add(new Paragraph(" ", normalFont));
        }

        long hours = Duration.between(start, end).toHours();
        JFreeChart line = buildLineChart(i18n, summary.series(), hours);
        if (line != null) {
            document.add(new Paragraph(i18n.get("report.chart.volume"), sectionFont));
            document.add(imageFromChart(line, 520, 220));
            document.add(new Paragraph(" ", normalFont));
        }

        document.add(new Paragraph(i18n.get("report.table.title"), sectionFont));
        if (tableRows.isEmpty()) {
            document.add(new Paragraph(i18n.get("report.table.empty"), mutedFont));
        } else {
            document.add(buildTable(i18n, tableRows, normalFont, tableHeaderFont));
        }

        document.close();
        return baos.toByteArray();
    }

    private static PdfPTable accentBarTable(float widthPercent) {
        PdfPTable bar = new PdfPTable(1);
        bar.setWidthPercentage(widthPercent);
        PdfPCell barCell = new PdfPCell();
        barCell.setFixedHeight(3f);
        barCell.setBorder(Rectangle.NO_BORDER);
        barCell.setBackgroundColor(IntelizapDashboardPdfTheme.ACCENT_CYAN);
        bar.addCell(barCell);
        return bar;
    }

    private static PdfPTable executiveSummaryBlock(
            ReportI18n i18n,
            DashboardSummary summary,
            Font sectionFont,
            Font bodyFont,
            Color cardBg,
            Color borderCol)
            throws DocumentException {
        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderWidth(1f);
        cell.setBorderColor(borderCol);
        cell.setBackgroundColor(cardBg);
        cell.setPadding(14f);
        cell.addElement(new Paragraph(i18n.get("report.executive"), sectionFont));
        cell.addElement(
                new Paragraph(
                        i18n.get("report.totalClients") + ": " + summary.totalClients(), bodyFont));
        wrap.addCell(cell);
        return wrap;
    }

    private PdfPTable buildTable(
            ReportI18n i18n,
            List<AnalyticsExportDetailRow> rows,
            Font cellFont,
            Font headerFont)
            throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {2.2f, 1.8f, 1.8f, 1.5f, 3.2f});
        addHeaderCell(table, i18n.get("csv.header.datetime"), headerFont);
        addHeaderCell(table, i18n.get("csv.header.phone"), headerFont);
        addHeaderCell(table, i18n.get("csv.header.intent"), headerFont);
        addHeaderCell(table, i18n.get("csv.header.sentiment"), headerFont);
        addHeaderCell(table, i18n.get("csv.header.firstMessage"), headerFont);
        int ri = 0;
        for (AnalyticsExportDetailRow row : rows) {
            Color rowBg =
                    ri % 2 == 0
                            ? IntelizapDashboardPdfTheme.CARD_BG
                            : IntelizapDashboardPdfTheme.TABLE_ROW_ALT;
            addBodyCell(table, i18n.formatInstantFriendly(row.classifiedAt()), cellFont, rowBg);
            addBodyCell(table, nullToEmpty(row.phone()), cellFont, rowBg);
            addBodyCell(table, i18n.intentLabel(row.primaryIntent()), cellFont, rowBg);
            addBodyCell(table, i18n.sentimentLabel(row.sentiment()), cellFont, rowBg);
            addBodyCell(
                    table, truncateForPdfPreview(row.firstUserMessage()), cellFont, rowBg);
            ri++;
        }
        return table;
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(IntelizapDashboardPdfTheme.TABLE_HEADER_BG);
        c.setBorder(Rectangle.BOX);
        c.setBorderWidth(0.6f);
        c.setBorderColor(IntelizapDashboardPdfTheme.BORDER);
        c.setPadding(8f);
        table.addCell(c);
    }

    private static void addBodyCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.BOX);
        c.setBorderWidth(0.5f);
        c.setBorderColor(IntelizapDashboardPdfTheme.BORDER);
        c.setPadding(6f);
        table.addCell(c);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String truncateForPdfPreview(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.length() <= PDF_FIRST_MESSAGE_PREVIEW_CHARS) {
            return message;
        }
        return message.substring(0, PDF_FIRST_MESSAGE_PREVIEW_CHARS) + "...";
    }

    private static BaseFont loadHelveticaWinAnsi() throws DocumentException {
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (IOException e) {
            throw new DocumentException(e);
        }
    }

    private JFreeChart buildPieChart(ReportI18n i18n, EnumMap<PrimaryIntentCategory, Long> perCat) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        long sum = 0;
        for (PrimaryIntentCategory c : PrimaryIntentCategory.values()) {
            long n = perCat.get(c);
            sum += n;
            if (n > 0) {
                dataset.setValue(i18n.intentLabel(c.name()), n);
            }
        }
        if (sum == 0) {
            return null;
        }
        JFreeChart chart =
                ChartFactory.createPieChart(
                        i18n.get("report.chart.intents"), dataset, true, false, false);
        chart.setBackgroundPaint(IntelizapDashboardPdfTheme.PAGE_BG);
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(IntelizapDashboardPdfTheme.FG);
        }
        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setBackgroundPaint(IntelizapDashboardPdfTheme.CARD_BG);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelBackgroundPaint(IntelizapDashboardPdfTheme.CARD_BG);
        plot.setLabelPaint(IntelizapDashboardPdfTheme.FG);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setSectionOutlinesVisible(false);
        NumberFormat qty = NumberFormat.getIntegerInstance(i18n.locale());
        NumberFormat pct = NumberFormat.getPercentInstance(i18n.locale());
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1} ({2})", qty, pct));
        Color[] palette = IntelizapDashboardPdfTheme.PIE_SLICES;
        for (int i = 0; i < dataset.getItemCount(); i++) {
            plot.setSectionPaint(dataset.getKey(i), palette[i % palette.length]);
        }
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(IntelizapDashboardPdfTheme.PAGE_BG);
            chart.getLegend().setItemPaint(IntelizapDashboardPdfTheme.FG_MUTED);
        }
        return chart;
    }

    private JFreeChart buildLineChart(ReportI18n i18n, List<DashboardSeriesPoint> series, long hoursInRange) {
        if (series == null || series.isEmpty()) {
            return null;
        }
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        Locale loc = i18n.locale();
        DateTimeFormatter shortTime =
                DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC).withLocale(loc);
        DateTimeFormatter dayFmt =
                DateTimeFormatter.ofPattern("d MMM", loc).withZone(ZoneOffset.UTC);
        String seriesKey = i18n.get("report.chart.volume");
        for (DashboardSeriesPoint p : series) {
            Instant bucket = Instant.parse(p.bucketStart());
            String label = hoursInRange <= 48 ? shortTime.format(bucket) : dayFmt.format(bucket);
            dataset.addValue(p.count(), seriesKey, label);
        }
        JFreeChart chart =
                ChartFactory.createLineChart(
                        null,
                        null,
                        null,
                        dataset,
                        PlotOrientation.VERTICAL,
                        false,
                        false,
                        false);
        chart.setBackgroundPaint(IntelizapDashboardPdfTheme.PAGE_BG);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(IntelizapDashboardPdfTheme.CARD_BG);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinePaint(IntelizapDashboardPdfTheme.BORDER);
        plot.setRangeGridlinePaint(IntelizapDashboardPdfTheme.BORDER);
        plot.setAxisOffset(org.jfree.chart.ui.RectangleInsets.ZERO_INSETS);
        plot.getDomainAxis().setLabel(i18n.get("report.chart.axisDomain"));
        plot.getRangeAxis().setLabel(i18n.get("report.chart.axisRange"));
        plot.getDomainAxis().setLabelPaint(IntelizapDashboardPdfTheme.FG_MUTED);
        plot.getDomainAxis().setTickLabelPaint(IntelizapDashboardPdfTheme.FG_MUTED);
        plot.getDomainAxis().setAxisLinePaint(IntelizapDashboardPdfTheme.BORDER);
        plot.getRangeAxis().setLabelPaint(IntelizapDashboardPdfTheme.FG_MUTED);
        plot.getRangeAxis().setTickLabelPaint(IntelizapDashboardPdfTheme.FG_MUTED);
        plot.getRangeAxis().setAxisLinePaint(IntelizapDashboardPdfTheme.BORDER);
        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, IntelizapDashboardPdfTheme.ACCENT_CYAN);
        renderer.setSeriesStroke(0, new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShapesFilled(0, true);
        renderer.setSeriesFillPaint(0, IntelizapDashboardPdfTheme.ACCENT_EMERALD);
        renderer.setUseOutlinePaint(false);
        renderer.setDefaultOutlineStroke(new BasicStroke(0f));
        renderer.setDrawOutlines(false);
        return chart;
    }

    private Image imageFromChart(JFreeChart chart, int w, int h) throws DocumentException {
        try {
            BufferedImage bi = chart.createBufferedImage(w, h);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", png);
            Image img = Image.getInstance(png.toByteArray());
            img.scaleToFit(PageSize.A4.getWidth() - 80, h + 20);
            return img;
        } catch (Exception e) {
            throw new DocumentException(e);
        }
    }

    private static EnumMap<PrimaryIntentCategory, Long> newZeroIntentCounts() {
        EnumMap<PrimaryIntentCategory, Long> m = new EnumMap<>(PrimaryIntentCategory.class);
        for (PrimaryIntentCategory c : PrimaryIntentCategory.values()) {
            m.put(c, 0L);
        }
        return m;
    }

    private static String csvLine(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvEscape(values[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
