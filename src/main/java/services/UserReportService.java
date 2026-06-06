package services;

import entities.User;
import entities.UserReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.json.JSONObject;
import services.UserService;

import java.awt.Color;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLDataException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserReportService {

    // ── Formatters ────────────────────────────────────────────────────────────
    private static final DateTimeFormatter MONTH_LABEL    = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE);
    private static final DateTimeFormatter REPORT_DATETIME= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.FRANCE);
    private static final DateTimeFormatter PILL_DATETIME  = DateTimeFormatter.ofPattern("dd MMMM yyyy  HH:mm", Locale.FRANCE);
    private static final String            LOGO_PATH      = "/views/images/Colored PNG White Logo.png";

    // ── PDF Dimensions ────────────────────────────────────────────────────────
    private static final float PW = PDRectangle.A4.getWidth();   // 595
    private static final float PH = PDRectangle.A4.getHeight();  // 842
    private static final float ML = 42f;
    private static final float MR = 42f;
    private static final float CW = PW - ML - MR;

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color C_PRIMARY       = new Color(55,  48,  212);
    private static final Color C_PRIMARY_DARK  = new Color(38,  32,  168);
    private static final Color C_PRIMARY_LIGHT = new Color(238, 240, 255);
    private static final Color C_ACCENT        = new Color(99,  102, 241);
    private static final Color C_DARK          = new Color(15,  23,  42);
    private static final Color C_MUTED         = new Color(100, 116, 139);
    private static final Color C_LIGHT         = new Color(248, 250, 252);
    private static final Color C_BORDER        = new Color(226, 232, 240);
    private static final Color C_SUCCESS       = new Color(16,  185, 129);
    private static final Color C_WARNING       = new Color(245, 158, 11);
    private static final Color C_DANGER        = new Color(239, 68,  68);
    private static final Color C_WHITE         = Color.WHITE;

    // ── Legacy colors (kept for PdfCursor header) ─────────────────────────────
    private static final Color COLOR_PRIMARY      = new Color(55, 48, 212);
    private static final Color COLOR_PRIMARY_SOFT = new Color(91, 86, 232);
    private static final Color COLOR_TEXT_DARK    = new Color(31, 46, 69);
    private static final Color COLOR_TEXT_MUTED   = new Color(99, 115, 140);
    private static final Color COLOR_BORDER       = new Color(210, 222, 240);

    // ── Services ──────────────────────────────────────────────────────────────
    private final UserService userService;
    private final services.GroqAiService groqAiService;

    public UserReportService() {
        this(new UserService(), new services.GroqAiService());
    }

    public UserReportService(UserService userService, services.GroqAiService groqAiService) {
        this.userService   = userService;
        this.groqAiService = groqAiService;
    }

    public UserReport generateActiveUsersMonthlyReport() throws SQLDataException {
        if (userService == null) throw new IllegalStateException("UserService n'est pas disponible.");
        List<User> users = userService.getAll();
        return generateActiveUsersMonthlyReport(users);
    }

    public UserReport generateActiveUsersMonthlyReportFromSnapshot(List<User> users) {
        return generateActiveUsersMonthlyReport(users);
    }

    UserReport generateActiveUsersMonthlyReport(List<User> users) {
        YearMonth     currentMonth = YearMonth.now();
        ReportMetrics metrics      = computeMetrics(users, currentMonth);

        UserReport report = new UserReport();
        report.setGeneratedAt(LocalDateTime.now());
        report.setPeriodLabel(capitalize(currentMonth.format(MONTH_LABEL)));
        report.setTitle("Resumé des utilisateurs actifs - " + report.getPeriodLabel());

        String localSummary = buildLocalSummary(metrics, report.getPeriodLabel());
        report.setExecutiveSummary(localSummary);
        report.setInsights(buildLocalInsights(metrics));
        report.setAlerts(buildLocalAlerts(metrics));
        report.setRecommendedActions(buildLocalActions(metrics));
        report.setAiEnhanced(false);

        try {
            if (groqAiService == null) throw new IllegalStateException("GroqAiService indisponible");
            JSONObject context = buildAiContext(metrics, report.getPeriodLabel());
            GroqAiService.AiReport aiReport = groqAiService.generateUserReport(
                    "Resumé des utilisateurs actifs ce mois", context.toString());

            if (aiReport != null && !isBlank(aiReport.executiveSummary)) {
                if (!isBlank(aiReport.title))              report.setTitle(aiReport.title);
                report.setExecutiveSummary(aiReport.executiveSummary);
                if (!aiReport.insights.isEmpty())          report.setInsights(aiReport.insights);
                if (!aiReport.alerts.isEmpty())            report.setAlerts(aiReport.alerts);
                if (!aiReport.recommendedActions.isEmpty())report.setRecommendedActions(aiReport.recommendedActions);
                report.setAiEnhanced(true);
            }
        } catch (Exception ignored) {
            // Rapport local conservé si l'IA est indisponible.
        }

        report.setReportText(formatReportAsText(report, metrics));
        return report;
    }


    public Path exportReportAsText(UserReport report, Path outputFile) throws Exception {
        if (report == null || outputFile == null)
            throw new IllegalArgumentException("Rapport ou chemin de sortie invalide.");
        if (outputFile.getParent() != null) Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, report.getReportText(), StandardCharsets.UTF_8);
        return outputFile;
    }


    public Path exportReportAsPdf(UserReport report, Path outputFile) throws Exception {
        if (report == null || outputFile == null)
            throw new IllegalArgumentException("Rapport ou chemin de sortie invalide.");
        if (outputFile.getParent() != null) Files.createDirectories(outputFile.getParent());

        try (PDDocument doc = new PDDocument()) {
            PDImageXObject logo = loadLogoImage(doc);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PH;

                y = pdfDrawHeader(cs, logo, report.getTitle());
                y = pdfDrawTitleBlock(cs, report, y);
                y = pdfDrawSectionTitle(cs, "Résumé exécutif", y);
                y = pdfDrawSummaryBox(cs, report.getExecutiveSummary(), y);
                y = pdfDrawSectionTitle(cs, "Insights & Alertes", y);
                y = pdfDrawTwoColumns(cs, report.getInsights(), report.getAlerts(), y);
                y = pdfDrawSectionTitle(cs, "Actions recommandées", y);
                pdfDrawActionTable(cs, report.getRecommendedActions(), y);

                pdfDrawFooter(cs, 1);
            }

            doc.save(outputFile.toFile());
        }
        return outputFile;
    }


    private float pdfDrawHeader(PDPageContentStream cs, PDImageXObject logo, String subtitle) throws Exception {
        float bandH = 68f;

        // Dégradé simulé via deux rectangles adjacents
        pdfFill(cs, C_PRIMARY_DARK, 0,       PH - bandH, PW / 2f, bandH);
        pdfFill(cs, C_PRIMARY,      PW / 2f, PH - bandH, PW / 2f, bandH);

        // Bande accent
        pdfFill(cs, C_ACCENT, 0, PH - bandH - 3f, PW, 3f);

        // Logo
        if (logo != null) cs.drawImage(logo, ML, PH - bandH + 15f, 50f, 50f);

        // Nom société + label
        pdfText(cs, PDType1Font.HELVETICA_BOLD, 14f, C_WHITE,
                ML + 60f, PH - bandH + 44f, "ARTIUM");
        pdfText(cs, PDType1Font.HELVETICA, 8.5f, new Color(197, 210, 254),
                ML + 60f, PH - bandH + 28f, "RAPPORT UTILISATEURS");

        // Sous-titre + confidentiel (côté droit)
        pdfTextRight(cs, PDType1Font.HELVETICA, 8.5f, new Color(165, 180, 252),
                PW - MR, PH - bandH + 44f, pdfSafe(subtitle));
        pdfTextRight(cs, PDType1Font.HELVETICA_BOLD, 7f, new Color(165, 180, 252),
                PW - MR, PH - bandH + 28f, "CONFIDENTIEL");

        return PH - bandH - 16f;
    }


    private float pdfDrawTitleBlock(PDPageContentStream cs, UserReport r, float y) throws Exception {
        pdfText(cs, PDType1Font.HELVETICA_BOLD, 19f, C_DARK, ML, y - 24f, pdfSafe(safe(r.getTitle())));

        y -= 32f;
        pdfLine(cs, C_BORDER, ML, y, PW - MR, y, 0.7f);
        y -= 8f;

        String dateStr = r.getGeneratedAt() != null ? PILL_DATETIME.format(r.getGeneratedAt()) : "N/A";
        String mode    = r.isAiEnhanced() ? "IA activee" : "Local";

        float px = ML;
        px = pdfDrawPill(cs, "Periode", safe(r.getPeriodLabel()), px, y, C_PRIMARY_LIGHT, C_PRIMARY);
        px += 7f;
        px = pdfDrawPill(cs, "Generé",  dateStr,                  px, y, C_LIGHT,         C_MUTED);
        px += 7f;
        pdfDrawPill(cs, "Mode",    mode,                     px, y,
                r.isAiEnhanced() ? new Color(236, 253, 245) : C_LIGHT,
                r.isAiEnhanced() ? C_SUCCESS                : C_MUTED);

        return y - 26f;
    }

    private float pdfDrawPill(PDPageContentStream cs, String label, String value,
                              float x, float y, Color bg, Color fg) throws Exception {
        float lw = pdfTextWidth(PDType1Font.HELVETICA_BOLD, 7f,   label + ": ");
        float vw = pdfTextWidth(PDType1Font.HELVETICA,      8.5f, value);
        float pw = lw + vw + 18f;
        float ph = 15f;

        pdfFill(cs, bg, x, y - ph + 4f, pw, ph);
        pdfText(cs, PDType1Font.HELVETICA_BOLD, 7f,   C_MUTED, x + 6f,      y - 4f, label + ": ");
        pdfText(cs, PDType1Font.HELVETICA,      8.5f, fg,      x + 6f + lw, y - 4f, value);
        return x + pw;
    }

    private float pdfDrawSectionTitle(PDPageContentStream cs, String title, float y) throws Exception {
        y -= 14f;
        float barH = 17f;
        pdfFill(cs, C_PRIMARY,       ML,        y - barH + 4f, 3.5f,      barH);
        pdfFill(cs, C_PRIMARY_LIGHT, ML + 3.5f, y - barH + 4f, CW - 3.5f, barH);
        pdfText(cs, PDType1Font.HELVETICA_BOLD, 10.5f, C_PRIMARY, ML + 9f, y - 4f, title.toUpperCase());
        return y - barH - 5f;
    }

    private float pdfDrawSummaryBox(PDPageContentStream cs, String rawText, float y) throws Exception {
        List<String> lines = wrapText(safe(rawText), 95);
        float boxH = lines.size() * 13.5f + 18f;

        pdfFill(cs, C_PRIMARY_LIGHT, ML, y - boxH, CW, boxH);
        pdfStrokeRect(cs, C_PRIMARY, ML, y - boxH, CW, boxH, 0.8f);
        pdfFill(cs, C_PRIMARY, ML, y - boxH, 3.5f, boxH); // bande accent gauche

        float ty = y - 14f;
        for (String line : lines) {
            pdfText(cs, PDType1Font.HELVETICA, 9.5f, C_DARK, ML + 12f, ty, line);
            ty -= 13.5f;
        }
        return y - boxH - 10f;
    }

    private float pdfDrawTwoColumns(PDPageContentStream cs,
                                    List<String> insights, List<String> alerts,
                                    float y) throws Exception {
        float colW     = (CW - 8f) / 2f;
        int   maxChars = 42; // caractères max par ligne dans chaque colonne

        // Calculer la hauteur dynamiquement selon le contenu wrappé
        int insightLines = countWrappedLines(insights, maxChars);
        int alertLines   = countWrappedLines(alerts,   maxChars);
        int maxLines     = Math.max(insightLines, alertLines);
        float colH = Math.max(maxLines * 14f + 34f, 50f);

        pdfDrawListColumn(cs, "Insights", insights, ML,             y, colW, colH, C_PRIMARY, C_SUCCESS, maxChars);
        pdfDrawListColumn(cs, "Alertes",  alerts,   ML + colW + 8f, y, colW, colH, C_DANGER,  C_DANGER,  maxChars);

        return y - colH - 10f;
    }

    private int countWrappedLines(List<String> items, int maxChars) {
        if (items == null || items.isEmpty()) return 1;
        int total = 0;
        for (String item : items) {
            total += wrapText(safe(item), maxChars).size();
        }
        return total;
    }

    private void pdfDrawListColumn(PDPageContentStream cs, String title, List<String> items,
                                   float x, float y, float w, float h,
                                   Color titleColor, Color bulletColor, int maxChars) throws Exception {
        pdfFill(cs, C_LIGHT,  x, y - h, w, h);
        pdfStrokeRect(cs, C_BORDER, x, y - h, w, h, 0.5f);

        // Barre de titre colorée
        pdfFill(cs, titleColor, x, y - 20f, w, 20f);
        pdfText(cs, PDType1Font.HELVETICA_BOLD, 9.5f, C_WHITE, x + 8f, y - 13f, title);

        float ty = y - 34f;
        List<String> safeItems = (items == null || items.isEmpty()) ? List.of("Aucune") : items;
        for (String item : safeItems) {
            List<String> wrappedLines = wrapText(safe(item), maxChars);
            for (int li = 0; li < wrappedLines.size(); li++) {
                if (li == 0) {
                    // Bullet uniquement sur la première ligne
                    pdfFill(cs, bulletColor, x + 8f, ty + 3f, 4f, 4f);
                    pdfText(cs, PDType1Font.HELVETICA, 8.5f, C_DARK, x + 16f, ty + 1f, wrappedLines.get(0));
                } else {
                    // Lignes suivantes indentées, sans bullet
                    pdfText(cs, PDType1Font.HELVETICA, 8.5f, C_DARK, x + 16f, ty + 1f, wrappedLines.get(li));
                }
                ty -= 14f;
            }
        }
    }

    private void pdfDrawActionTable(PDPageContentStream cs, List<String> actions, float y) throws Exception {
        if (actions == null || actions.isEmpty()) return;

        int    maxChars = 80; // caractères max par ligne pour les actions
        float  rowH     = 22f;
        float  lineH    = 13f;

        Color[]  badges   = {C_DANGER, C_WARNING, C_PRIMARY};
        String[] priority = {"Priorite haute", "Priorite haute", "Priorite moyenne"};

        // Pré-calculer les lignes wrappées pour chaque action
        List<List<String>> wrappedActions = new ArrayList<>();
        for (String action : actions) {
            wrappedActions.add(wrapText(safe(action), maxChars));
        }

        // Calculer la hauteur totale du tableau
        float tableH = 0f;
        for (List<String> lines : wrappedActions) {
            tableH += Math.max(rowH, lines.size() * lineH + 8f);
        }

        pdfStrokeRect(cs, C_BORDER, ML, y - tableH, CW, tableH, 0.5f);

        float ry = y;
        for (int i = 0; i < actions.size(); i++) {
            List<String> lines     = wrappedActions.get(i);
            float        actualRowH = Math.max(rowH, lines.size() * lineH + 8f);
            Color        rowBg      = (i % 2 == 0) ? C_LIGHT : C_WHITE;

            pdfFill(cs, rowBg, ML + 0.5f, ry - actualRowH + 0.5f, CW - 1f, actualRowH - 1f);

            // Badge numéroté
            Color badge = i < badges.length ? badges[i] : C_MUTED;
            pdfFill(cs, badge, ML + 6f, ry - actualRowH / 2f - 7f, 16f, 14f);
            pdfText(cs, PDType1Font.HELVETICA_BOLD, 8f, C_WHITE,
                    ML + 11f, ry - actualRowH / 2f - 2f, String.valueOf(i + 1));

            // Texte de l'action (multi-ligne)
            float textY = ry - lineH;
            for (String line : lines) {
                pdfText(cs, PDType1Font.HELVETICA, 9f, C_DARK, ML + 28f, textY, line);
                textY -= lineH;
            }

            // Tag de priorité
            String pLabel = i < priority.length ? priority[i] : "Normal";
            Color  pColor = pLabel.contains("haute") ? C_DANGER : C_WARNING;
            float  tagW   = 72f;
            pdfFill(cs, new Color(pColor.getRed(), pColor.getGreen(), pColor.getBlue(), 28),
                    PW - MR - tagW - 2f, ry - actualRowH / 2f - 6f, tagW, 12f);
            pdfText(cs, PDType1Font.HELVETICA_BOLD, 7f, pColor,
                    PW - MR - tagW + 4f, ry - actualRowH / 2f - 1f, pLabel);

            // Séparateur de ligne
            if (i < actions.size() - 1)
                pdfLine(cs, C_BORDER, ML, ry - actualRowH, PW - MR, ry - actualRowH, 0.3f);

            ry -= actualRowH;
        }
    }

    /** Footer : bande grise + texte confidentiel + badge de pagination */
    private void pdfDrawFooter(PDPageContentStream cs, int pageNum) throws Exception {
        pdfFill(cs, C_LIGHT, 0, 0, PW, 26f);
        pdfLine(cs, C_BORDER, 0, 26f, PW, 26f, 0.5f);
        pdfText(cs, PDType1Font.HELVETICA, 7.5f, C_MUTED,
                ML, 9f, "2026 ARTIUM - Confidentiel, usage interne uniquement");
        pdfFill(cs, C_PRIMARY, PW - MR - 18f, 5f, 22f, 16f);
        pdfText(cs, PDType1Font.HELVETICA_BOLD, 8f, C_WHITE, PW - MR - 11f, 9f, String.valueOf(pageNum));
    }


    private void pdfFill(PDPageContentStream cs, Color c, float x, float y, float w, float h) throws Exception {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void pdfStrokeRect(PDPageContentStream cs, Color c,
                               float x, float y, float w, float h, float lw) throws Exception {
        cs.setStrokingColor(c);
        cs.setLineWidth(lw);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private void pdfLine(PDPageContentStream cs, Color c,
                         float x1, float y1, float x2, float y2, float lw) throws Exception {
        cs.setStrokingColor(c);
        cs.setLineWidth(lw);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void pdfText(PDPageContentStream cs, PDType1Font font, float size,
                         Color c, float x, float y, String s) throws Exception {
        if (s == null || s.isBlank()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(c);
        cs.newLineAtOffset(x, y);
        cs.showText(pdfSafe(s));
        cs.endText();
    }

    private void pdfTextRight(PDPageContentStream cs, PDType1Font font, float size,
                              Color c, float rightX, float y, String s) throws Exception {
        pdfText(cs, font, size, c, rightX - pdfTextWidth(font, size, s), y, s);
    }

    private float pdfTextWidth(PDType1Font font, float size, String s) {
        try { return font.getStringWidth(pdfSafe(s)) / 1000f * size; }
        catch (Exception e) { return s.length() * size * 0.5f; }
    }


    private ReportMetrics computeMetrics(List<User> users, YearMonth month) {
        List<User> safeUsers = users == null ? List.of() : users;

        int totalUsers        = safeUsers.size();
        int blockedUsers      = 0;
        int activeUsers       = 0;
        int newUsersThisMonth = 0;

        Map<String, Integer> roleDistribution = new LinkedHashMap<>();
        roleDistribution.put("Admin",   0);
        roleDistribution.put("Artiste", 0);
        roleDistribution.put("Amateur", 0);

        for (User user : safeUsers) {
            String role = normalizeRoleLabel(user.getRole());
            roleDistribution.put(role, roleDistribution.getOrDefault(role, 0) + 1);

            if (isBlocked(user.getStatut())) blockedUsers++;
            else                             activeUsers++;

            LocalDate dateInscription = user.getDateInscription();
            if (dateInscription != null && YearMonth.from(dateInscription).equals(month))
                newUsersThisMonth++;
        }

        return new ReportMetrics(totalUsers, activeUsers, blockedUsers, newUsersThisMonth, roleDistribution);
    }

    private JSONObject buildAiContext(ReportMetrics metrics, String periodLabel) {
        JSONObject context = new JSONObject();
        context.put("period",           periodLabel);
        context.put("totalUsers",       metrics.totalUsers);
        context.put("activeUsers",      metrics.activeUsers);
        context.put("blockedUsers",     metrics.blockedUsers);
        context.put("newUsersThisMonth",metrics.newUsersThisMonth);
        context.put("activeRate",       metrics.activeRatePercent());
        context.put("roleDistribution", metrics.roleDistribution);
        return context;
    }

    private String buildLocalSummary(ReportMetrics metrics, String periodLabel) {
        return String.format(Locale.FRANCE,
                "Pour %s, %d utilisateurs sur %d sont actifs (%.1f%%). %d nouveaux comptes ont ete crees ce mois et %d comptes sont bloqués.",
                periodLabel, metrics.activeUsers, metrics.totalUsers,
                metrics.activeRatePercent(), metrics.newUsersThisMonth, metrics.blockedUsers);
    }

    private List<String> buildLocalInsights(ReportMetrics metrics) {
        List<String> insights = new ArrayList<>();
        insights.add("Taux d'activité global: " + String.format(Locale.FRANCE, "%.1f%%", metrics.activeRatePercent()));
        insights.add("Nouveaux comptes ce mois: " + metrics.newUsersThisMonth);
        insights.add("Répartition des roles: " + formatRoleDistribution(metrics.roleDistribution));
        return insights;
    }

    private List<String> buildLocalAlerts(ReportMetrics metrics) {
        List<String> alerts = new ArrayList<>();
        if (metrics.totalUsers == 0) {
            alerts.add("Aucun utilisateur n'a été trouvé dans la base.");
            return alerts;
        }
        double blockedRate = metrics.blockedRatePercent();
        if (blockedRate >= 30.0)
            alerts.add("Le taux de comptes bloqués est elevé (" + String.format(Locale.FRANCE, "%.1f%%", blockedRate) + ").");
        if (metrics.newUsersThisMonth == 0)
            alerts.add("Aucune nouvelle inscription sur la période en cours.");
        return alerts;
    }

    private List<String> buildLocalActions(ReportMetrics metrics) {
        List<String> actions = new ArrayList<>();
        if (metrics.blockedUsers > 0)
            actions.add("Vérifier les comptes bloqués et activer les profils legitimes.");
        actions.add("Lancer une campagne de reactivation pour les utilisateurs inactifs.");
        if (metrics.newUsersThisMonth < 5)
            actions.add("Booster l'acquisition (reseaux sociaux, partenariat, contenu).");
        return actions;
    }

    private String formatReportAsText(UserReport report, ReportMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append(report.getTitle()).append('\n');
        sb.append("Géneré le: ").append(report.getGeneratedAt()).append('\n');
        sb.append("Periode: ").append(report.getPeriodLabel()).append('\n');
        sb.append("Mode: ").append(report.isAiEnhanced() ? "IA" : "Fallback local").append("\n\n");

        sb.append("Resumé executif\n");
        sb.append(report.getExecutiveSummary()).append("\n\n");

        sb.append("Indicateurs cles\n");
        sb.append("- Total utilisateurs: ").append(metrics.totalUsers).append('\n');
        sb.append("- Utilisateurs actifs: ").append(metrics.activeUsers).append('\n');
        sb.append("- Utilisateurs bloqués: ").append(metrics.blockedUsers).append('\n');
        sb.append("- Nouveaux ce mois: ").append(metrics.newUsersThisMonth).append('\n');
        sb.append("- Répartition roles: ").append(formatRoleDistribution(metrics.roleDistribution)).append("\n\n");

        appendSection(sb, "Insights",             report.getInsights());
        appendSection(sb, "Alertes",              report.getAlerts());
        appendSection(sb, "Actions recommandées", report.getRecommendedActions());
        return sb.toString();
    }


    private void appendSection(StringBuilder sb, String title, List<String> lines) {
        sb.append(title).append('\n');
        if (lines == null || lines.isEmpty()) { sb.append("- Aucune\n\n"); return; }
        for (String line : lines) sb.append("- ").append(line).append('\n');
        sb.append('\n');
    }

    private String formatRoleDistribution(Map<String, Integer> roleDistribution) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : roleDistribution.entrySet())
            parts.add(e.getKey() + ": " + e.getValue());
        return String.join(" | ", parts);
    }

    private String normalizeRoleLabel(String role) {
        if (isBlank(role)) return "Autre";
        String v = role.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(v))   return "Admin";
        if ("artiste".equals(v)) return "Artiste";
        if ("amateur".equals(v)) return "Amateur";
        return "Autre";
    }

    private boolean isBlocked(String status) {
        if (isBlank(status)) return false;
        String n = Normalizer.normalize(status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
        return "bloque".equals(n) || "blocked".equals(n);
    }

    private String capitalize(String value) {
        if (isBlank(value)) return "";
        return value.substring(0, 1).toUpperCase(Locale.FRANCE) + value.substring(1);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s.trim();
    }

    private List<String> wrapText(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) { result.add(""); return result; }
        for (String para : text.split("\\R", -1)) {
            if (para.isBlank()) { result.add(""); continue; }
            StringBuilder sb = new StringBuilder();
            for (String word : para.split("\\s+")) {
                if (sb.isEmpty()) { sb.append(word); continue; }
                if (sb.length() + 1 + word.length() <= maxChars) sb.append(' ').append(word);
                else { result.add(sb.toString()); sb.setLength(0); sb.append(word); }
            }
            if (!sb.isEmpty()) result.add(sb.toString());
        }
        return result;
    }

    private String pdfSafe(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private PDImageXObject loadLogoImage(PDDocument document) {
        try (InputStream in = UserReportService.class.getResourceAsStream(LOGO_PATH)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) return null;
            return PDImageXObject.createFromByteArray(document, bytes, "logo2.png");
        } catch (Exception ignored) { return null; }
    }


    private static final class ReportMetrics {
        private final int                 totalUsers;
        private final int                 activeUsers;
        private final int                 blockedUsers;
        private final int                 newUsersThisMonth;
        private final Map<String,Integer> roleDistribution;

        private ReportMetrics(int totalUsers, int activeUsers, int blockedUsers,
                              int newUsersThisMonth, Map<String, Integer> roleDistribution) {
            this.totalUsers        = totalUsers;
            this.activeUsers       = activeUsers;
            this.blockedUsers      = blockedUsers;
            this.newUsersThisMonth = newUsersThisMonth;
            this.roleDistribution  = roleDistribution;
        }

        private double activeRatePercent() {
            return totalUsers <= 0 ? 0.0 : (activeUsers * 100.0) / totalUsers;
        }

        private double blockedRatePercent() {
            return totalUsers <= 0 ? 0.0 : (blockedUsers * 100.0) / totalUsers;
        }
    }
}