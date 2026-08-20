package com.hwalro.regulation.report.ai;

import com.hwalro.regulation.report.ai.ReportPromptFactory.Prompt;
import com.hwalro.regulation.report.dto.ReportContent;
import com.hwalro.regulation.report.exception.InvalidReportDraftException;
import com.hwalro.regulation.report.exception.ReportDraftGenerationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("ai")
public class OpenAiReportDraftGenerator implements ReportDraftGenerator {
    private static final String FAILURE_MESSAGE = "AI 보고서 초안을 생성할 수 없습니다.";

    private final ReportPromptFactory promptFactory;
    private final Completion completion;

    @Autowired
    public OpenAiReportDraftGenerator(ReportPromptFactory promptFactory, ChatClient.Builder chatClientBuilder) {
        this.promptFactory = promptFactory;
        ChatClient chatClient = chatClientBuilder.build();
        this.completion = prompt -> chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .call()
                .entity(ReportContent.class);
    }

    OpenAiReportDraftGenerator(ReportPromptFactory promptFactory, Completion completion) {
        this.promptFactory = promptFactory;
        this.completion = completion;
    }

    @Override
    public ReportContent generate(ReportDraftInput input) {
        try {
            ReportContent content = completion.complete(promptFactory.create(input));
            validate(content);
            return content;
        } catch (ReportDraftGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReportDraftGenerationException(FAILURE_MESSAGE, exception);
        }
    }

    private void validate(ReportContent content) {
        if (content == null
                || !StringUtils.hasText(content.overview())
                || !StringUtils.hasText(content.analysis())
                || !StringUtils.hasText(content.improvements())) {
            throw new InvalidReportDraftException(FAILURE_MESSAGE);
        }
    }

    @FunctionalInterface
    interface Completion {
        ReportContent complete(Prompt prompt);
    }
}
