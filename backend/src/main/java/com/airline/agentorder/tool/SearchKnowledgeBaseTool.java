package com.airline.agentorder.tool;

import com.airline.agentorder.service.KnowledgeBaseService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

@Component
public class SearchKnowledgeBaseTool implements BiFunction<SearchKnowledgeBaseTool.SearchKnowledgeBaseInput, ToolContext, String> {

    private final KnowledgeBaseService knowledgeBaseService;

    public SearchKnowledgeBaseTool(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String apply(SearchKnowledgeBaseInput input, ToolContext toolContext) {
        return knowledgeBaseService.search(input == null ? null : input.getQuery());
    }

    public static class SearchKnowledgeBaseInput {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
