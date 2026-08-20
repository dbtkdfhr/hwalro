package com.hwalro.regulation.report.dto;

public class ReportDraftInsert {
    private Long id;
    private final Long authorId;
    private final String title;
    private final String content;
    private final String status;
    private final String generationRequest;

    public ReportDraftInsert(Long authorId, String title, String content, String status) {
        this(authorId, title, content, status, null);
    }

    public ReportDraftInsert(Long authorId, String title, String content, String status, String generationRequest) {
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.generationRequest = generationRequest;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getStatus() {
        return status;
    }

    public String getGenerationRequest() {
        return generationRequest;
    }
}
