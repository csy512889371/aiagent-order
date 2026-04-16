package com.airline.agentorder.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeBaseService {

    private final List<KnowledgeSection> sections;

    public KnowledgeBaseService() {
        this.sections = loadSections();
    }

    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "当前知识库查询内容为空，请明确说明您想了解的预订、改签或退票问题。";
        }

        List<String> keywords = tokenize(query);
        return sections.stream()
            .map(section -> new KnowledgeMatch(section, score(section, keywords, query)))
            .filter(match -> match.score() > 0)
            .sorted(Comparator.comparingInt(KnowledgeMatch::score).reversed())
            .limit(2)
            .map(match -> "【" + match.section().title() + "】\n" + match.section().content())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("知识库中暂未找到完全匹配的内容。当前可优先咨询预订航班、更改预订、取消预订和手续费规则。");
    }

    private List<KnowledgeSection> loadSections() {
        try {
            String markdown = new ClassPathResource("knowledge-base.md")
                .getContentAsString(StandardCharsets.UTF_8);
            return parseSections(markdown);
        } catch (IOException ex) {
            throw new IllegalStateException("知识库加载失败", ex);
        }
    }

    private List<KnowledgeSection> parseSections(String markdown) {
        List<KnowledgeSection> result = new ArrayList<>();
        String currentTitle = "服务条款";
        StringBuilder currentContent = new StringBuilder();

        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("## ")) {
                appendSection(result, currentTitle, currentContent);
                currentTitle = line.substring(3).trim();
                currentContent = new StringBuilder();
                continue;
            }
            if (line.startsWith("# ") || line.equals("---")) {
                continue;
            }
            if (!line.isEmpty()) {
                if (currentContent.length() > 0) {
                    currentContent.append('\n');
                }
                currentContent.append(line);
            }
        }
        appendSection(result, currentTitle, currentContent);
        return result;
    }

    private void appendSection(List<KnowledgeSection> result, String title, StringBuilder content) {
        if (content.length() == 0) {
            return;
        }
        result.add(new KnowledgeSection(title, content.toString()));
    }

    private int score(KnowledgeSection section, List<String> keywords, String query) {
        String haystack = (section.title() + "\n" + section.content()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword)) {
                score += section.title().toLowerCase(Locale.ROOT).contains(keyword) ? 4 : 2;
            }
        }
        if (haystack.contains(query.toLowerCase(Locale.ROOT))) {
            score += 3;
        }
        return score;
    }

    private List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        String normalized = query.toLowerCase(Locale.ROOT)
            .replace("？", " ")
            .replace("。", " ")
            .replace("，", " ")
            .replace("、", " ");
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        if (normalized.contains("预订")) {
            tokens.add("预订");
        }
        if (normalized.contains("改签") || normalized.contains("更改")) {
            tokens.add("更改");
        }
        if (normalized.contains("退票") || normalized.contains("取消")) {
            tokens.add("取消");
        }
        return tokens;
    }

    private record KnowledgeSection(String title, String content) {
    }

    private record KnowledgeMatch(KnowledgeSection section, int score) {
    }
}
