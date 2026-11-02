package com.github.yimeng261.maidspell.utils;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 配置里的 id 名单匹配：完整 id 精确匹配，{@code regex:} 前缀的条目按正则整串匹配。写错的正则跳过并记警告。
 */
public record IdMatcher(Set<String> exactMatches, List<Pattern> regexPatterns) {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String REGEX_PREFIX = "regex:";
    private static final IdMatcher EMPTY = new IdMatcher(Collections.emptySet(), Collections.emptyList());

    public static IdMatcher empty() {
        return EMPTY;
    }

    public static IdMatcher from(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY;
        }

        Set<String> exactMatches = new HashSet<>();
        List<Pattern> regexPatterns = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (!entry.startsWith(REGEX_PREFIX)) {
                exactMatches.add(entry);
                continue;
            }
            String regex = entry.substring(REGEX_PREFIX.length());
            if (regex.isBlank()) {
                continue;
            }
            try {
                regexPatterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException exception) {
                LOGGER.warn("名单中的正则无效：{}，已跳过", entry, exception);
            }
        }

        if (exactMatches.isEmpty() && regexPatterns.isEmpty()) {
            return EMPTY;
        }
        return new IdMatcher(exactMatches, regexPatterns);
    }

    public boolean matches(String id) {
        if (exactMatches.contains(id)) {
            return true;
        }
        for (Pattern pattern : regexPatterns) {
            if (pattern.matcher(id).matches()) {
                return true;
            }
        }
        return false;
    }
}
