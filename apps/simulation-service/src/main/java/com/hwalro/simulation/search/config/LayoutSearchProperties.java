package com.hwalro.simulation.search.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulation.layout-search")
public class LayoutSearchProperties {
    private String script = "engine/layout_search.py";
    private String workDirectory = "";
    private String surrogateMode = "SHADOW";
    private String surrogateBundle = "";
    private Duration searchTimeout = Duration.ofMinutes(5);
    private int beamWidth = 2;
    private double improvementMargin = 0.02;
    private double abortMargin = 0.15;
    private int trialConcurrency = 2;
    private boolean keepJobDirectory = false;
    private Map<String, Budget> budgets = new LinkedHashMap<>();

    public record Budget(int trials, int rounds) {}

    public Budget budget(String preset) {
        Budget budget = budgets.get(preset);
        if (budget == null) {
            throw new IllegalArgumentException("지원하지 않는 검색 예산입니다: " + preset);
        }
        return budget;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getWorkDirectory() {
        return workDirectory;
    }

    public void setWorkDirectory(String workDirectory) {
        this.workDirectory = workDirectory;
    }

    public String getSurrogateMode() {
        return surrogateMode;
    }

    public void setSurrogateMode(String surrogateMode) {
        this.surrogateMode = surrogateMode;
    }

    public String getSurrogateBundle() {
        return surrogateBundle;
    }

    public void setSurrogateBundle(String surrogateBundle) {
        this.surrogateBundle = surrogateBundle;
    }

    public Duration getSearchTimeout() {
        return searchTimeout;
    }

    public void setSearchTimeout(Duration searchTimeout) {
        this.searchTimeout = searchTimeout;
    }

    public int getBeamWidth() {
        return beamWidth;
    }

    public void setBeamWidth(int beamWidth) {
        this.beamWidth = beamWidth;
    }

    public double getImprovementMargin() {
        return improvementMargin;
    }

    public void setImprovementMargin(double improvementMargin) {
        this.improvementMargin = improvementMargin;
    }

    public double getAbortMargin() {
        return abortMargin;
    }

    public void setAbortMargin(double abortMargin) {
        this.abortMargin = abortMargin;
    }

    public int getTrialConcurrency() {
        return trialConcurrency;
    }

    public void setTrialConcurrency(int trialConcurrency) {
        this.trialConcurrency = trialConcurrency;
    }

    public boolean isKeepJobDirectory() {
        return keepJobDirectory;
    }

    public void setKeepJobDirectory(boolean keepJobDirectory) {
        this.keepJobDirectory = keepJobDirectory;
    }

    public Map<String, Budget> getBudgets() {
        return budgets;
    }

    public void setBudgets(Map<String, Budget> budgets) {
        this.budgets = budgets;
    }
}
