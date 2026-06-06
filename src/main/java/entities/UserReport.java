package entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserReport {

    private String title;
    private String periodLabel;
    private String executiveSummary;
    private List<String> insights = new ArrayList<>();
    private List<String> alerts = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private String reportText;
    private boolean aiEnhanced;
    private LocalDateTime generatedAt = LocalDateTime.now();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
    }

    public List<String> getInsights() {
        return insights;
    }

    public void setInsights(List<String> insights) {
        this.insights = insights != null ? new ArrayList<>(insights) : new ArrayList<>();
    }

    public List<String> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<String> alerts) {
        this.alerts = alerts != null ? new ArrayList<>(alerts) : new ArrayList<>();
    }

    public List<String> getRecommendedActions() {
        return recommendedActions;
    }

    public void setRecommendedActions(List<String> recommendedActions) {
        this.recommendedActions = recommendedActions != null ? new ArrayList<>(recommendedActions) : new ArrayList<>();
    }

    public String getReportText() {
        return reportText;
    }

    public void setReportText(String reportText) {
        this.reportText = reportText;
    }

    public boolean isAiEnhanced() {
        return aiEnhanced;
    }

    public void setAiEnhanced(boolean aiEnhanced) {
        this.aiEnhanced = aiEnhanced;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}

