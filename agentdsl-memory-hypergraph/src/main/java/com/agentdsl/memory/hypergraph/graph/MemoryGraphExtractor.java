package com.agentdsl.memory.hypergraph.graph;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
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
 * 记忆图抽取器。
 *
 * <p>CJK 文本使用 jieba 分词（SEARCH 模式），相比 n-gram 机械切字，
 * 能正确识别"黑咖啡"、"机器学习"等词汇边界，显著提升 contextTags 质量。</p>
 *
 * <p>Latin 文本：正则匹配有效 token，去除停用词。</p>
 *
 * <p>{@link JiebaSegmenter} 初始化需加载约 5MB 词典（~100ms），
 * 使用静态懒加载单例避免重复初始化。</p>
 *
 * <p>后续可替换为 LLMGraphExtractor（保持相同接口），以获得语义级的图谱抽取。</p>
 */
public class MemoryGraphExtractor {

    /**
     * jieba 静态单例：线程安全，词典只加载一次。
     * 使用懒加载内部类模式，避免影响测试场景下的类加载速度。
     */
    private static final class JiebaHolder {
        static final JiebaSegmenter INSTANCE = new JiebaSegmenter();
    }

    private static final Pattern LATIN_PATTERN =
            Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}_-]{2,24}");

    private static final Set<String> STOP_WORDS = Set.of(
            // CJK 高频虚词、代词、副词
            "这个", "那个", "我们", "你们", "他们", "自己", "已经", "可以", "一下", "之后", "时候",
            "用户", "记住", "今天", "明天", "需要", "然后", "现在", "就是", "还是", "以及",
            "一个", "没有", "什么", "这样", "那样", "如果", "因为", "所以", "但是", "而且",
            "进行", "通过", "相关", "情况", "方面", "问题", "工作", "使用", "提供", "包括",
            "我", "你", "他", "她", "它", "的", "了", "在", "是", "有", "和", "不", "也", "都",
            // 常见 Latin 停用词
            "agent", "memory", "plugin", "the", "and", "with", "from", "that", "this",
            "have", "has", "been", "are", "was", "were", "for", "not", "but", "you", "me");

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
        return hasCjkCharacters(text) ? extractJiebaTokens(text) : extractLatinTokens(text);
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
     * 使用 jieba SEARCH 模式对 CJK 文本分词，提取有效 token 作为 contextTags。
     *
     * <p>SEARCH 模式会在 INDEX 模式基础上对长词再切分（如"机器学习" → "机器"+"学习"+"机器学习"），
     * 从而兼顾长词语义和短词匹配覆盖率，适合关键词提取。</p>
     *
     * <p>过滤策略：长度 ≥ 2、不在停用词表中、非纯数字、非纯标点。</p>
     */
    private List<String> extractJiebaTokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        List<SegToken> tokens = JiebaHolder.INSTANCE.process(text, JiebaSegmenter.SegMode.SEARCH);
        for (SegToken token : tokens) {
            String word = token.word.trim();
            if (isValidTag(word)) {
                result.add(word);
                if (result.size() >= 8) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean isValidTag(String word) {
        if (word.length() < 2) {
            return false;
        }
        if (STOP_WORDS.contains(word)) {
            return false;
        }
        // 过滤纯数字
        if (word.chars().allMatch(Character::isDigit)) {
            return false;
        }
        // 过滤纯标点/空白
        if (word.chars().allMatch(c -> !Character.isLetterOrDigit(c))) {
            return false;
        }
        return true;
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
            if (result.size() >= 8) {
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
        // 仅对纯 Latin 文本用大写字母判断是否为实体
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
        // name-based UUID：相同 memoryId/type/content 在多次整合中得到稳定 nodeId
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
