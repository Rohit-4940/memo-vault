package com.nexmemo.web.service;

import com.nexmemo.core.entity.MemoTemplate;
import com.nexmemo.core.repository.MemoTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateEngineService {

    private final MemoTemplateRepository memoTemplateRepository;

    @Transactional(readOnly = true)
    public String renderTemplate(Long templateId, Map<String, String> variables) {
        MemoTemplate template = memoTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

        String content = template.getContent();
        if (content == null) return "";

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }

        return content;
    }
}
