package com.agentdsl.memory.hypergraph.graph;

import com.agentdsl.memory.hypergraph.model.EmotionTag;
import com.agentdsl.memory.hypergraph.model.MemoryNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 轻量图抽取器。
 *
 * 对 Latin 文本：按空格/标点分词，取前 6 个有效 token。
 * 对 CJK 文本：提取汉字连续串的 2-3 字 n-gram，相比整段短语匹配质量更高，
 *              两条均含"机器学习"的消息即使前后措辞不同也能通过 bigram 关联。
 *
 * 后续可替换为 LLMGraphExtractor（保持相同接口），以获得语义级的图谱抽取。
 */
public class MemoryGraphExtractor {

    private static final Pattern LATIN_PATTERN =
            Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}_-]{2,24}");

    private static final Set<String> STOP_WORDS = Set.of(
            // CJK 高频虚词
            "这个", "那个", "我们", "你们", "他们", "自己", "已经", "可以", "一下", "之后", "时候",
            "用户", "记住", "今天", "明天", "需要", "然后", "现在", "就是", "还是", "以及",
            "一个", "没有", "什么", "这样", "那样", "如果", "因为", "所以", "但是", "而且",
            // 常见 Latin 停用词
            "agent", "memory", "plugin", "the", "and", "with", "from", "that", "this",
            "have", "has", "been", "are", "was", "were", "for", "not", "but");

    public GraphSnapshot extract(String memoryId, String content, Instant now) {
        String text = content != null ? content.trim() : "";
        List<String> contextTags = extractContextTags(text);
        List<MemoryNode> nodes = buildNodes(memoryId, text, contextTags, now);
        EmotionTag emotionTag = detectEmotion(text);
        if (emotionTag != EmotionTag.NEUTRAL) {
            nodes.add(node(memoryId, emotionTag.name().toLowerCase(Locale.ROOT),
                    MemoryNode.NodeType.EMOTION, now));
        }
        List<String> nodeIds = nodes.stream().map(MemoryNode::id).distinct().toList();
        return new GraphSnapshot(List.copyOf(nodes), nodeIds, contextTags, emotionTag);
    }

    /**
     * 仅提取 MemoryNode 列表，供 Consolidation 阶段持久化节点时使用。
     * 调用方已从 HyperEdge 拿到 contextTags 和 emotionTag，无需重新计算全量 snapshot。
     */
    public List<MemoryNode> extractNodes(String memoryId, String content, Instant now) {
        String text = content != null ? content.trim() : "";
        List<String> contextTags = extractContextTags(text);
        List<MemoryNode> nodes = buildNodes(memoryId, text, contextTags, now);
        EmotionTag emotionTag = detectEmotion(text);
        if (emotionTag != EmotionTag.NEUTRAL) {
            nodes.add(node(memoryId, emotionTag.name().toLowerCase(Locale.ROOT),
                    MemoryNode.NodeType.EMOTION, now));
        }
        return List.copyOf(nodes);
    }

    private List<MemoryNode> buildNodes(String memoryId, String text,
            List<String> contextTags, Instant now) {
        List<MemoryNode> nodes = new ArrayList<>();
        if (!text.isBlank()) {
            nodes.add(node(memoryId, text, MemoryNode.NodeType.EVENT, now));
        }
        for (String tag : contextTags) {
            nodes.add(node(memoryId, tag, classifyTag(tag), now));
        }
        return nodes;
    }

    // -----------------------------------------------------------------------
    // 标签提取
    // -----------------------------------------------------------------------

    List<String> extractContextTags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return hasCjkCharacters(text) ? extractCjkNgrams(text) : extractLatinTokens(text);
    }

    private boolean hasCjkCharacters(String text) {
        for (int i = 0; i < text.length(); i++) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对 CJK 文本提取 2-3 字 n-gram。
     *
     * 先扫描出所有汉字连续串，再从每段串中生成 bigram（2字）和 trigram（3字），
     * 过滤停用词后取前 6 个。即使同一主题在不同句子中措辞不同，
     * 只要含相同的 2-3 字组合（如"机器学习"），依然能被关联到。
     */
    private List<String> extractCjkNgrams(String text) {
        Set<String> result = new LinkedHashSet<>();
        StringBuilder run = new StringBuilder();

        for (int i = 0; i < text.length() && result.size() < 12; i++) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                run.appendCodePoint(cp);
            } else {
                if (run.length() >= 2) {
                    addNgrams(run.toString(), result);
                }
                run.setLength(0);
            }
        }
        if (run.length() >= 2) {
            addNgrams(run.toString(), result);
        }

        return result.stream().limit(6).collect(Collectors.toList());
    }

    private void addNgrams(String cjkRun, Set<String> result) {
        // bigram（2字）优先
        for (int i = 0; i + 2 <= cjkRun.length() && result.size() < 12; i++) {
            String gram = cjkRun.substring(i, i + 2);
            if (!STOP_WORDS.contains(gram)) {
                result.add(gram);
            }
        }
        // trigram（3字）补充
        for (int i = 0; i + 3 <= cjkRun.length() && result.size() < 12; i++) {
            String gram = cjkRun.substring(i, i + 3);
            if (!STOP_WORDS.contains(gram)) {
                result.add(gram);
            }
        }
    }

    private List<String> extractLatinTokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = LATIN_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group().trim();
            String normalized = token.toLowerCase(Locale.ROOT);
            if (STOP_WORDS.contains(normalized)) {
                continue;
            }
            result.add(token);
            if (result.size() >= 6) {
                break;
            }
        }
        return List.copyOf(result);
    }

    // -----------------------------------------------------------------------
    // 节点类型分类
    // -----------------------------------------------------------------------

    private MemoryNode.NodeType classifyTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return MemoryNode.NodeType.CONCEPT;
        }
        // 仅对纯 Latin 文本用大写字母判断是否为实体（CJK 中大写字母无意义）
        boolean isLatinOnly = tag.chars().noneMatch(c -> c > 0x2E80);
        if (isLatinOnly && tag.chars().anyMatch(Character::isUpperCase)) {
            return MemoryNode.NodeType.ENTITY;
        }
        if (tag.matches(".*\\d{2,}.*")) {
            return MemoryNode.NodeType.EVENT;
        }
        return MemoryNode.NodeType.CONCEPT;
    }

    // -----------------------------------------------------------------------
    // 情绪检测
    // -----------------------------------------------------------------------

    private EmotionTag detectEmotion(String text) {
        if (text == null || text.isBlank()) {
            return EmotionTag.NEUTRAL;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "紧急", "严重", "critical", "故障", "事故", "必须立即")) {
            return EmotionTag.CRITICAL;
        }
        if (containsAny(normalized, "开心", "高兴", "满意", "喜欢", "赞", "happy", "great", "excellent")) {
            return EmotionTag.POSITIVE;
        }
        if (containsAny(normalized, "担心", "讨厌", "错误", "失败", "问题", "bug", "error", "糟糕", "失望")) {
            return EmotionTag.NEGATIVE;
        }
        return EmotionTag.NEUTRAL;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // 工具方法
    // -----------------------------------------------------------------------

    private MemoryNode node(String memoryId, String content, MemoryNode.NodeType type, Instant now) {
        String normalized = content == null ? "" : content.trim().toLowerCase(Locale.ROOT);
        // name-based UUID：相同 memoryId/type/content 组合在多次整合中得到稳定 nodeId
        String seed = memoryId + "|" + type.name() + "|" + normalized;
        String id = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        return new MemoryNode(id, memoryId, content, type, null, now, now, 1);
    }

    public record GraphSnapshot(
            List<MemoryNode> nodes,
            List<String> nodeIds,
            List<String> contextTags,
            EmotionTag emotionTag) {
    }
}
