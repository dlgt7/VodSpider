package com.github.catvod.spider;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.SliderVerifyUtils;
import com.github.catvod.utils.Util;
import com.github.catvod.net.OkHttp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XBPQ 爬虫实现类（基于香草规则引擎）
 * <p>
 * 支持功能：
 * <ul>
 *   <li>首页内容分类获取与推荐</li>
 *   <li>分类列表视频数据抓取</li>
 *   <li>详情页信息解析</li>
 *   <li>播放列表与线路识别</li>
 *   <li>搜索结果解析</li>
 *   <li>播放地址嗅探与解析</li>
 * </ul>
 *
 * @author CatVodSpider Team
 * @version 2.0
 */
public class XBPQ extends Spider {

    // ==================== 常量定义 ====================

    /** Base64 编码标志 */
    protected static final int BASE64_FLAG = Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP;

    /** 默认视频格式后缀列表 */
    private static final List<String> DEFAULT_VIDEO_FORMATS = Arrays.asList(
            ".m3u8", ".mp4", ".mpeg", ".flv", ".mkv"
    );

    /** 最大 HTML 内容截取长度（2MB） */
    private static final int MAX_HTML_LENGTH = 2 * 1024 * 1024;

    /** 最大匹配项数量限制 */
    private static final int MAX_MATCH_COUNT = 30;

    /** 分类ID阈值（超过此值认为是视频ID而非分类ID） */
    private static final int CATEGORY_ID_THRESHOLD = 100;

    /** 首页每个分类默认抓取的最大视频数 */
    private static final int DEFAULT_HOME_MAX_VIDEOS = 20;

    /** 默认标题边界（无 url_title 规则时用于截取链接文本） */
    private static final String[] DEFAULT_TITLE_BOUNDS = {">", "<"};

    /** 用户代理常量 */
    private static final String UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 11; Mi 10 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.152 Mobile Safari/537.36";

    // ==================== 实例变量 ====================

    /** 扩展配置（JSON或URL） */
    protected String ext = null;

    /** 解析后的规则对象 */
    public JSONObject rule = null;

    /** 视频格式检测列表 */
    private List<String> videoFormatList = new ArrayList<>(DEFAULT_VIDEO_FORMATS);

    /** 是否倒序显示 */
    private boolean reverseOrder = false;

    /** 分段标志符（用于猜测分类时） */
    private String splitFlag = "";

    // ==================== 增强特性字段（借鉴第18次升级版/参考文件） ====================

    /** 类级调试开关（任一源 openDebug=1 即全局生效，"只升不降"，避免多源互相关闭日志） */
    private static volatile boolean debug = false;

    /** 类级公共请求头表（"公共请求头"/headerJson 配置填充，所有请求默认携带，跨实例共享） */
    private static final Map<String, String> headerMap = new ConcurrentHashMap<>();

    /** 图片远端 base64 代理前缀（fixCover 双模式：非空时走 远端代理防盗链图床中转） */
    private static volatile String baseEncodeUrl = "";

    /** 图片代理签名密钥（fixCover 追加 &key=MD5(pic+secretKey)，loadPic 校验防代理滥用） */
    private static volatile String secretKey = "";

    /** 静态主页 URL 快照（供 getCom/getBL 等静态工具方法稳定访问；适用于单源/非并发场景） */
    private static volatile String staticHomeUrl = "";

    /** 类级共享状态写锁：串行化 initEnhancedConfig/setBL 对 staticHomeUrl/baseEncodeUrl/secretKey/headerMap/debug 的写入，
     *  避免多源并发初始化互相覆盖（P0-3 线程安全）。注意：共享的"类级公共请求头"本身是设计意图，本锁保证写入原子性而非逐源隔离。 */
    private static final Object GLOBAL_STATE_LOCK = new Object();

    /** 实例级调试开关 */
    private boolean isDebug = false;

    /** 最近一次请求响应码（失败追踪，配合 errorCodes/failCodes/successCodes 判定） */
    private int lastResponseCode = 200;

    /** 请求失败标志（触发 Init.show 消息弹窗提示用户） */
    private boolean requestFailed = false;

    /** 请求失败消息 */
    private String failMessage = "";

    /** 变量映射表（{{变量}} 替换）；使用 ConcurrentHashMap 避免多线程读写冲突（P0-3） */
    private final Map<String, String> variableMap = new ConcurrentHashMap<>();

    /** 随机数生成器（随机图标背景色） */
    private final Random random = new Random();

    // ==================== 6 个新字段实例状态 ====================

    /** 播放图片（详情/列表缺封面时的兜底图，对应配置 播放图片） */
    private String playImage = "";

    /** 线路合并开关（多线路合并为单一线路，对应配置 线路合并=1） */
    private boolean mergeLines = false;

    /** 热门推荐开关（首页聚合主页热门/推荐视频，对应配置 热门推荐=1） */
    private boolean hotRecommend = false;

    /** 列表显示开关（首页以列表形式展示，结果通过 ext 透传，对应配置 列表显示=1） */
    private boolean listDisplay = false;

    // ==================== 预编译正则常量（避免每次调用重复 Pattern.compile，P1 性能优化） ====================

    /** selectByRule: CSS :eq(n) 选择器 */
    private static final Pattern P_CSS_EQ = Pattern.compile(":eq\\s*\\(\\s*(\\d+)\\s*\\)");
    /** selectByRule: CSS [n] 下标选择器 */
    private static final Pattern P_CSS_INDEX = Pattern.compile("\\[\\s*(\\d+)\\s*\\]$");
    /** CSS 选择器 :last 的特殊索引标记（表示最后一个元素） */
    private static final int LAST_INDEX = -1;
    /** 处理标记 [替换|包含|不包含:...] */
    private static final Pattern P_PROC_MARK = Pattern.compile("\\[(替换|包含|不包含):([^\\]]+)\\]");
    /** 表单 action 属性提取（不区分大小写） */
    private static final Pattern P_ACTION_ATTR = Pattern.compile("action=\"(.+?)\"", Pattern.CASE_INSENSITIVE);
    /** 花括号变量 {xxx} */
    private static final Pattern P_BRACE_VAR = Pattern.compile("\\{(.*?)\\}");
    /** 分类 URL 已知花括号占位符白名单（仅这些未赋值时才被清除，其余 {xxx} 原样保留） */
    private static final Set<String> KNOWN_BRACE_KEYS = new HashSet<>(Arrays.asList(
            "cateId", "catePg", "cateIdEn", "class", "area", "by", "year", "lang", "letter", "page", "pg"));
    /**
     * 播放器对象 var player_xxx = {...}
     * 平衡花括号匹配（支持 vod_data 等两层嵌套），结尾分号可有可无——
     * 旧写法 (\{.+?\}); 要求 "\};" 收尾，无分号站点（如热剧TV网）整段匹配失败
     */
    private static final Pattern P_PLAYER_OBJ = Pattern.compile("var player_\\w+\\s*=\\s*(\\{(?:[^{}]|\\{(?:[^{}]|\\{[^{}]*\\})*\\})*\\})");
    /** 播放器 url 字段提取（[\s\S] 跨越嵌套对象，旧 [^}]*? 遇 vod_data 的 } 即断） */
    private static final Pattern P_PLAYER_URL = Pattern.compile("var player_\\w+\\s*=\\s*\\{[\\s\\S]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
    /** encrypt 标志提取 */
    private static final Pattern P_ENCRYPT = Pattern.compile("\"encrypt\"\\s*:\\s*(\\d+)");
    /** Unicode 转义 \\uXXXX 解码（convertUnicodeToChinese，外层分组 group(2)=十六进制） */
    private static final Pattern P_UNICODE_SEQ = Pattern.compile("(\\\\u([0-9A-Fa-f]{4}))");
    /** 宝塔 WAF token（JSON/属性） */
    private static final Pattern P_BTWAF_TOKEN_JSON = Pattern.compile("btwaf[\"'=]\\s*:\\s*[\"']([^\"']+)[\"']");
    /** 宝塔 WAF token（query 参数） */
    private static final Pattern P_BTWAF_TOKEN_QUERY = Pattern.compile("[?&]btwaf=([^&\"'\\s>]+)");
    /** meta refresh 跳转（不区分大小写） */
    private static final Pattern P_META_REFRESH = Pattern.compile("content\\s*=\\s*[\"']\\d+;\\s*url=([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    /** location.href 跳转 */
    private static final Pattern P_LOCATION_HREF = Pattern.compile("location\\.href\\s*=\\s*[\"']([^\"']+)[\"']");
    /** window.location 跳转 */
    private static final Pattern P_WINDOW_LOCATION = Pattern.compile("window\\.location\\s*=\\s*[\"']([^\"']+)[\"']");
    /** selectByRule: xxx:eq(n) 形式 */
    private static final Pattern P_SELECT_EQ = Pattern.compile("(.+?):eq\\((\\d+)\\)");
    /** {{变量}} 模板 */
    private static final Pattern P_TEMPLATE_VAR = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    /** Unicode 转义 \\uXXXX 解码（hexEscapeDecode，group(1)=十六进制） */
    private static final Pattern P_UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");
    /** clan(): 干扰标记 [.*?] */
    private static final Pattern P_CLAN_BRACKET = Pattern.compile("\\[.*?\\]");
    /** clan(): 干扰标记 ￥.*?￥ */
    private static final Pattern P_CLAN_YUAN = Pattern.compile("￥.*?￥");
    /** cleanHtml: HTML 标签 <...> */
    private static final Pattern P_HTML_TAG = Pattern.compile("<[^>]+>");
    /** cleanHtml: HTML 实体 &xxx; */
    private static final Pattern P_HTML_ENTITY = Pattern.compile("&[a-zA-Z]{1,10};");
    /** cleanHtml: 残留符号 (/> )< */
    private static final Pattern P_RESIDUAL_SYMS = Pattern.compile("[(/>)<]");
    /** cleanHtml: 连续空白 */
    private static final Pattern P_WHITESPACE = Pattern.compile("\\s+");
    /** cleanHtmlResponse: HTML 注释 <!--...--> */
    private static final Pattern P_HTML_COMMENT = Pattern.compile("<!--.+?-->");

    // ==================== 字段映射表（统一命名标准，见 XBPQKey） ====================

    /**
     * 「任意写法 -> 英文规范键」映射表：兼容中文键、历史别名与其它爬虫的同义键。
     * <p>
     * 单一事实源为 XBPQKey：新增字段只需在 XBPQKey 登记表中加一行，
     * 中文名 / 英文别名 / 历史拼写在此自动生效，本类无需改动。
     * 仅收录「非规范写法 -> 规范键」，规范英文键原样保留，避免归一化打乱规则键序。
     */
    private static final Map<String, String> CHINESE_KEY_MAP = XBPQKey.aliasMap();

    /** 标准分类名称列表（用于猜测分类） */
    protected final List<String> standardCategoryNames = Arrays.asList(
            "电影", "剧集", "电视剧", "连续剧", "综艺", "动漫"
    );

    /** 无效分类名称列表（过滤用） */
    protected final List<String> invalidCategoryNames = Arrays.asList(
            "更多", "下载", "首页", "资讯", "留言", "导航", "专题",
            "短视频", "热榜", "排行", "追剧", "更新", "APP",
            "直播", "label", "Netflix"
    );

    /** 详情页字段名称列表 */
    protected final List<String> detailFieldNames = Arrays.asList(
            "导演", "主演", "演员", "地区", "类型", "年份", "年代"
    );

    /** 详情页字段对应的JSON Key */
    protected final List<String> detailFieldKeys = Arrays.asList(
            "vod_director", "vod_actor", "vod_actor", "vod_area",
            "type_name", "vod_year", "vod_year"
    );

    // ==================== 内部数据结构 ====================

    /**
     * HTML 匹配信息
     * 用于存储正则匹配结果和DOM节点位置信息
     */
    protected static class HtmlMatchInfo {
        public String group0;           // 完整匹配字符串
        public String group1;           // 第一组捕获（通常为href）
        public String group2;           // 第二组捕获
        public String diff;             // 与另一个匹配的差异部分
        public int startPos;            // 匹配起始位置
        public int endPos;              // 匹配结束位置
        public List<Integer> uploads;   // 祖先节点索引列表
        public int matchedUpNodePos;    // 最匹配的祖先节点位置
        public int diffStartIndex;      // 差异起始索引
        public int diffEndIndex;        // 差异结束索引

        /**
         * 从Matcher初始化匹配信息
         */
        public void init(Matcher m) {
            this.group0 = m.group(0);
            if (m.groupCount() > 0) this.group1 = m.group(1);
            if (m.groupCount() > 1) this.group2 = m.group(2);
            this.startPos = m.start(0);
            this.endPos = m.end(0);
        }

        /**
         * 比较两个匹配信息的差异
         * @param rhs 另一个匹配信息
         * @param splitFlag 分隔标志字符
         * @return 是否成功找到差异
         */
        public boolean findDiffStr(HtmlMatchInfo rhs, String splitFlag) {
            int len = Math.min(group1.length(), rhs.group1.length());

            // 找差异起始位置
            for (int i = 0; i < len; ++i) {
                char a = group1.charAt(i);
                char b = rhs.group1.charAt(i);
                if (a == b && splitFlag.indexOf(a) != -1) {
                    diffStartIndex = i + 1;
                    rhs.diffStartIndex = i + 1;
                }
                if (a != b) break;
            }

            // 找差异结束位置
            diffEndIndex = group1.length();
            rhs.diffEndIndex = rhs.group1.length();
            for (int i = 1; i < len; ++i) {
                char a = group1.charAt(group1.length() - i);
                char b = rhs.group1.charAt(rhs.group1.length() - i);
                if (a == b && splitFlag.indexOf(a) != -1) {
                    diffEndIndex = group1.length() - i;
                    rhs.diffEndIndex = rhs.group1.length() - i;
                }
                if (a != b) break;
            }

            if ((this.diff == null || this.diff.isEmpty()) && diffStartIndex < diffEndIndex) {
                diff = group1.substring(diffStartIndex, diffEndIndex);
            } else {
                if (diffEndIndex < diffStartIndex || !diff.equals(group1.substring(diffStartIndex, diffEndIndex))) {
                    return false;
                }
            }

            if (rhs.diffStartIndex < rhs.diffEndIndex) {
                rhs.diff = rhs.group1.substring(rhs.diffStartIndex, rhs.diffEndIndex);
            }
            return true;
        }

        /**
         * 判断是否有相同的祖先节点
         */
        boolean hasSameUpNode(HtmlMatchInfo rhs) {
            if (rhs.uploads.size() != this.uploads.size()) return false;
            for (int i = 0; i < uploads.size(); ++i) {
                if (uploads.get(i).intValue() != rhs.uploads.get(i).intValue()) continue;
                if (matchedUpNodePos == -1 || uploads.get(i).intValue() == matchedUpNodePos) {
                    matchedUpNodePos = uploads.get(i).intValue();
                    rhs.matchedUpNodePos = uploads.get(i).intValue();
                    return true;
                }
                return false;
            }
            return false;
        }
    }

    // ==================== HTML 解析工具类 ====================

    /**
     * HTML 节点解析工具类
     * 提供HTML标签查找、节点遍历、文本清理等功能
     */
    public static class HtmlNodeHelper {
        /** 非配对标签列表 */
        private static final List<String> UNPAIRED_TAGS = Arrays.asList(
                "img", "br", "meta", "!--", "input", "hr", "source", "embed",
                "col", "wbr", "base", "area", "param", "track"
        );

        /**
         * 判断是否为配对的HTML标签
         */
        public static boolean isPairedHtmlTag(String str, int startPos) {
            String tmp = str.substring(startPos, Math.min(str.length(), startPos + 10));
            for (String tag : UNPAIRED_TAGS) {
                if (tmp.indexOf(tag) != -1) {
                    for (int i = startPos + 1; i < str.length(); ++i) {
                        if (str.charAt(i) == '>') {
                            return str.charAt(i - 1) == '/';
                        }
                    }
                    return false;
                }
            }
            return true;
        }

        /**
         * 判断指定位置开始的标签是否为自闭合写法（以 /> 结束）
         */
        public static boolean isSelfClosedTag(String str, int startPos) {
            for (int i = startPos + 1; i < str.length(); ++i) {
                char c = str.charAt(i);
                if (c == '>') return str.charAt(i - 1) == '/';
                if (c == '<') return false;
            }
            return false;
        }

        /**
         * 获取从指定位置开始的完整HTML节点字符串
         * @param str HTML源码
         * @param pos 标签起始位置（必须是 '<'）
         * @return 完整的节点字符串
         */
        public static String nodeString(String str, int pos) {
            if (pos < 0 || pos >= str.length() || str.charAt(pos) != '<') return str;
            int depth = 0;
            for (int i = pos; i < str.length() - 1; ++i) {
                switch (str.charAt(i)) {
                    case '/':
                        // 仅闭合标签 </ 使深度减一；自闭合 /> 的开标签未配对增深度，此处保持中性
                        // （旧实现 /> 也减一，含 <img/> 的节点会在该处被提前截断）
                        if (str.charAt(i - 1) == '<') {
                            depth--;
                        }
                        break;
                    case '>':
                        if (depth == 0) return str.substring(pos, i + 1);
                        break;
                    case '<':
                        if (str.charAt(i + 1) != '/' && isPairedHtmlTag(str, i)) {
                            depth++;
                        }
                        break;
                    default:
                        break;
                }
            }
            return str.substring(pos);
        }

        /**
         * 查找指定位置的祖先节点
         * @param str HTML源码
         * @param pos 当前位置
         * @param lookback 回溯层数
         * @return 祖先节点位置列表
         */
        public static List<Integer> findUpNodes(String str, int pos, int lookback) {
            List<Integer> nodes = new ArrayList<>();
            if (pos == -1) return nodes;
            int depth = 0;
            for (int i = pos; i >= 0; --i) {
                switch (str.charAt(i)) {
                    case '/':
                        if (str.charAt(i + 1) == '>') {
                            depth++;
                        } else if (str.charAt(i - 1) == '<') {
                            depth++;
                            --i;
                        }
                        break;
                    case '<':
                        if (depth == 0) {
                            nodes.add(i);
                        } else {
                            // 配对标签的开标签、或已越过 /> 的自闭合标签，均抵消此前的深度自增；
                            // 非自闭合的未配对标签（<br>）保持中性。旧实现漏算自闭合标签，
                            // 反向扫描跨越 <img/> 等标签后深度恒大于 0，祖先节点定位失效
                            if (isPairedHtmlTag(str, i) || isSelfClosedTag(str, i)) {
                                depth--;
                                if (depth < 0) depth = 0;
                            }
                        }
                        break;
                    default:
                        break;
                }
                if (nodes.size() >= lookback) break;
            }
            return nodes;
        }

        /**
         * 获取当前节点的所有子节点
         */
        public static List<String> getChildNodes(String str) {
            List<String> arr = new ArrayList<>();
            int pos = 0;
            if (pos < 0 || pos >= str.length() || str.charAt(pos) != '<') return arr;
            ++pos;
            while (pos > -1 && pos < str.length()) {
                pos = str.indexOf('<', pos);
                String p = nodeString(str, pos);
                if (p.isEmpty()) break;
                arr.add(p);
                pos += p.length();
            }
            return arr;
        }

        /**
         * 移除HTML标签并清理空白字符
         * @param str 原始HTML字符串
         * @param replace 替换标记
         * @return 清理后的纯文本
         */
        public static String trimHtmlString(String str, String replace) {
            return str.replace("\r\n", "")
                    .replace("\n", "")
                    .replaceAll("<.+?>", replace)
                    .replaceAll("\\s+", " ")
                    .replace("&nbsp;", "")
                    .replace("&emsp;", "")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .trim();
        }

        /**
         * 移除HTML标签（默认替换为空）
         */
        public static String trimHtmlString(String str) {
            return trimHtmlString(str, "");
        }
    }

    // ==================== JSOUP / CSS 选择器解析工具类 ====================

    /**
     * Jsoup CSS 选择器提取工具类
     * <p>
     * 提供基于 CSS 选择器的 HTML 字段提取能力，支持：
     * <ul>
     *   <li>标准 CSS 选择器：.class, #id, [attr], tag.class 等</li>
     *   <li>属性提取：@href, @src, @data-src 等</li>
     *   <li>文本提取：text(), text(0), ownText() 等</li>
     *   <li>HTML 提取：html(), outerHtml()</li>
     *   <li>伪选择器扩展：:first, :last, :eq(n), :gt(n), :lt(n)</li>
     *   <li>链式子选择：> 子选择器（空格分隔多级）</li>
     * </ul>
     *
     * <h3>规则语法示例：</h3>
     * <pre>{@code
     * // 基础选择器 - 提取文本
     * "css:.module-item .video-serial-main .title"
     *
     * // 属性提取 - 提取 href
     * "css:a.play-img@href"
     *
     * // 带索引的属性提取
     * "css:.stui-vodlist__thumb.lazyload@data-original"
     *
     * // 文本提取（指定第N个元素）
     * "css:.detail-content .title a@text"
     *
     * // HTML 内容提取
     * "css:.detail-content@html"
     *
     * // 多级嵌套选择
     * "css:.container > .row > .col-md-9 a.stui-vodlist__thumb@href"
     *
     * // 组合规则（与正则共存时优先使用 css:// 前缀）
     * "css://.my-list a.item-link@href&&前缀&&后缀"
     * }</pre>
     *
     * @author CatVodSpider Team
     * @version 2.0
     * @since 2.0
     */
    public static class JsoupExtractor {

        /** CSS 选择器协议前缀 */
        public static final String CSS_PREFIX = "css:";
        public static final String CSS_PREFIX_FULL = "css://";

        /** 属性提取标记 */
        private static final char ATTR_MARKER = '@';

        /** 文本提取标记 */
        private static final String TEXT_EXTRACT = "@text";
        private static final String OWN_TEXT_EXTRACT = "@ownText";
        private static final String HTML_EXTRACT = "@html";
        private static final String OUTER_HTML_EXTRACT = "@outerHtml";

        /**
         * 判断规则字符串是否为 CSS 选择器格式
         *
         * @param rule 规则字符串
         * @return true 如果是 CSS 选择器规则
         */
        public static boolean isCssRule(String rule) {
            return rule != null && (rule.startsWith(CSS_PREFIX_FULL) || rule.startsWith(CSS_PREFIX));
        }

        /**
         * 解析 CSS 选择器规则并返回结构化信息
         *
         * @param rule 原始规则字符串（可能包含 css:// 前缀）
         * @return CssRuleInfo 结构化规则信息
         */
        public static CssRuleInfo parseRule(String rule) {
            if (rule == null || rule.isEmpty()) return null;

            // 去掉协议前缀
            String cleanRule = stripPrefix(rule);

            // CSS 简写语法转换（p:tag->attr / p:tag->text，借鉴第18次升级版 parseCssShortSyntax）
            cleanRule = parseCssShortSyntax(cleanRule);

            // 解析提取模式
            ExtractMode mode = ExtractMode.TEXT;
            String attrName = "";
            int index = 0;

            if (cleanRule.contains(TEXT_EXTRACT)) {
                mode = ExtractMode.TEXT;
                cleanRule = cleanRule.replace(TEXT_EXTRACT, "");
            } else if (cleanRule.contains(OWN_TEXT_EXTRACT)) {
                mode = ExtractMode.OWN_TEXT;
                cleanRule = cleanRule.replace(OWN_TEXT_EXTRACT, "");
            } else if (cleanRule.contains(HTML_EXTRACT)) {
                mode = ExtractMode.HTML;
                cleanRule = cleanRule.replace(HTML_EXTRACT, "");
            } else if (cleanRule.contains(OUTER_HTML_EXTRACT)) {
                mode = ExtractMode.OUTER_HTML;
                cleanRule = cleanRule.replace(OUTER_HTML_EXTRACT, "");
            } else if (cleanRule.indexOf(ATTR_MARKER) != -1) {
                // 属性提取模式
                int atIdx = cleanRule.indexOf(ATTR_MARKER);
                attrName = cleanRule.substring(atIdx + 1).trim();
                cleanRule = cleanRule.substring(0, atIdx);
                mode = ExtractMode.ATTRIBUTE;
            }

            // 处理索引 :eq(n) 或 [n]
            index = parseIndex(cleanRule);
            cleanRule = cleanIndexMarkers(cleanRule);

            // 清理选择器
            cleanRule = cleanRule.trim();

            CssRuleInfo info = new CssRuleInfo();
            info.selector = cleanRule;
            info.mode = mode;
            info.attributeName = attrName;
            info.index = index;
            info.originalRule = rule;
            return info;
        }

        /**
         * 从 HTML 内容中提取单个字段值
         *
         * @param html   HTML 源内容
         * @param rule   CSS 选择器规则
         * @param result 结果列表（可选，用于收集多个结果）
         * @return 第一个匹配的值，或空字符串
         */
        public static String extractSingle(String html, String rule, List<String> result) {
            try {
                Document doc = Jsoup.parse(html);
                CssRuleInfo info = parseRule(rule);
                if (info == null || info.selector.isEmpty()) return "";

                Elements elements = doc.select(info.selector);
                if (elements.isEmpty()) return "";

                Element target = selectByIndex(elements, info.index);
                if (target == null) return "";

                String value = extractValue(target, info.mode, info.attributeName);
                if (!value.isEmpty() && result != null) {
                    result.add(value);
                }
                return value;
            } catch (Exception e) {
                SpiderDebug.log("Jsoup extract error: " + e.getMessage());
            }
            return "";
        }

        /**
         * 从 HTML 内容中提取多个字段值
         *
         * @param html HTML 源内容
         * @param rule CSS 选择器规则
         * @return 所有匹配值的列表
         */
        public static List<String> extractList(String html, String rule) {
            List<String> results = new ArrayList<>();
            try {
                Document doc = Jsoup.parse(html);
                CssRuleInfo info = parseRule(rule);
                if (info == null || info.selector.isEmpty()) return results;

                Elements elements = doc.select(info.selector);
                for (Element el : elements) {
                    String value = extractValue(el, info.mode, info.attributeName);
                    if (!value.isEmpty()) {
                        results.add(value);
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log("Jsoup extract list error: " + e.getMessage());
            }
            return results;
        }

        /**
         * 从 HTML 内容中提取所有匹配元素的完整信息（用于列表项构建）
         *
         * @param html      HTML 源内容
         * @param containerRule 容器选择器规则（每个容器代表一个列表项）
         * @param fieldRules 字段规则映射（字段名 -> CSS 规则）
         * @return 列表项 JSON 数组
         */
        public static JSONArray extractItems(String html, String containerRule,
                                             Map<String, String> fieldRules) {
            JSONArray items = new JSONArray();
            try {
                Document doc = Jsoup.parse(html);
                CssRuleInfo containerInfo = parseRule(containerRule);
                if (containerInfo == null || containerInfo.selector.isEmpty()) return items;

                Elements containers = doc.select(containerInfo.selector);
                for (Element container : containers) {
                    JSONObject item = new JSONObject();
                    for (Map.Entry<String, String> entry : fieldRules.entrySet()) {
                        String fieldName = entry.getKey();
                        String fieldRule = entry.getValue();
                        try {
                            CssRuleInfo fieldInfo = parseRule(fieldRule);
                            if (fieldInfo == null) continue;

                            Elements fields = container.select(fieldInfo.selector);
                            Element target = selectByIndex(fields, fieldInfo.index);
                            if (target != null) {
                                String value = extractValue(target, fieldInfo.mode, fieldInfo.attributeName);
                                item.put(fieldName, value);
                            }
                        } catch (Exception e) {
                            SpiderDebug.log("extractItems 字段提取跳过 [" + fieldName + "]: " + e.getMessage());
                        }
                    }

                    // 只添加非空项
                    if (item.length() > 0) {
                        items.put(item);
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log("Jsoup extract items error: " + e.getMessage());
            }
            return items;
        }

        /**
         * 使用 CSS 选择器进行二次截取（替代 applySecondCut 的 CSS 版本）
         *
         * @param html HTML 源内容
         * @param rule CSS 截取规则（支持 css://selector 格式）
         * @return 截取后的 HTML 片段
         */
        public static String cutRegion(String html, String rule) {
            try {
                if (!isCssRule(rule)) return html;
                Document doc = Jsoup.parse(html);
                CssRuleInfo info = parseRule(rule);
                if (info == null) return html;

                Elements elements = doc.select(info.selector);
                if (elements.isEmpty()) return html;

                StringBuilder sb = new StringBuilder();
                for (Element el : elements) {
                    sb.append(el.outerHtml());
                }
                return sb.toString();
            } catch (Exception e) {
                SpiderDebug.log("Jsoup cut region error: " + e.getMessage());
            }
            return html;
        }

        /**
         * 智能提取：自动判断规则类型并执行提取
         * 支持 CSS 选择器和传统正则/字符串截取两种模式
         *
         * @param html HTML 源内容
         * @param rule 规则字符串（自动识别 css:// 前缀）
         * @return 提取结果
         */
        public static String smartExtract(String html, String rule) {
            if (isCssRule(rule)) {
                return extractSingle(html, rule, null);
            }
            // 非CSS规则返回空，由调用方使用原有逻辑处理
            return "";
        }

        // ========== 内部辅助方法 ==========

        /**
         * 去掉 CSS 协议前缀
         */
        private static String stripPrefix(String rule) {
            if (rule.startsWith(CSS_PREFIX_FULL)) {
                return rule.substring(CSS_PREFIX_FULL.length());
            } else if (rule.startsWith(CSS_PREFIX)) {
                return rule.substring(CSS_PREFIX.length());
            }
            return rule;
        }

        /**
         * 解析索引值
         * 支持 :eq(n), :nth-of-type(n), [n] 等格式
         */
        private static int parseIndex(String selector) {
            // :eq(n) 格式
            Pattern eqPattern = P_CSS_EQ;
            Matcher eqM = eqPattern.matcher(selector);
            if (eqM.find()) {
                return Integer.parseInt(eqM.group(1));
            }
            // :first / :last
            if (selector.contains(":first")) return 0;
            if (selector.contains(":last")) return LAST_INDEX;
            // [n] 格式
            Pattern bracketPattern = P_CSS_INDEX;
            Matcher bm = bracketPattern.matcher(selector);
            if (bm.find()) {
                return Integer.parseInt(bm.group(1));
            }
            return 0; // 默认第一个
        }

        /**
         * 清理索引标记（从选择器中移除以便 Jsoup 正确解析）
         */
        private static String cleanIndexMarkers(String selector) {
            return selector.replaceAll(":eq\\s*\\(\\s*\\d+\\s*\\)", "")
                    .replaceAll(":first", "").replaceAll(":last", "")
                    .replaceAll("\\[\\d+\\]$", "")
                    .trim();
        }

        /**
         * 根据索引选择元素
         */
        private static Element selectByIndex(Elements elements, int index) {
            if (index >= elements.size()) return null;
            if (index == LAST_INDEX) return elements.last(); // :last
            return elements.get(index);
        }

        /**
         * 根据提取模式从元素中提取值
         */
        private static String extractValue(Element element, ExtractMode mode, String attrName) {
            switch (mode) {
                case ATTRIBUTE:
                    return element.attr(attrName).trim();
                case TEXT:
                    return element.text().trim();
                case OWN_TEXT:
                    return element.ownText().trim();
                case HTML:
                    return element.html().trim();
                case OUTER_HTML:
                    return element.outerHtml().trim();
                default:
                    return element.text().trim();
            }
        }

        // ========== 数据结构定义 ==========

        /**
         * CSS 提取模式枚举
         */
        public enum ExtractMode {
            /** 文本内容（含子元素） */
            TEXT,
            /** 自身文本（不含子元素） */
            OWN_TEXT,
            /** HTML 内容 */
            HTML,
            /** 外部 HTML（含自身标签） */
            OUTER_HTML,
            /** 属性值 */
            ATTRIBUTE
        }

        /**
         * CSS 规则解析结果
         */
        public static class CssRuleInfo {
            /** CSS 选择器 */
            public String selector = "";
            /** 提取模式 */
            public ExtractMode mode = ExtractMode.TEXT;
            /** 属性名（mode=ATTRIBUTE 时有效） */
            public String attributeName = "";
            /** 元素索引（0=第一个） */
            public int index = 0;
            /** 原始规则字符串 */
            public String originalRule = "";

            @Override
            public String toString() {
                return String.format("CssRuleInfo{selector='%s', mode=%s, attr='%s', index=%d}",
                        selector, mode.name(), attributeName, index);
            }
        }
    }

    // ==================== 规则处理工具类 ====================

    /**
     * 规则处理工具类
     * 提供字符串截取、区域查找、回溯等功能
     */
    public static class RuleUtils {

        /**
         * 查找列表块的起始位置
         * 取最靠近共同祖先节点的位置
         */
        public static int findBlockPos(List<Integer> a, List<Integer> b) {
            if (a == null || b == null) return 0;
            int len = Math.min(a.size(), b.size());
            if (len == 1) return b.get(0);
            for (int i = 0; i < len; ++i) {
                if (a.get(i).intValue() == b.get(i).intValue()) {
                    return i > 0 ? b.get(i - 1) : b.get(0);
                }
            }
            return b.get(len - 1);
        }

        /**
         * 在字符串中查找子串
         * @param str 源字符串
         * @param startPos 起始位置
         * @param keys 规则数组 [前缀, 后缀, 左偏移, 右偏移]
         * @param defaultValue 默认值
         * @return 找到的子串
         */
        public static String findSubString(String str, int startPos, JSONArray keys, String defaultValue) {
            try {
                if (keys == null) return defaultValue;
                String prefix = keys.getString(0);
                String suffix = keys.getString(1);
                int offsetLeft = keys.length() > 2 ? keys.getInt(2) : 0;
                int offsetRight = keys.length() > 3 ? keys.getInt(3) : 0;

                int start = str.indexOf(prefix, startPos) + prefix.length();
                if (start < prefix.length()) return defaultValue;
                int end = str.indexOf(suffix, start);
                if (end < start) return defaultValue;

                return HtmlNodeHelper.trimHtmlString(str.substring(start + offsetLeft, end + offsetRight));
            } catch (JSONException e) {
                SpiderDebug.log(e);
            }
            return defaultValue;
        }

        public static String findSubString(String str, int startPos, JSONArray keys) {
            return findSubString(str, startPos, keys, "");
        }

        /**
         * 获取回看层数
         */
        public static int getLookbackCount(JSONArray keys) {
            try {
                if (keys != null && keys.length() > 4) return keys.getInt(4);
            } catch (Exception e) {
                SpiderDebug.log("getLookbackCount 解析失败，回退为0: " + e.getMessage());
            }
            return 0;
        }

        /**
         * 遍历JSONObject查找可用的回溯规则
         */
        public static JSONArray getLookbackArray(JSONObject obj) {
            try {
                JSONArray preferred = null;
                Iterator<?> iter = obj.keys();
                while (iter.hasNext()) {
                    String key = (String) iter.next();
                    Object val = obj.get(key);
                    if (val instanceof JSONArray) {
                        int count = getLookbackCount((JSONArray) val);
                        if (count > 0) {
                            // 优先返回名为 "vod" 的 lookback（数组规则），避免与 vod_id 等字段冲突
                            if ("vod".equals(key)) return (JSONArray) val;
                            if (preferred == null) preferred = (JSONArray) val;
                        }
                    }
                }
                return preferred;
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
            return null;
        }

        /**
         * 统计子串出现次数
         */
        public static int getSubStringCount(String str, String sub) {
            // 空子串防护：indexOf("") 恒返回 pos 且 pos+=0，此循环将永不终止
            // （空前缀规则如 "&&</div>" 经 stringCutToLookback 后即产生空匹配串）
            if (sub == null || sub.isEmpty()) return 0;
            int pos = 0;
            int count = 0;
            while (pos < str.length()) {
                pos = str.indexOf(sub, pos);
                if (pos == -1) break;
                pos += sub.length();
                ++count;
            }
            return count;
        }

        /**
         * 获取指定区域的字符串
         */
        public static String getRegion(String str, JSONObject obj) {
            try {
                if (obj == null) return str;
                JSONArray region = obj.optJSONArray("region");
                if (region == null) return str;
                String prefix = region.getString(0);
                int start = str.indexOf(prefix);
                if (start == -1) return str;
                int end = str.length();
                if (region.length() > 1) {
                    end = str.indexOf(region.getString(1), start + prefix.length());
                    if (end == -1) end = str.length();
                }
                return str.substring(start, end);
            } catch (JSONException e) {
                SpiderDebug.log(e);
            }
            return str;
        }
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化爬虫
     * @param context 上下文
     * @param extend 扩展配置（JSON字符串或URL）
     */
    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        this.ext = extend;
    }

    // ==================== 标准模板适配层（网页解析标准模板 4+1 接口） ====================
    //
    // 标准模板对外只暴露「分类 / 列表 / 详情 / 播放解析 / 搜索」五个方法加一组 http 工具。
    // 本层把 XBPQ 的规则引擎包装成模板签名，内部全部复用已有实现，不另起炉灶。
    //
    // 返回信封统一为：
    //   列表/搜索：{code,msg,page,pagecount,limit,total,list:[{vod_id,vod_name,vod_pic,vod_remarks}]}
    //   详情    ：{code,msg,data:{...},list:[{...}]}（data 供模板消费，list 供三平台消费）
    //   分类    ：影视仓 {"code","msg","list":[{type_id,type_name}]}；海螺/苹果直接输出数组
    //
    // 分隔符（三平台一致）：线路 $$$ ；同线多集 # ；集名与地址 $
    // ================================================================================

    /** 标准成功码 */
    public static final int CODE_OK = 200;
    /** 标准失败码 */
    public static final int CODE_FAIL = 500;
    /** 线路分隔符（多条播放源） */
    public static final String SEP_FROM = "$$$";
    /** 集数分隔符（同一线路多集） */
    public static final String SEP_EPISODE = "#";
    /** 集名与播放地址分隔符 */
    public static final String SEP_URL = "$";

    /**
     * 当前源主页地址（等同标准模板的 static BASE_URL）。
     * 设为实例级：CatVod 多源共存时静态字段会互串，实例字段天然隔离。
     */
    public String BASE_URL = "";

    /** 平台：catvod=影视仓（分类外层需包 list）；apple=苹果；hl=海螺 */
    private String platform = "catvod";

    /**
     * 同步标准模板字段：BASE_URL / platform 与规则保持一致。
     * <p>
     * 平台差异（见标准模板三平台对照表）：影视仓分类必须包 {@code {"list":[]}}，
     * 海螺、苹果直接输出数组；播放分隔符三平台完全一致。
     */
    private void applyStandardTemplateConfig() {
        try {
            BASE_URL = rule.optString("homeUrl", "");
            String p = getRuleVal("platform").trim().toLowerCase();
            platform = p.isEmpty() ? "catvod" : p;
        } catch (Exception e) {
            SpiderDebug.log("applyStandardTemplateConfig: " + e.getMessage());
        }
    }

    /** 当前源主页地址（{@link #BASE_URL} 的 getter，规则未加载时先加载） */
    public String getBaseUrl() {
        fetchRule();
        return BASE_URL;
    }

    /** 覆盖主页地址（同步回规则的 homeUrl，使后续相对路径补全生效） */
    public void setBaseUrl(String url) {
        try {
            fetchRule();
            BASE_URL = url == null ? "" : url.trim();
            if (rule != null && !BASE_URL.isEmpty()) rule.put("homeUrl", BASE_URL);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /** 是否「海螺/苹果」平台（分类直接返回裸数组，不包 list） */
    protected boolean isBareArrayPlatform() {
        return "hl".equals(platform) || "apple".equals(platform);
    }

    // ------------------------------------------------------------------
    // 【标准模板 1】分类
    // ------------------------------------------------------------------

    /**
     * 【标准模板 1】分类获取。
     *
     * @return 影视仓：{@code {"code":200,"msg":"ok","list":[{"type_id":"1","type_name":"电影"}]}}；
     *         海螺/苹果：直接输出 {@code [{"type_id":"1","type_name":"电影"}]}
     */
    public String getCategory() throws Exception {
        fetchRule();
        JSONArray classes = rule == null ? new JSONArray() : buildClassList(false);
        if (isBareArrayPlatform()) return classes.toString();
        // 影视仓 CatVod：分类接口不能直接返回数组，必须包一层 list，否则分类空白
        JSONObject result = new JSONObject();
        result.put("code", CODE_OK);
        result.put("msg", "ok");
        result.put("list", classes);
        return result.toString();
    }

    // ------------------------------------------------------------------
    // 【标准模板 2】列表
    // ------------------------------------------------------------------

    /**
     * 【标准模板 2】分类列表。
     *
     * @param tid  分类ID
     * @param page 页码（从 1 开始，非法值由引擎回退为 1）
     * @return {@code {"code":200,"msg":"ok","page":1,"pagecount":N,"limit":N,"total":N,"list":[...]}}
     */
    public String getVideoList(String tid, String page) throws Exception {
        return categoryContent(tid, page == null ? "1" : page, false, new HashMap<String, String>());
    }

    // ------------------------------------------------------------------
    // 【标准模板 3】详情
    // ------------------------------------------------------------------

    /**
     * 【标准模板 3】详情。
     *
     * @param vid 影片ID（裸ID 或本类产出的 base64 包裹ID 均可）
     * @return {@code {"code":200,"msg":"ok","data":{...},"list":[{...}]}}
     */
    public String getDetail(String vid) throws Exception {
        List<String> ids = new ArrayList<>();
        ids.add(encodeId(vid));
        return detailContent(ids);
    }

    /**
     * 把 vid 包装成 {@link #detailContent} 需要的 id。
     * 已是本类产出的 base64 包裹（内部为 JSON 对象）时原样透传，避免二次编码。
     */
    protected String encodeId(String vid) {
        if (vid == null || vid.isEmpty()) return "";
        try {
            String plain = new String(Base64.decode(vid, BASE64_FLAG), "UTF-8");
            if (plain.trim().startsWith("{")) {
                new JSONObject(plain);
                return vid;
            }
        } catch (Exception ignored) {
            // 不是 base64 包裹，走下面的包装分支
        }
        try {
            JSONObject o = new JSONObject();
            o.put("vod_id", vid);
            return Base64.encodeToString(o.toString().getBytes(StandardCharsets.UTF_8), BASE64_FLAG);
        } catch (Exception e) {
            SpiderDebug.log("encodeId error: " + e.getMessage());
            return "";
        }
    }

    // ------------------------------------------------------------------
    // 【标准模板 4】搜索
    // ------------------------------------------------------------------

    /**
     * 【标准模板 4】搜索。
     *
     * @param key  关键词（内部自动 URL 编码）
     * @param page 页码
     * @return 同 {@link #getVideoList} 的列表信封
     */
    public String getSearch(String key, String page) throws Exception {
        return searchContent(key, false, page == null ? "1" : page);
    }

    // ------------------------------------------------------------------
    // 【标准模板 5】播放解析
    // ------------------------------------------------------------------

    /**
     * 【标准模板 5】播放地址解析。
     *
     * @param url 选集链接
     * @return {@code {"parse":0,"playUrl":"","url":"..."}} 形态的播放结果
     */
    public String playParse(String url) throws Exception {
        return playerContent("", url, new ArrayList<String>());
    }

    // ------------------------------------------------------------------
    // 标准信封
    // ------------------------------------------------------------------

    /**
     * 标准列表信封：{@code {code,msg,page,pagecount,limit,total,list}}。
     * <p>
     * 空页时 pagecount 回填「当前页-1」（第 1 页空则为 0），让前端停止展示「下一页」；
     * 有内容时维持 Integer.MAX_VALUE（引擎无法预知真实总页数）。
     * 瞬时请求失败导致的误判代价仅是少翻一页，重新进入分类即可恢复。
     */
    protected JSONObject wrapList(JSONArray videos, String pg) throws JSONException {
        int size = videos == null ? 0 : videos.length();
        JSONObject result = new JSONObject();
        result.put("code", CODE_OK);
        result.put("msg", "ok");
        result.put("page", pg == null || pg.trim().isEmpty() ? "1" : pg.trim());
        if (size == 0) {
            int page = 1;
            try {
                page = Integer.parseInt(String.valueOf(pg).trim());
            } catch (Exception ignored) {
                // 非数字页码按第 1 页处理
            }
            result.put("pagecount", Math.max(0, page - 1));
            result.put("total", 0);
        } else {
            result.put("pagecount", Integer.MAX_VALUE);
            result.put("total", Integer.MAX_VALUE);
        }
        result.put("limit", Math.max(90, size));
        result.put("list", videos == null ? new JSONArray() : videos);
        return result;
    }

    // ------------------------------------------------------------------
    // 标准模板的 http 工具（全部复用既有链路，不另建 OkHttp 客户端）
    // ------------------------------------------------------------------

    /**
     * 【标准模板】GET 请求。
     * 走完整链路：{{变量}} 替换 → SSRF 校验 → 公共请求头合并 → 反爬绕过 → 响应清洗。
     */
    public String httpGet(String url) {
        return fetchUrl(url, null);
    }

    /** GET 请求（附加自定义请求头，JSON 对象形式） */
    public String httpGet(String url, JSONObject headers) {
        return fetchUrl(url, headers);
    }

    /** GET 请求（失败返回空串，不抛异常，容错模板） */
    public String httpGetSafe(String url) {
        try {
            return fetchUrl(url, null);
        } catch (Exception e) {
            SpiderDebug.log(safeLog("httpGetSafe error: " + e.getMessage()));
            return "";
        }
    }

    /**
     * GET 原始响应（跳过 HTML 注释/换行清洗，JSON 接口专用）。
     * 仍保留 SSRF 校验与公共请求头，安全性与其它请求路径一致。
     */
    public String httpGetRaw(String url) {
        try {
            if (isInternalUrl(url) && !"1".equals(getRuleVal("allow_internal"))) {
                SpiderDebug.log(safeLog("httpGetRaw SSRF blocked: " + url));
                return "";
            }
            return OkHttp.string(url, getHeaders(url));
        } catch (Exception e) {
            SpiderDebug.log(safeLog("httpGetRaw error: " + e.getMessage()));
            return "";
        }
    }

    /**【标准模板】POST 表单请求 */
    public String httpPost(String url, Map<String, String> params) {
        return fetchPostForm(url, params, null);
    }

    /** POST JSON 请求 */
    public String httpPostJson(String url, String json) {
        try {
            if (isInternalUrl(url) && !"1".equals(getRuleVal("allow_internal"))) {
                SpiderDebug.log(safeLog("httpPostJson SSRF blocked: " + url));
                return "";
            }
            return OkHttp.post(url, json, getHeaders(url));
        } catch (Exception e) {
            SpiderDebug.log(safeLog("httpPostJson error: " + e.getMessage()));
            return "";
        }
    }

    // ------------------------------------------------------------------
    // JSON 接口模式（标准模板「异步 JSON-API」：Vue/React 站点直连后端接口）
    // 复用项目工具 Json.pathFindBy / Json.pathGet 做路径取值，不手写解析
    // ------------------------------------------------------------------

    /**
     * 按 JSON 路径抽取影片数组，输出标准 vod 对象数组。
     *
     * @param json     接口原始响应
     * @param path     数组路径（支持 {@code data.list}、{@code list[0].items} 等语法）
     * @param idKey    id 字段名（空则跳过）
     * @param nameKey  名称字段名
     * @param picKey   封面字段名
     * @param noteKey  备注字段名
     */
    protected JSONArray extractVideosByJson(String json, String path,
                                            String idKey, String nameKey,
                                            String picKey, String noteKey) {
        JSONArray videos = new JSONArray();
        if (json == null || json.isEmpty() || path == null || path.isEmpty()) return videos;
        try {
            JsonElement el = firstArray(Json.pathFindBy(Json.parse(json), path));
            if (el == null) return videos;
            Set<String> seen = new HashSet<>();
            for (JsonElement item : el.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                JsonObject o = item.getAsJsonObject();
                String id = jsonPick(o, idKey);
                String name = jsonPick(o, nameKey);
                if (id.isEmpty() && name.isEmpty()) continue;
                if (!id.isEmpty()) {
                    if (seen.contains(id)) continue;
                    seen.add(id);
                }
                JSONObject v = new JSONObject();
                v.put("vod_id", applyIdAffix(id));
                v.put("vod_name", name);
                v.put("vod_pic", addHttpPrefix(jsonPick(o, picKey)));
                v.put("vod_remarks", jsonPick(o, noteKey));
                v.put("vod_id", encodeVodId(v));
                videos.put(v);
            }
        } catch (Exception e) {
            SpiderDebug.log("JSON 模式解析失败: " + e.getMessage());
        }
        return videos;
    }

    /**
     * 按 JSON 路径抽取分类数组，输出 {@code [{type_id,type_name}]}。
     * 由 {@code 分类JSON列表(catjsonlist)} 等字段驱动。
     */
    protected JSONArray extractCategoriesByJson(String json, String path,
                                                String idKey, String nameKey) {
        JSONArray classes = new JSONArray();
        if (json == null || json.isEmpty() || path == null || path.isEmpty()) return classes;
        try {
            JsonElement el = firstArray(Json.pathFindBy(Json.parse(json), path));
            if (el == null) return classes;
            for (JsonElement item : el.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                JsonObject o = item.getAsJsonObject();
                String id = jsonPick(o, idKey);
                String name = jsonPick(o, nameKey);
                if (id.isEmpty() || name.isEmpty()) continue;
                JSONObject c = new JSONObject();
                c.put("type_id", id);
                c.put("type_name", name);
                classes.put(c);
            }
        } catch (Exception e) {
            SpiderDebug.log("分类 JSON 模式解析失败: " + e.getMessage());
        }
        return classes;
    }

    /** 取元素本身；路径指向对象时取其内部第一个数组（容错：path 少写一层） */
    private static JsonElement firstArray(JsonElement el) {
        if (el == null) return null;
        if (el.isJsonArray()) return el;
        if (!el.isJsonObject()) return null;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonArray()) return e.getValue();
        }
        return null;
    }

    /**
     * 从 JsonObject 取值：普通字段名直接取；含 {@code .} 或 {@code [} 时按路径取。
     */
    private static String jsonPick(JsonObject obj, String key) {
        if (obj == null || key == null || key.isEmpty()) return "";
        try {
            if (key.indexOf('.') >= 0 || key.indexOf('[') >= 0) {
                List<String> got = Json.pathGet(obj.toString(), key, "");
                return got.isEmpty() ? "" : got.get(0);
            }
            return Json.getString(obj, key);
        } catch (Exception e) {
            return "";
        }
    }

    /** 套用 链接前缀/链接后缀（与正则模式保持一致） */
    private String applyIdAffix(String id) {
        if (id == null || id.isEmpty()) return "";
        return getRuleVal("list_prefix") + id + getRuleVal("list_suffix");
    }

    /**
     * 分类的 JSON 接口模式：{@code 分类JSON列表(catjsonlist)} 非空时优先走后端接口取分类。
     *
     * @return 命中并产出分类返回 true；否则 false，由调用方回退到网页解析
     */
    private boolean tryBuildFromJson(JSONArray classes) throws JSONException {
        String jsonPath = getRuleVal("catjsonlist");
        if (jsonPath.isEmpty()) return false;
        String url = rule.optString("class_url", "");
        // 含分类占位符的 URL 是网页路由，不是固定接口，回退到主页地址
        if (url.contains("{cateId}")) url = "";
        if (url.isEmpty()) url = rule.optString("homeUrl", "");
        if (url.isEmpty()) return false;
        String json = httpGetRaw(addHttpPrefix(url));
        if (json.isEmpty()) return false;
        JSONArray parsed = extractCategoriesByJson(json, jsonPath,
                getRuleVal("catjsonid"), getRuleVal("catjsonname"));
        for (int i = 0; i < parsed.length(); i++) classes.put(parsed.get(i));
        return classes.length() > 0;
    }

    /**
     * 将JSON中的中文字段名转换为英文Key
     * 支持递归处理嵌套对象和数组
     */
    protected JSONObject convertChineseKeys(JSONObject json) {
        try {
            // 转换前的原始键集合：中文键与英文键同时存在时，中文键值优先（见 使用说明 注意事项）
            Set<String> originalKeys = new HashSet<>();
            Iterator<String> originIt = json.keys();
            while (originIt.hasNext()) originalKeys.add(originIt.next());

            // 收集要重命名的键（避免边遍历边修改）
            List<String> toRename = new ArrayList<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (CHINESE_KEY_MAP.containsKey(key)) {
                    toRename.add(key);
                }
            }
            // 执行重命名
            for (String key : toRename) {
                String enKey = CHINESE_KEY_MAP.get(key);
                Object val = json.get(key);
                json.remove(key);
                // 防覆盖：多个中文键别名映射到同一英文键时（如「搜索后缀」「搜索链接后缀」→search_suffix）。
                // org.json 键序不确定，不能简单「保留首个」：已有值为空占位（空串/空/&&）而新值有效时用新值补位；
                // 中文键与原始英文键同时配置且中文值有效时，中文键优先（对齐使用说明约定）
                if (!json.has(enKey)
                        || (isEmptyRuleVal(json.opt(enKey)) && !isEmptyRuleVal(val))
                        || (originalKeys.contains(enKey) && !isEmptyRuleVal(val))) {
                    json.put(enKey, val);
                }
            }
            // 未映射中文键告警：文档宣称但未实现的键、或拼写错误都会走到这里，避免规则静默失效
            List<String> unknownKeys = new ArrayList<>();
            Iterator<String> allIt = json.keys();
            while (allIt.hasNext()) {
                String key = allIt.next();
                if (!CHINESE_KEY_MAP.containsKey(key) && containsCjk(key)) unknownKeys.add(key);
            }
            if (!unknownKeys.isEmpty()) {
                SpiderDebug.log("XBPQ 未识别的中文键(已按原样保留): " + TextUtils.join(", ", unknownKeys));
            }
            // 递归处理嵌套对象
            List<String> allKeys = new ArrayList<>();
            keys = json.keys();
            while (keys.hasNext()) allKeys.add(keys.next());
            for (String key : allKeys) {
                Object val = json.get(key);
                if (val instanceof JSONObject) {
                    json.put(key, convertChineseKeys((JSONObject) val));
                } else if (val instanceof JSONArray) {
                    JSONArray arr = (JSONArray) val;
                    for (int i = 0; i < arr.length(); i++) {
                        if (arr.get(i) instanceof JSONObject) {
                            arr.put(i, convertChineseKeys((JSONObject) arr.get(i)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return json;
    }

    /** 规则空占位值判断（与 getRuleVal 的「空」语义一致） */
    private static boolean isEmptyRuleVal(Object val) {
        if (!(val instanceof String)) return false;
        String s = (String) val;
        return s.isEmpty() || "空".equals(s) || "&&".equals(s);
    }

    /** 是否含中日韩统一表意文字（用于识别未映射的中文键） */
    private static boolean containsCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    /**
     * 获取规则值
     * 处理"空"、"&&"等特殊值
     * @param key 规则键
     * @param defaultValue 默认值
     * @return 规则值
     */
    protected String getRuleVal(String key, String defaultValue) {
        if (rule == null) return defaultValue;
        String value = rule.optString(key, "");
        if (value.isEmpty() || "空".equals(value) || "&&".equals(value)) {
            return defaultValue;
        }
        return value;
    }

    protected String getRuleVal(String key) {
        return getRuleVal(key, "");
    }

    /**
     * 获取并解析规则配置
     * 这是核心初始化方法，负责加载和转换所有规则
     */
    protected void fetchRule() {
        if (rule == null) {
            if (ext != null) {
                try {
                    JSONObject rawRule;
                    if (ext.startsWith("http")) {
                        // URL形式：检查是否为简单URL模板
                        if (ext.contains("{cateId}") || ext.contains("{catePg}")) {
                            rule = new JSONObject();
                            rule.put("homeUrl", ext);
                        } else {
                            String json = OkHttp.string(ext, null);
                            rawRule = new JSONObject(json);
                            rule = convertChineseKeys(rawRule);
                        }
                    } else {
                        // JSON字符串形式
                        rawRule = new JSONObject(ext);
                        rule = convertChineseKeys(rawRule);
                    }

                    initializeRuleConfig();
                    initEnhancedConfig();
                    applyPrefMenu();
                    applyStandardTemplateConfig();

                    SpiderDebug.log(sanitizeRuleLog(rule));
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        }
    }

    /** 敏感规则键（不区分大小写）：日志输出时掩码，避免 Cookie/密钥 等凭据泄漏到日志 */
    private static final Set<String> SENSITIVE_RULE_KEYS = new HashSet<>(Arrays.asList(
            "cookie", "secretkey", "password", "passwd", "pwd",
            "token", "accesstoken", "authorization", "auth",
            "header", "headerjson", "userheader", "key", "sign", "signkey", "secret"));

    /**
     * 生成规则的安全日志文本：
     * <ul>
     *   <li>未开启调试时仅打印顶层键名列表，绝不输出值；</li>
     *   <li>开启调试时打印完整结构，但敏感键的值一律掩码。</li>
     * </ul>
     * 防止 homeUrl/search_url 之外的 Cookie、secretKey、header 等凭据被明文打印（旧实现直接 rule.toString()）。
     */
    private static String sanitizeRuleLog(JSONObject ruleObj) {
        try {
            if (!debug) {
                JSONArray keys = ruleObj.names();
                StringBuilder sb = new StringBuilder("已加载规则 keys=[");
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(keys.getString(i));
                    }
                }
                sb.append("]");
                return sb.toString();
            }
            JSONObject safe = new JSONObject();
            Iterator<String> it = ruleObj.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (SENSITIVE_RULE_KEYS.contains(k.toLowerCase())) {
                    safe.put(k, "***掩码***");
                } else {
                    safe.put(k, ruleObj.get(k));
                }
            }
            return "默认rule: " + safe.toString();
        } catch (Exception e) {
            return "默认rule: <日志生成失败>";
        }
    }

    /**
     * 初始化规则配置的详细设置
     */
    private void initializeRuleConfig() throws JSONException, MalformedURLException {
        // 兼容旧字段：list.url → class_url
        if (!rule.has("class_url") && rule.has("list") && rule.getJSONObject("list").has("url")) {
            rule.put("class_url", rule.getJSONObject("list").getString("url"));
        }

        // 处理视频格式配置
        processVideoFormatConfig();

        // 确保 list 对象存在
        if (!rule.has("list")) {
            rule.put("list", new JSONObject());
        }
        JSONObject list = rule.getJSONObject("list");

        // 初始化 homeUrl 和 list.url
        initializeHomeUrl(list);

        // 初始化截断标志
        initializeSplitFlag(list);

        // 确保 detail 对象存在
        if (!rule.has("detail")) {
            rule.put("detail", new JSONObject());
        }

        // 确保 playlist 对象存在
        if (!rule.has("playlist")) {
            rule.put("playlist", new JSONObject());
        }

        // 初始化搜索配置
        initializeSearchConfig();

        // 初始化播放配置
        initializePlayConfig();

        // 注入扁平字段到嵌套对象
        injectFlatFieldsToList(list);

        // 应用字符串截取规则
        applyAllStringCutRules(list);

        // 处理 playlist 扁平字段
        processPlaylistFlatFields();

        // 处理详情扁平字段（影片名称/类型/年份/地区/状态/主演/导演/简介）
        processDetailFlatFields();

        // 处理线路二次截取和多线字段
        processLineConfigs();

        // 设置倒序开关
        reverseOrder = "1".equals(getRuleVal("reverse"));

        // 猜测列表 vod_id 规则（首页请求在后续列表解析流程中本就会发生，边际成本低；
        // 分类与搜索猜测已延迟到各自消费点，避免初始化阶段同步网络请求拖慢首次数据返回）
        guessVodIdIfNeeded(list);

        // 初始化CSS选择器规则（新增）
        initializeCssRules(list);
    }

    /**
     * 处理视频格式配置
     */
    private void processVideoFormatConfig() {
        if (rule.has("video_format")) {
            String vf = rule.optString("video_format", "");
            if (!vf.isEmpty()) {
                videoFormatList.clear();
                for (String f : vf.split("#")) {
                    if (!f.trim().isEmpty()) videoFormatList.add(f.trim());
                }
            }
        }
    }

    /**
     * 初始化 homeUrl 和 list.url
     */
    private void initializeHomeUrl(JSONObject list) throws JSONException, MalformedURLException {
        // 主页地址缺省时从 分类url/列表url 推导：大量规则只写「分类url」，
        // 旧实现直接 rule.getString("homeUrl") 抛异常，会中断整个 initializeRuleConfig，
        // 导致搜索配置、list.url、标准模板配置全部不生效（分类/列表/搜索全空）。
        if (rule.optString("homeUrl", "").isEmpty()) {
            String derived = deriveHomeUrl(rule.optString("class_url", ""));
            if (derived.isEmpty() && list != null) derived = deriveHomeUrl(list.optString("url", ""));
            if (derived.isEmpty() && list != null) derived = deriveHomeUrl(getRuleVal("list_url"));
            if (!derived.isEmpty()) {
                rule.put("homeUrl", derived);
                SpiderDebug.log("未配置 主页url，已由分类url推导: " + derived);
            }
        }
        if (rule.optString("homeUrl", "").isEmpty()) {
            // 仍拿不到主页地址：相对路径补全只能原样返回，此处不再抛出，保证其余配置继续初始化
            SpiderDebug.log("警告: 未配置 主页url 且无法推导，相对链接将无法补全");
            return;
        }
        String homeUrl = rule.getString("homeUrl");
        if (homeUrl.contains("{cateId}")) {
            URL url = new URL(homeUrl);
            String path = url.getPath();
            rule.put("homeUrl", homeUrl.substring(0, homeUrl.indexOf(path)));
            if (!list.has("url")) {
                list.put("url", homeUrl);
            }
        }
        // class_url 未写入 list.url 时兜底
        if (!list.has("url")) {
            String classUrl = rule.optString("class_url", "");
            if (!classUrl.isEmpty()) {
                list.put("url", classUrl);
            }
        }
    }

    /**
     * 从任意站点 URL 推导主页地址（协议+主机+端口）。
     * 会先剥离 {@code ;;} 后缀与未赋值的 {占位符}，再取 origin。
     *
     * @return 形如 {@code https://www.example.com}；无法解析时返回空串
     */
    private static String deriveHomeUrl(String rawUrl) {
        if (rawUrl == null) return "";
        String u = rawUrl.trim();
        if (u.isEmpty()) return "";
        int sep = u.indexOf(";;");
        if (sep >= 0) u = u.substring(0, sep);
        // 占位符用具体值替换后再解析，避免 URL 解析失败
        u = u.replace("{cateId}", "1")
                .replace("{catePg}", "1")
                .replace("{catepg}", "1")
                .replace("{pg}", "1")
                .replace("{wd}", "x");
        // 其余未赋值占位符直接移除
        u = u.replaceAll("\\{[^}]*\\}", "").trim();
        if (!u.contains("://")) return "";
        try {
            URL parsed = new URL(u);
            String host = parsed.getHost();
            if (host == null || host.isEmpty()) return "";
            return parsed.getProtocol() + "://" + host + (parsed.getPort() > 0 ? ":" + parsed.getPort() : "");
        } catch (MalformedURLException e) {
            return "";
        }
    }

    /**
     * 初始化截断标志
     */
    private void initializeSplitFlag(JSONObject list) {
        String listUrl = list.optString("url", "");
        if (listUrl.contains("/")) splitFlag += '/';
        if (listUrl.contains(".")) splitFlag += '.';
        if (listUrl.contains("-")) splitFlag += '-';
    }

    /**
     * 初始化搜索配置
     */
    private void initializeSearchConfig() throws JSONException {
        boolean hasFlatSearch = !getRuleVal("search_url").isEmpty()
                || !getRuleVal("search_array").isEmpty()
                || !getRuleVal("search_name").isEmpty()
                || !getRuleVal("search_pic").isEmpty()
                || !getRuleVal("search_id").isEmpty();

        Object searchObjRaw = rule.opt("search");
        boolean hasSearchStrFormat = searchObjRaw instanceof String && !((String) searchObjRaw).isEmpty();

        // 如果没有搜索配置且没有扁平搜索字段，生成默认suggest搜索
        if (!rule.has("search") && !hasFlatSearch && !hasSearchStrFormat) {
            generateDefaultSearchConfig();
        }

        // 将字符串格式的 search 转换为 JSONObject 格式
        if (hasSearchStrFormat) {
            convertSearchStringToJson((String) searchObjRaw);
        }

        // 有扁平搜索字段时强制覆盖
        if (hasFlatSearch) {
            applyFlatSearchFields();
        }
    }

    /**
     * 生成默认搜索配置（suggest模式）
     */
    private void generateDefaultSearchConfig() {
        try {
            String url = addHttpPrefix("index.php/ajax/suggest?mid=1&wd=阿凡达");
            JSONObject result = new JSONObject(OkHttp.string(url, getHeaders(url)));
            JSONObject search = new JSONObject();
            search.put("vod_id", "id");
            search.put("vod_name", "name");
            search.put("vod_pic", "pic");
            search.put("url", addHttpPrefix("index.php/ajax/suggest?mid=1&wd={wd}"));
            rule.put("search", search);
        } catch (Exception e) {
            SpiderDebug.log("默认搜索配置生成失败（suggest接口不可用）: " + e.getMessage());
        }
    }

    /**
     * 将字符串格式搜索转换为JSONObject
     */
    private void convertSearchStringToJson(String searchUrlStr) throws JSONException {
        JSONObject searchJson = new JSONObject();
        searchJson.put("url", addHttpPrefix(searchUrlStr));
        rule.put("search", searchJson);
    }

    /**
     * 应用扁平搜索字段
     */
    private void applyFlatSearchFields() throws JSONException {
        if (!rule.has("search")) {
            rule.put("search", new JSONObject());
        }
        JSONObject searchObj = rule.getJSONObject("search");
        String searchUrlFlat = getRuleVal("search_url");
        if (!searchUrlFlat.isEmpty()) {
            searchObj.put("url", searchUrlFlat);
        }

        String[][] flatSearchFields = {
                {"search_name", "vod_name"},
                {"search_pic", "vod_pic"},
                {"search_id", "vod_id"},
                {"search_remarks", "vod_remarks"}
        };
        for (String[] pair : flatSearchFields) {
            String value = getRuleVal(pair[0]);
            if (!value.isEmpty()) {
                JSONArray pairArr = stringCutPair(value);
                if (pairArr != null) searchObj.put(pair[1], pairArr);
            }
        }
    }

    /**
     * 初始化播放配置
     */
    private void initializePlayConfig() throws JSONException {
        if (!rule.has("play")) {
            JSONObject play = new JSONObject();
            JSONArray region = new JSONArray();
            region.put("var player_aaaa=");
            // 结束标记必须是字符串：数字等非字符串值经 getString() 变成字面量结束标记，
            // 会把 player JSON 在第一个 "0" 字符处（如 "trysee":0）截断
            region.put("</script>");

            JSONArray vodUrl = new JSONArray();
            vodUrl.put("\"url\":\"");
            // 结束标记不能为空串：findSubString 对空后缀恒返回空串，直链永远取不到
            vodUrl.put("\"");

            play.put("region", region);
            play.put("vod_url", vodUrl);
            rule.put("play", play);
        }

        // 处理自定义嗅探关键字
        processPlayKeywords();
    }

    /**
     * 处理播放关键字配置
     */
    private void processPlayKeywords() throws JSONException {
        if (rule.has("play")) {
            JSONObject play = rule.getJSONObject("play");
            JSONArray keywords = play.optJSONArray("keywords");
            if (keywords != null) {
                videoFormatList.clear();
                for (int i = 0; i < keywords.length(); ++i) {
                    videoFormatList.add(keywords.getString(i));
                }
            }
        }
    }

    /**
     * 注入扁平字段到 list 对象
     */
    private void injectFlatFieldsToList(JSONObject list) throws JSONException {
        String[][] flatListFields = {
                {"list_name", "vod_name"},
                {"list_pic", "vod_pic"},
                {"list_id", "vod_id"},
                {"list_remarks", "vod_remarks"}
        };
        for (String[] pair : flatListFields) {
            String value = getRuleVal(pair[0]);
            if (!value.isEmpty() && !list.has(pair[1])) {
                JSONArray pairArr = stringCutPair(value);
                if (pairArr != null) list.put(pair[1], pairArr);
            }
        }

        // 搜索侧同理
        JSONObject search = rule.optJSONObject("search");
        if (search != null) {
            for (String[] pair : flatListFields) {
                String value = getRuleVal(pair[0].replace("list_", "search_"));
                if (!value.isEmpty() && !search.has(pair[1])) {
                    JSONArray pairArr = stringCutPair(value);
                    if (pairArr != null) search.put(pair[1], pairArr);
                }
            }
        }
    }

    /**
     * 应用所有字符串截取规则
     */
    private void applyAllStringCutRules(JSONObject list) throws JSONException {
        // 数组规则必须存入 list.vod：extractVideoList 经 getLookbackArray 取节点级数组规则，
        // 且依赖「5 元素数组唯一属于数组规则」的约定（字段规则为 2 元素，见 stringCutPair）。
        // 旧实现存为 list.list，键序不定时 getLookbackArray 会误选字段数组，列表解析静默为空
        if (list != null && !list.has("vod")) {
            String listArray = getRuleVal("list_array");
            if (!listArray.isEmpty()) {
                JSONArray lookback = stringCutToLookback(applyOrSelector(listArray));
                if (lookback != null) list.put("vod", lookback);
            }
        }
        applyStringCutRules(rule.optJSONObject("search"), "search_array");
        applyStringCutRules(rule.optJSONObject("playlist"), "play_array");
        applyStringCutRules(rule.optJSONObject("playlist"), "from_array");
        applyStringCutRules(rule.optJSONObject("detail"), "detail_array");
    }

    /**
     * 判断位置是否位于 style/script 块内部：内联 CSS/JS 里的类名、属性文本
     * 会命中数组锚点（如 .article-list .article-item），这类命中不构成片单节点
     */
    private boolean insideNoParseBlock(String content, int pos) {
        int styleStart = content.lastIndexOf("<style", pos);
        if (styleStart >= 0) {
            int styleEnd = content.indexOf("</style", styleStart);
            if (styleEnd == -1 || styleEnd > pos) return true;
        }
        int scriptStart = content.lastIndexOf("<script", pos);
        if (scriptStart >= 0) {
            int scriptEnd = content.indexOf("</script", scriptStart);
            if (scriptEnd == -1 || scriptEnd > pos) return true;
        }
        return false;
    }

    /**
     * 字段规则转 2 元素数组 [前缀, 后缀]：仅供 findSubString 消费，
     * 不带回看层级——避免与数组规则在 getLookbackArray 中竞争
     */
    protected JSONArray stringCutPair(String rule) {        if (rule == null || rule.isEmpty()) return null;
        String cutRule = applyPostProcessors(applyOrSelector(rule));
        if (!cutRule.contains("&&")) return null;
        String[] parts = cutRule.split("&&", 2);
        JSONArray arr = new JSONArray();
        arr.put(parts[0].trim());
        arr.put(parts.length > 1 ? parts[1].trim() : "");
        return arr;
    }

    /**
     * 处理 playlist 扁平字段
     */
    private void processPlaylistFlatFields() throws JSONException {
        JSONObject playlist = rule.getJSONObject("playlist");

        // url_url / url_array → vod_play_url
        String urlUrl = getRuleVal("url_url");
        if (!urlUrl.isEmpty() && !playlist.has("vod_play_url")) {
            JSONArray pairArr = stringCutPair(urlUrl);
            if (pairArr != null) playlist.put("vod_play_url", pairArr);
        }
        if (!playlist.has("vod_play_url")) {
            String urlArray = getRuleVal("url_array");
            if (!urlArray.isEmpty()) {
                JSONArray pairArr = stringCutPair(urlArray);
                if (pairArr != null) playlist.put("vod_play_url", pairArr);
            }
        }

        // url_title → vod_play_url_title
        String urlTitle = getRuleVal("url_title");
        if (!urlTitle.isEmpty() && !playlist.has("vod_play_url_title")) {
            JSONArray pairArr = stringCutPair(urlTitle);
            if (pairArr != null) playlist.put("vod_play_url_title", pairArr);
        }
    }

    /**
     * 处理详情扁平字段：把 影片名称/类型/年份/地区/状态/主演/导演/简介 等
     * 顶层 && 截取规则注入 detail 对象（vod_name/type_name/vod_year/...），
     * 供 extractDetailFields 的 findSubString 消费。此前这些中文键只有映射、
     * 无任何消费点，配置后在真机上静默失效。
     */
    private void processDetailFlatFields() throws JSONException {
        JSONObject detail = rule.optJSONObject("detail");
        if (detail == null) return;
        String[][] flatDetailFields = {
                {"detail_name", "vod_name"},
                {"detail_type", "type_name"},
                {"detail_year", "vod_year"},
                {"detail_area", "vod_area"},
                {"detail_remarks", "vod_remarks"},
                {"detail_actor", "vod_actor"},
                {"detail_director", "vod_director"},
                {"detail_content", "vod_content"}
        };
        for (String[] pair : flatDetailFields) {
            String value = getRuleVal(pair[0]);
            // 详情字段依赖 前缀&&后缀 截取，非 && 写法无法构成规则数组，跳过并告警
            if (value.isEmpty() || detail.has(pair[1])) continue;
            if (!value.contains("&&")) {
                SpiderDebug.log("XBPQ 详情字段 " + pair[0] + " 未包含 && 截取语法，已忽略");
                continue;
            }
            JSONArray pairArr = stringCutPair(value);
            if (pairArr != null) detail.put(pair[1], pairArr);
        }
    }

    /**
     * 处理线路配置：验证并预处理线路二次截取和多线字段
     * <p>
     * 线路相关配置在 tryExtractFromArray / tryMultiLineMode / tryFromLinkMode 中使用，
     * 此处统一验证配置合法性并记录日志，便于排查问题。
     */
    private void processLineConfigs() {
        String lineSecondCut = getRuleVal("line_second_cut");
        String multiLineArray = getRuleVal("multi_line_array");
        String multiLineUrl = getRuleVal("multi_line_url");
        String multiLineTwice = getRuleVal("multi_line_twice");
        String multiLinePrefix = getRuleVal("multi_line_prefix");
        String multiLineSuffix = getRuleVal("multi_line_suffix");

        boolean hasLineConfig = !lineSecondCut.isEmpty()
                || (!multiLineArray.isEmpty() && !multiLineUrl.isEmpty())
                || !multiLineTwice.isEmpty();

        if (hasLineConfig) {
            SpiderDebug.log(String.format("线路配置: line_second_cut=%s, multi_line_array=%s, multi_line_url=%s",
                    lineSecondCut.isEmpty() ? "未配置" : lineSecondCut,
                    multiLineArray.isEmpty() ? "未配置" : multiLineArray,
                    multiLineUrl.isEmpty() ? "未配置" : multiLineUrl));
        }

        // 验证多线模式配置完整性
        if (!multiLineArray.isEmpty() ^ !multiLineUrl.isEmpty()) {
            SpiderDebug.log("警告: multi_line_array 和 multi_line_url 需同时配置才能启用多线模式");
        }
    }

    /**
     * 首页内容缓存（避免重复请求）
     * Minor23：增加 TTL 过期判断，防止长会话中站点改版/翻页后一直使用陈旧首页
     */
    private String cachedHomePageBody = null;
    /** 首页缓存写入时间戳（SystemClock.elapsedRealtime） */
    private long cachedHomePageBodyAt = 0L;
    /** 首页缓存 TTL：5 分钟 */
    private static final long HOME_CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 尝试猜测分类规则（如果用户未显式配置）
     */
    private void guessCateManualIfNeeded() {
        try {
            JSONObject cateManual = rule.optJSONObject("cateManual");
            String body = fetchOrCacheHomePageBody();
            boolean hasExplicitCate = !getRuleVal("fenlei").isEmpty()
                    || (!getRuleVal("class_name").isEmpty() && !getRuleVal("class_value").isEmpty());

            if (cateManual == null && !hasExplicitCate) {
                cateManual = guessRuleCateManual(body);
                if (cateManual != null) {
                    rule.put("cateManual", cateManual);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 猜测列表 vod_id 规则
     */
    private void guessVodIdIfNeeded(JSONObject list) {
        try {
            if (!list.has("vod_id")) {
                String body = fetchOrCacheHomePageBody();
                JSONArray listVodId = guessRuleVodId(body);
                list.put("vod_id", listVodId);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 猜测搜索URL规则
     */
    private void guessSearchUrlIfNeeded() {
        try {
            if (!rule.has("search")) {
                String body = fetchOrCacheHomePageBody();
                String url = guessRuleSearchUrl(body);
                if (!url.isEmpty()) {
                    JSONObject searchRule = new JSONObject();
                    searchRule.put("url", url);
                    rule.put("search", searchRule);
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 获取首页内容（带缓存，避免重复请求）
     * Minor23：TTL 过期后重新拉取，防止陈旧首页污染猜测规则
     */
    private String fetchOrCacheHomePageBody() {
        boolean expired = cachedHomePageBody == null
                || SystemClock.elapsedRealtime() - cachedHomePageBodyAt >= HOME_CACHE_TTL_MS;
        if (expired) {
            cachedHomePageBody = fetchHomePageBody();
            cachedHomePageBodyAt = SystemClock.elapsedRealtime();
        }
        return cachedHomePageBody;
    }

    /**
     * 获取首页内容（带长度限制）
     */
    private String fetchHomePageBody() {
        try {
            String body = fetchUrl(rule.getString("homeUrl"), rule.optJSONObject("header"));
            if (body.length() > MAX_HTML_LENGTH) {
                // Minor23：截断点回退到上一个 '>'（标签边界），
                // 防止把半个标签/半段文本喂给猜测规则导致前缀后缀错位
                body = body.substring(0, MAX_HTML_LENGTH);
                int tagEnd = body.lastIndexOf('>');
                if (tagEnd > 0) body = body.substring(0, tagEnd + 1);
            }
            return body;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    // ==================== CSS 选择器规则初始化 ====================

    /**
     * 初始化CSS选择器规则配置
     * <p>
     * 将用户配置的CSS选择器规则转换为内部可用的格式，
     * 并注入到对应的 list/detail/search/playlist 对象中。
     *
     * @param list 规则中的list对象
     */
    private void initializeCssRules(JSONObject list) throws JSONException {
        // 检查是否启用了JSOUP解析模式
        boolean jsoupMode = "1".equals(getRuleVal("jsoup_parse", "0"));
        if (!jsoupMode && !hasAnyCssRule()) return;

        SpiderDebug.log("初始化CSS/Jsoup提取规则...");

        // 处理列表字段CSS规则
        initializeListCssRules(list);

        // 处理详情字段CSS规则
        initializeDetailCssRules();

        // 处理搜索字段CSS规则
        initializeSearchCssRules();

        // 处理播放列表CSS规则
        initializePlaylistCssRules();

        // 处理分类CSS规则
        initializeCategoryCssRules();

        SpiderDebug.log("CSS/Jsoup规则初始化完成");
    }

    /**
     * 检查是否存在任何CSS规则配置
     */
    private boolean hasAnyCssRule() {
        String[] cssKeys = {"css_selector", "list_css", "detail_css",
                           "search_css", "playlist_css", "cat_css"};
        for (String key : cssKeys) {
            if (rule.has(key)) return true;
        }
        // 检查各子对象的字段是否包含 css:// 前缀
        return hasCssPrefixInObject(rule.optJSONObject("list"));
    }

    /**
     * 递归检查JSON对象中是否有CSS前缀的值
     */
    private boolean hasCssPrefixInObject(JSONObject obj) {
        if (obj == null) return false;
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                Object val = obj.get(key);
                if (val instanceof String && JsoupExtractor.isCssRule((String) val)) {
                    return true;
                } else if (val instanceof JSONObject) {
                    if (hasCssPrefixInObject((JSONObject) val)) return true;
                }
            } catch (Exception e) {
                SpiderDebug.log("hasCssPrefixInObject 检查跳过 [" + key + "]: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * 初始化列表字段的CSS规则
     */
    private void initializeListCssRules(JSONObject list) throws JSONException {
        // 容器选择器
        String containerCss = getRuleVal("list_css_container");
        if (!containerCss.isEmpty() && !list.has("css_container")) {
            list.put("css_container", applyOrSelector(containerCss));
        }

        // 各字段CSS规则映射
        String[][] listCssFields = {
                {"list_name_css", "vod_name"},
                {"list_pic_css", "vod_pic"},
                {"list_id_css", "vod_id"},
                {"list_remarks_css", "vod_remarks"}
        };

        for (String[] pair : listCssFields) {
            String cssVal = getRuleVal(pair[0]);
            if (!cssVal.isEmpty()) {
                String processed = applyOrSelector(cssVal);
                if (JsoupExtractor.isCssRule(processed)) {
                    list.put(pair[1] + "_css", processed);
                    SpiderDebug.log(String.format("列表字段 %s 使用CSS规则: %s", pair[1], processed));
                }
            }
        }

        // 如果有容器选择器，标记使用CSS模式
        if (list.has("css_container")) {
            list.put("_use_css_mode", true);
        }
    }

    /**
     * 初始化详情字段的CSS规则
     */
    private void initializeDetailCssRules() throws JSONException {
        JSONObject detail = rule.optJSONObject("detail");
        if (detail == null) detail = new JSONObject();

        String[][] detailCssFields = {
                {"detail_content_css", "vod_content"},
                {"detail_director_css", "vod_director"},
                {"detail_actor_css", "vod_actor"},
                {"detail_type_css", "type_name"},
                {"detail_year_css", "vod_year"},
                {"detail_area_css", "vod_area"},
                {"detail_remarks_css", "vod_remarks"},
                {"detail_pic_css", "vod_pic"},
                {"detail_name_css", "vod_name"}
        };

        boolean hasCss = false;
        for (String[] pair : detailCssFields) {
            String cssVal = getRuleVal(pair[0]);
            if (!cssVal.isEmpty()) {
                String processed = applyOrSelector(cssVal);
                if (JsoupExtractor.isCssRule(processed)) {
                    detail.put(pair[1] + "_css", processed);
                    hasCss = true;
                }
            }
        }

        if (hasCss) {
            detail.put("_use_css_mode", true);
            if (!rule.has("detail")) rule.put("detail", detail);
        }
    }

    /**
     * 初始化搜索字段的CSS规则
     */
    private void initializeSearchCssRules() throws JSONException {
        JSONObject search = rule.optJSONObject("search");
        if (search == null) search = new JSONObject();

        String[][] searchCssFields = {
                {"search_name_css", "vod_name"},
                {"search_pic_css", "vod_pic"},
                {"search_id_css", "vod_id"},
                {"search_remarks_css", "vod_remarks"}
        };

        boolean hasCss = false;
        for (String[] pair : searchCssFields) {
            String cssVal = getRuleVal(pair[0]);
            if (!cssVal.isEmpty()) {
                String processed = applyOrSelector(cssVal);
                if (JsoupExtractor.isCssRule(processed)) {
                    search.put(pair[1] + "_css", processed);
                    hasCss = true;
                }
            }
        }

        if (hasCss) {
            search.put("_use_css_mode", true);
            if (!rule.has("search")) rule.put("search", search);
        }
    }

    /**
     * 初始化播放列表的CSS规则
     */
    private void initializePlaylistCssRules() throws JSONException {
        JSONObject playlist = rule.optJSONObject("playlist");
        if (playlist == null) playlist = new JSONObject();

        String fromCss = getRuleVal("from_array_css");
        if (!fromCss.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(fromCss))) {
            playlist.put("vod_play_from_css", applyOrSelector(fromCss));
        }

        String urlTitleCss = getRuleVal("url_title_css");
        if (!urlTitleCss.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(urlTitleCss))) {
            playlist.put("vod_play_url_title_css", applyOrSelector(urlTitleCss));
        }

        String urlUrlCss = getRuleVal("url_url_css");
        if (!urlUrlCss.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(urlUrlCss))) {
            playlist.put("vod_play_url_css", applyOrSelector(urlUrlCss));
        }

        // 播放数组CSS模式
        String playArrayCss = getRuleVal("play_array_css");
        if (!playArrayCss.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(playArrayCss))) {
            playlist.put("play_array_css", applyOrSelector(playArrayCss));
            playlist.put("_use_css_mode", true);
        }

        if (!rule.has("playlist") || playlist.optBoolean("_use_css_mode", false)) {
            rule.put("playlist", playlist);
        }
    }

    /**
     * 初始化分类的CSS规则
     */
    private void initializeCategoryCssRules() throws JSONException {
        String catArrayCss = getRuleVal("cat_array_css");
        if (!catArrayCss.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(catArrayCss))) {
            rule.put("cat_array_css", applyOrSelector(catArrayCss));
        }

        String catTitleCss = getRuleVal("cat_title_css");
        if (!catTitleCss.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(catTitleCss))) {
            rule.put("cat_title_css", applyOrSelector(catTitleCss));
        }

        String catIdCss = getRuleVal("cat_id_css");
        if (!catIdCss.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(catIdCss))) {
            rule.put("cat_id_css", applyOrSelector(catIdCss));
        }
    }

    // ==================== CSS 提取核心方法 ====================

    /**
     * 使用CSS选择器从HTML中提取视频列表（替代正则方式）
     *
     * @param html HTML内容
     * @param list 规则中的list对象
     * @return 视频列表JSONArray
     */
    protected JSONArray extractVideoListByCss(String html, JSONObject list) throws JSONException {
        JSONArray videos = new JSONArray();
        Set<String> seenIds = new HashSet<>();

        // 获取容器选择器
        String containerRule = list.optString("css_container", "");
        if (containerRule.isEmpty()) return videos;

        // 构建字段规则映射
        Map<String, String> fieldRules = new LinkedHashMap<>();
        putIfHasCss(fieldRules, list, "vod_id", "vod_id_css");
        putIfHasCss(fieldRules, list, "vod_name", "vod_name_css");
        putIfHasCss(fieldRules, list, "vod_pic", "vod_pic_css");
        putIfHasCss(fieldRules, list, "vod_remarks", "vod_remarks_css");

        if (fieldRules.isEmpty()) return videos;

        // 使用JsoupExtractor批量提取
        JSONArray items = JsoupExtractor.extractItems(html, containerRule, fieldRules);

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String vodId = item.optString("vod_id", "");
            if (!vodId.isEmpty()) {
                vodId = getRuleVal("list_prefix") + vodId + getRuleVal("list_suffix");
            }
            if (vodId.isEmpty() || seenIds.contains(vodId)) continue;

            // 过滤词检查
            if (shouldFilter(item.toString(), vodId, list)) continue;

            seenIds.add(vodId);

            // 补充缺失字段（回退到猜测方法）
            supplementMissingFields(item, html);

            // 编码
            item.put("vod_id", encodeVodId(item));

            videos.put(item);
        }

        // 倒序
        if (reverseOrder) videos = reverseArray(videos);
        return videos;
    }

    /**
     * 将CSS规则放入映射（如果存在）
     */
    private void putIfHasCss(Map<String, String> map, JSONObject obj,
                               String fieldName, String cssKey) {
        String cssRule = obj.optString(cssKey, "");
        if (!cssRule.isEmpty()) {
            map.put(fieldName, cssRule);
        }
    }

    /**
     * 补充缺失的字段（当CSS提取不完整时）
     * Minor24：接入已有的 guessValue* 猜测链兜底（此前仅打日志静默降级，
     * CSS 模式下缺图/缺名的条目直接展示为空白，体验劣化）
     */
    private void supplementMissingFields(JSONObject item, String html) {
        try {
            // 图片补充
            if (item.optString("vod_pic", "").isEmpty()) {
                String pic = guessValueVodPic(html, 0);
                if (!pic.isEmpty()) {
                    item.put("vod_pic", pic);
                } else {
                    SpiderDebug.log("CSS 提取缺少 vod_pic 字段，猜测兜底失败");
                }
            }
            // 名称补充
            String name = item.optString("vod_name", "");
            if (name.isEmpty()) {
                name = guessValueVodName(html, 0);
                if (!name.isEmpty()) {
                    item.put("vod_name", name);
                } else {
                    SpiderDebug.log("CSS 提取缺少 vod_name 字段，猜测兜底失败");
                }
            }
            // 备注补充（依赖名称，放最后）
            if (item.optString("vod_remarks", "").isEmpty() && !name.isEmpty()) {
                String remarks = guessValueVodRemarks(html, 0, name);
                if (!remarks.isEmpty()) item.put("vod_remarks", remarks);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 使用CSS选择器提取详情页信息
     *
     * @param html   详情页HTML
     * @param detail 规则中的detail对象
     * @return 详情信息JSONObject
     */
    protected JSONObject extractDetailByCss(String html, JSONObject detail) throws JSONException {
        JSONObject vod = new JSONObject();

        String[][] fields = {
                {"vod_name", "vod_name_css"},
                {"vod_pic", "vod_pic_css"},
                {"type_name", "type_name_css"},
                {"vod_year", "vod_year_css"},
                {"vod_area", "vod_area_css"},
                {"vod_remarks", "vod_remarks_css"},
                {"vod_actor", "vod_actor_css"},
                {"vod_director", "vod_director_css"},
                {"vod_content", "vod_content_css"}
        };

        for (String[] field : fields) {
            String cssKey = field[1];
            if (detail.has(cssKey)) {
                String value = JsoupExtractor.extractSingle(html, detail.getString(cssKey), null);
                if ("vod_pic".equals(field[0]) && !value.isEmpty()) {
                    value = addHttpPrefix(value);
                }
                vod.put(field[0], value);
            }
        }

        return vod;
    }

    /**
     * 使用CSS选择器提取搜索结果
     *
     * @param html   搜索结果HTML
     * @param search 规则中的search对象
     * @return 搜索结果JSONArray
     */
    protected JSONArray extractSearchResultsByCss(String html, JSONObject search) throws JSONException {
        JSONArray videos = new JSONArray();
        Set<String> seenIds = new HashSet<>();

        // 容器选择器
        String containerRule = search.optString("css_container", "");

        Map<String, String> fieldRules = new LinkedHashMap<>();
        putIfHasCss(fieldRules, search, "vod_id", "vod_id_css");
        putIfHasCss(fieldRules, search, "vod_name", "vod_name_css");
        putIfHasCss(fieldRules, search, "vod_pic", "vod_pic_css");
        putIfHasCss(fieldRules, search, "vod_remarks", "vod_remarks_css");

        if (fieldRules.isEmpty()) {
            // 无容器模式：逐个提取
            return extractSearchByCssIndividual(html, search);
        }

        JSONArray items = JsoupExtractor.extractItems(html, containerRule, fieldRules);

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String vodId = item.optString("vod_id", "");
            if (!vodId.isEmpty()) {
                vodId = getRuleVal("search_prefix") + vodId + getRuleVal("search_suffix");
            }
            if (vodId.isEmpty() || seenIds.contains(vodId)) continue;

            seenIds.add(vodId);
            item.put("vod_id", encodeVodId(item));
            videos.put(item);
        }

        if (reverseOrder) videos = reverseArray(videos);
        return videos;
    }

    /**
     * 无容器的逐个CSS搜索提取
     */
    private JSONArray extractSearchByCssIndividual(String html, JSONObject search) throws JSONException {
        JSONArray videos = new JSONArray();
        Set<String> seenIds = new HashSet<>();
        int maxCount = Math.min(
                RuleUtils.getSubStringCount(html, search.optString("vod_id_css", "").replace("css:", "")),
                30);

        for (int i = 0; i < maxCount; i++) {
            JSONObject v = new JSONObject();

            if (search.has("vod_id_css")) {
                String idRule = search.getString("vod_id_css").replace("@text", ":eq(" + i + ")@text");
                v.put("vod_id", JsoupExtractor.extractSingle(html, idRule, null));
            }
            if (search.has("vod_name_css")) {
                String nameRule = search.getString("vod_name_css").replace("@text", ":eq(" + i + ")@text");
                v.put("vod_name", JsoupExtractor.extractSingle(html, nameRule, null));
            }
            if (search.has("vod_pic_css")) {
                String picRule = search.getString("vod_pic_css").replace("@attr", ":eq(" + i + ")@attr");
                v.put("vod_pic", addHttpPrefix(JsoupExtractor.extractSingle(html, picRule, null)));
            }
            if (search.has("vod_remarks_css")) {
                String remarksRule = search.getString("vod_remarks_css").replace("@text", ":eq(" + i + ")@text");
                v.put("vod_remarks", JsoupExtractor.extractSingle(html, remarksRule, null));
            }

            if (!v.optString("vod_id", "").isEmpty()) {
                // 与容器模式对齐：应用 search_prefix/search_suffix 并按最终 id 去重
                String rawId = getRuleVal("search_prefix") + v.optString("vod_id", "") + getRuleVal("search_suffix");
                if (seenIds.contains(rawId)) continue;
                seenIds.add(rawId);
                v.put("vod_id", rawId);
                v.put("vod_id", encodeVodId(v));
                videos.put(v);
            }
        }
        return videos;
    }

    /**
     * 使用CSS选择器提取分类列表
     *
     * @param html 首页HTML
     * @return 分类JSONArray
     */
    protected JSONArray extractCategoriesByCss(String html) throws JSONException {
        JSONArray classes = new JSONArray();
        Set<String> seenNames = new HashSet<>();

        String titleCss = rule.optString("cat_title_css", "");
        String idCss = rule.optString("cat_id_css", "");

        if (titleCss.isEmpty() || idCss.isEmpty()) return classes;

        List<String> titles = JsoupExtractor.extractList(html, titleCss);
        List<String> ids = JsoupExtractor.extractList(html, idCss);

        int count = Math.min(titles.size(), ids.size());
        for (int i = 0; i < count; i++) {
            String name = titles.get(i).trim();
            String id = ids.get(i).trim();

            if (name.isEmpty() || id.isEmpty()) continue;
            if (seenNames.contains(name)) continue;
            if (isValidCategoryName(name)) {
                seenNames.add(name);
                JSONObject item = new JSONObject();
                item.put("type_name", name);
                item.put("type_id", id);
                classes.put(item);
            }
        }

        return classes;
    }

    /**
     * 使用CSS选择器提取播放线路名称
     *
     * @param html         播放页HTML
     * @param expectedSize 预期线路数量
     * @return 线路名称列表
     */
    protected List<String> extractPlayFromByCss(String html, int expectedSize) {
        List<String> result = new ArrayList<>();
        try {
            JSONObject playlist = rule.getJSONObject("playlist");
            String fromCss = playlist.optString("vod_play_from_css", "");
            if (fromCss.isEmpty()) return result;

            List<String> lines = JsoupExtractor.extractList(html, fromCss);
            for (String line : lines) {
                String cleaned = cleanHtml(line).trim();
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
                if (result.size() >= expectedSize) break;
            }

            // 使用 from_title 精炼
            if (!result.isEmpty()) {
                result = refinePlayFromNames(result);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 使用CSS选择器提取播放URL列表
     *
     * @param html 播放页HTML
     * @return 播放URL列表（每项为"标题$链接"格式，用#分隔集数）
     */
    protected List<String> extractPlayUrlByCss(String html) {
        List<String> result = new ArrayList<>();
        try {
            JSONObject playlist = rule.getJSONObject("playlist");

            // 模式1: play_array_css 分块提取
            String playArrayCss = playlist.optString("play_array_css", "");
            if (!playArrayCss.isEmpty()) {
                result = extractPlayUrlByCssBlocks(html, playArrayCss, playlist);
                if (!result.isEmpty()) return result;
            }

            // 模式2: 单一 vod_play_url_css 提取
            String urlCss = playlist.optString("vod_play_url_css", "");
            String titleCss = playlist.optString("vod_play_url_title_css", "");
            if (!urlCss.isEmpty()) {
                result = extractPlayUrlByCssSingle(html, urlCss, titleCss);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * CSS分块模式提取播放URL
     */
    private List<String> extractPlayUrlByCssBlocks(String html, String playArrayCss,
                                                     JSONObject playlist) {
        List<String> blocks = JsoupExtractor.extractList(html, playArrayCss);
        List<String> allEpisodes = new ArrayList<>();

        for (String block : blocks) {
            String urlCss = playlist.optString("vod_play_url_css", "");
            String titleCss = playlist.optString("vod_play_url_title_css", ">");

            List<String> episodes = extractEpisodesFromBlock(block, urlCss, titleCss);
            if (!episodes.isEmpty()) {
                allEpisodes.add(TextUtils.join("#", episodes));
            }
        }
        return allEpisodes;
    }

    /**
     * 从单个块中提取剧集
     */
    private List<String> extractEpisodesFromBlock(String block, String urlCss, String titleCss) {
        List<String> eps = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(block);
            JsoupExtractor.CssRuleInfo urlInfo = JsoupExtractor.parseRule(urlCss);
            JsoupExtractor.CssRuleInfo titleInfo = JsoupExtractor.parseRule(titleCss);

            Elements links = doc.select(urlInfo.selector);
            for (int i = 0; i < links.size(); i++) {
                Element link = links.get(i);
                String href = extractValueByMode(link, urlInfo.mode, urlInfo.attributeName).trim();
                if (href.isEmpty()) continue;

                String title = "";
                if (!titleCss.isEmpty() && titleInfo != null) {
                    // 尝试从同级或父级获取标题
                    Element titleEl = link.selectFirst(titleInfo.selector);
                    if (titleEl == null) titleEl = link.parent().selectFirst(titleInfo.selector);
                    if (titleEl != null) {
                        title = extractValueByMode(titleEl, titleInfo.mode, titleInfo.attributeName).trim();
                    }
                }
                if (title.isEmpty()) title = "第" + (eps.size() + 1) + "集";

                eps.add(title + "$" + addHttpPrefix(href));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return eps;
    }

    /**
     * CSS单一模式提取播放URL
     */
    private List<String> extractPlayUrlByCssSingle(String html, String urlCss, String titleCss) {
        List<String> result = new ArrayList<>();
        List<String> urls = JsoupExtractor.extractList(html, urlCss);
        List<String> titles = titleCss.isEmpty()
                ? new ArrayList<>()
                : JsoupExtractor.extractList(html, titleCss);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i).trim();
            if (url.isEmpty()) continue;
            String title = (i < titles.size()) ? titles.get(i).trim() : "第" + (i + 1) + "集";
            if (title.isEmpty()) title = "第" + (i + 1) + "集";
            if (sb.length() > 0) sb.append("#");
            sb.append(title).append("$").append(addHttpPrefix(url));
        }
        if (sb.length() > 0) {
            result.add(sb.toString());
        }
        return result;
    }

    /**
     * 根据模式从Element提取值（委托给 JsoupExtractor.extractValue，消除重复实现）
     */
    private static String extractValueByMode(Element el, JsoupExtractor.ExtractMode mode, String attrName) {
        return JsoupExtractor.extractValue(el, mode, attrName);
    }

    /**
     * 智能判断并执行提取（CSS优先，正则兜底）
     *
     * @param html      HTML内容
     * @param ruleValue 规则值（可能是CSS规则或传统规则）
     * @param fallback  兜底值
     * @return 提取结果
     */
    protected String smartExtractField(String html, String ruleValue, String fallback) {
        if (ruleValue == null || ruleValue.isEmpty()) return fallback;
        if (JsoupExtractor.isCssRule(ruleValue)) {
            String result = JsoupExtractor.extractSingle(html, ruleValue, null);
            return result.isEmpty() ? fallback : result;
        }
        return fallback;
    }

    /**
     * 判断指定规则对象是否启用了CSS模式
     * <p>
     * 检查条件（满足任一即启用）：
     * <ul>
     *   <li>对象包含 _use_css_mode = true</li>
     *   <li>对象包含 css_container 字段</li>
     *   <li>对象包含任意 *_css 后缀的字段</li>
     *   <li>全局 jsoup_parse = "1"</li>
     * </ul>
     *
     * @param obj 规则对象（list/detail/search/playlist）
     * @return true 如果应使用CSS模式
     */
    protected boolean isCssModeEnabled(JSONObject obj) {
        try {
            // 全局开关
            if ("1".equals(getRuleVal("jsoup_parse", "0"))) return true;

            if (obj == null) return false;

            // 显式标记
            if (obj.optBoolean("_use_css_mode", false)) return true;

            // 容器选择器
            if (obj.has("css_container") && !obj.optString("css_container", "").isEmpty()) return true;

            // 检查任意 *_css 字段
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.endsWith("_css")) {
                    String val = obj.optString(key, "");
                    if (!val.isEmpty() && JsoupExtractor.isCssRule(val)) return true;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("isCssModeEnabled error: " + e.getMessage());
        }
        return false;
    }

    /**
     * 补充CSS提取的详情字段（从上下文信息中补充缺失值）
     *
     * @param vod       已提取的部分详情对象
     * @param vinfo     原始视频信息
     * @param vid       视频ID
     * @param detailUrl 详情页URL
     */
    private void supplementDetailFieldsFromContext(JSONObject vod, JSONObject vinfo,
                                                   String vid, String detailUrl) throws JSONException {
        // 从 vinfo 补充基础字段
        if (vod.optString("vod_id", "").isEmpty()) {
            vod.put("vod_id", vinfo.optString("vod_id", ""));
        }

        // 名称补充
        if (vod.optString("vod_name", "").isEmpty() && vinfo.has("vod_name")) {
            vod.put("vod_name", vinfo.getString("vod_name"));
        }

        // 图片补充
        if (vod.optString("vod_pic", "").isEmpty() && vinfo.has("vod_pic")) {
            String pic = vinfo.getString("vod_pic");
            if ("1".equals(getRuleVal("PicNeedProxy")) && !pic.isEmpty()) {
                pic = fixCover(pic, detailUrl);
            }
            vod.put("vod_pic", addHttpPrefix(pic));
        }

        // 备注补充
        if (vod.optString("vod_remarks", "").isEmpty() && vinfo.has("vod_remarks")) {
            vod.put("vod_remarks", vinfo.getString("vod_remarks"));
        }
    }

    // ==================== URL 和请求头处理 ====================

    /**
     * 为相对URL添加HTTP前缀
     * @param url 相对或绝对URL
     * @return 完整URL
     */
    public String addHttpPrefix(String url) {
        try {
            if (url.isEmpty()) return "";
            if (url.startsWith("http")) return url;
            // P2P 协议 URI（磁力/迅雷/ed2k）不是相对路径，补主页前缀会生成无法播放的链接
            String lower = url.toLowerCase();
            if (lower.startsWith("magnet:") || lower.startsWith("thunder:")
                    || lower.startsWith("ed2k:") || lower.startsWith("mailto:")) {
                return url;
            }
            String result = rule.getString("homeUrl");
            if (result.endsWith("/")) {
                result = result.substring(0, result.length() - 1);
            }
            result += url.startsWith("/") ? url : "/" + url;
            return result;
        } catch (JSONException e) {
            SpiderDebug.log(e);
        }
        return url;
    }

    /**
     * 获取请求头
     * 合并顺序：headerMap（类级公共请求头，"公共请求头"配置） → rule.header（本源配置） → 默认UA
     * @param url 请求URL
     * @return 请求头Map
     */
    protected Map<String, String> getHeaders(String url) {
        Map<String, String> headers = new HashMap<>();
        try {
            // 1. 类级公共请求头（多源共享 Cookie/Token 等，避免重复配置）
            if (!headerMap.isEmpty()) headers.putAll(headerMap);
            // 2. 本源 rule.header 配置（优先级高于公共请求头）
            if (rule.has("header")) {
                Object headerObj = rule.get("header");
                if (headerObj instanceof JSONObject) {
                    JSONObject header = (JSONObject) headerObj;
                    Iterator<String> iter = header.keys();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        headers.put(key, header.getString(key));
                    }
                } else if (headerObj instanceof String) {
                    JSONObject hdr = parseHeader((String) headerObj);
                    Iterator<String> iter = hdr.keys();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        headers.put(key, hdr.getString(key));
                    }
                }
            }
            // 展开 UA 占位符
            resolveUserAgent(headers);
            // 支持独立 User-Agent 和 Referer 配置
            applyIndependentUaAndReferer(headers);
        } catch (JSONException e) {
            SpiderDebug.log(e);
        }
        // 默认UA
        if (!headers.containsKey("User-Agent")) {
            headers.put("User-Agent", Util.CHROME);
        }
        return headers;
    }

    /**
     * 解析 User-Agent 占位符
     */
    private void resolveUserAgent(Map<String, String> headers) {
        String uaVal = headers.get("User-Agent");
        if ("PC_UA".equals(uaVal)) {
            headers.put("User-Agent", UA_PC);
        } else if ("MOBILE_UA".equals(uaVal)) {
            headers.put("User-Agent", UA_MOBILE);
        }
    }

    /**
     * 应用独立的 User-Agent 和 Referer 配置
     */
    private void applyIndependentUaAndReferer(Map<String, String> headers) throws JSONException {
        String ua = rule.optString("User-Agent", "");
        if (!ua.isEmpty()) {
            if ("PC_UA".equals(ua)) {
                headers.put("User-Agent", UA_PC);
            } else if ("MOBILE_UA".equals(ua)) {
                headers.put("User-Agent", UA_MOBILE);
            } else {
                headers.put("User-Agent", ua);
            }
        }
        String referer = rule.optString("Referer", "");
        if (!referer.isEmpty() && referer.startsWith("http")) {
            headers.put("Referer", referer);
        }
    }

    /**
     * 解析请求头字符串为 JSONObject
     * 支持格式："Key1$Value1#Key2$Value2" 或 JSON格式
     */
    protected JSONObject parseHeader(String headerStr) {
        try {
            if (headerStr.startsWith("{")) {
                return new JSONObject(headerStr);
            }
            // 内置UA简写支持
            String normalized = headerStr.trim();
            if ("手机".equals(normalized) || "MOBILE_UA".equals(normalized)) {
                JSONObject hdr = new JSONObject();
                hdr.put("User-Agent", UA_MOBILE);
                return hdr;
            }
            if ("电脑".equals(normalized) || "PC_UA".equals(normalized)) {
                JSONObject hdr = new JSONObject();
                hdr.put("User-Agent", UA_PC);
                return hdr;
            }
            JSONObject hdr = new JSONObject();
            String[] pairs = headerStr.split("#");
            for (String pair : pairs) {
                String[] kv = pair.split("\\$", 2);
                if (kv.length >= 2) {
                    hdr.put(kv[0].trim(), kv[1].trim());
                }
            }
            return hdr;
        } catch (JSONException e) {
            SpiderDebug.log(e);
        }
        return new JSONObject();
    }

    // ==================== 内容提取工具方法 ====================

    /**
     * 提取子内容（基于起止标记）
     * @param content 源内容
     * @param startFlag 起始标记
     * @param endFlag 结束标记
     * @return 提取的内容列表
     */
    /** subContent 编译后正则缓存（规则标志位有限，条目数有界；DOTALL 支持跨换行匹配） */
    private static final Map<String, Pattern> SUB_CONTENT_PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取/缓存 subContent 使用的正则。
     * <ul>
     *   <li>缓存避免每次调用都 Pattern.compile（列表/详情解析是热点，单页数十次调用）；</li>
     *   <li>使用 Pattern.DOTALL，使 起止标志 之间可跨换行匹配，兼容美化后的多行 HTML，
     *       修正原先非 DOTALL 下“规则在部分页面匹配、部分页面静默失败”的问题。</li>
     * </ul>
     * 标志为字面量，已由 escapeExprSpecialWord 转义为字面匹配，组合后语义不变。
     */
    private static Pattern getSubContentPattern(String escapedStart, String escapedEnd) {
        String regex = escapedStart + "(.*?)" + (escapedEnd.isEmpty() ? "$" : escapedEnd);
        Pattern p = SUB_CONTENT_PATTERN_CACHE.get(regex);
        if (p == null) {
            p = Pattern.compile(regex, Pattern.DOTALL);
            SUB_CONTENT_PATTERN_CACHE.put(regex, p);
        }
        return p;
    }

    protected static List<String> subContent(String content, String startFlag, String endFlag) {
        List<String> result = new ArrayList<>();
        if (startFlag.isEmpty() && endFlag.isEmpty()) {
            result.add(content);
            return result;
        }
        try {
            String escapedStart = escapeExprSpecialWord(startFlag);
            String escapedEnd = escapeExprSpecialWord(endFlag);
            Matcher matcher = getSubContentPattern(escapedStart, escapedEnd).matcher(content);
            while (matcher.find()) {
                result.add(matcher.group(1).trim());
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        if (result.isEmpty()) result.add("");
        return result;
    }

    /**
     * 转义正则表达式特殊字符
     */
    public static String escapeExprSpecialWord(String keyword) {
        if (!keyword.isEmpty()) {
            String[] specialChars = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (String ch : specialChars) {
                if (keyword.contains(ch)) {
                    keyword = keyword.replace(ch, "\\" + ch);
                }
            }
        }
        return keyword;
    }

    // ==================== 条件选择器和后处理器 ====================

    /**
     * 应用 || 条件选择器
     * 按顺序返回第一个非空结果
     * 格式："默认--空||首页--module-items\">&&class=\"content\""
     */
    protected String applyOrSelector(String data) {
        if (data == null || !data.contains("||")) return data;
        String[] parts = data.split("\\|\\|");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String resolved = part.trim();
            // 去掉 key-- 前缀
            int doubleDash = resolved.indexOf("--");
            if (doubleDash > 0) {
                resolved = resolved.substring(doubleDash + 2);
            }
            if (!resolved.isEmpty()) return resolved;
        }
        return data;
    }

    /**
     * 应用二次截取规则
     * 格式："前缀&&后缀"
     */
    protected String applySecondCut(String content, String cutRule) {
        if (content == null || content.isEmpty() || cutRule == null || cutRule.isEmpty()) return content;
        cutRule = applyPostProcessors(cutRule);
        String[] parts = cutRule.split("&&");
        if (parts.length == 0) return content;
        int start = 0;
        if (!parts[0].isEmpty()) {
            start = content.indexOf(parts[0]);
            if (start < 0) return content;
            start += parts[0].length();
        }
        if (parts.length >= 2 && !parts[1].isEmpty()) {
            int end = content.indexOf(parts[1], start);
            if (end < 0) return content.substring(start);
            return content.substring(start, end).trim();
        }
        return content.substring(start).trim();
    }

    /**
     * 应用后处理器
     * 支持 [替换:a>>b] [包含:关键词] [不包含:关键词]
     */
    protected String applyPostProcessors(String str) {
        if (str == null || str.isEmpty()) return str;
        try {
            // 先收集所有处理器并剥离标记得到干净的基础内容，
            // 再依序作用于该内容：避免多标记共存时基于已变更文本二次匹配导致错乱，
            // 也避免 [替换:a>>b] 误伤后续标记的参数本身
            List<String[]> procs = new ArrayList<>();
            StringBuilder stripped = new StringBuilder();
            Matcher m = P_PROC_MARK.matcher(str);
            int last = 0;
            while (m.find()) {
                stripped.append(str, last, m.start());
                last = m.end();
                procs.add(new String[]{m.group(1), m.group(2)});
            }
            stripped.append(str, last, str.length());
            String result = stripped.toString();
            for (String[] p : procs) {
                switch (p[0]) {
                    case "替换":
                        // 多对写法以 # 分隔（[替换:a>>b#c>>d]），单对为直写（[替换:a>>b]）；
                        // 旧实现只认单对，多对规则静默失效
                        for (String pair : p[1].split("#")) {
                            String[] kv = pair.split(">>", 2);
                            if (kv.length != 2 || kv[0].trim().isEmpty()) continue;
                            String oldStr = kv[0].trim();
                            String newStr = kv[1].trim();
                            if (oldStr.contains("*")) {
                                // 兔爷系兼容：old 含 * 时按通配处理（* 匹配任意最短串），
                                // 如 [替换:@*@>>] 清理资源站注入的 @广告词@；尾通配（old 以 * 结尾）
                                // 匹配到串尾，如 [替换:@*>>] 去掉无闭合的尾部注入；new 原样输出不做正则展开
                                String[] segs = oldStr.split("\\*", -1);
                                StringBuilder rx = new StringBuilder();
                                for (int si = 0; si < segs.length; si++) {
                                    if (!segs[si].isEmpty()) rx.append(Pattern.quote(segs[si]));
                                    if (si < segs.length - 1) {
                                        boolean trailingWildcard = (si == segs.length - 2) && segs[segs.length - 1].isEmpty();
                                        rx.append(trailingWildcard ? ".*" : ".*?");
                                    }
                                }
                                result = result.replaceAll(rx.toString(), Matcher.quoteReplacement(newStr));
                            } else {
                                result = result.replace(oldStr, newStr);
                            }
                        }
                        break;
                    case "包含":
                        result = result.contains(p[1].trim()) ? result : "";
                        break;
                    case "不包含":
                        result = result.contains(p[1].trim()) ? "" : result;
                        break;
                }
                if (result.isEmpty() && !"替换".equals(p[0])) break;
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log("applyPostProcessors 异常：" + e.getMessage());
            return str;
        }
    }

    /**
     * 将字符串截取规则转换为 lookback JSONArray
     * 格式：[前缀, 后缀, 左偏移, 右偏移, 回看层级]
     */
    protected JSONArray stringCutToLookback(String rule) {
        if (rule == null || rule.isEmpty()) return null;
        String cutRule = applyPostProcessors(rule);
        String[] parts = cutRule.split("&&");
        JSONArray lookback = new JSONArray();
        lookback.put(parts.length >= 1 ? parts[0].trim() : "");       // 前缀
        lookback.put(parts.length >= 2 && !parts[1].trim().isEmpty() ? parts[1].trim() : 0);  // 后缀
        lookback.put(0);  // 左偏移
        lookback.put(0);  // 右偏移
        lookback.put(1);  // 回看层级
        return lookback;
    }

    /**
     * 应用字符串截取规则到目标对象
     */
    protected void applyStringCutRules(JSONObject target, String ruleKey) {
        if (target == null) return;
        String ruleVal = getRuleVal(ruleKey);
        if (ruleVal.isEmpty()) return;
        String processed = applyOrSelector(ruleVal);
        if (processed.isEmpty()) return;
        JSONArray lookback = stringCutToLookback(processed);
        if (lookback != null) {
            String fieldName = ruleKey.replace("_array", "");
            try {
                if (!target.has(fieldName)) {
                    target.put(fieldName, lookback);
                }
            } catch (JSONException e) {
                SpiderDebug.log(e);
            }
        }
    }

    // ==================== 规则猜测方法 ====================

    /**
     * 猜测分类列表的HTML区间
     */
    protected String guessCateManualHtmlString(String body) {
        // 旧写法把分类词表当作 String.format 参数却没有 %s 占位，
        // 实际编译出的正则是 "<a.+?href=\"(.+?)\".*?<(" —— 括号未闭合，
        // Pattern.compile 直接抛 "Unclosed group"，分类猜测功能整体失效。
        // 这里改为「href 捕获组 + 分类词非捕获组」的闭合写法。
        String regex = String.format("<a[^>]+href=\"([^\"]+)\"[^>]*>\\s*(?:%s)\\s*<",
                TextUtils.join("|", standardCategoryNames));
        Pattern pattern = Pattern.compile(regex);
        Matcher m = pattern.matcher(body);
        List<HtmlMatchInfo> matchList = new ArrayList<>();
        int matchCount = 0;

        while (m.find()) {
            ++matchCount;
            if (matchCount > MAX_MATCH_COUNT && !matchList.isEmpty()) break;

            HtmlMatchInfo cate = new HtmlMatchInfo();
            cate.init(m);
            cate.group2 = HtmlNodeHelper.trimHtmlString(HtmlNodeHelper.nodeString(body, cate.startPos));
            if (cate.group2.isEmpty()) continue;

            // 验证是否为有效分类
            boolean isValid = false;
            for (String name : standardCategoryNames) {
                if (cate.group2.indexOf(name) != -1) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) continue;

            cate.uploads = HtmlNodeHelper.findUpNodes(body, cate.startPos, 3);

            if (!matchList.isEmpty()) {
                if (!matchList.get(0).hasSameUpNode(cate)) {
                    if (matchList.size() > 1) {
                        return HtmlNodeHelper.nodeString(body, matchList.get(0).matchedUpNodePos);
                    }
                    matchList.clear();
                }
            }
            matchList.add(cate);
        }

        if (matchList.size() > 1) {
            return HtmlNodeHelper.nodeString(body, matchList.get(0).matchedUpNodePos);
        }
        return "";
    }

    /**
     * 从HTML中猜测分类规则
     */
    protected JSONObject guessRuleCateManual(String body) {
        try {
            String str = guessCateManualHtmlString(body);
            if (str.isEmpty()) return new JSONObject();

            String regex = String.format("<a.+?href=\"(.+?)\".*?[\"|>](\\s*?\\S+?\\s*?)(\"|<)", TextUtils.join("|", standardCategoryNames));
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(str);
            List<HtmlMatchInfo> matchList = new ArrayList<>();

            while (m.find()) {
                HtmlMatchInfo cate = new HtmlMatchInfo();
                cate.init(m);
                if (cate.group1.length() < 5) continue;
                cate.group2 = HtmlNodeHelper.trimHtmlString(HtmlNodeHelper.nodeString(str, cate.startPos));
                if (cate.group2.isEmpty()) continue;

                // 验证分类名有效性
                if (!isValidCategoryName(cate.group2)) continue;

                // 比较差异
                if (!matchList.isEmpty()) {
                    if (!matchList.get(0).findDiffStr(cate, splitFlag)) {
                        continue;
                    }
                }
                matchList.add(cate);
            }

            return buildCateManualFromMatches(matchList);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return new JSONObject();
    }

    /**
     * 验证分类名是否有效
     */
    private boolean isValidCategoryName(String name) {
        for (String invalid : invalidCategoryNames) {
            if (name.indexOf(invalid) != -1) return false;
        }
        return true;
    }

    /**
     * 从匹配结果构建 cateManual
     */
    private JSONObject buildCateManualFromMatches(List<HtmlMatchInfo> matchList) throws JSONException {
        // 找基准项
        List<Integer> baseIndices = new ArrayList<>();
        for (int i = 0; i < matchList.size(); ++i) {
            for (String name : standardCategoryNames) {
                if (matchList.get(i).group2.indexOf(name) != -1) {
                    baseIndices.add(i);
                    break;
                }
            }
        }

        // 以基准项重建差异
        int baseIndex = 0;
        for (int i = 1; i < baseIndices.size(); ++i) {
            baseIndex = baseIndices.get(0);
            matchList.get(baseIndex).findDiffStr(matchList.get(baseIndices.get(i)), splitFlag);
        }

        // 构建 cateManual
        JSONObject cateManual = new JSONObject();
        for (int i = 0; i < matchList.size(); ++i) {
            HtmlMatchInfo info = matchList.get(i);
            if (info.diff == null || info.diff.isEmpty()) {
                if (!matchList.get(baseIndex).findDiffStr(info, splitFlag)) continue;
            }

            String name = info.group2;
            String id = info.diff;
            if (id == null || id.isEmpty()) continue;
            if (name == null || name.isEmpty()) continue;

            // 验证ID不含分隔符
            boolean validId = true;
            for (int k = 0; k < id.length(); ++k) {
                if (splitFlag.indexOf(id.charAt(k)) != -1) {
                    validId = false;
                    break;
                }
            }
            if (!validId) continue;

            // 再次验证分类名
            if (isValidCategoryName(name) && !cateManual.has(name)) {
                cateManual.put(name, id);
            }
        }

        rule.put("cateManual", cateManual);
        return cateManual;
    }

    /**
     * 猜测搜索页URL规则
     */
    protected String guessRuleSearchUrl(String body) {
        String regex = "<input.+?name=\"(.+?)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(body);
        if (m.find()) {
            String wd = m.group(1);
            for (int i = 1; i < 4; ++i) {
                List<Integer> arr = HtmlNodeHelper.findUpNodes(body, m.start(0), i);
                String r = HtmlNodeHelper.nodeString(body, arr.get(arr.size() - 1));
                Matcher m2 = P_ACTION_ATTR.matcher(r);
                if (m2.find()) {
                    String url = m2.group(1);
                    char separator = url.indexOf('?') == -1 ? '?' : '&';
                    url = addHttpPrefix(url + separator + wd + "={wd}");
                    return url;
                }
            }
        }
        return "";
    }

    /**
     * 猜测列表数据的 vod_id 规则
     */
    public JSONArray guessRuleVodId(String body) {
        try {
            String regex = "<a.+?href=\"(.+?)\"";
            Pattern pattern = Pattern.compile(regex);
            Matcher m = pattern.matcher(body);
            Map<String, JSONArray> founds = new HashMap<>();
            List<HtmlMatchInfo> matchList = new ArrayList<>();

            while (m.find()) {
                HtmlMatchInfo info = new HtmlMatchInfo();
                info.init(m);
                info.uploads = HtmlNodeHelper.findUpNodes(body, info.startPos, 4);

                if (!matchList.isEmpty()) {
                    if (info.group1.equals(matchList.get(matchList.size() - 1).group1)) continue;
                    if (!matchList.get(matchList.size() - 1).hasSameUpNode(info)) {
                        if (matchList.size() > 1) {
                            processVodIdCandidate(matchList, founds);
                        }
                        matchList.clear();
                    }
                }
                matchList.add(info);
                if (matchList.size() > MAX_MATCH_COUNT) break;
            }

            // 处理剩余的匹配
            if (matchList.size() > 5 || (matchList.size() > 1 && founds.isEmpty())) {
                processVodIdCandidate(matchList, founds);
            }

            // 返回最佳结果
            return selectBestVodIdResult(founds);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    /**
     * 处理 vod_id 候选
     */
    private void processVodIdCandidate(List<HtmlMatchInfo> matchList, Map<String, JSONArray> founds) throws JSONException {
        HtmlMatchInfo info = matchList.get(0);
        info.findDiffStr(matchList.get(1), splitFlag);
        int id = 0;
        try { id = Integer.parseInt(info.diff); } catch (Exception e) {
            SpiderDebug.log("vod_id 候选非数字，按分类ID处理 [" + info.diff + "]: " + e.getMessage());
        }

        if (id > CATEGORY_ID_THRESHOLD) {
            String url = info.group1.replace(matchList.get(0).diff, "{vid}");
            JSONArray arr = buildVodIdArray(url, info, matchList.size());
            updateFoundsMap(founds, url, arr, matchList.size());
        }
    }

    /**
     * 构建 vod_id 数组
     */
    private JSONArray buildVodIdArray(String url, HtmlMatchInfo info, int count) throws JSONException {
        String prefix = url.substring(0, url.indexOf("{vid}"));
        String suffix = url.substring(prefix.length() + "{vid}".length());
        int lookback = info.uploads.indexOf(info.matchedUpNodePos) - 1;
        if (lookback < 1) lookback = 1;

        JSONArray arr = new JSONArray();
        arr.put(prefix);
        arr.put(suffix);
        arr.put(0);
        arr.put(0);
        arr.put(lookback);
        arr.put(count);
        return arr;
    }

    /**
     * 更新发现映射
     */
    private void updateFoundsMap(Map<String, JSONArray> founds, String url, JSONArray arr, int count) throws JSONException {
        if (!founds.containsKey(url)) {
            founds.put(url, arr);
        } else {
            int newLen = founds.get(url).getInt(5) + count;
            arr.put(5, newLen);
            founds.put(url, arr);
        }
    }

    /**
     * 选择最佳的 vod_id 结果
     */
    private JSONArray selectBestVodIdResult(Map<String, JSONArray> founds) throws JSONException {
        JSONArray best = null;
        for (JSONArray v : founds.values()) {
            if (best == null || best.getInt(5) < v.getInt(5)) {
                best = v;
            }
        }
        return best;
    }

    /**
     * 猜测播放列表规则
     */
    public JSONArray guessRuleVodPlayUrl(String str, String vid) {
        String regex = "href=\"(/.+?)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(str);
        HtmlMatchInfo info = new HtmlMatchInfo();
        List<String> vec = new ArrayList<>();
        boolean foundStandardFormat = false;

        while (m.find()) {
            String href = m.group(1);
            if (href.length() > 100) continue;
            if (href.indexOf(vid) == -1) continue;

            boolean isStandardFormat = (href.indexOf(vid + "-") != -1);
            if (!isStandardFormat && !vec.isEmpty() && vec.get(vec.size() - 1).length() > href.length()) continue;
            if (isStandardFormat && !foundStandardFormat) vec.clear();
            if (foundStandardFormat && !isStandardFormat) continue;

            info.init(m);
            if (vec.isEmpty()) foundStandardFormat = isStandardFormat;
            vec.add(m.group(1));

            if (vec.size() > 10 && vec.get(vec.size() - 2).length() == href.length()) break;
        }

        if (info.group0 != null) {
            return findPlayListNode(str, info, vid);
        }
        return null;
    }

    /**
     * 查找播放列表根节点
     */
    private JSONArray findPlayListNode(String str, HtmlMatchInfo info, String vid) {
        for (int i = 1; i < 4; ++i) {
            List<Integer> nodes = HtmlNodeHelper.findUpNodes(str, info.startPos, i);
            int startPos = nodes.get(nodes.size() - 1);
            String nodeStr = HtmlNodeHelper.nodeString(str, startPos);

            if (nodeStr.startsWith("<ul") || nodeStr.startsWith("<div") || i == 3) {
                String prefix = info.group1.substring(0, info.group1.indexOf(vid));
                JSONArray arr = new JSONArray();
                arr.put(prefix);
                arr.put("\"");
                arr.put(0 - prefix.length());
                arr.put(0);
                arr.put(i);
                return arr;
            }
        }
        return null;
    }

    // ==================== 值猜测方法 ====================

    /**
     * 猜测视频名称
     */
    public String guessValueVodName(String nodeContent, int startPos) {
        try {
            JSONArray vec = new JSONArray();
            vec.put("alt=\"");
            vec.put("\"");
            String val = RuleUtils.findSubString(nodeContent, startPos, vec);

            if (val.isEmpty()) {
                vec.put(0, "\" title=\"");
                val = RuleUtils.findSubString(nodeContent, startPos, vec);
            }
            if (val.isEmpty()) {
                val = guessNameFromTextContent(nodeContent);
            }
            return cleanCommonPrefixes(val);
        } catch (Exception e) {
            // ignored
        }
        return "";
    }

    /**
     * 从文本内容猜测名称（取出现次数最多的非更新词）
     */
    private String guessNameFromTextContent(String nodeContent) {
        String all = HtmlNodeHelper.trimHtmlString(nodeContent, "!!!!");
        String[] words = all.split("!!!!");
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (String word : words) {
            word = word.trim();
            if (!word.isEmpty() && word.indexOf("更新") == -1) {
                int count = frequencyMap.containsKey(word) ? frequencyMap.get(word) + 1 : 1;
                frequencyMap.put(word, count);
            }
        }

        String best = "";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * 清理常见前缀词汇
     */
    private String cleanCommonPrefixes(String val) {
        return val.replace("在线", "").replace("立即", "").replace("观看", "")
                .replace("点播", "").replace("影片", "").replace("信息", "")
                .replace("播放", "").trim();
    }

    /**
     * 猜测备注信息
     */
    public String guessValueVodRemarks(String nodeContent, int startPos, String vodName) {
        try {
            String all = HtmlNodeHelper.trimHtmlString(nodeContent, "!!!!");
            String[] words = all.split("!!!!");
            String remarks = "";
            for (String word : words) {
                String wd = word.trim();
                if (!wd.isEmpty() && wd.indexOf(vodName) == -1) {
                    String separator = remarks.isEmpty() ? "" : ",";
                    String tmp = remarks + separator + wd;
                    if (tmp.length() > 20) break;
                    remarks = tmp;
                }
            }
            return remarks;
        } catch (Exception e) {
            // ignored
        }
        return "";
    }

    /**
     * 节点级 vod_id 猜测：取节点内第一个 href。
     * <p>
     * 兜底场景：规则未配置「链接」(list_id) 且页级 {@link #guessRuleVodId} 也没猜中时，
     * list.vod_id 会缺失，旧实现所有条目因 vod_id 为空被整页丢弃，
     * 表现为「分类列表全空且日志无任何报错」。
     */
    public String guessValueVodId(String nodeContent) {
        if (nodeContent == null || nodeContent.isEmpty()) return "";
        try {
            JSONArray vec = new JSONArray();
            vec.put("href=\"");
            vec.put("\"");
            return RuleUtils.findSubString(nodeContent, 0, vec);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 猜测图片地址
     */
    public String guessValueVodPic(String nodeContent, int startPos) {
        try {
            String[][] picAttrs = {{"data-original", "\""}, {"data-src", "\""}, {"src", "\""}, {"data-bg", "\""}};
            for (String[] attr : picAttrs) {
                JSONArray vec = new JSONArray();
                vec.put(attr[0] + "=\"");
                vec.put(attr[1]);
                String val = RuleUtils.findSubString(nodeContent, startPos, vec);
                if (!val.isEmpty()) return addHttpPrefix(val);
            }
        } catch (Exception e) {
            // ignored
        }
        return "";
    }

    // ==================== 视频格式检测 ====================

    @Override
    public boolean isVideoFormat(String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        // 协议白名单前置校验：仅接受 http(s)（含 URL 编码形式），
        // 阻断 javascript:/file:/data:/ws: 等危险协议被误判为可播放地址
        String lower = trimmed.toLowerCase();
        if (!(lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("http%3a%2f%2f") || lower.startsWith("https%3a%2f%2f"))) {
            return false;
        }
        if (lower.contains("=http") || lower.contains("=https") ||
            lower.contains("=https%3a%2f") || lower.contains("=http%3a%2f")) {
            return false;
        }
        // 视频过滤词：命中即判定为非视频（排除伪装成直链的网页、广告与跳转页）
        String videoFilter = getRuleVal("video_filter");
        if (!videoFilter.isEmpty()) {
            for (String kw : videoFilter.split("#")) {
                String k = kw.trim().toLowerCase();
                if (!k.isEmpty() && lower.contains(k)) return false;
            }
        }
        for (String format : videoFormatList) {
            if (lower.contains(format)) return true;
        }
        return false;
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        // 免嗅：0=交给框架嗅探，1=免嗅探（由本类的 isVideoFormat 自管判定）。
        // 未配置时沿用既有行为（自管），保证历史规则行为不变。
        String v = getRuleVal("manualVideoCheck");
        return v.isEmpty() || !"0".equals(v);
    }

    // ==================== 首页内容接口 ====================

    @Override
    public String homeContent(boolean filter) {
        try {
            fetchRule();
            initEnhancedConfig();
            JSONObject result = new JSONObject();
            JSONArray classes = buildClassList(filter);
            // 插入「偏好设置」「源内搜索」action tab（配置 actionTabs=1 或 SSTop 开启）
            classes = insertActionTabs(classes);
            result.put("class", classes);

            // 处理筛选条件
            if (filter && rule.has("filter")) {
                result.put("filters", rule.getJSONObject("filter"));
            }
            processFilterData(result, filter);
            processSortFilter(result, filter);

            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 构建分类列表
     */
    protected JSONArray buildClassList(boolean filter) throws JSONException {
        JSONArray classes = new JSONArray();
        // 优先走后端 JSON 接口（标准模板「异步 JSON-API」），未命中回退网页解析
        if (tryBuildFromJson(classes)) return classes;
        JSONObject cateManual = rule.optJSONObject("cateManual");
        String fenleiExplicit = rule.optString("fenlei", "");

        if (!fenleiExplicit.isEmpty()) {
            cateManual = null;
        }

        // 懒加载兜底：无显式分类配置时才发起首页猜测（延迟到消费点，避免拖慢 init）
        if (cateManual == null && fenleiExplicit.isEmpty()) {
            guessCateManualIfNeeded();
            cateManual = rule.optJSONObject("cateManual");
        }

        // 应用 cat_twice 二次截取
        String catTwice = getRuleVal("cat_twice");
        if (!catTwice.isEmpty() && cateManual == null) {
            String body = fetchHomePageBody();
            body = applySecondCut(body, applyOrSelector(catTwice));
            cateManual = guessRuleCateManual(body);
            if (cateManual != null) rule.put("cateManual", cateManual);
        }

        // 优先级：class_name/class_value > cateManual > cat_array/cat_title/cat_id > fenlei
        if (tryBuildFromClassPair(classes)) return classes;
        if (cateManual != null && cateManual.length() > 0) {
            buildClassesFromCateManual(classes, cateManual);
            return classes;
        }
        if (tryBuildFromCatArray(classes, catTwice)) return classes;
        if (tryBuildFromFenlei(classes)) return classes;

        // 兜底处理
        fallbackClassBuild(classes);
        return classes;
    }

    /**
     * 尝试从 class_name/class_value 构建分类
     */
    private boolean tryBuildFromClassPair(JSONArray classes) throws JSONException {
        String classNames = rule.optString("class_name", "");
        String classValues = rule.optString("class_value", "");
        if (classNames.isEmpty() || classValues.isEmpty()) return false;

        String[] names = classNames.split("&");
        String[] values = classValues.split("&");
        int len = Math.min(names.length, values.length);
        for (int i = 0; i < len; i++) {
            JSONObject item = new JSONObject();
            // ＆＆ 为 & 分隔符的全角转义约定，name/value 对称还原
            item.put("type_name", names[i].replace("＆＆", "&"));
            item.put("type_id", values[i].replace("＆＆", "&"));
            classes.put(item);
        }
        return true;
    }

    /**
     * 从 cateManual 构建分类
     */
    private void buildClassesFromCateManual(JSONArray classes, JSONObject cateManual) throws JSONException {
        Iterator<String> keys = cateManual.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = new JSONObject();
            item.put("type_name", key);
            item.put("type_id", cateManual.getString(key));
            classes.put(item);
        }
    }

    /**
     * 尝试从 cat_array/cat_title/cat_id 构建分类
     */
    private boolean tryBuildFromCatArray(JSONArray classes, String catTwice) throws JSONException {
        String catArrayRule = getRuleVal("cat_array");
        String catTitleRule = getRuleVal("cat_title");
        String catIdRule = getRuleVal("cat_id");

        if (catArrayRule.isEmpty() || catTitleRule.isEmpty() || catIdRule.isEmpty()) return false;

        String body = fetchHomePageBody();
        if (!catTwice.isEmpty()) {
            body = applySecondCut(body, applyOrSelector(catTwice));
        }

        // 截取数组区域
        body = extractCatArrayRegion(body, catArrayRule);

        // 按 title/id 规则提取分类
        extractCategoriesFromBody(classes, body, catTitleRule, catIdRule);
        return classes.length() > 0;
    }

    /**
     * 截取分类数组区域
     */
    private String extractCatArrayRegion(String body, String catArrayRule) {
        String processed = applyOrSelector(catArrayRule);
        String[] parts = processed.split("&&");
        if (parts.length == 0 || parts[0].isEmpty()) return body;

        int startIdx = body.indexOf(parts[0]);
        if (startIdx < 0) return body;
        body = body.substring(startIdx + parts[0].length());

        if (parts.length > 1 && !parts[1].isEmpty()) {
            int endIdx = body.indexOf(parts[1]);
            if (endIdx >= 0) body = body.substring(0, endIdx);
        }
        return body;
    }

    /**
     * 从HTML内容提取分类
     */
    private void extractCategoriesFromBody(JSONArray classes, String body, String titleRule, String idRule) {
        String[] titleParts = titleRule.split("&&");
        String[] idParts = idRule.split("&&");
        String[] items = splitByEndFlag(body, titleParts.length > 1 ? titleParts[1] : "");

        for (String item : items) {
            try {
                String title = extractField(item, titleParts);
                String id = extractField(item, idParts);
                if (!title.isEmpty()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("type_name", title);
                    jsonObject.put("type_id", id);
                    classes.put(jsonObject);
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
    }

    /**
     * 按结束标记拆分
     */
    private String[] splitByEndFlag(String body, String endFlag) {
        if (endFlag.isEmpty() || !body.contains(endFlag)) {
            return new String[]{body};
        }
        List<String> items = new ArrayList<>();
        int idx = 0;
        while (idx < body.length()) {
            int next = body.indexOf(endFlag, idx);
            if (next < 0) break;
            items.add(body.substring(idx, next + endFlag.length()));
            idx = next + endFlag.length();
        }
        return items.toArray(new String[0]);
    }

    /**
     * 从item中提取字段
     */
    private String extractField(String item, String[] parts) {
        if (parts.length < 2) return item.trim();
        int start = item.indexOf(parts[0]);
        if (start < 0) return "";
        start += parts[0].length();
        int end = item.indexOf(parts[1], start);
        return end > 0 ? item.substring(start, end).trim() : item.substring(start).trim();
    }

    /**
     * 尝试从 fenlei 构建分类
     */
    private boolean tryBuildFromFenlei(JSONArray classes) throws JSONException {
        String fenlei = rule.optString("fenlei", "");
        if (fenlei.isEmpty()) return false;

        for (String item : fenlei.split("#")) {
            String[] kv = item.split("\\$");
            if (kv.length >= 2) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type_name", kv[0]);
                jsonObject.put("type_id", kv[1]);
                classes.put(jsonObject);
            }
        }
        return true;
    }

    /**
     * 兜底分类构建
     */
    private void fallbackClassBuild(JSONArray classes) throws JSONException {
        if (classes.length() > 0) return;
        String fenlei = rule.optString("fenlei", "");
        if (fenlei.isEmpty()) return;

        String classUrl = rule.optString("class_url", "");
        String cateId = extractCateId(classUrl);
        if (!cateId.isEmpty()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type_name", fenlei);
            jsonObject.put("type_id", cateId);
            classes.put(jsonObject);
        }
    }

    /**
     * 从 classUrl 提取分类ID
     */
    private String extractCateId(String classUrl) {
        if (classUrl.contains("tid=")) {
            int start = classUrl.indexOf("tid=") + 4;
            int end = classUrl.indexOf("&", start);
            return end > start ? classUrl.substring(start, end) : classUrl.substring(start);
        } else if (classUrl.contains("{cateId}") || classUrl.contains("?")) {
            return "1";
        }
        return "";
    }

    /**
     * 处理筛选数据
     */
    private void processFilterData(JSONObject result, boolean filter) throws JSONException {
        if (!filter || !rule.has("filterdata")) return;
        Object filterdata = rule.get("filterdata");
        if (filterdata instanceof JSONObject) {
            result.put("filters", (JSONObject) filterdata);
        } else if (filterdata instanceof String) {
            String furl = (String) filterdata;
            if (furl.startsWith("http")) {
                try {
                    String fjson = OkHttp.string(furl, null);
                    result.put("filters", new JSONObject(fjson));
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
        }
    }

    /**
     * 处理排序筛选
     */
    private void processSortFilter(JSONObject result, boolean filter) throws JSONException {
        if (!filter) return;
        String sortType = getRuleVal("sort_type");
        String sortValue = getRuleVal("sort_value");
        if (sortType.isEmpty() || sortValue.isEmpty()) return;

        String classUrl = rule.optString("class_url", "");
        if (!classUrl.contains("{by}")) return;

        JSONObject filterObj = new JSONObject();
        JSONObject byItem = new JSONObject();
        byItem.put("key", "by");
        JSONArray listArr = new JSONArray();

        String[] names = sortType.split("&");
        String[] values = sortValue.split("&");
        int len = Math.min(names.length, values.length);
        for (int i = 0; i < len; i++) {
            JSONObject opt = new JSONObject();
            opt.put("n", names[i].trim());
            opt.put("v", values[i].trim());
            listArr.put(opt);
        }
        byItem.put("value", listArr);
        filterObj.put("by", byItem);
        // 合并进已有筛选（规则 筛选 JSON 可能已按 tid 配置年份等），
        // 旧实现整体覆盖导致 排序 与 筛选 互斥
        JSONObject existingFilters = result.optJSONObject("filters");
        if (existingFilters != null) {
            existingFilters.put("by", byItem);
            result.put("filters", existingFilters);
        } else {
            result.put("filters", filterObj);
        }
    }

    // ==================== 首页推荐接口 ====================

    @Override
    public String homeVideoContent() {
        try {
            fetchRule();
            String homeVal = getRuleVal("firstpage");
            // 无首页配置时，若开启热门推荐仍可聚合首页视频，不提前返回
            if (homeVal.isEmpty() && !hotRecommend) return "";

            // 解析首页配置（纯数字=每区/总条数上限；否则为 分区名$分区ID#... 列表）
            int maxVideos = DEFAULT_HOME_MAX_VIDEOS;
            List<Pair<String, String>> sections;
            String trimmedHome = homeVal.trim();
            if (trimmedHome.matches("\\d+")) {
                maxVideos = Math.max(1, Integer.parseInt(trimmedHome));
                sections = new ArrayList<>();
            } else {
                sections = parseHomeConfig(homeVal);
            }

            // 获取分类列表作为备选（仅在有首页配置或需要分类聚合时才拉取）
            JSONArray classes = null;
            if (!homeVal.isEmpty() || !hotRecommend) {
                String homeContentStr = homeContent(true);
                if (homeContentStr.isEmpty()) return "";
                classes = new JSONObject(homeContentStr).optJSONArray("class");
                if (classes == null) return "";
            }

            // 未配置分区时按分类聚合（仅当有首页配置时；纯热门推荐模式跳过分类遍历）
            if (sections.isEmpty() && classes != null) {
                for (int i = 0; i < classes.length(); i++) {
                    JSONObject c = classes.getJSONObject(i);
                    sections.add(new Pair<>(c.optString("type_name", ""), c.optString("type_id", "")));
                }
            }

            // 收集视频
            Set<String> seen = new HashSet<>();
            JSONArray allVideos = new JSONArray();
            int count = 0;
            for (int i = 0; i < sections.size() && count < maxVideos; i++) {
                Pair<String, String> sec = sections.get(i);
                JSONArray got = fetchHomeSection(sec.second, seen, maxVideos - count);

                // 分区ID失效时尝试从分类列表找回
                if (got.length() == 0 && !sec.first.isEmpty()) {
                    got = recoverHomeSection(sec.first, classes, seen, maxVideos - count);
                }

                for (int j = 0; j < got.length() && count < maxVideos; j++) {
                    allVideos.put(got.get(j));
                    count++;
                }
            }

            // 倒序
            if (reverseOrder) allVideos = reverseArray(allVideos);

            // 热门推荐：聚合主页热门/推荐视频到首页列表头部
            if (hotRecommend) {
                JSONArray hot = fetchHomePageVideos(maxVideos - count);
                for (int j = 0; j < hot.length() && count < maxVideos; j++) {
                    JSONObject v = hot.getJSONObject(j);
                    String key = v.optString("vod_id", "");
                    if (key.isEmpty() || seen.contains(key)) continue;
                    seen.add(key);
                    allVideos.put(v);
                    count++;
                }
            }

            JSONObject result = wrapList(allVideos, "1");
            // 列表显示：通过 ext 透传列表展示偏好（框架扩展字段，向后兼容）
            if (listDisplay) {
                JSONObject ext = new JSONObject();
                ext.put("listDisplay", "1");
                result.put("ext", ext);
            }
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 解析首页分区配置（分区名$分区ID#... 列表）
     */
    private List<Pair<String, String>> parseHomeConfig(String homeVal) {
        List<Pair<String, String>> sections = new ArrayList<>();
        for (String item : homeVal.split("#")) {
            String[] kv = item.split("\\$");
            if (kv.length >= 2 && !kv[1].trim().isEmpty()) {
                sections.add(new Pair<>(kv[0].trim(), kv[1].trim()));
            }
        }
        return sections;
    }

    /**
     * 恢复失效分区
     */
    private JSONArray recoverHomeSection(String sectionName, JSONArray classes, Set<String> seen, int cap) throws Exception {
        for (int j = 0; j < classes.length(); j++) {
            JSONObject c = classes.getJSONObject(j);
            if (sectionName.equals(c.optString("type_name", "")) && !c.optString("type_id", "").isEmpty()) {
                return fetchHomeSection(c.getString("type_id"), seen, cap);
            }
        }
        return new JSONArray();
    }

    /**
     * 反转数组
     */
    private JSONArray reverseArray(JSONArray arr) throws JSONException {
        JSONArray reversed = new JSONArray();
        for (int i = arr.length() - 1; i >= 0; i--) {
            reversed.put(arr.get(i));
        }
        return reversed;
    }

    /**
     * 拉取推荐分区的视频
     */
    protected JSONArray fetchHomeSection(String tid, Set<String> seen, int cap) {
        JSONArray result = new JSONArray();
        if (tid == null || tid.isEmpty()) return result;
        try {
            String content = categoryContent(tid, "1", false, new HashMap<>());
            if (content.isEmpty()) return result;
            JSONArray list = new JSONObject(content).optJSONArray("list");
            if (list == null) return result;
            for (int i = 0; i < list.length() && result.length() < cap; i++) {
                JSONObject v = list.getJSONObject(i);
                String key = v.optString("vod_id", "");
                if (key.isEmpty() || seen.contains(key)) continue;
                seen.add(key);
                result.put(v);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    /**
     * 拉取主页热门/推荐视频（热门推荐=1 时使用）
     * 直接复用列表解析器 extractVideoList 解析主页（主页url）中的视频项
     */
    private JSONArray fetchHomePageVideos(int cap) {
        JSONArray result = new JSONArray();
        if (cap <= 0) return result;
        try {
            String body = fetchHomePageBody();
            if (body.isEmpty()) return result;
            JSONObject list = rule.optJSONObject("list");
            if (list == null || !list.has("vod_id")) return result;
            JSONArray videos = extractVideoList(body, list, rule.optString("homeUrl", ""));
            for (int i = 0; i < videos.length() && result.length() < cap; i++) {
                result.put(videos.get(i));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return result;
    }

    // ==================== 分类内容接口 ====================

    /**
     * 构建分类请求URL
     */
    protected String categoryUrl(String tid, String pg, boolean filter, Map<String, String> extend) {
        try {
            JSONObject list = this.rule.getJSONObject("list");
            String cateUrl = list.optString(pg, "");
            if (cateUrl.isEmpty()) cateUrl = list.getString("url");

            // 剥离反引号（部分规则习惯用 ` 包裹 URL，引擎不做模板求值，保留会拼出非法 URL）
            cateUrl = stripBackticks(cateUrl);

            // 处理 ;; 模式后缀
            if (cateUrl.contains(";;")) {
                cateUrl = cateUrl.substring(0, cateUrl.indexOf(";;")).trim();
            }

            // 应用筛选参数（URLEncoder 的空格是 +，站点路由普遍期望 %20）
            if (filter && extend != null && !extend.isEmpty()) {
                for (Iterator<String> it = extend.keySet().iterator(); it.hasNext();) {
                    String key = it.next();
                    String value = extend.get(key);
                    if (!value.isEmpty()) {
                        cateUrl = cateUrl.replace("{" + key + "}", URLEncoder.encode(value, "UTF-8").replace("+", "%20"));
                    }
                }
            }

            // 替换占位符
            cateUrl = cateUrl.replace("{cateId}", tid).replace("{catePg}", shiftStartPage(pg));
            // 清除剩余花括号变量：仅处理已知占位符白名单，
            // 防止规则误写的 {xxx} 或 URL 中合法的花括号字符被通配正则误删
            Matcher matcher = P_BRACE_VAR.matcher(cateUrl);
            StringBuilder sbCate = new StringBuilder();
            int lastEnd = 0;
            while (matcher.find()) {
                String name = matcher.group(1) == null ? "" : matcher.group(1).trim();
                if (!KNOWN_BRACE_KEYS.contains(name)) continue;
                // 已知但未赋值的占位符：移除变量本体
                sbCate.append(cateUrl, lastEnd, matcher.start());
                lastEnd = matcher.end();
                // 移除紧随其后的同名路径段 "/name/"（如 /type/{cateId}/ → /type/）
                String rest = cateUrl.substring(lastEnd);
                String seg = "/" + name + "/";
                int segIdx = rest.indexOf(seg);
                if (segIdx >= 0 && (segIdx == 0 || rest.charAt(segIdx - 1) == '/')) {
                    lastEnd += segIdx + seg.length();
                }
            }
            if (lastEnd > 0) {
                sbCate.append(cateUrl, lastEnd, cateUrl.length());
                return sbCate.toString();
            }
            return cateUrl;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 剥离字符串首尾的反引号（`）
     * 部分站点规则习惯用 ` 包裹 URL 模板，引擎不做模板求值，反引号保留会拼出非法 URL
     */
    protected String stripBackticks(String url) {
        if (url == null) return "";
        String result = url.trim();
        while (result.startsWith("`")) result = result.substring(1).trim();
        while (result.endsWith("`")) result = result.substring(0, result.length() - 1).trim();
        return result;
    }

    /**
     * 页码偏移（根据起始页配置）
     */
    protected String shiftStartPage(String pg) {
        int startPage = parseIntSafely(getRuleVal("startpage"), 1);
        if (startPage < 0) startPage = 0;
        return String.valueOf(parseIntSafely(pg, 1) + startPage - 1);
    }

    /**
     * 搜索页码偏移（根据 搜索起始页码/sea_firstpage 配置，未配置时等价于页码不变）
     */
    protected String shiftSearchPage(String pg) {
        String cfg = getRuleVal("sea_firstpage");
        int startPage = cfg.isEmpty() ? 1 : parseIntSafely(cfg, 1);
        if (startPage < 0) startPage = 0;
        return String.valueOf(parseIntSafely(pg, 1) + startPage - 1);
    }

    /**
     * 安全整数解析
     */
    protected int parseIntSafely(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            fetchRule(); // 与其他入口（home/detail/player/search）一致：保证规则已加载，避免被提前调用时规则为空
            JSONObject list = this.rule.getJSONObject("list");
            String url = categoryUrl(tid, pg, filter, extend);

            // ========== JSON 接口模式（标准模板「异步 JSON-API」：直连后端接口，不解析 HTML） ==========
            if ("1".equals(getRuleVal("list_mode"))) {
                JSONArray jsonVideos = extractVideosByJson(httpGetRaw(url),
                        getRuleVal("listjsonlist"), getRuleVal("listjsonid"),
                        getRuleVal("listjsonname"), getRuleVal("listjsonpic"),
                        getRuleVal("listjsonnote"));
                if (jsonVideos.length() > 0) return wrapList(jsonVideos, pg).toString();
                SpiderDebug.log("列表 JSON 模式无结果，回退到网页解析");
            }

            String body = fetchUrl(url, list.optJSONObject("header"));
            String content = RuleUtils.getRegion(body, list);

            // ========== CSS选择器模式（优先） ==========
            String listTwice = getRuleVal("list_twice");
            if (isCssModeEnabled(list)) {
                SpiderDebug.log("分类列表使用CSS/Jsoup模式提取");
                // CSS二次截取
                if (!listTwice.isEmpty() && JsoupExtractor.isCssRule(applyOrSelector(listTwice))) {
                    content = JsoupExtractor.cutRegion(content, applyOrSelector(listTwice));
                } else if (!listTwice.isEmpty()) {
                    content = applySecondCut(content, applyOrSelector(listTwice));
                }
                JSONArray cssVideos = extractVideoListByCss(content, list);
                if (cssVideos.length() > 0) {
                    return buildCategoryResult(cssVideos, pg);
                }
                SpiderDebug.log("CSS提取无结果，回退到正则模式");
            }

            // ========== 传统正则/字符串截取模式 ==========
            // 应用二次截取
            if (!listTwice.isEmpty()) {
                content = applySecondCut(content, applyOrSelector(listTwice));
            }

            // 提取视频列表
            JSONArray videos = extractVideoList(content, list, url);

            // 构建返回结果
            return buildCategoryResult(videos, pg);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 提取视频列表
     */
    private JSONArray extractVideoList(String content, JSONObject list, String url) throws JSONException {
        JSONArray videos = new JSONArray();
        // 若 list 为空，从规则配置自动构建 list（兼容没有 list_array 的纯文本模式）
        if (list == null || list.length() == 0) {
            list = buildListFromRules();
        }
        JSONArray lookback = RuleUtils.getLookbackArray(list);
        if (lookback != null) lookback = new JSONArray(lookback.toString());
        Set<String> seenIds = new HashSet<>();
        int pos = 0;

        while (lookback != null) {
            int matchPos = content.indexOf(lookback.getString(0), pos);
            if (matchPos == -1) break;

            // 跳过 style/script 内的命中（如内联 CSS 里的类名），这类节点无片单内容
            if (insideNoParseBlock(content, matchPos)) {
                pos = matchPos + 1;
                continue;
            }

            // 使用 do-while 循环调整回看层级
            NodeExtractionResult result = adjustAndExtractNode(content, matchPos, lookback, list);
            if (result == null) break;

            // 防护：层级震荡导致提取失败时 endPos 不前进（可能为 0），
            // 强制前移避免同一匹配点被无限重试
            if (result.endPos <= matchPos) {
                pos = matchPos + 1;
            } else {
                pos = result.endPos;
            }
            String vodId = result.vodId;

            // 无链接的块（如首页页脚「友情链接」li、装饰元素）不构成片单，剔除，
            // 否则会以猜测名称产出一条无法打开的脏条目
            if (vodId.isEmpty()) continue;

            if (!seenIds.contains(vodId)) {
                // 过滤词检查
                if (shouldFilter(result.node, vodId, list)) continue;

                seenIds.add(vodId);
                JSONObject video = buildVideoObject(result.node, vodId, list, url);
                videos.put(video);
            }
        }

        // 倒序
        if (reverseOrder) videos = reverseArray(videos);
        return videos;
    }

    /** 列表 vod_id 规则缺失告警（每次规则加载只提示一次，避免逐条刷屏） */
    private boolean vodIdRuleWarned = false;

    private void warnMissingVodIdRule() {
        if (vodIdRuleWarned) return;
        vodIdRuleWarned = true;
        SpiderDebug.log("列表未配置「链接」(list_id) 且自动猜测失败，已启用节点级 href 兜底；"
                + "建议显式配置以获取稳定结果");
    }

    /**
     * 节点提取结果
     */
    private static class NodeExtractionResult {
        String node;
        String vodId;
        int endPos;
    }

    /**
     * 调整回看层级并提取节点
     */
    private NodeExtractionResult adjustAndExtractNode(String content, int pos, JSONArray lookback, JSONObject list) throws JSONException {
        List<Integer> urlNodes = null;
        List<Integer> arr = null;
        int blockPos = 0;
        String node = "";
        int lookup = -1;
        int iterations = 0;
        final int MAX_ITERATIONS = 20;

        do {
            // 防护：限制最大迭代次数，避免回看层级震荡导致死循环
            if (++iterations > MAX_ITERATIONS) {
                SpiderDebug.log(String.format("adjustAndExtractNode 达到最大迭代次数(%d)，当前层级=%d，强制退出", MAX_ITERATIONS, lookback.getInt(4)));
                break;
            }

            arr = HtmlNodeHelper.findUpNodes(content, pos - 1, lookback.getInt(4));
            if (urlNodes == null) {
                urlNodes = arr;
                blockPos = arr.get(arr.size() - 1);
            } else {
                blockPos = RuleUtils.findBlockPos(urlNodes, arr);
            }
            node = HtmlNodeHelper.nodeString(content, blockPos);

            // 层级修正
            lookup = checkAndAdjustLevel(node, lookup, lookback, urlNodes, blockPos);
            if (lookup < 0) {
                urlNodes = null;
                blockPos = 0;
                node = "";
            }
        } while (lookup < 0);

        NodeExtractionResult result = new NodeExtractionResult();
        result.node = node;
        result.vodId = RuleUtils.findSubString(node, 0, list.optJSONArray("vod_id"));
        // 与 CSS 模式（extractVideoListByCss）对齐：&& 模式同样套用 链接前缀/链接后缀
        String listPrefix = getRuleVal("list_prefix");
        String listSuffix = getRuleVal("list_suffix");
        if (!result.vodId.isEmpty() && (!listPrefix.isEmpty() || !listSuffix.isEmpty())) {
            result.vodId = listPrefix + result.vodId + listSuffix;
        }
        // 未配置「链接」且页级猜测也未命中时，退到节点级取首个 href。
        // 仅当节点同时能猜出名称或封面才认定为有效片单，避免页脚/导航链接混入。
        if (result.vodId.isEmpty() && list.optJSONArray("vod_id") == null) {
            String guessed = guessValueVodId(node);
            if (!guessed.isEmpty()
                    && (!guessValueVodName(node, 0).isEmpty() || !guessValueVodPic(node, 0).isEmpty())) {
                result.vodId = addHttpPrefix(guessed);
                warnMissingVodIdRule();
            }
        }
        result.endPos = blockPos + node.length();
        return result;
    }

    /**
     * 检查并调整回看层级
     */
    private int checkAndAdjustLevel(String node, int currentLookup, JSONArray lookback,
                                     List<Integer> urlNodes, int blockPos) throws JSONException {
        if (currentLookup >= 0) return currentLookup;

        int level = lookback.getInt(4);
        final int MIN_LEVEL = 1;
        final int MAX_LEVEL = 5;

        // 防护：层级边界检查，避免越界
        if (level < MIN_LEVEL) {
            lookback.put(4, MIN_LEVEL);
            level = MIN_LEVEL;
            SpiderDebug.log(String.format("回看层级低于下限，重置为%d", MIN_LEVEL));
        }
        if (level > MAX_LEVEL) {
            lookback.put(4, MAX_LEVEL);
            level = MAX_LEVEL;
            SpiderDebug.log(String.format("回看层级超过上限，重置为%d", MAX_LEVEL));
        }

        int count = RuleUtils.getSubStringCount(node, lookback.getString(0));
        if (count > 3 && level > MIN_LEVEL) {
            // 降低层级
            lookback.put(4, level - 1);
            SpiderDebug.log(String.format("找到过多的url匹配项(%d)，降低匹配层级为%d", count, level - 1));
            return -2;
        }

        // 尝试找图片和标题
        String pic = guessValueVodPic(node, 0);
        String vName = guessValueVodName(node, 0);
        if (pic.isEmpty() || vName.isEmpty()) {
            // 防护：检测震荡（在相邻层级间反复横跳）
            // 如果当前已在最高层仍找不到，说明规则不匹配，强制返回当前层级避免死循环
            if (level >= MAX_LEVEL) {
                SpiderDebug.log(String.format("回看层级已达上限(%d)仍未找到图片/标题，规则可能不匹配，强制使用当前层级", MAX_LEVEL));
                return level;
            }
            // 增加层级
            lookback.put(4, level + 1);
            SpiderDebug.log(String.format("当前层级未找到(%s)，增加匹配层级为%d", pic.isEmpty() ? "图片" : "标题", level + 1));
            return -2;
        }

        return level;
    }

    /**
     * 检查是否应过滤
     */
    private boolean shouldFilter(String node, String vodId, JSONObject list) {
        String filterWord = getRuleVal("filter_word");
        if (filterWord.isEmpty()) return false;

        String vodName = RuleUtils.findSubString(node, 0, list.optJSONArray("vod_name"));
        for (String word : filterWord.split("[,，]")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty() && (vodId.contains(trimmed) || vodName.contains(trimmed))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建视频对象
     */
    private JSONObject buildVideoObject(String node, String vodId, JSONObject list, String url) throws JSONException {
        JSONObject v = new JSONObject();
        v.put("vod_id", vodId);

        // 名称
        String vodName = RuleUtils.findSubString(node, 0, list.optJSONArray("vod_name"));
        if (vodName.isEmpty()) vodName = guessValueVodName(node, 0);
        v.put("vod_name", vodName);

        // 图片
        String vodPic = addHttpPrefix(RuleUtils.findSubString(node, 0, list.optJSONArray("vod_pic")));
        if (vodPic.isEmpty()) vodPic = guessValueVodPic(node, 0);
        if ("1".equals(getRuleVal("PicNeedProxy")) && !vodPic.isEmpty()) {
            vodPic = fixCover(vodPic, url);
        }
        // 播放图片：列表缺封面时的兜底图（对应配置 播放图片）
        if (vodPic.isEmpty() && !playImage.isEmpty()) {
            vodPic = playImage;
        }
        v.put("vod_pic", vodPic);

        // 备注
        String remarks = RuleUtils.findSubString(node, 0, list.optJSONArray("vod_remarks"));
        if (remarks.isEmpty()) remarks = guessValueVodRemarks(node, 0, vodName);
        v.put("vod_remarks", remarks);

        // 编码 vod_id
        v.put("vod_id", encodeVodId(v));
        return v;
    }

    /**
     * 当 list 为空时，从配置规则自动构建 list（兼容没有 list_array 的纯文本模式）
     */
    private JSONObject buildListFromRules() throws JSONException {
        JSONObject list = new JSONObject();
        String listArray = getRuleVal("list_array");
        if (listArray.isEmpty()) return list;

        String listId = getRuleVal("list_id");
        String listName = getRuleVal("list_name");
        String listPic = getRuleVal("list_pic");
        String listRemarks = getRuleVal("list_remarks");

        // 构建 vod lookback: [搜索字符串, 后缀, leftOffset, rightOffset, lookbackLevel]
        // 后缀不能为 ""，否则 findBlockPos 和 nodeString 逻辑异常
        JSONArray vodLookback = new JSONArray();
        vodLookback.put(listArray);       // 0: 搜索起始字符串（数组匹配字符串）
        vodLookback.put(listArray);       // 1: 后缀（用自身作为终止符）
        vodLookback.put(0);               // 2: 左偏移
        vodLookback.put(0);               // 3: 右偏移
        vodLookback.put(3);               // 4: 回看层级

        list.put("vod", vodLookback);

        // 构建字段规则（格式: [前缀, 后缀, leftOffset, rightOffset]）
        // 后缀不能为 ""，否则 findSubString 中 indexOf("") 返回 startPos，导致 end < start
        // 用前缀自身作为后缀（类似 "href=\"/anime/&&\"" 中的写法），仅取第一个匹配
        if (!listId.isEmpty()) {
            JSONArray idRule = new JSONArray();
            idRule.put(listId); idRule.put(listId); idRule.put(0); idRule.put(0);
            list.put("vod_id", idRule);
        }
        if (!listName.isEmpty()) {
            JSONArray nameRule = new JSONArray();
            nameRule.put(listName); nameRule.put(listName); nameRule.put(0); nameRule.put(0);
            list.put("vod_name", nameRule);
        }
        if (!listPic.isEmpty()) {
            JSONArray picRule = new JSONArray();
            picRule.put(listPic); picRule.put(listPic); picRule.put(0); picRule.put(0);
            list.put("vod_pic", picRule);
        }
        if (!listRemarks.isEmpty()) {
            JSONArray remarksRule = new JSONArray();
            remarksRule.put(listRemarks); remarksRule.put(listRemarks); remarksRule.put(0); remarksRule.put(0);
            list.put("vod_remarks", remarksRule);
        }

        SpiderDebug.log("buildListFromRules: list_array=" + listArray + " list_id=" + listId);
        return list;
    }

    /**
     * 构建分类返回结果
     * <p>
     * 空页时将 pagecount 回填为 当前页-1（第1页空则为0），让前端停止展示"下一页"；
     * 有内容时维持 Integer.MAX_VALUE（引擎无法预知真实总页数）。
     * 瞬时请求失败导致的误判代价仅是少翻一页，重新进入分类即可恢复。
     */
    private String buildCategoryResult(JSONArray videos, String pg) throws JSONException {
        return wrapList(videos, pg).toString();
    }

    // ==================== 播放列表相关 ====================

    /**
     * 生成默认播放线路名称
     */
    public List<String> makeVodPlayFrom(int size) {
        List<String> vec = new ArrayList<>();
        for (int i = 1; i <= size; ++i) {
            vec.add("播放列表" + i);
        }
        return vec;
    }

    /**
     * 查找播放线路名称
     */
    public List<String> findVodPlayFrom(String content, int expectedSize) {
        try {
            JSONObject playlist = this.rule.getJSONObject("playlist");
            if (!playlist.has("vod_play_from")) {
                // 尝试 from_array 规则
                List<String> fromArrayResult = tryExtractFromArray(content, expectedSize);
                if (fromArrayResult != null) return fromArrayResult;
                return makeVodPlayFrom(expectedSize);
            }
            return extractFromRuleConfig(content, playlist, expectedSize);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return makeVodPlayFrom(expectedSize);
    }

    /**
     * 尝试从 from_array 提取线路名
     */
    private List<String> tryExtractFromArray(String content, int expectedSize) {
        String fromArray = getRuleVal("from_array");
        if (fromArray.isEmpty()) return null;

        String lineSecondCut = getRuleVal("line_second_cut");
        if (!lineSecondCut.isEmpty()) {
            content = applySecondCut(content, applyOrSelector(lineSecondCut));
        }

        String processed = applyOrSelector(fromArray);
        String cutRule = applyPostProcessors(processed);
        String[] parts = cutRule.split("&&");
        if (parts.length < 2) return null;

        List<String> lines = extractLinesByRule(content, parts[0].trim(), parts.length > 1 ? parts[1].trim() : "", expectedSize);
        return lines.isEmpty() ? null : refinePlayFromNames(lines);
    }

    /**
     * 按规则提取行
     */
    private List<String> extractLinesByRule(String content, String start, String end, int maxSize) {
        List<String> lines = new ArrayList<>();
        int linePos = 0;
        while (lines.size() < maxSize) {
            int startPos = content.indexOf(start, linePos);
            if (startPos < 0) break;
            int startIndex = startPos + start.length();
            int endIndex = calculateEndIndex(content, startIndex, end, start);
            lines.add(content.substring(startIndex, endIndex).trim());
            linePos = endIndex + (end.isEmpty() ? 0 : end.length());
        }
        return lines;
    }

    /**
     * 计算结束位置
     */
    private int calculateEndIndex(String content, int startIndex, String end, String start) {
        if (end.isEmpty()) {
            int nextStart = content.indexOf(start, startIndex);
            int endIndex = nextStart >= 0 ? nextStart : content.length();
            int quote = content.indexOf('"', startIndex);
            if (quote >= 0 && quote < endIndex) endIndex = quote;
            int tag = content.indexOf('<', startIndex);
            if (tag >= 0 && tag < endIndex) endIndex = tag;
            return endIndex;
        } else {
            int endIndex = content.indexOf(end, startIndex);
            return endIndex >= 0 ? endIndex : content.length();
        }
    }

    /**
     * 从规则配置提取线路名
     */
    private List<String> extractFromRuleConfig(String content, JSONObject playlist, int expectedSize) throws JSONException {
        List<Pair<Integer, String>> playFromList = new ArrayList<>();
        JSONArray rulePlayFrom = playlist.getJSONArray("vod_play_from");

        for (int i = 0; i < rulePlayFrom.length(); ++i) {
            Object entry = rulePlayFrom.get(i);
            String key = "";
            String alias = "";

            if (entry instanceof String) {
                key = alias = (String) entry;
            } else if (entry instanceof JSONArray) {
                JSONArray item = (JSONArray) entry;
                key = alias = item.getString(0);
                if (item.length() > 1) alias = item.getString(1);
            } else {
                return makeVodPlayFrom(expectedSize);
            }

            int position = content.indexOf(key);
            if (position == -1) continue;
            playFromList.add(new Pair<>(position, alias));
        }

        if (playFromList.size() != expectedSize) return makeVodPlayFrom(expectedSize);

        // 排序
        Collections.sort(playFromList, Comparator.comparingInt(pair -> pair.first));

        List<String> result = new ArrayList<>();
        for (Pair<Integer, String> pair : playFromList) {
            result.add(pair.second);
        }
        return result;
    }

    /**
     * 用 from_title 规则精炼线路名
     */
    protected List<String> refinePlayFromNames(List<String> lines) {
        try {
            String fromTitle = getRuleVal("from_title");
            if (fromTitle.isEmpty()) return lines;

            String[] tp = applyPostProcessors(applyOrSelector(fromTitle)).split("&&");
            String ts = tp.length > 0 ? tp[0].trim() : "";
            String te = tp.length > 1 ? tp[1].trim() : "";
            if (ts.isEmpty()) return lines;

            List<String> refined = new ArrayList<>();
            for (String line : lines) {
                String val = line;
                int a = line.indexOf(ts);
                if (a >= 0) {
                    a += ts.length();
                    int b = te.isEmpty() ? line.length() : line.indexOf(te, a);
                    if (b < 0) b = line.length();
                    val = cleanHtml(line.substring(a, b));
                }
                refined.add(val.isEmpty() ? line : val);
            }
            return refined;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return lines;
    }

    /**
     * 查找播放列表URL
     */
    public List<String> findVodPlayUrl(String content) {
        List<String> tmpPlayUrl = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        try {
            JSONObject playlist = this.rule.getJSONObject("playlist");
            int sort = playlist.optInt("sort", 0);
            Set<Integer> removeSet = new HashSet<>();

            // 尝试线路链接模式（线路数组 + 线路链接：每条线路指向独立页面，逐页拉取剧集）
            List<String> fromLinkResult = tryFromLinkMode(content);
            if (fromLinkResult != null) return fromLinkResult;

            // 尝试多线模式
            List<String> multiLineResult = tryMultiLineMode(content);
            if (multiLineResult != null) return multiLineResult;

            // 尝试 play_array 分块模式
            List<String> playArrayResult = tryPlayArrayMode(content, playlist, sort, tmpPlayUrl, removeSet);
            if (playArrayResult != null) return playArrayResult;

            // 兜底：配置了「播放数组」却漏配「播放链接」时，按默认 href 规则解析选集
            List<String> playArrayDefault = tryPlayArrayDefaultMode(content, sort);
            if (playArrayDefault != null) return playArrayDefault;

            // 收集结果
            for (int i = 0; i < tmpPlayUrl.size(); ++i) {
                if (!removeSet.contains(i)) {
                    playUrl.add(tmpPlayUrl.get(i));
                }
            }

            // 兜底：三条显式路径均未命中时，消费 playlist.vod_play_url 回看规则
            // （来源：播放列表/url_array 扁平字段或 guessRuleVodPlayUrl 的猜测结果，
            //   单集页站点整页即一集，取首个匹配作为唯一剧集）
            if (playUrl.isEmpty()) {
                JSONArray playLookback = playlist.optJSONArray("vod_play_url");
                if (playLookback != null) {
                    // 逐条扫描直到拿到一条像样的选集链接：
                    // 旧实现只取全页第一个 href，站点 <head> 里的 favicon / CSS 会被当成播放地址
                    String url = "";
                    int scan = 0;
                    for (int i = 0; i < MAX_FALLBACK_SCAN && scan < content.length(); i++) {
                        String one = RuleUtils.findSubString(content, scan, playLookback);
                        if (one.isEmpty()) break;
                        int at = content.indexOf(one, scan);
                        scan = at >= 0 ? at + one.length() : scan + 1;
                        if (isPlausibleEpisodeUrl(one)) {
                            url = one;
                            break;
                        }
                    }
                    if (!url.isEmpty()) {
                        // 与 extractEpisodes 产出对齐：标题$链接 格式，链接补全为绝对地址
                        // 标题取链接结束位置之后的默认边界（>...<，即 <a> 文本）
                        int urlEnd = content.indexOf(url) + url.length();
                        String[] titleBounds = getTitleBounds();
                        String title = extractEpisodeTitle(content, urlEnd, titleBounds);
                        if (title.isEmpty() || title.contains("\n")) title = "第1集";
                        playUrl.add(title + "$" + addHttpPrefix(url));
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return playUrl;
    }

    /**
     * 尝试多线模式
     */
    private List<String> tryMultiLineMode(String content) throws JSONException {
        String multiLineArray = getRuleVal("multi_line_array");
        String multiLineUrl = getRuleVal("multi_line_url");
        if (multiLineArray.isEmpty() || multiLineUrl.isEmpty()) return null;

        String multiLineTwice = getRuleVal("multi_line_twice");
        String multiLinePrefix = getRuleVal("multi_line_prefix");
        String multiLineSuffix = getRuleVal("multi_line_suffix");

        String processedBody = content;
        if (!multiLineTwice.isEmpty()) {
            processedBody = applySecondCut(content, applyOrSelector(multiLineTwice));
        }

        return extractMultiLines(processedBody, multiLineArray, multiLineUrl, multiLinePrefix, multiLineSuffix);
    }

    /**
     * 提取多线数据
     */
    private List<String> extractMultiLines(String body, String arrayRule, String urlRule,
                                            String prefix, String suffix) throws JSONException {
        String[] arrayParts = applyOrSelector(arrayRule).split("&&");
        String[] urlParts = applyOrSelector(urlRule).split("&&");
        if (arrayParts.length < 2 || urlParts.length < 2) return null;

        List<String> lines = new ArrayList<>();
        int linePos = 0;
        while (lines.size() < 10) {
            int aStart = body.indexOf(arrayParts[0].trim(), linePos);
            if (aStart < 0) break;
            int aEnd = body.indexOf(arrayParts[1].trim(), aStart + arrayParts[0].length());
            if (aEnd < 0) break;

            String lineContent = body.substring(aStart + arrayParts[0].length(), aEnd);
            int uStart = lineContent.indexOf(urlParts[0].trim());
            if (uStart < 0) { linePos = aEnd + arrayParts[1].length(); continue; }

            String afterUrlStart = lineContent.substring(uStart + urlParts[0].length());
            int uEnd = afterUrlStart.indexOf(urlParts[1].trim());
            if (uEnd < 0) { linePos = aEnd + arrayParts[1].length(); continue; }

            lines.add(prefix + afterUrlStart.substring(0, uEnd) + suffix);
            linePos = aEnd + arrayParts[1].length();
        }

        if (lines.isEmpty()) return null;
        List<String> result = new ArrayList<>();
        result.add(TextUtils.join("#", lines));
        return result;
    }

    /**
     * 尝试线路链接模式（对应配置 线路数组 + 线路链接）
     * <p>
     * 与「多线模式」(multi_line_array + multi_line_url) 的区别：本模式线路名来自 线路数组(from_array)，
     * 每条线路是一个指向独立页面的链接（线路链接 multi_line_url 提取 href），需逐页拉取剧集后合并。
     * 仅在 from_array 与 multi_line_url 同时存在、且未配置 multi_line_array 时触发，避免与多线模式重复处理。
     */
    private List<String> tryFromLinkMode(String content) throws JSONException {
        String fromArray = getRuleVal("from_array");
        String lineUrl = getRuleVal("multi_line_url");
        String multiLineArray = getRuleVal("multi_line_array");
        if (fromArray.isEmpty() || lineUrl.isEmpty() || !multiLineArray.isEmpty()) return null;

        // 应用线路二次截取
        String processedBody = content;
        String lineSecondCut = getRuleVal("line_second_cut");
        if (!lineSecondCut.isEmpty()) {
            processedBody = applySecondCut(content, applyOrSelector(lineSecondCut));
        }

        // 拆分 线路数组 起止规则
        String[] parts = applyPostProcessors(applyOrSelector(fromArray)).split("&&");
        if (parts.length < 2) return null;
        String start = parts[0].trim();
        String end = parts.length > 1 ? parts[1].trim() : "";

        // 提取每条线路的区块（含 href 与名称）
        List<String> lineRegions = extractLinesByRule(processedBody, start, end, 10);
        if (lineRegions.isEmpty()) return null;

        JSONObject playlist = rule.optJSONObject("playlist");
        int sort = playlist != null ? playlist.optInt("sort", 0) : 0;
        List<String> lines = new ArrayList<>();
        for (String region : lineRegions) {
            if (lines.size() >= 10) break;
            String url = extractSingleUrl(region, lineUrl);
            if (url.isEmpty()) continue;
            url = addHttpPrefix(url);

            // 拉取线路页并解析剧集（复用 play_array 分块解析）
            try {
                String lineBody = fetchUrl(url, playlist != null ? playlist.optJSONObject("header") : null);
                lineBody = RuleUtils.getRegion(lineBody, playlist);
                String playTwice = getRuleVal("play_twice");
                if (!playTwice.isEmpty()) {
                    lineBody = applySecondCut(lineBody, applyOrSelector(playTwice));
                }
                List<String> eps = tryPlayArrayMode(lineBody, playlist, sort, new ArrayList<>(), new HashSet<>());
                if (eps != null && !eps.isEmpty()) {
                    lines.add(TextUtils.join("#", eps));
                }
            } catch (Exception e) {
                SpiderDebug.log(safeLog("线路链接模式拉取失败: " + url + " -> " + e.getMessage()));
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    /**
     * 从区块文本中按 URL 规则提取单个链接
     * urlRule 形如 href="&&" 或 "&&"，按 && 拆分为起止边界
     */
    private String extractSingleUrl(String region, String urlRule) {
        try {
            String[] up = applyPostProcessors(applyOrSelector(urlRule)).split("&&", 2);
            if (up.length < 2) return "";
            String s = up[0].trim();
            String e = up[1].trim();
            if (s.isEmpty()) return "";
            int a = region.indexOf(s);
            if (a < 0) return "";
            int b = e.isEmpty() ? region.indexOf('"', a + s.length())
                    : region.indexOf(e, a + s.length());
            if (b < 0) b = region.length();
            return region.substring(a + s.length(), b).trim();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * 尝试 play_array 分块模式
     */
    private List<String> tryPlayArrayMode(String content, JSONObject playlist, int sort,
                                           List<String> tmpPlayUrl, Set<Integer> removeSet) throws JSONException {
        String playArrayRule = getRuleVal("play_array");
        String urlUrlRule = getRuleVal("url_url");
        if (playArrayRule.isEmpty() || urlUrlRule.isEmpty() ||
            !playArrayRule.contains("&&") || !urlUrlRule.contains("&&")) return null;

        String[] pa = applyPostProcessors(applyOrSelector(playArrayRule)).split("&&", 2);
        String[] ua = applyPostProcessors(applyOrSelector(urlUrlRule)).split("&&", 2);
        String listStart = pa[0].trim();
        String listEnd = pa.length > 1 ? pa[1].trim() : "</ul>";
        String hrefStart = ua[0].trim();
        String hrefEnd = ua.length > 1 ? ua[1].trim() : "\"";

        // 标题规则
        String[] titleBounds = getTitleBounds();
        // 播放列表(url_array)：选集元素定位（如 "<a&&/a>[包含:magnet]"），
        // 配置了就按「元素」逐条取 href/标题，未配置则沿用旧的整体块内取 href。
        String urlArrayRule = getRuleVal("url_array");
        String[] itemBounds = parseItemBounds(urlArrayRule);
        int listPos = 0;
        int blockCount = 0;

        // 剧集过滤未显式配置时，若默认特征（/play/、vodplay）一条都筛不出，
        // 则放宽过滤重试一次：默认特征只适用于 maccms 系站点，
        // 磁力、网盘、自定义路由的源会被整条线路清空。
        boolean filterConfigured = !getRuleVal("episode_filter").isEmpty();

        while (true) {
            int ls = content.indexOf(listStart, listPos);
            if (ls < 0) break;
            int le = content.indexOf(listEnd, ls + listStart.length());
            if (le < 0) break;
            String block = content.substring(ls, le);
            listPos = le + listEnd.length();
            blockCount++;

            // 脚本噪声块：JS 里引用同名选择器（如 $(".hl-plays-list")）会切出横跨 </script> 的假块，
            // 混入后面的真实选集造成重复线路；真实选集 UL 内不会出现 </script>
            if (block.contains("</script")) continue;

            List<String> eps = itemBounds == null
                    ? extractEpisodes(block, hrefStart, hrefEnd, titleBounds, sort)
                    : extractEpisodesByItem(block, itemBounds[0], itemBounds[1],
                                            hrefStart, hrefEnd, titleBounds, sort);
            if (eps.isEmpty() && !filterConfigured) {
                eps = itemBounds == null
                        ? extractEpisodes(block, hrefStart, hrefEnd, titleBounds, sort, true)
                        : extractEpisodesByItem(block, itemBounds[0], itemBounds[1],
                                                hrefStart, hrefEnd, titleBounds, sort, true);
            }
            if (!eps.isEmpty()) {
                tmpPlayUrl.add(TextUtils.join("#", eps));
            }
        }

        if (!tmpPlayUrl.isEmpty()) {
            SpiderDebug.log("playArray: blocks=" + blockCount + " episodes=" + tmpPlayUrl.size());
            List<String> result = new ArrayList<>();
            for (int i = 0; i < tmpPlayUrl.size(); ++i) {
                if (!removeSet.contains(i)) result.add(tmpPlayUrl.get(i));
            }
            return result;
        }
        return null;
    }

    /** 选集链接兜底扫描的最大条数 */
    private static final int MAX_FALLBACK_SCAN = 50;

    /** 静态资源后缀：兜底扫描时剔除，避免把 favicon/CSS/JS 当成播放地址 */
    private static final List<String> STATIC_RESOURCE_EXTS = Arrays.asList(
            ".ico", ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".webp", ".woff", ".woff2", ".ttf", ".eot", ".xml", ".txt");

    /**
     * 判断候选链接是否像一条选集链接。
     * 用于「播放数组」未命中时的兜底扫描：剔除 favicon、样式表、脚本、图片等静态资源
     * 以及页内锚点和 javascript: 伪协议。
     */
    private static boolean isPlausibleEpisodeUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.isEmpty()) return false;
        String lower = u.toLowerCase();
        if (lower.startsWith("#") || lower.startsWith("javascript:")
                || lower.startsWith("mailto:") || lower.contains("favicon")) {
            return false;
        }
        int q = lower.indexOf('?');
        String path = q > 0 ? lower.substring(0, q) : lower;
        for (String ext : STATIC_RESOURCE_EXTS) {
            if (path.endsWith(ext)) return false;
        }
        return true;
    }

    /**
     * 选集解析兜底：规则提供了「播放数组」却没有「播放链接」(url_url) 时，
     * 在每个播放块内按默认 {@code href=" && "} 抽取选集链接。
     * <p>
     * 仅在 {@link #tryPlayArrayMode} 未命中（即 url_url 缺失或不含 &&）时才会走到这里，
     * 因此不会改变任何已配置播放链接的规则的行为。
     *
     * @return 解析出的线路列表；不适用时返回 null
     */
    private List<String> tryPlayArrayDefaultMode(String content, int sort) throws JSONException {
        String playArrayRule = getRuleVal("play_array");
        if (playArrayRule.isEmpty() || !playArrayRule.contains("&&")) return null;
        // 显式配置了播放链接时交给主流程处理，这里只补“漏配”的场景
        if (!getRuleVal("url_url").isEmpty()) return null;

        String[] pa = applyPostProcessors(applyOrSelector(playArrayRule)).split("&&", 2);
        String listStart = pa[0].trim();
        String listEnd = pa.length > 1 ? pa[1].trim() : "</ul>";
        String[] titleBounds = getTitleBounds();

        List<String> lines = new ArrayList<>();
        int listPos = 0;
        while (lines.size() < 10) {
            int ls = content.indexOf(listStart, listPos);
            if (ls < 0) break;
            int le = content.indexOf(listEnd, ls + listStart.length());
            if (le < 0) break;
            String block = content.substring(ls, le);
            listPos = le + listEnd.length();
            // 与 tryPlayArrayMode 一致：剔除横跨脚本的假块
            if (block.contains("</script")) continue;
            List<String> eps = extractEpisodes(block, "href=\"", "\"", titleBounds, sort);
            if (!eps.isEmpty()) lines.add(TextUtils.join("#", eps));
        }
        if (lines.isEmpty()) return null;
        SpiderDebug.log("播放链接(url_url) 未配置，已按默认 href 规则兜底解析 "
                + lines.size() + " 条线路");
        return lines;
    }

    /**
     * 获取标题边界
     */
    private String[] getTitleBounds() {
        String urlTitleRule = getRuleVal("url_title");
        if (!urlTitleRule.isEmpty() && urlTitleRule.contains("&&")) {
            return applyPostProcessors(applyOrSelector(urlTitleRule)).split("&&", 2);
        }
        return DEFAULT_TITLE_BOUNDS.clone();
    }

    /**
     * 解析「播放列表」(url_array) 的选集元素起止标记。
     * <p>
     * 形如 {@code "<a&&/a>[包含:magnet]"}：先切出每个 {@code <a ... /a>} 元素，
     * 再在元素内套用「播放链接」与「播放标题」。后处理器（如 [包含:magnet]）已由
     * {@link #applyPostProcessors} 剥离，此处只取起止标记。
     *
     * @return [起始标记, 结束标记]；未配置或无法解析返回 null
     */
    private String[] parseItemBounds(String urlArrayRule) {
        if (urlArrayRule.isEmpty()) return null;
        String[] up = applyPostProcessors(applyOrSelector(urlArrayRule)).split("&&", 2);
        String itemStart = up[0].trim();
        if (itemStart.isEmpty()) return null;
        String itemEnd = up.length > 1 ? up[1].trim() : "";
        if (itemEnd.isEmpty()) itemEnd = "</a>";
        return new String[]{itemStart, itemEnd};
    }

    /**
     * 按「选集元素」逐条提取剧集（播放列表 url_array 模式）。
     */
    private List<String> extractEpisodesByItem(String block, String itemStart, String itemEnd,
                                               String hrefStart, String hrefEnd,
                                               String[] titleBounds, int sort) {
        return extractEpisodesByItem(block, itemStart, itemEnd, hrefStart, hrefEnd,
                titleBounds, sort, false);
    }

    /**
     * 按「选集元素」逐条提取剧集。
     *
     * @param ignoreFilter true 时跳过「剧集过滤」判定（放宽重试用）
     */
    private List<String> extractEpisodesByItem(String block, String itemStart, String itemEnd,
                                               String hrefStart, String hrefEnd,
                                               String[] titleBounds, int sort, boolean ignoreFilter) {
        List<String> eps = new ArrayList<>();
        int p = 0;
        while (true) {
            int s = block.indexOf(itemStart, p);
            if (s < 0) break;
            int e = block.indexOf(itemEnd, s + itemStart.length());
            if (e < 0) break;
            String item = block.substring(s, e);
            p = e + itemEnd.length();

            int hs = item.indexOf(hrefStart);
            if (hs < 0) continue;
            int he0 = hs + hrefStart.length();
            int he = item.indexOf(hrefEnd, he0);
            if (he < 0) continue;
            String href = item.substring(he0, he).trim();
            if (href.contains("&amp;")) href = href.replace("&amp;", "&");
            if (!ignoreFilter && !matchEpisodeFilter(href)) continue;

            String title = extractEpisodeTitle(item, he, titleBounds);
            if (title.contains("展开全部")) continue;
            if (title.isEmpty()) title = "第" + (eps.size() + 1) + "集";
            eps.add(title + "$" + addHttpPrefix(href));
        }
        if (sort != 0) Collections.reverse(eps);
        return eps;
    }

    /**
     * 提取剧集列表
     */
    private List<String> extractEpisodes(String block, String hrefStart, String hrefEnd,
                                          String[] titleBounds, int sort) {
        return extractEpisodes(block, hrefStart, hrefEnd, titleBounds, sort, false);
    }

    /**
     * 提取剧集列表
     *
     * @param ignoreFilter true 时跳过「剧集过滤」判定（放宽重试用）
     */
    private List<String> extractEpisodes(String block, String hrefStart, String hrefEnd,
                                          String[] titleBounds, int sort, boolean ignoreFilter) {
        List<String> eps = new ArrayList<>();
        int hp = 0;
        while (true) {
            int hs = block.indexOf(hrefStart, hp);
            if (hs < 0) break;
            int he0 = hs + hrefStart.length();
            int he = block.indexOf(hrefEnd, he0);
            if (he < 0) break;
            String href = block.substring(he0, he).trim();
            // HTML 实体还原：磁力等链接里的 &amp; 需还原为 &（首参数之外的参数才不被破坏）
            if (href.contains("&amp;")) href = href.replace("&amp;", "&");
            hp = he + hrefEnd.length();
            if (!ignoreFilter && !matchEpisodeFilter(href)) continue;

            String title = extractEpisodeTitle(block, he, titleBounds);
            if (title.contains("展开全部")) continue;
            if (title.isEmpty()) title = "第" + (eps.size() + 1) + "集";
            eps.add(title + "$" + addHttpPrefix(href));
        }
        if (sort != 0) Collections.reverse(eps);
        return eps;
    }

    /**
     * 剧集链接过滤（可配置）
     * <p>
     * 配置 episode_filter：
     * <ul>
     *   <li>未配置：沿用默认特征 href 含 "/play/" 或 "vodplay"（向后兼容）</li>
     *   <li>"0"：关闭过滤，所有 url_url 匹配到的链接均视为剧集</li>
     *   <li>其他值：按 "#" 分隔的多关键词，任一命中即保留</li>
     * </ul>
     */
    private boolean matchEpisodeFilter(String href) {
        if (href == null || href.isEmpty()) return false;
        String cfg = getRuleVal("episode_filter");
        if (cfg.equals("0")) return true;
        if (!cfg.isEmpty()) {
            for (String kw : cfg.split("#")) {
                kw = kw.trim();
                if (!kw.isEmpty() && href.contains(kw)) return true;
            }
            return false;
        }
        // 默认：兼容旧版硬编码行为
        return href.contains("/play/") || href.contains("vodplay");
    }

    /**
     * 提取剧集标题
     */
    private String extractEpisodeTitle(String block, int hrefEnd, String[] titleBounds) {
        String titleStart = titleBounds[0];
        String titleEnd = titleBounds[1];
        int ts = block.indexOf(titleStart, hrefEnd);
        if (ts >= 0 && ts < hrefEnd + 120) {
            int te = block.indexOf(titleEnd, ts + titleStart.length());
            if (te > ts) return cleanHtml(block.substring(ts + titleStart.length(), te));
        }
        return "";
    }

    // ==================== 详情页接口 ====================

    /**
     * 猜测详情数据区域
     */
    protected String guessDetailContentRegion(String body) {
        String regex = String.format(">\\s*?(%s)|(%s)", TextUtils.join("|", detailFieldNames), TextUtils.join("：|", detailFieldNames));
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(body);
        List<HtmlMatchInfo> matchList = new ArrayList<>();

        while (m.find()) {
            HtmlMatchInfo info = new HtmlMatchInfo();
            info.init(m);
            info.uploads = HtmlNodeHelper.findUpNodes(body, info.startPos, 5);

            if (!matchList.isEmpty()) {
                if (!matchList.get(0).hasSameUpNode(info)) {
                    if (matchList.size() > 1) {
                        boolean hasDirector = false;
                        for (HtmlMatchInfo item : matchList) {
                            if (item.group0.indexOf("导演") != -1) {
                                hasDirector = true;
                                break;
                            }
                        }
                        if (hasDirector) return HtmlNodeHelper.nodeString(body, matchList.get(0).matchedUpNodePos);
                        matchList.clear();
                    }
                    matchList.clear();
                }
            }
            matchList.add(info);
        }

        if (matchList.size() > 1) {
            return HtmlNodeHelper.nodeString(body, matchList.get(0).matchedUpNodePos);
        }
        return "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            fetchRule();
            JSONObject vinfo = new JSONObject(new String(Base64.decode(ids.get(0), BASE64_FLAG), "UTF-8"));
            JSONObject detail = rule.optJSONObject("detail");
            if (detail == null) return "";

            // 构建详情页URL
            String vid = vinfo.optString("vod_id", "");
            String detailUrl = buildDetailUrl(detail, vid);

            // 获取详情页内容
            String body = fetchUrl(detailUrl, detail.optJSONObject("header"));
            String content = RuleUtils.getRegion(body, detail);

            // 详情二次截取
            String detailTwice = getRuleVal("detail_twice");
            if (!detailTwice.isEmpty()) {
                content = applySecondCut(content, applyOrSelector(detailTwice));
            }

            // ========== CSS 选择器模式（优先） ==========
            if (isCssModeEnabled(detail)) {
                SpiderDebug.log("详情页使用 CSS/Jsoup 模式提取");
                JSONObject cssVod = extractDetailByCss(body, detail);
                if (cssVod.length() > 0) {
                    supplementDetailFieldsFromContext(cssVod, vinfo, vid, detailUrl);
                    playlistContent(ids, cssVod, body);
                    return buildDetailResult(cssVod);
                }
                SpiderDebug.log("CSS 提取无结果，回退到传统模式");
            }

            // ========== 传统正则/字符串截取模式 ==========
            // 定位详情数据范围
            DetailExtractionContext ctx = locateDetailRegion(content, body);
            JSONObject vod = extractDetailFields(ctx, vinfo, vid, detailUrl);

            // 获取播放列表
            playlistContent(ids, vod, body);

            // 返回结果
            return buildDetailResult(vod);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return "";
        }
    }

    /**
     * 详情提取上下文
     */
    private static class DetailExtractionContext {
        String content;
        String nodeString;
        int startPos;
        JSONArray lookback;
    }

    /**
     * 构建详情页URL
     */
    private String buildDetailUrl(JSONObject detail, String vid) throws JSONException {
        // 顶层「详情url」优先（标准模板字段）；未配置时沿用 detail.url 子对象写法
        String flatUrl = getRuleVal("detail_url");
        if (!flatUrl.isEmpty()) {
            return addHttpPrefix(flatUrl.replace("${vid}", vid).replace("{vid}", vid));
        }
        if (detail.has("url")) {
            return detail.getString("url").replace("${vid}", vid).replace("{vid}", vid);
        } else if (vid.startsWith("http://") || vid.startsWith("https://") || vid.startsWith("/")) {
            return addHttpPrefix(vid);
        } else {
            JSONObject list = rule.optJSONObject("list");
            if (list != null) {
                JSONArray tmp = list.optJSONArray("vod_id");
                if (tmp != null && tmp.length() >= 2) {
                    return addHttpPrefix(tmp.getString(0) + vid + tmp.getString(1));
                }
            }
            return addHttpPrefix(vid);
        }
    }

    /**
     * 定位详情数据区域
     */
    private DetailExtractionContext locateDetailRegion(String content, String body) throws JSONException {
        DetailExtractionContext ctx = new DetailExtractionContext();
        ctx.content = content;
        ctx.startPos = 0;

        JSONObject detail = rule.optJSONObject("detail");
        ctx.lookback = RuleUtils.getLookbackArray(detail);
        if (ctx.lookback != null) {
            int pos = content.indexOf(ctx.lookback.getString(0), 0);
            if (pos != -1) {
                List<Integer> arr = HtmlNodeHelper.findUpNodes(content, pos - 1, ctx.lookback.getInt(4));
                if (!arr.isEmpty()) {
                    ctx.startPos = arr.get(arr.size() - 1);
                    ctx.nodeString = HtmlNodeHelper.nodeString(content, ctx.startPos);
                }
            }
        }

        // 没有指定区域则猜测
        if (ctx.nodeString == null || ctx.nodeString.isEmpty()) {
            ctx.nodeString = guessDetailContentRegion(body);
        }

        // 猜测失败（空串）时保留完整页面作为区域——旧判断把空串也当成有效区域，
        // 导致详情字段全部截空
        if (ctx.nodeString != null && !ctx.nodeString.isEmpty() && ctx.nodeString.length() != content.length()) {
            ctx.content = ctx.nodeString;
            ctx.startPos = 0;
        }
        return ctx;
    }

    /**
     * 提取详情字段
     */
    private JSONObject extractDetailFields(DetailExtractionContext ctx, JSONObject vinfo,
                                             String vid, String detailUrl) throws JSONException {
        JSONObject detail = rule.optJSONObject("detail");
        JSONObject vod = new JSONObject();
        vod.put("vod_id", vinfo.optString("vod_id", ""));

        // 基本字段提取
        vod.put("vod_name", extractWithFallback(ctx, detail, "vod_name", vinfo, "vod_name"));
        vod.put("vod_pic", addHttpPrefix(extractWithFallback(ctx, detail, "vod_pic", vinfo, "vod_pic")));
        vod.put("type_name", RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray("type_name")));
        vod.put("vod_year", RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray("vod_year")));
        vod.put("vod_area", RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray("vod_area")));
        vod.put("vod_remarks", RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray("vod_remarks")));
        vod.put("vod_actor", RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray("vod_actor")));
        vod.put("vod_director", RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray("vod_director")));
        vod.put("vod_content", RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray("vod_content")));

        // 名称补充
        if (vod.getString("vod_name").isEmpty()) {
            vod.put("vod_name", guessValueVodName(ctx.content, ctx.startPos));
        }

        // 图片补充
        if (vod.getString("vod_pic").isEmpty()) {
            vod.put("vod_pic", guessValueVodPic(ctx.content, ctx.startPos));
        }
        // 播放图片：详情缺封面时的兜底图（对应配置 播放图片）
        if (vod.getString("vod_pic").isEmpty() && !playImage.isEmpty()) {
            vod.put("vod_pic", playImage);
        }
        if ("1".equals(getRuleVal("PicNeedProxy")) && !vod.getString("vod_pic").isEmpty()) {
            vod.put("vod_pic", fixCover(vod.getString("vod_pic"), detailUrl));
        }

        // 补充详情字段
        supplementDetailFields(vod, ctx);

        // 详情分隔符：按 label 词从纯文本补缺（仅补空字段）
        applyDetailSeparator(vod, ctx);
        // 详情合并字段：把指定字段以「中文名：值」追加进简介
        mergeDetailFields(vod);

        return vod;
    }

    /**
     * 详情分隔符：从详情页纯文本中按 label 词提取字段，仅补缺、不覆盖已有值。
     * <p>
     * 配置形如 {@code "导演：|主演：|地区：|年份：|状态："}（| 分隔多个 label）。
     * 自动映射：导演→vod_director、主演/演员→vod_actor、地区/国家→vod_area、
     * 年份/年代→vod_year、状态/备注/更新→vod_remarks、类型/分类→type_name、
     * 简介/剧情/介绍→vod_content。
     */
    private void applyDetailSeparator(JSONObject vod, DetailExtractionContext ctx) throws JSONException {
        String sep = getRuleVal("detail_separator");
        if (sep.isEmpty()) return;
        // 与 guessSupplementDetailFields 一致：以 !!!! 作标签分隔哨兵
        String text = HtmlNodeHelper.trimHtmlString(ctx.content, "!!!!");
        String[] labels = sep.split("\\|");
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i].trim();
            if (label.isEmpty()) continue;
            int at = text.indexOf(label);
            if (at < 0) continue;
            int from = at + label.length();
            int to = text.length();
            for (int j = i + 1; j < labels.length; j++) {
                String next = labels[j].trim();
                if (next.isEmpty()) continue;
                int p = text.indexOf(next, from);
                if (p >= 0 && p < to) to = p;
            }
            String value = text.substring(from, to).trim();
            String field = mapDetailLabel(label);
            if (field != null && !value.isEmpty() && value.length() < 500
                    && vod.optString(field, "").isEmpty()) {
                vod.put(field, value);
            }
        }
    }

    /** 详情 label 词 → 标准字段名；无法识别返回 null */
    private static String mapDetailLabel(String label) {
        if (label.contains("导演")) return "vod_director";
        if (label.contains("主演") || label.contains("演员")) return "vod_actor";
        if (label.contains("地区") || label.contains("国家")) return "vod_area";
        if (label.contains("年份") || label.contains("年代")) return "vod_year";
        if (label.contains("状态") || label.contains("备注") || label.contains("更新")) return "vod_remarks";
        if (label.contains("类型") || label.contains("分类")) return "type_name";
        if (label.contains("简介") || label.contains("剧情") || label.contains("介绍")) return "vod_content";
        return null;
    }

    /**
     * 详情合并字段：把逗号分隔的字段以「中文名：值」换行追加到简介尾部（只增量，不覆盖已有正文）。
     * 字段可写中文 label（状态、类型、年份）或标准英文键（vod_year、type_name）。
     */
    private void mergeDetailFields(JSONObject vod) throws JSONException {
        String merge = getRuleVal("detail_merge");
        if (merge.isEmpty()) return;
        StringBuilder sb = new StringBuilder(vod.optString("vod_content", ""));
        for (String raw : merge.split("[,，]")) {
            String f = raw.trim();
            if (f.isEmpty()) continue;
            String field = mapDetailLabel(f);
            if (field == null) field = XBPQKey.norm(f);
            String val = vod.optString(field, "");
            if (val.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(XBPQKey.cn(field)).append("：").append(val);
        }
        vod.put("vod_content", sb.toString());
    }

    /**
     * 提取字段（带回退）
     */
    private String extractWithFallback(DetailExtractionContext ctx, JSONObject detail,
                                        String field, JSONObject source, String sourceField) throws JSONException {
        String value = RuleUtils.findSubString(ctx.content, ctx.startPos, detail.optJSONArray(field));
        if (value.isEmpty()) {
            value = source.optString(sourceField, "");
        }
        return value;
    }

    /**
     * 补充详情字段
     */
    private void supplementDetailFields(JSONObject vod, DetailExtractionContext ctx) throws JSONException {
        if (ctx.lookback == null || ctx.lookback.length() <= 1) {
            // 猜测模式
            guessSupplementDetailFields(vod, ctx);
        } else {
            // lookback 模式
            lookbackSupplementDetailFields(vod, ctx);
        }
    }

    /**
     * lookback 模式补充
     */
    private void lookbackSupplementDetailFields(JSONObject vod, DetailExtractionContext ctx) throws JSONException {
        JSONArray key = new JSONArray();
        String name = ctx.lookback.getString(0);
        String skey = findSimilarKeyName(name);

        key.put(name);
        key.put(ctx.lookback.getString(1));

        if (vod.getString("vod_director").isEmpty()) {
            key.put(0, name.replace(skey, "导演"));
            vod.put("vod_director", RuleUtils.findSubString(ctx.content, ctx.startPos, key));
        }
        if (vod.getString("vod_actor").isEmpty()) {
            key.put(0, name.replace(skey, "主演"));
            vod.put("vod_actor", RuleUtils.findSubString(ctx.content, ctx.startPos, key));
        }
        if (vod.getString("vod_content").isEmpty()) {
            vod.put("vod_content", extractLongestText(ctx.content));
        }
    }

    /**
     * 查找相似的关键字
     */
    private String findSimilarKeyName(String name) {
        List<String> candidates = Arrays.asList("导演", "演员", "类型", "年份");
        for (String candidate : candidates) {
            if (name.indexOf(candidate) != -1) return candidate;
        }
        return name;
    }

    /**
     * 提取最长文本（作为简介）
     */
    private String extractLongestText(String content) {
        String all = HtmlNodeHelper.trimHtmlString(content, "!!!!");
        String[] words = all.split("!!!!");
        String longest = "";
        for (String word : words) {
            if (word.length() > longest.length()) longest = word;
        }
        return HtmlNodeHelper.trimHtmlString(longest);
    }

    /**
     * 猜测模式补充详情字段
     */
    private void guessSupplementDetailFields(JSONObject vod, DetailExtractionContext ctx) throws JSONException {
        if (ctx.nodeString == null || ctx.nodeString.isEmpty()) return;

        List<String> childNodes = HtmlNodeHelper.getChildNodes(ctx.nodeString);
        String content = "";
        String delimiter = TextUtils.join("|", detailFieldNames);
        Pattern pattern = Pattern.compile(delimiter, Pattern.CASE_INSENSITIVE);

        for (String node : childNodes) {
            String text = HtmlNodeHelper.trimHtmlString(node, " ").replace("：", "");
            if (text.length() > content.length()) content = text;

            String[] items = text.split(delimiter);
            List<String> nonEmptyItems = new ArrayList<>();
            for (String item : items) {
                if (!item.isEmpty()) nonEmptyItems.add(item);
            }

            Matcher m = pattern.matcher(text);
            int index = 0;
            while (m.find() && index < nonEmptyItems.size()) {
                String matched = m.group(0);
                for (int j = 0; j < detailFieldNames.size(); ++j) {
                    if (matched.indexOf(detailFieldNames.get(j)) != -1) {
                        String key = detailFieldKeys.get(j);
                        if (vod.getString(key).isEmpty()) {
                            vod.put(key, nonEmptyItems.get(index).trim());
                        }
                        break;
                    }
                }
                ++index;
            }
        }

        if (vod.getString("vod_content").isEmpty()) {
            vod.put("vod_content", content);
        }
    }

    /**
     * 构建详情返回结果
     */
    private String buildDetailResult(JSONObject vod) throws JSONException {
        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject result = new JSONObject();
        // 标准模板消费 data，影视仓/海螺/苹果消费 list；同时给出，互不干扰
        result.put("code", CODE_OK);
        result.put("msg", "ok");
        result.put("data", vod);
        result.put("list", list);
        return result.toString();
    }

    /**
     * 获取播放列表
     */
    protected void playlistContent(List<String> ids, JSONObject vod, String body) {
        try {
            fetchRule();
            JSONObject vinfo = new JSONObject(new String(Base64.decode(ids.get(0), BASE64_FLAG), "UTF-8"));

            JSONObject playlist = rule.optJSONObject("playlist");
            if (playlist == null) return;

            // 可能需要单独请求播放列表
            if (playlist.has("url")) {
                JSONObject detailRule = rule.optJSONObject("detail");
                String detailUrl = (detailRule == null) ? "" : detailRule.optString("url", "");
                String playListUrl = playlist.getString("url");
                if (!detailUrl.equals(playListUrl)) {
                    String url = playListUrl.replace("{vid}", vinfo.getString("vod_id"));
                    body = fetchUrl(url, playlist.optJSONObject("header"));
                }
            }

            String content = RuleUtils.getRegion(body, playlist);

            // 播放二次截取（借鉴 线路二次截取 line_second_cut 的处理方式）
            String playTwice = getRuleVal("play_twice");
            if (!playTwice.isEmpty()) {
                content = applySecondCut(content, applyOrSelector(playTwice));
            }

            // 获取播放URL列表
            List<String> vodPlayUrl = obtainPlayUrlList(content, playlist, vinfo);
            List<String> vodPlayFrom = findVodPlayFrom(content, vodPlayUrl == null ? 0 : vodPlayUrl.size());

            // 选集链接加前缀/后缀：值可含 && 规则（对详情页源码截取）与 PG_URL 占位（=当前详情页地址）
            String epiPrefix = resolveEpiUrlVal(getRuleVal("epiurl_prefix"), body, currentDetailUrl(vinfo));
            String epiSuffix = resolveEpiUrlVal(getRuleVal("epiurl_suffix"), body, currentDetailUrl(vinfo));
            if (!epiPrefix.isEmpty() || !epiSuffix.isEmpty()) {
                vodPlayUrl = applyEpiUrlAdjust(vodPlayUrl, epiPrefix, epiSuffix);
            }

            // 空播放兜底：解析不出任何选集时补一条占位线路，避免详情页无播放入口
            boolean noEpisodes = vodPlayUrl == null || vodPlayUrl.isEmpty()
                    || (vodPlayUrl.size() == 1 && vodPlayUrl.get(0).trim().isEmpty());
            String emptyPlayUrl = getRuleVal("empty_play_url");
            if (noEpisodes && !emptyPlayUrl.isEmpty()) {
                String lineName = getRuleVal("empty_play_from");
                if (lineName.isEmpty()) lineName = "空播放";
                vodPlayUrl = new ArrayList<>(1);
                vodPlayUrl.add(emptyPlayUrl.contains("$") ? emptyPlayUrl : "第1集$" + emptyPlayUrl);
                vodPlayFrom = new ArrayList<>(1);
                vodPlayFrom.add(lineName);
                SpiderDebug.log("空播放兜底：已补占位线路 " + lineName);
            }

            // 排序线路
            reorderPlaySources(playlist, vodPlayUrl, vodPlayFrom);

            // 线路合并：将多线路剧集合并为单一线路（线路合并=1）
            if (mergeLines && vodPlayUrl.size() > 1) {
                String firstName = !vodPlayFrom.isEmpty() ? vodPlayFrom.get(0) : "线路";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < vodPlayUrl.size(); i++) {
                    if (i > 0) sb.append("$");
                    sb.append(vodPlayUrl.get(i));
                }
                vodPlayUrl.clear();
                vodPlayUrl.add(sb.toString());
                vodPlayFrom.clear();
                vodPlayFrom.add(firstName);
                SpiderDebug.log("线路合并：已将 " + (sb.toString().split("\\$").length) + " 条线路合并为单一线路");
            }

            // 写入结果
            vod.put("vod_play_url", TextUtils.join("$$$", vodPlayUrl));
            vod.put("vod_play_from", TextUtils.join("$$$", vodPlayFrom));
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 获取播放URL列表
     */
    private List<String> obtainPlayUrlList(String content, JSONObject playlist, JSONObject vinfo) throws JSONException {
        List<String> vodPlayUrl = null;
        if (!playlist.has("vod_play_url")) {
            JSONArray guessedRule = guessRuleVodPlayUrl(content, vinfo.getString("vod_id"));
            if (guessedRule != null) {
                playlist.put("vod_play_url", guessedRule);
            }
        }
        vodPlayUrl = findVodPlayUrl(content);

        // 兜底：当 explicit play 规则 + guessRuleVodPlayUrl 均返回空时，尝试从面包屑区域取值。
        // 场景：单集页站点（详情即自身）用 guessRuleVodPlayUrl 取到相对 href 后，因 origin
        // 与 vod_id 绝对路径不匹配被拒绝；此时改用面包屑锚点法直接取 href。
        if (vodPlayUrl == null || vodPlayUrl.isEmpty()) {
            String breadcrumbLink = tryExtractFromBreadcrumb(content);
            if (breadcrumbLink != null) {
                SpiderDebug.log("面包屑兜底：" + breadcrumbLink);
                vodPlayUrl = new ArrayList<>(1);
                vodPlayUrl.add(breadcrumbLink);
            }
        }
        return vodPlayUrl;
    }

    /** 当前详情页地址（选集链接前缀/后缀的 PG_URL 占位用） */
    private String currentDetailUrl(JSONObject vinfo) {
        try {
            return buildDetailUrl(rule.getJSONObject("detail"), vinfo.optString("vod_id", ""));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 选集链接前缀/后缀取值：普通字符串直接用；含 "&&" 时按规则从详情页源码截取；
     * 含 PG_URL 占位时替换为当前详情页地址（对齐 XYQHiker 同名键语义）
     */
    private String resolveEpiUrlVal(String cfg, String html, String pageUrl) {
        if (cfg.isEmpty()) return "";
        String val = applyPostProcessors(applyOrSelector(cfg));
        if (val.contains("&&")) {
            val = extractField(html, val);
        }
        if (val.contains("PG_URL")) {
            val = val.replace("PG_URL", pageUrl).replaceAll("'", "");
        }
        return val.trim();
    }

    /**
     * 对每条线路的选集链接套用前缀/后缀。
     * 条目格式为 标题$链接（# 分隔多集）；配置了前缀时不再自动补主页地址，相对路径由规则作者接管
     */
    private List<String> applyEpiUrlAdjust(List<String> lines, String prefix, String suffix) {
        if (lines == null || lines.isEmpty()) return lines;
        List<String> adjusted = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                adjusted.add(line);
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (String seg : line.split("#")) {
                int d = seg.indexOf('$');
                String title = d >= 0 ? seg.substring(0, d) : seg;
                String url = d >= 0 ? seg.substring(d + 1) : "";
                if (url.isEmpty()) continue;
                String newUrl = prefix.isEmpty() ? addHttpPrefix(url) : prefix + url;
                if (!suffix.isEmpty()) newUrl = newUrl + suffix;
                if (sb.length() > 0) sb.append('#');
                sb.append(title).append('$').append(newUrl);
            }
            adjusted.add(sb.toString());
        }
        return adjusted;
    }

    /**
     * 尝试从面包屑区域提取剧集链接并格式化为 "标题$链接"。
     * 返回 null 表示未找到。
     */
    private String tryExtractFromBreadcrumb(String content) {
        if (content == null) return null;
        // 取 vod_id：可能是 Base64 包裹的完整 URL，也可能是裸相对路径
        // 这里取原始 HTML 里最近的 /shipin/xxx.html 作为目标 href
        String targetHref = extractRelativeVideoHref(content);
        if (targetHref == null) return null;

        // 先尝试在面包屑区域精准定位
        String region = extractBreadcrumbRegion(content);
        if (region != null && !region.isEmpty()) {
            String link = extractHrefFromRegion(region, targetHref);
            if (link != null) {
                return makeTitleDollarLink(region, link);
            }
        }
        // 兜底：在全页扫描（容错慢速）
        return makeTitleDollarLink(content, targetHref);
    }

    /**
     * 从内容中提取相对视频 href（如 /shipin/15557.html）。
     * 优先级：面包屑区域 > 全页第一个匹配。
     */
    private String extractRelativeVideoHref(String content) {
        // 优先从面包屑区域找
        String region = extractBreadcrumbRegion(content);
        if (region != null) {
            String m = findFirstRelativeShipinHref(region);
            if (m != null) return m;
        }
        // 回退：全页扫描
        return findFirstRelativeShipinHref(content);
    }

    /**
     * 提取面包屑区域字符串，找不到返回 null。
     * 按引擎习惯使用 RuleUtils.findSubString 做字面量截取。
     */
    private static final String[] BREADCRUMB_PATTERNS = {
            "<div class=\"pc\">&&</div>",
            "<div class=\"pc crumbs\">&&</div>",
            "<div class=\"crumbs\">&&</div>",
            "<ul class=\"nav-bread\">&&</ul>",
            "<ul class=\"nav_bread\">&&</ul>",
    };

    private String extractBreadcrumbRegion(String content) {
        for (String pattern : BREADCRUMB_PATTERNS) {
            // BREADCRUMB_PATTERNS 格式为 "前缀&&后缀"，直接喂给 findSubString
            JSONArray keys = stringCutToLookback(pattern);
            if (keys != null) {
                String region = RuleUtils.findSubString(content, 0, keys);
                if (!region.isEmpty()) return region;
            }
        }
        // 容错：class="bread" 元素（属性顺序可能打乱，无法用纯字面量规则表达）
        int bi = content.indexOf("class=\"bread\"");
        if (bi >= 0) {
            int li = content.lastIndexOf('<', bi);
            if (li >= 0) {
                int ri = content.indexOf('>', bi);
                if (ri > li) {
                    int end = content.indexOf("</", ri);
                    if (end > ri) {
                        return content.substring(li, end + 2);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 在指定区域内找第一个相对 /shipin/xxx.html 或 /play/xxx.html 形式的 href。
     */
    private String findFirstRelativeShipinHref(String text) {
        if (text == null) return null;
        // 优先匹配 /shipin/ 格式
        String prefix = "href=\"/shipin/";
        int idx = text.indexOf(prefix);
        if (idx < 0) {
            // 兜底：匹配 /play/ 格式（DJ 耶耶网等音乐站点）
            prefix = "href=\"/play/";
            idx = text.indexOf(prefix);
        }
        if (idx < 0) return null;
        int hrefStart = idx + prefix.length();
        int hrefEnd = text.indexOf('"', hrefStart);
        return hrefEnd > hrefStart ? text.substring(hrefStart, hrefEnd) : null;
    }

    /**
     * 在区域内找匹配 targetHref 的 &lt;a&gt; 节点，提取它的 href 与标题。
     */
    private String extractHrefFromRegion(String region, String targetHref) {
        if (region == null || targetHref == null) return null;
        int ai = region.indexOf(targetHref);
        if (ai < 0) return null;
        int li = Math.max(0, region.lastIndexOf('<', ai));
        int ri = region.indexOf('>', ai);
        if (li < 0 || ri < 0 || ri <= li) return null;
        String anchor = region.substring(li, ri + 1);
        int hi = anchor.indexOf("href=\"");
        if (hi < 0) return null;
        int hs = hi + 6;
        int he = anchor.indexOf('"', hs);
        if (he <= hs) return null;
        return anchor.substring(hs, he);
    }

    /**
     * 格式化结果为 "标题$链接"。
     */
    private String makeTitleDollarLink(String region, String href) {
        if (region == null || href == null) return null;
        int li = region.lastIndexOf('<', region.indexOf(href));
        if (li < 0) li = 0;
        int ri = region.indexOf('>', li);
        if (ri <= li) return null;
        String anchor = region.substring(li, ri + 1);
        // 标题 = anchor 内 &gt; 之后、下一个 &lt; 之前的文本
        int ti = anchor.indexOf('>', li) + 1;
        int te = anchor.indexOf('<', ti);
        String title = (te > ti) ? anchor.substring(ti, te).trim() : "";
        if (title.isEmpty()) title = "第1集";
        // 补全为绝对 URL
        String absoluteUrl = addHttpPrefix(href);
        return title + "$" + absoluteUrl;
    }

    /**
     * 重排播放源顺序
     */
    private void reorderPlaySources(JSONObject playlist, List<String> vodPlayUrl,
                                      List<String> vodPlayFrom) throws JSONException {
        if (!playlist.has("vod_play_from") || vodPlayUrl == null || vodPlayUrl.isEmpty()) return;

        String joinedFrom = TextUtils.join("$$$", vodPlayFrom);
        String defaultFrom = TextUtils.join("$$$", makeVodPlayFrom(vodPlayUrl.size()));
        if (joinedFrom.equals(defaultFrom)) return;

        List<String> urls = new ArrayList<>();
        List<String> froms = new ArrayList<>();

        JSONArray rulePlayFrom = playlist.getJSONArray("vod_play_from");
        for (int i = 0; i < rulePlayFrom.length(); ++i) {
            Object entry = rulePlayFrom.get(i);
            String alias = "";
            if (entry instanceof String) {
                alias = (String) entry;
            } else if (entry instanceof JSONArray) {
                JSONArray item = (JSONArray) entry;
                alias = item.getString(0);
                if (item.length() > 1) alias = item.getString(1);
            }

            for (int j = 0; j < vodPlayFrom.size(); ++j) {
                if (vodPlayFrom.get(j).equals(alias)) {
                    urls.add(vodPlayUrl.get(j));
                    froms.add(vodPlayFrom.get(j));
                }
            }
        }

        if (!urls.isEmpty()) {
            vodPlayUrl.clear();
            vodPlayUrl.addAll(urls);
            vodPlayFrom.clear();
            vodPlayFrom.addAll(froms);
        }
    }

    // ==================== 播放器接口 ====================

    /**
     * 从播放页解析播放URL
     */
    protected String parsePlayUrl(String flag, String url, String html, List<String> list) {
        try {
            JSONObject play = rule.optJSONObject("play");
            if (play == null) return "";

            String body = RuleUtils.getRegion(html, play);
            int startPos = 0;

            JSONArray lookback = RuleUtils.getLookbackArray(play);
            if (lookback != null) {
                int pos = body.indexOf(lookback.getString(0), 0);
                if (pos != -1) {
                    List<Integer> arr = HtmlNodeHelper.findUpNodes(body, pos - 1, lookback.getInt(4));
                    if (!arr.isEmpty()) {
                        startPos = arr.get(arr.size() - 1);
                    } else {
                        startPos = pos;
                    }
                }
            }

            String vodUrl = RuleUtils.findSubString(body, startPos, play.optJSONArray("vod_url"));
            vodUrl = vodUrl.replace("\\/", "/");
            // hexEscapeDecode 清理敏感词（\\uXXXX Unicode 转义还原 + 多余换行/反斜杠清理）
            vodUrl = hexEscapeDecode(vodUrl);
            // player JSON 的 url 常经 encrypt=1 百分号编码 / =2 Base64 处理，按页内 encrypt 字段还原真实直链
            vodUrl = tryDecryptParsedUrl(body, vodUrl);
            if (vodUrl.isEmpty() || !isVideoFormat(vodUrl)) return "";

            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", vodUrl);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String url, List<String> vipFlags) throws Exception {
        try {
            fetchRule();
            initEnhancedConfig();

            // 直接播放模式
            String forcePlayResult = tryForcePlay(url);
            if (forcePlayResult != null) return appendDanmuParam(forcePlayResult);

            // 统一抓取一次播放页（带播放请求头），供脚本块解析/直链解析/跳转链接三条链路共用，
            // 旧实现各链路各自 fetchUrl，一次选集点击最多重复请求同一页面 3 次
            JSONObject play = rule.optJSONObject("play");
            String html = fetchUrl(url, play == null ? null : play.optJSONObject("header"));

            // MacPlayer 模式
            String macPlayerResult = tryMacPlayer(html);
            if (macPlayerResult != null) return appendDanmuParam(macPlayerResult);

            // 尝试解析直链
            String directResult = parsePlayUrl(flag, url, html, vipFlags);
            if (!directResult.isEmpty()) return appendDanmuParam(directResult);

            // 跳转播放链接
            String jumpResult = tryJumpUrl(url, html);
            if (jumpResult != null) return appendDanmuParam(jumpResult);

            // 返回嗅探
            return buildSniffResult(url);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 为播放结果注入弹幕参数（Minor18 接线）：
     * 规则配置 danmu_url 时，向播放 JSON 注入 proxy://do=XBPQ&danmu_url=... 参数，
     * 使 proxy() 的 loadDanmu 分支可达（此前该分支无任何生产方，属不可达代码）。
     */
    private String appendDanmuParam(String playerResult) {
        if (playerResult == null || playerResult.isEmpty()) return playerResult;
        try {
            String danmuUrl = getRuleVal("danmuUrl");
            if (danmuUrl.isEmpty()) return playerResult;
            JSONObject result = new JSONObject(playerResult);
            result.put("danmaku", "proxy://do=XBPQ&danmu_url="
                    + URLEncoder.encode(danmuUrl, "UTF-8"));
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log("appendDanmuParam error: " + e.getMessage());
            return playerResult;
        }
    }

    /**
     * 尝试直接播放
     */
    private String tryForcePlay(String url) throws JSONException {
        String forcePlay = rule.optString("force_play", "0");
        if (!"1".equals(forcePlay) && !"2".equals(forcePlay)) return null;

        JSONObject result = new JSONObject();
        String webUrl = rule.optString("play_prefix", "") + url + rule.optString("play_suffix", "");

        // 播放请求头
        applyPlayHeader(result, webUrl);

        // XYQBiu 同款约定：链接含 #isVideo=true# 标记时强制按视频直连处理，
        // 磁力/网盘直链等非视频格式依赖它绕过嗅探；先判后剥，剥离标记后再下发
        boolean forceVideo = webUrl.contains("#isVideo=true#");
        webUrl = webUrl.replaceAll("#isVideo=true#", "");

        // 判断类型
        if (Util.isVideoFormat(webUrl) || forceVideo) {
            result.put("parse", 0);
            result.put("playUrl", "");
        } else if (Util.isThunder(webUrl)) {
            // 磁力/迅雷/ed2k 直链交播放器处理（资源下载站规则），嗅探必然失败不能走 parse:1
            result.put("parse", 0);
            result.put("playUrl", "");
        } else if (Util.isVip(webUrl)) {
            result.put("parse", 1);
            result.put("jx", "1");
            result.put("url", webUrl);
            return result.toString();
        } else {
            result.put("parse", 1);
            result.put("playUrl", "");
        }
        result.put("url", webUrl);
        return result.toString();
    }

    /**
     * 应用播放请求头
     */
    private void applyPlayHeader(JSONObject result, String webUrl) throws JSONException {
        String playHeader = rule.optString("play_header", "");
        if (playHeader.isEmpty()) return;

        if (playHeader.startsWith("{")) {
            result.put("header", playHeader);
        } else {
            JSONObject hdr = new JSONObject();
            for (String user : playHeader.split("#")) {
                String[] head = user.split("\\$");
                if (head.length >= 2) hdr.put(head[0], " " + head[1]);
            }
            result.put("header", hdr.toString());
        }
    }

    /**
     * 尝试 MacPlayer 解析
     */
    private String tryMacPlayer(String html) throws Exception {
        // "1"/"2" 均启用（说明文档曾写 "2"=正则解析模式，与代码不一致，此处统一）
        String mode = rule.optString("Anal_MacPlayer", "0");
        if (!"1".equals(mode) && !"2".equals(mode)) return null;

        Pattern scriptPattern = P_PLAYER_OBJ;
        Matcher scriptMatcher = scriptPattern.matcher(html);
        if (!scriptMatcher.find()) return null;

        JSONObject player = new JSONObject(scriptMatcher.group(1));
        String videoUrl = player.getString("url");

        // 解密处理
        if (player.has("encrypt")) {
            videoUrl = decryptPlayerUrl(videoUrl, player.getInt("encrypt"));
        }

        return buildPlayerResult(videoUrl);
    }

    /**
     * 解密播放URL
     */
    private String decryptPlayerUrl(String url, int encrypt) throws Exception {
        if (encrypt == 1) {
            return java.net.URLDecoder.decode(url, "UTF-8");
        } else if (encrypt == 2) {
            String decoded = new String(Base64.decode(url, Base64.DEFAULT), "UTF-8");
            return java.net.URLDecoder.decode(decoded, "UTF-8");
        }
        return url;
    }

    /**
     * 构建播放器结果
     */
    private String buildPlayerResult(String videoUrl) throws JSONException {
        if (Util.isVip(videoUrl)) {
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("jx", "1");
            result.put("url", videoUrl);
            return result.toString();
        } else if (Util.isVideoFormat(videoUrl)) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", videoUrl);
            return result.toString();
        }
        return null;
    }

    /**
     * 尝试跳转播放链接
     */
    private String tryJumpUrl(String webUrl, String html) throws Exception {
        String jumpUrl = rule.optString("jump_url", "");
        if (jumpUrl.isEmpty()) return null;

        jumpUrl = applyPostProcessors(jumpUrl);
        String[] parts = jumpUrl.split("&&", 2);
        String startFlag = parts[0];
        String endFlag = parts.length > 1 ? parts[1] : "";

        String parsedUrl = extractJumpUrl(html, startFlag, endFlag);
        if (parsedUrl.isEmpty()) return null;

        // 尝试解密
        parsedUrl = tryDecryptParsedUrl(html, parsedUrl);
        parsedUrl = parsedUrl.replace("\\/", "/");

        if (parsedUrl.isEmpty()) return null;

        if (Util.isVideoFormat(parsedUrl) || isVideoFormat(parsedUrl)) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", parsedUrl);
            return result.toString();
        }
        if (Util.isVip(parsedUrl)) {
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("jx", "1");
            result.put("url", parsedUrl);
            return result.toString();
        }
        return null;
    }

    /**
     * 提取跳转URL
     */
    private String extractJumpUrl(String html, String startFlag, String endFlag) {
        if (startFlag.contains("*")) {
            // 优先按平衡花括号截出 player 对象再解析 JSON，可跨过嵌套的 vod_data；
            // 旧 P_PLAYER_URL 的 [^}]*? 遇嵌套对象的 } 即断，取不到 url
            Matcher m = P_PLAYER_OBJ.matcher(html);
            if (m.find()) {
                try {
                    return new JSONObject(m.group(1)).optString("url", "");
                } catch (JSONException e) {
                    SpiderDebug.log(e);
                }
            }
            Matcher um = P_PLAYER_URL.matcher(html);
            if (um.find()) return um.group(1);
        } else {
            List<String> results = subContent(html, startFlag, endFlag);
            if (!results.isEmpty()) return results.get(0);
        }
        return "";
    }

    /**
     * 尝试解密解析出的URL
     */
    private String tryDecryptParsedUrl(String html, String parsedUrl) {
        try {
            Pattern ep = P_ENCRYPT;
            Matcher em = ep.matcher(html);
            if (em.find()) {
                int encrypt = Integer.parseInt(em.group(1));
                return decryptPlayerUrl(parsedUrl, encrypt);
            }
        } catch (Exception e) {
            SpiderDebug.log("解密播放地址失败，返回原始URL: " + e.getMessage());
        }
        return parsedUrl;
    }

    /**
     * 构建嗅探结果
     */
    private String buildSniffResult(String url) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("url", url);
        return result.toString();
    }

    // ==================== 搜索接口 ====================

    /**
     * 递归解析JSON搜索结果
     */
    protected Object parseJsonSearchResult(Object obj) {
        try {
            if (obj == null) return null;
            JSONObject search = rule.optJSONObject("search");
            if (search == null) return null;

            String keyVodId = search.getString("vod_id");
            String keyVodName = search.getString("vod_name");

            if (obj instanceof JSONObject) {
                JSONObject object = (JSONObject) obj;
                if (object.has(keyVodId) && object.has(keyVodName)) return object;
                for (Iterator<String> iter = object.keys(); iter.hasNext();) {
                    Object r = parseJsonSearchResult(object.get(iter.next()));
                    if (r != null) return r;
                }
            } else if (obj instanceof JSONArray) {
                JSONArray array = (JSONArray) obj;
                for (int i = 0; i < array.length(); ++i) {
                    if (parseJsonSearchResult(array.get(i)) != null) return array;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    /**
     * 解析搜索结果
     */
    protected String parseSearchResult(String body, String page) {
        try {
            JSONObject obj = new JSONObject(body);
            Object info = parseJsonSearchResult(obj);
            if (info == null) return "";

            JSONArray arr;
            if (info instanceof JSONObject) {
                arr = new JSONArray();
                arr.put((JSONObject) info);
            } else {
                arr = (JSONArray) info;
            }

            return buildSearchResults(arr, page);
        } catch (Exception e) {
            // ignored
        }
        return "";
    }

    /**
     * 构建搜索结果（统一走标准列表信封，使 searchContent 与分类列表输出结构一致）
     */
    private String buildSearchResults(JSONArray arr, String page) throws JSONException {
        JSONObject search = rule.optJSONObject("search");
        if (search == null) return "";
        JSONArray videos = new JSONArray();

        for (int i = 0; i < arr.length(); ++i) {
            JSONObject o = arr.getJSONObject(i);
            if (!search.has("vod_id") || !o.has(search.getString("vod_id"))) continue;

            JSONObject v = new JSONObject();
            v.put("vod_id", o.get(search.getString("vod_id")).toString());
            v.put("vod_name", search.has("vod_name") && o.has(search.getString("vod_name"))
                    ? o.get(search.getString("vod_name")).toString() : "未知");
            v.put("vod_pic", search.has("vod_pic") && o.has(search.getString("vod_pic"))
                    ? o.get(search.getString("vod_pic")).toString() : "");
            v.put("vod_remarks", search.has("vod_remarks") && o.has(search.getString("vod_remarks"))
                    ? o.get(search.getString("vod_remarks")).toString() : "");
            v.put("vod_id", encodeVodId(v));
            videos.put(v);
        }

        return wrapList(videos, page).toString();
    }

    @Override
    public String searchContent(String keyword, boolean quick) {
        // 二参版委托三参版（与 DJhub/Czys 等爬虫一致），默认第 1 页
        return searchContent(keyword, quick, "1");
    }

    @Override
    public String searchContent(String keyword, boolean quick, String pg) {
        try {
            fetchRule();
            JSONObject search = rule.optJSONObject("search");
            String searchUrlFlat = rule.optString("search_url", "");

            // 页码归一化：非法/空值回退为 1
            String page = (pg == null || pg.trim().isEmpty()) ? "1" : pg.trim();
            try {
                if (Integer.parseInt(page) < 1) page = "1";
            } catch (NumberFormatException e) {
                page = "1";
            }

            // 懒加载兜底：无搜索配置时先尝试猜测/生成默认配置（延迟到消费点，避免拖慢 init）
            if ((search == null || !search.has("url")) && searchUrlFlat.isEmpty()) {
                guessSearchUrlIfNeeded();
                if (rule.has("search") && !getRuleVal("search_url").isEmpty()) applyFlatSearchFields();
                initializeSearchConfig();
                search = rule.optJSONObject("search");
                searchUrlFlat = rule.optString("search_url", "");
                if ((search == null || !search.has("url")) && searchUrlFlat.isEmpty()) return "";
            }

            // 获取搜索内容
            SearchFetchResult fetchResult = fetchSearchContent(keyword, search, searchUrlFlat, page);
            if (fetchResult == null) return "";

            String content = fetchResult.content;
            String url = fetchResult.url;

            // ========== JSON 接口模式（标准模板「异步 JSON-API」） ==========
            String searchJsonPath = getRuleVal("searchjsonlist");
            if (!searchJsonPath.isEmpty()) {
                JSONArray jsonVideos = extractVideosByJson(content, searchJsonPath,
                        getRuleVal("searchjsonid"), getRuleVal("searchjsonname"),
                        getRuleVal("searchjsonpic"), getRuleVal("searchjsonnote"));
                if (jsonVideos.length() > 0) return wrapList(jsonVideos, page).toString();
            }

            // 区域截取
            content = RuleUtils.getRegion(content, search);

            // 二次截取
            String searchTwice = getRuleVal("search_twice");
            if (!searchTwice.isEmpty()) {
                content = applySecondCut(content, applyOrSelector(searchTwice));
            }

            // 模式0：优先 JSON 解析；模式1：优先网页截取（互为兜底）。
            // 旧实现模式1 直接 return 原始 HTML，导致 mode-1 规则的搜索整体失效
            boolean htmlFirst = "1".equals(getRuleVal("search_mode", "0"));
            if (!htmlFirst) {
                String jsonResult = parseSearchResult(content, page);
                if (jsonResult != null && !jsonResult.isEmpty()) return jsonResult;
            }

            // 继承 list 的 vod_id 规则
            inheritVodIdRuleIfNeeded(search);

            // ========== CSS 选择器模式（优先） ==========
            if (isCssModeEnabled(search)) {
                SpiderDebug.log("搜索结果使用 CSS/Jsoup 模式提取");
                JSONArray cssVideos = extractSearchResultsByCss(content, search);
                if (cssVideos.length() > 0) return wrapList(cssVideos, page).toString();
                SpiderDebug.log("CSS 提取无结果，回退到传统模式");
            }

            // HTML 解析
            String htmlResult = parseHtmlSearchResults(content, search, url, page);

            // 模式1 网页解析无结果时，按文档语义回退 JSON 解析
            if (htmlFirst) {
                try {
                    JSONArray arr = new JSONObject(htmlResult).optJSONArray("list");
                    if (arr == null || arr.length() == 0) {
                        String jsonResult = parseSearchResult(content, page);
                        if (jsonResult != null && !jsonResult.isEmpty()) return jsonResult;
                    }
                } catch (Exception ignored) {
                }
            }
            return htmlResult;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 搜索获取结果
     */
    private static class SearchFetchResult {
        String content;
        String url;
    }

    /**
     * 获取搜索内容
     */
    private SearchFetchResult fetchSearchContent(String keyword, JSONObject search, String searchUrlFlat, String page) throws Exception {
        SearchFetchResult result = new SearchFetchResult();

        if (!searchUrlFlat.isEmpty()) {
            // 扁平搜索URL优先（buildSearchUrl 内部已应用 search_suffix）
            result.url = buildSearchUrl(searchUrlFlat, keyword, page);
            JSONObject headers = parseSearchHeaders(getRuleVal("search_header"));
            result.content = unwrapJsonString(fetchUrl(result.url, headers));
        } else if (search != null && search.has("url")) {
            result.url = applySearchSuffix(addHttpPrefix(search.getString("url")
                    .replace("{wd}", keyword).replace("{pg}", shiftSearchPage(page))));
            JSONObject headers = parseSearchHeaders(getRuleVal("search_header"));
            if (headers == null && search != null) headers = search.optJSONObject("header");
            result.content = unwrapJsonString(fetchUrl(result.url, headers));
        }
        return result;
    }

    /**
     * 响应体若是 JSON 字符串字面量包裹的 HTML（FastAdmin 等框架对 AJAX 请求返回
     * "\"&lt;html&gt;...\"" 形式，内部含 \" 与 \/ 转义），剥掉外壳还原纯 HTML，
     * 避免 && 截取规则撞上转义字符全部失效
     */
    protected String unwrapJsonString(String body) {
        if (body == null) return body;
        String trimmed = body.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '"' || trimmed.charAt(trimmed.length() - 1) != '"') {
            return body;
        }
        try {
            Object value = new org.json.JSONTokener(trimmed).nextValue();
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Exception e) {
            SpiderDebug.log("unwrapJsonString 解析失败，按原始响应处理");
        }
        return body;
    }

    /**
     * 构建搜索URL
     */
    private String buildSearchUrl(String searchUrlFlat, String keyword, String page) throws Exception {
        return addHttpPrefix(applySearchSuffix(searchUrlFlat
                .replace("{wd}", URLEncoder.encode(keyword, "UTF-8"))
                .replace("{pg}", shiftSearchPage(page))));
    }

    /**
     * 应用搜索后缀（search_suffix / 搜索后缀 / 搜索链接后缀）
     * <p>
     * 旧实现为 void 空方法，后缀被静默丢弃；此处改为返回拼接后的 URL，使搜索后缀真正生效。
     */
    private String applySearchSuffix(String url) {
        String suffix = getRuleVal("search_suffix");
        if (!suffix.isEmpty() && !url.isEmpty()) {
            return url + suffix;
        }
        return url;
    }

    /**
     * 解析搜索请求头
     */
    private JSONObject parseSearchHeaders(String searchHeader) {
        if (searchHeader.isEmpty()) return null;
        return parseHeader(searchHeader);
    }

    /**
     * 继承 vod_id 规则
     */
    private void inheritVodIdRuleIfNeeded(JSONObject search) throws JSONException {
        // 仅配置「搜索url」而未生成 search 子对象时（如基础初始化被中断）search 为 null，
        // 旧实现直接 search.has(...) 抛 NPE，整个搜索链路崩掉
        if (search == null) {
            SpiderDebug.log("搜索: 缺少 search 规则对象，跳过 vod_id 规则继承");
            return;
        }
        if (!search.has("vod_id")) {
            JSONObject list = rule.optJSONObject("list");
            if (list != null && list.has("vod_id")) {
                search.put("vod_id", list.getJSONArray("vod_id"));
            } else if (list != null) {
                guessVodIdIfNeeded(list);
                if (list.has("vod_id")) {
                    search.put("vod_id", list.getJSONArray("vod_id"));
                }
            }
        }
        // suggest 模式覆盖
        if (search.has("vod_id") && "id".equals(search.optString("vod_id", ""))) {
            JSONObject list = rule.optJSONObject("list");
            if (list != null && list.has("vod_id")) {
                search.put("vod_id", list.getJSONArray("vod_id"));
            }
        }
    }

    /**
     * 解析HTML搜索结果
     */
    private String parseHtmlSearchResults(String content, JSONObject search, String url, String page) throws JSONException {
        JSONArray videos = new JSONArray();
        Set<String> seenIds = new HashSet<>();
        int pos = 0;

        JSONArray lookback = search.optJSONArray("search");
        if (lookback == null || RuleUtils.getLookbackCount(lookback) <= 0) {
            lookback = RuleUtils.getLookbackArray(search);
        }

        while (lookback != null) {
            int matchPos = content.indexOf(lookback.getString(0), pos);
            if (matchPos == -1) break;

            // 跳过 style/script 内的命中
            if (insideNoParseBlock(content, matchPos)) {
                pos = matchPos + 1;
                continue;
            }

            // 调整层级并提取
            SearchNodeResult nodeResult = extractSearchNode(content, matchPos, lookback, search, url);
            if (nodeResult == null) break;

            // 防护：提取失败时 endPos 不前进，强制前移避免死循环
            if (nodeResult.endPos <= matchPos) {
                pos = matchPos + 1;
            } else {
                pos = nodeResult.endPos;
            }
            String vodId = nodeResult.vodId;

            if (!seenIds.contains(vodId)) {
                // 过滤检查
                if (shouldFilterSearchResult(nodeResult.node, vodId, search)) continue;

                seenIds.add(vodId);
                JSONObject v = buildSearchVideo(nodeResult.node, vodId, search, url);
                videos.put(v);
            }
        }

        // 倒序
        if (reverseOrder) videos = reverseArray(videos);

        return wrapList(videos, page).toString();
    }

    /**
     * 搜索节点提取结果
     */
    private static class SearchNodeResult {
        String node;
        String vodId;
        int endPos;
    }

    /**
     * 提取搜索节点
     */
    private SearchNodeResult extractSearchNode(String content, int pos, JSONArray lookback,
                                                  JSONObject search, String url) throws JSONException {
        List<Integer> urlNodes = null;
        List<Integer> arr = null;
        int blockPos = 0;
        String node = "";
        int lookup = -1;
        int iterations = 0;
        final int MAX_ITERATIONS = 20;

        do {
            // 防护：限制最大迭代次数，避免回看层级震荡导致死循环
            if (++iterations > MAX_ITERATIONS) {
                SpiderDebug.log(String.format("extractSearchNode 达到最大迭代次数(%d)，当前层级=%d，强制退出", MAX_ITERATIONS, lookback.getInt(4)));
                break;
            }

            arr = HtmlNodeHelper.findUpNodes(content, pos - 1, lookback.getInt(4));
            if (urlNodes == null) {
                urlNodes = arr;
                blockPos = arr.get(arr.size() - 1);
            } else {
                blockPos = RuleUtils.findBlockPos(urlNodes, arr);
            }
            node = HtmlNodeHelper.nodeString(content, blockPos);

            // 层级修正（与分类列表相同逻辑）
            lookup = checkAndAdjustLevelForSearch(node, lookup, lookback, urlNodes, blockPos);
            if (lookup < 0) {
                urlNodes = null;
                blockPos = 0;
                node = "";
            }
        } while (lookup < 0);

        SearchNodeResult result = new SearchNodeResult();
        result.node = node;
        result.vodId = RuleUtils.findSubString(node, 0, search.optJSONArray("vod_id"));
        result.endPos = blockPos + node.length();
        return result;
    }

    /**
     * 检查并调整搜索结果的回看层级
     */
    private int checkAndAdjustLevelForSearch(String node, int currentLookup, JSONArray lookback,
                                               List<Integer> urlNodes, int blockPos) throws JSONException {
        // 与分类列表使用相同的层级修正逻辑
        return checkAndAdjustLevel(node, currentLookup, lookback, urlNodes, blockPos);
    }

    /**
     * 检查搜索结果是否应过滤
     */
    private boolean shouldFilterSearchResult(String node, String vodId, JSONObject search) {
        String filterWord = getRuleVal("filter_word");
        if (filterWord.isEmpty()) return false;

        String searchName = RuleUtils.findSubString(node, 0, search.optJSONArray("vod_name"));
        for (String word : filterWord.split("[,，]")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty() && (vodId.contains(trimmed) || searchName.contains(trimmed))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建搜索视频对象
     */
    private JSONObject buildSearchVideo(String node, String vodId, JSONObject search, String url) throws JSONException {
        JSONObject v = new JSONObject();
        v.put("vod_id", vodId);
        v.put("vod_name", RuleUtils.findSubString(node, 0, search.optJSONArray("vod_name")));

        String vodPic = addHttpPrefix(RuleUtils.findSubString(node, 0, search.optJSONArray("vod_pic")));
        if (vodPic.isEmpty()) vodPic = guessValueVodPic(node, 0);
        if ("1".equals(getRuleVal("PicNeedProxy")) && !vodPic.isEmpty()) {
            vodPic = fixCover(vodPic, url);
        }
        v.put("vod_pic", vodPic);

        v.put("vod_remarks", RuleUtils.findSubString(node, 0, search.optJSONArray("vod_remarks")));

        // 补充缺失字段
        if (v.getString("vod_name").isEmpty()) {
            v.put("vod_name", guessValueVodName(node, 0));
        }
        if (v.getString("vod_pic").isEmpty()) {
            v.put("vod_pic", guessValueVodPic(node, 0));
        }
        if (v.getString("vod_remarks").isEmpty()) {
            v.put("vod_remarks", guessValueVodRemarks(node, 0, v.getString("vod_name")));
        }

        v.put("vod_id", encodeVodId(v));
        return v;
    }

    // ==================== 网络请求工具 ====================

    /**
     * 统一的网络请求方法
     * <p>
     * 增强点（借鉴第18次升级版）：
     * <ul>
     *   <li>请求前解析 {{变量}} 模板（如主页url、Token 等动态值）</li>
     *   <li>合并调用方传入的独立请求头（list/detail/search/playlist header）</li>
     *   <li>追踪响应码 lastResponseCode，按 errorCodes/failCodes/successCodes 判定失败</li>
     *   <li>失败时设置 requestFailed/failMessage 并通过 Init.show 提示用户</li>
     * </ul>
     */
    protected String fetchUrl(String url, JSONObject headers) {
        try {
            // 解析 {{变量}} 模板（{{主页url}} 等动态值）
            url = resolveVariables(url);
            // SSRF 防护：拦截内网/危险协议回源；allow_internal=1 时放行（调试用）
            if (isInternalUrl(url) && !"1".equals(getRuleVal("allow_internal"))) {
                SpiderDebug.log(safeLog("fetchUrl SSRF blocked: " + url));
                failMessage = "SSRF blocked";
                return "";
            }
            Map<String, String> h = getHeaders(url);
            if (headers != null) h = mergeHeaders(h, headers);

            okhttp3.Response resp = OkHttp.newCall(url, h);
            String html;
            try {
                lastResponseCode = resp.code();
                html = resp.body().string();
            } finally {
                resp.close();
            }

            // 反爬/WAF 统一绕过（宝塔/等待重载盾/滑块/CF，含通用重试；正常页面原样返回）
            html = handleAntiCrawler(url, html, headers);

            // 失败状态追踪：绕过后仍是反爬/错误页时才拦截，避免脏 HTML 流入解析链
            if (isFail(lastResponseCode) && isAntiCrawlerPage(html)) {
                failMessage = "访问失败: " + lastResponseCode;
                if (!requestFailed) Init.show(failMessage);
                requestFailed = true;
                return "";
            }
            requestFailed = false;
            failMessage = "";

            return cleanHtmlResponse(html);
        } catch (Exception e) {
            lastResponseCode = 0;
            failMessage = e.getMessage() == null ? "请求异常" : e.getMessage();
            if (!requestFailed) Init.show(failMessage);
            requestFailed = true;
            SpiderDebug.log(safeLog("fetchUrl error: " + failMessage));
            return "";
        }
    }

    /**
     * 清理HTML响应
     */
    private String cleanHtmlResponse(String html) {
        return P_HTML_COMMENT.matcher(html).replaceAll("")
                .replace("\r\n", "")
                .replace("\n", "");
    }

    /**
     * Unicode转中文
     */
    protected String convertUnicodeToChinese(String str) {
        if (str == null || !str.contains("\\u")) return str;
        try {
            Matcher matcher = P_UNICODE_SEQ.matcher(str);
            while (matcher.find()) {
                String unicodeNum = matcher.group(2);
                char c = (char) Integer.parseInt(unicodeNum, 16);
                str = str.replace(matcher.group(1), String.valueOf(c));
            }
            return str;
        } catch (Exception e) {
            // parseInt 理论上不会失败（正则已限定 [0-9A-Fa-f]{4}），兜底防中断
            return str;
        }
    }

    /**
     * 统一获取页面内容（带Unicode转换）
     */
    protected String fetch(String webUrl) {
        if (isInternalUrl(webUrl)) {
            SpiderDebug.log(safeLog("fetch SSRF blocked: " + webUrl));
            return "";
        }
        String html = OkHttp.string(webUrl, getHeaders(webUrl));
        html = handleAntiCrawler(webUrl, html, null);
        html = convertUnicodeToChinese(html);
        return cleanHtmlResponse(html);
    }

    /**
     * 提取字段
     */
    protected String extractField(String block, String rule) {
        if (rule == null || rule.isEmpty()) return "";
        if (rule.contains("&&")) {
            String[] se = rule.split("&&", 2);
            List<String> r = subContent(block, se[0], se[1]);
            return r.isEmpty() ? "" : cleanHtml(r.get(0));
        }
        return cleanHtml(rule);
    }

    /**
     * 清理HTML标签
     */
    protected static String cleanHtml(String s) {
        if (s == null) return "";
        String r = P_HTML_TAG.matcher(s).replaceAll("");
        r = P_HTML_ENTITY.matcher(r).replaceAll("");
        r = P_RESIDUAL_SYMS.matcher(r).replaceAll("");
        r = P_WHITESPACE.matcher(r).replaceAll(" ");
        return r.trim();
    }

    /**
     * 编码 vod_id：将 JSONObject 序列化为 Base64 字符串
     */
    protected String encodeVodId(JSONObject item) {
        try {
            return Base64.encodeToString(item.toString().getBytes(StandardCharsets.UTF_8), BASE64_FLAG);
        } catch (Exception e) {
            SpiderDebug.log("encodeVodId error: " + e.getMessage());
            return "";
        }
    }

    /**
     * POST请求
     */
    private String fetchPost(String webUrl) {
        try {
            String postUrl = webUrl.split("\\?")[0].replace("？？", "?");
            String body = webUrl.contains("?") ? webUrl.split("\\?")[1].split(";")[0] : "";
            if (body.startsWith("{")) {
                return convertUnicodeToChinese(OkHttp.post(postUrl, body, getHeaders(postUrl)));
            } else {
                LinkedHashMap<String, String> params = new LinkedHashMap<>();
                for (String p : body.split("&")) {
                    int idx = p.indexOf("=");
                    if (idx > 0) params.put(p.substring(0, idx), p.substring(idx + 1));
                }
                return convertUnicodeToChinese(OkHttp.post(postUrl, params, getHeaders(postUrl)));
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * POST请求搜索
     */
    protected String postSearch(String keyword, boolean quick) {
        try {
            JSONObject search = rule.optJSONObject("search");
            if (search == null) return "";

            String url = search.getString("url");
            JSONObject params = search.optJSONObject("post");
            if (params == null) params = search.optJSONObject("postBody");
            if (params == null) return "";

            Map<String, String> payload = new HashMap<>();
            Iterator<String> iter = params.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                String value = params.getString(key).replace("{wd}", keyword);
                payload.put(key, value);
            }

            Map<String, String> headers = getHeaders(url);
            headers.put("content-type", "application/x-www-form-urlencoded");
            return convertUnicodeToChinese(OkHttp.post(url, payload, headers));
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    // ==================== 反爬虫绕过（增强版） ====================
    //
    // 支持的反爬类型：
    // 1. 宝塔(BT)面板 WAF 防护 - btwaf 标记检测与Token提取
    // 2. Cloudflare 5秒盾 / JS Challenge - cf_clearance 检测
    // 3. 滑块验证 - 集成 SliderVerifyUtils 工具类
    // 4. 点选验证 / 图片验证码 - 外部打码服务支持
    // 5. Cookie 自动管理与持久化
    // 6. 请求指纹随机化（UA轮换、Referer伪造）
    // 7. 访问频率控制与重试机制
    // 8. IP代理支持（规则配置 proxy 字段）
    //

    /** 最大反爬重试次数 */
    private static final int MAX_ANTI_CRAWLER_RETRY = 5;

    /** 反爬绕过总预算（毫秒），可由规则 反爬超时/antiCrawlTimeout 配置，[5000,60000] */
    private long antiCrawlDeadline = 0;

    /** 反爬检测间隔（毫秒） */
    private static final long ANTI_CRAWLER_DELAY_MS = 1500;
    /** 「加载中」等待重载盾要求的间隔略大于页面标注的 2 秒 */
    private static final long REFRESH_WAIT_DELAY_MS = 2300;

    /** Cloudflare 检测关键词 */
    private static final List<String> CF_DETECT_KEYWORDS = Arrays.asList(
            "cf-browser-verification", "cf-challenge", "cf_clearance",
            "Just a moment...", "Checking your browser",
            "_cf_chl", "__cf_bm", "challenge-platform"
    );

    /** 宝塔WAF检测关键词 */
    private static final List<String> BT_DETECT_KEYWORDS = Arrays.asList(
            "btwaf", "检测中", "跳转中", "安全检测",
            "yanzheng_huadong", "huadong_", "/cdn-cgi/"
    );

    /** 滑块验证检测关键词 */
    private static final List<String> SLIDER_DETECT_KEYWORDS = Arrays.asList(
            "滑动验证", "滑块验证", "huadong_", "click_captcha",
            "slider-verify", "geetest", "captcha"
    );

    /**
     * 统一反爬入口方法（替代原有 jumpBtwaf）
     * <p>
     * 检测并自动处理以下反爬场景：
     * <ol>
     *   <li>Cloudflare JS Challenge / 5秒盾</li>
     *   <li>宝塔 WAF 防护</li>
     *   <li>滑块/点选验证</li>
     *   <li>其他常见 WAF 拦截</li>
     * </ol>
     *
     * @param webUrl 请求URL
     * @param html    原始HTML响应
     * @return 处理后的HTML内容（可能已通过验证重新获取）
     */
    /** 反爬总预算（毫秒）：规则 反爬超时/antiCrawlTimeout，缺省 20s，范围 [5s,60s] */
    private long antiCrawlBudget() {
        long budget = 0;
        try {
            budget = Long.parseLong(getRuleVal("antiCrawlTimeout", "0").trim());
        } catch (Exception ignored) {
        }
        if (budget <= 0) budget = 20000;
        return Math.max(5000, Math.min(60000, budget));
    }

    protected String handleAntiCrawler(String webUrl, String html) {
        try {
            if (html == null || html.isEmpty()) return html;

            // 快速判断：如果页面正常（无反爬标记），直接返回
            if (!isAntiCrawlerPage(html)) return html;

            antiCrawlDeadline = SystemClock.elapsedRealtime() + antiCrawlBudget();

            SpiderDebug.log(String.format("检测到反爬保护: %s", detectAntiCrawlerType(html)));

            // 1. 尝试 Cloudflare 绕过
            if (isCloudflarePage(html)) {
                html = bypassCloudflare(webUrl, html);
                if (!isAntiCrawlerPage(html)) return html;
            }

            // 2. 尝试宝塔 WAF 绕过
            if (isBaoTaWafPage(html)) {
                html = bypassBaoTaWaf(webUrl, html);
                if (!isAntiCrawlerPage(html)) return html;
            }

            // 3. 尝试滑块验证处理
            if (isSliderVerifyPage(html)) {
                boolean handled = handleSliderVerify(webUrl, html);
                if (handled) {
                    html = fetchUrl(webUrl, rule.optJSONObject("header"));
                    if (!isAntiCrawlerPage(html)) return html;
                }
            }

            // 3.5 「加载中」等待重载盾：收 Cookie + 按页面要求间隔重试
            if (isRefreshWaitPage(html)) {
                html = bypassRefreshWait(webUrl, html, null);
                if (!isAntiCrawlerPage(html)) return html;
            }

            // 4. 通用重试机制
            for (int i = 0; i < MAX_ANTI_CRAWLER_RETRY; i++) {
                if (SystemClock.elapsedRealtime() > antiCrawlDeadline) {
                    SpiderDebug.log("反爬重试超出总预算，提前结束");
                    break;
                }
                Thread.sleep(ANTI_CRAWLER_DELAY_MS);
                html = fetchUrlWithRetry(webUrl);
                if (!isAntiCrawlerPage(html)) break;
                SpiderDebug.log(String.format("反爬重试 %d/%d", i + 1, MAX_ANTI_CRAWLER_RETRY));
            }
        } catch (Exception e) {
            SpiderDebug.log("反爬处理异常: " + e.getMessage());
        }
        return html;
    }

    /**
     * 统一反爬入口方法（带自定义请求头版本）
     */
    protected String handleAntiCrawler(String webUrl, String html, JSONObject customHeaders) {
        try {
            if (html == null || html.isEmpty()) return html;
            if (!isAntiCrawlerPage(html)) return html;

            antiCrawlDeadline = SystemClock.elapsedRealtime() + antiCrawlBudget();

            // 使用自定义头部的版本优先
            if (isCloudflarePage(html)) {
                html = bypassCloudflare(webUrl, html, customHeaders);
            } else if (isBaoTaWafPage(html)) {
                html = bypassBaoTaWaf(webUrl, html, customHeaders);
            } else if (isSliderVerifyPage(html)) {
                handleSliderVerify(webUrl, html);
                html = fetchUrl(webUrl, customHeaders);
            } else if (isRefreshWaitPage(html)) {
                html = bypassRefreshWait(webUrl, html, customHeaders);
            }

            if (isAntiCrawlerPage(html)) {
                html = handleAntiCrawler(webUrl, html); // 回退到默认处理
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return html;
    }

    // ========== 反爬检测方法 ==========

    /**
     * 判断是否为反爬拦截页面
     */
    protected boolean isAntiCrawlerPage(String html) {
        if (html == null || html.isEmpty()) return false;
        return isCloudflarePage(html) || isBaoTaWafPage(html)
                || isSliderVerifyPage(html) || isGenericBlockPage(html)
                || isRefreshWaitPage(html);
    }

    /**
     * 检测「加载中」等待重载盾：首访返回 503 动画页 + Set-Cookie，页面要求
     * setTimeout 重载后放行（雷池式等价行为）。特征 = 加载中文案 + 自动重载脚本
     */
    protected boolean isRefreshWaitPage(String html) {
        if (html == null || html.isEmpty()) return false;
        return html.contains("页面加载中") && html.contains("location.reload");
    }

    /**
     * 「加载中」等待重载盾绕过
     * <p>
     * 策略：收取 503 响应下发的 Cookie（extractAllCookies 写入 rule.header，
     * getHeaders 自动携带），等待页面要求的重载间隔后带 Cookie 重试
     */
    protected String bypassRefreshWait(String webUrl, String html, JSONObject customHeaders) {
        try {
            for (int i = 0; i < MAX_ANTI_CRAWLER_RETRY; i++) {
                if (SystemClock.elapsedRealtime() > antiCrawlDeadline) {
                    SpiderDebug.log("等待重载盾重试超出反爬总预算，提前结束");
                    break;
                }
                Thread.sleep(REFRESH_WAIT_DELAY_MS);
                Map<String, String> headers = getHeaders(webUrl);
                if (customHeaders != null && customHeaders.length() > 0) {
                    headers = mergeHeaders(headers, customHeaders);
                }
                okhttp3.Response resp = OkHttp.newCall(webUrl, headers);
                int code;
                String body;
                try {
                    code = resp.code();
                    extractAllCookies(resp);
                    body = resp.body().string();
                } finally {
                    resp.close();
                }
                lastResponseCode = code;
                html = body;
                SpiderDebug.log(String.format("等待重载盾重试 %d/%d: %d", i + 1, MAX_ANTI_CRAWLER_RETRY, code));
                if (!isRefreshWaitPage(html)) return html;
            }
        } catch (Exception e) {
            SpiderDebug.log("等待重载盾绕过异常: " + e.getMessage());
        }
        return html;
    }

    /**
     * 检测是否为 Cloudflare 保护页面
     */
    protected boolean isCloudflarePage(String html) {
        if (html == null || html.isEmpty()) return false;
        for (String keyword : CF_DETECT_KEYWORDS) {
            if (html.toLowerCase().contains(keyword.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * 检测是否为宝塔 WAF 拦截页面
     */
    protected boolean isBaoTaWafPage(String html) {
        if (html == null || html.isEmpty()) return false;
        for (String keyword : BT_DETECT_KEYWORDS) {
            if (html.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 检测是否为滑块/验证码页面
     */
    protected boolean isSliderVerifyPage(String html) {
        if (html == null || html.isEmpty()) return false;
        // 复用 SliderVerifyUtils 的静态检测方法
        try {
            if (SliderVerifyUtils.isSliderVerifyPage(html)) return true;
        } catch (Exception e) {
            SpiderDebug.log("滑块页面检测异常，回退到关键词匹配: " + e.getMessage());
        }
        for (String keyword : SLIDER_DETECT_KEYWORDS) {
            if (html.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 检测是否为通用拦截页面（访问频率过高、IP被封等）
     */
    protected boolean isGenericBlockPage(String html) {
        if (html == null || html.isEmpty()) return false;
        String lowerHtml = html.toLowerCase();
        // 显式括号明确优先级（&& 本就优先于 ||，此处仅提升可读性，避免误改）
        return lowerHtml.contains("访问频率")
                || lowerHtml.contains("请求过于频繁")
                || (lowerHtml.contains("403 forbidden") && lowerHtml.contains("被拦截"))
                || (lowerHtml.contains("access denied")
                    && (lowerHtml.contains("waf") || lowerHtml.contains("firewall")));
    }

    /**
     * 检测反爬类型（用于日志记录）
     */
    protected String detectAntiCrawlerType(String html) {
        if (isCloudflarePage(html)) return "Cloudflare";
        if (isBaoTaWafPage(html)) return "宝塔WAF";
        if (isSliderVerifyPage(html)) return "滑块验证";
        if (isRefreshWaitPage(html)) return "等待重载盾";
        if (isGenericBlockPage(html)) return "通用拦截";
        return "未知";
    }

    // ========== Cloudflare 绕过 ==========

    /**
     * Cloudflare 5秒盾 / JS Challenge 绕过
     * <p>
     * 策略：
     * <ul>
     *   <li>等待一段时间后重新请求（模拟浏览器等待）</li>
     *   <li>携带已有的 cf_clearance cookie</li>
     *   <li>使用完整的浏览器 UA 和 Accept 头部</li>
     *   <li>尝试解析 challenge 页面中的 JS 验证逻辑</li>
     * </ul>
     *
     * @param webUrl 目标URL
     * @param html   当前HTML（CF挑战页面）
     * @return 通过验证后的HTML
     */
    protected String bypassCloudflare(String webUrl, String html) {
        return bypassCloudflare(webUrl, html, null);
    }

    /**
     * Cloudflare 绕过（带自定义头部）
     */
    protected String bypassCloudflare(String webUrl, String html, JSONObject customHeaders) {
        try {
            SpiderDebug.log("尝试绕过 Cloudflare 保护...");

            // 策略1：添加完整的浏览器伪装头
            Map<String, String> headers = customHeaders != null
                    ? mergeHeaders(getHeaders(webUrl), customHeaders)
                    : getHeaders(webUrl);
            headers = enhanceForCloudflare(headers);

            // 策略2：延迟后重试（模拟人类浏览行为）
            Thread.sleep(2000 + (long)(Math.random() * 2000));

            // 策略3：预访问一次目标页（携带增强浏览器头），
            // OkHttp 未配置 cookieJar（默认 NO_COOKIES），需手动从 Response 提取 set-cookie
            // 写入 rule.header.cookie 后由 getHeaders() 在后续请求自动携带。
            // 注：/cdn-cgi/challenge-platform 为 CF 静态资源路径，GET 它不会签发 cf_clearance，
            // 故不再伪挑战预访问，直接回访目标页收集服务端下发的 Cookie。
            okhttp3.Response cfResp = null;
            try {
                cfResp = OkHttp.newCall(webUrl, headers);
                extractAllCookies(cfResp);
            } catch (Exception cfEx) {
                SpiderDebug.log("Cloudflare 预访问异常: " + cfEx.getMessage());
            } finally {
                if (cfResp != null) cfResp.close();
            }

            // 再等一下
            Thread.sleep(3000);

            // 策略4：重新请求目标页面（cf_clearance 已写入 rule.header.cookie，getHeaders 自动携带）
            html = OkHttp.string(webUrl, headers);

            if (!isCloudflarePage(html)) {
                SpiderDebug.log("Cloudflare 绕过成功");
            }
        } catch (Exception e) {
            SpiderDebug.log("Cloudflare 绕过失败: " + e.getMessage());
        }
        return html;
    }

    /**
     * 增强 HTTP 头部以通过 Cloudflare 检测
     */
    private Map<String, String> enhanceForCloudflare(Map<String, String> headers) {
        // 必须的 CF 相关头部
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Cache-Control", "max-age=0");
        headers.put("Upgrade-Insecure-Requests", "1");
        // Sec-Fetch 系列头部（现代浏览器标准）
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");

        // 确保 UA 是桌面版 Chrome
        String ua = headers.getOrDefault("User-Agent", "");
        if (ua.contains("Mobile") || ua.isEmpty()) {
            headers.put("User-Agent", UA_PC);
        }
        return headers;
    }

    // ========== 宝塔 WAF 绕过 ==========

    /**
     * 宝塔(BT)面板 WAF 防护绕过
     * <p>
     * 增强原有 jumpBtwaf 方法，增加：
     * <ul>
     *   <li>多次重试（最多5次）</li>
     *   <li>Cookie 自动持久化</li>
     *   <li>Token 提取策略优化</li>
     *   <li>Referer 伪造</li>
     * </ul>
     */
    protected String bypassBaoTaWaf(String webUrl, String html) {
        return bypassBaoTaWaf(webUrl, html, null);
    }

    /**
     * 宝塔 WAF 绕过（带自定义头部）
     */
    protected String bypassBaoTaWaf(String webUrl, String html, JSONObject customHeaders) {
        try {
            if (!rule.optBoolean("btwaf", false) && !isBaoTaWafPage(html)) return html;

            SpiderDebug.log("尝试绕过宝塔 WAF 防护...");

            Map<String, String> headers = customHeaders != null
                    ? mergeHeaders(getHeaders(webUrl), customHeaders)
                    : getHeaders(webUrl);

            for (int i = 0; i < MAX_ANTI_CRAWLER_RETRY; i++) {
                if (!isBaoTaWafPage(html)) break;

                // 方式1：提取 btwaf token 并附带请求
                String btwafToken = extractBtwafTokenEnhanced(html);
                if (!btwafToken.isEmpty()) {
                    String btUrl = appendQueryParam(webUrl, "btwaf", btwafToken);
                    okhttp3.Response resp = null;
                    try {
                        resp = OkHttp.newCall(btUrl, headers);
                        extractAllCookies(resp);
                    } catch (Exception e) {
                        SpiderDebug.log("bypassBaoTaWaf btwaf 请求异常: " + e.getMessage());
                    } finally {
                        if (resp != null) resp.close();
                    }

                    // 延迟后重新请求原始页面
                    Thread.sleep(ANTI_CRAWLER_DELAY_MS);
                    html = OkHttp.string(webUrl, headers);
                    continue;
                }

                // 方式2：检查是否有跳转URL
                String redirectUrl = extractRedirectUrl(html);
                if (!redirectUrl.isEmpty()) {
                    OkHttp.string(redirectUrl, headers);
                    Thread.sleep(ANTI_CRAWLER_DELAY_MS);
                    html = OkHttp.string(webUrl, headers);
                    continue;
                }

                // 方式3：纯等待+重试
                Thread.sleep(ANTI_CRAWLER_DELAY_MS + i * 500);
                html = OkHttp.string(webUrl, headers);
            }

            if (!isBaoTaWafPage(html)) {
                SpiderDebug.log("宝塔 WAF 绕过成功");
            }
        } catch (Exception e) {
            SpiderDebug.log("宝塔 WAF 绕过异常: " + e.getMessage());
        }
        return html;
    }

    /**
     * 增强版 BTWAF Token 提取
     * 支持多种 token 格式和位置
     */
    private String extractBtwafTokenEnhanced(String html) {
        // 格式1: btwaf="xxxxx"
        Pattern p1 = P_BTWAF_TOKEN_JSON;
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return m1.group(1);

        // 格式2: ?btwaf=xxxxx（在链接中）
        Pattern p2 = P_BTWAF_TOKEN_QUERY;
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return m2.group(1);

        return "";
    }

    /**
     * 从反爬页面提取跳转URL
     */
    private String extractRedirectUrl(String html) {
        // meta refresh 跳转
        Pattern p1 = P_META_REFRESH;
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return m1.group(1).trim();

        // JavaScript location.href 跳转
        Pattern p2 = P_LOCATION_HREF;
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return m2.group(1).trim();

        // window.location 跳转
        Pattern p3 = P_WINDOW_LOCATION;
        Matcher m3 = p3.matcher(html);
        if (m3.find()) return m3.group(1).trim();

        return "";
    }

    // ========== 滑块验证集成 ==========

    /**
     * 处理滑块验证（集成 SliderVerifyUtils）
     * <p>
     * 支持：
     * <ul>
     *   <li>本地自动滑动（模拟轨迹）</li>
     *   <li>外部打码服务（ddddocr API）</li>
     *   <li>JS Key 接口获取验证码</li>
     * </ul>
     *
     * @param webUrl 目标网站URL
     * @param html   包含滑块验证的HTML
     * @return 是否成功处理
     */
    protected boolean handleSliderVerify(String webUrl, String html) {
        try {
            SpiderDebug.log("检测到滑块验证，开始处理...");

            // 创建验证工具实例
            SliderVerifyUtils verifier = createSliderVerifier(webUrl);

            // 配置验证参数
            configureSliderVerifier(verifier);

            // 执行验证流程：先检测是否为验证页面，再尝试带验证的请求
            boolean success;
            if (verifier.isVerifyPage(html)) {
                SpiderDebug.log("检测到滑块验证页面，尝试自动验证...");
                String verifiedHtml = verifier.requestWithVerify(webUrl);
                success = verifiedHtml != null && !verifiedHtml.isEmpty()
                        && !verifier.isVerifyPage(verifiedHtml);
                if (success) {
                    mergeVerifyCookie(verifier);
                }
            } else {
                success = true;
            }

            return success;
        } catch (Exception e) {
            SpiderDebug.log("滑块验证处理异常: " + e.getMessage());
        }
        return false;
    }

    /**
     * 创建滑块验证器实例
     */
    private SliderVerifyUtils createSliderVerifier(String siteUrl) {
        SliderVerifyUtils verifier = new SliderVerifyUtils(siteUrl);

        // 从规则配置读取 JS Key URL
        String jsKeyUrl = rule.optString("js_key_url", "");
        if (!jsKeyUrl.isEmpty()) {
            verifier.setJsKeyUrl(jsKeyUrl);
        }

        // 从规则配置读取外部打码API
        String ocrApi = rule.optString("ocr_api", "");
        if (!ocrApi.isEmpty()) {
            verifier.setDdddOcrApi(ocrApi);
        }

        return verifier;
    }

    /**
     * 配置滑块验证器参数
     */
    private void configureSliderVerifier(SliderVerifyUtils verifier) {
        try {
            // 设置验证类型（注意：SliderVerifyUtils 只支持 setVerifyType）
            String verifyType = rule.optString("verify_type", "auto");
            if ("slider".equals(verifyType)) {
                verifier.setVerifyType(SliderVerifyUtils.VerifyType.SLIDER);
            } else if ("click".equals(verifyType)) {
                verifier.setVerifyType(SliderVerifyUtils.VerifyType.CLICK);
            }
            // 注意：verify_timeout 和 verify_retries 在当前 SliderVerifyUtils 版本中不直接支持，
            // 配置项已被忽略。如需超时控制可在 OkHttp 层面设置。
        } catch (Exception e) {
            SpiderDebug.log("验证器偏好配置异常: " + e.getMessage());
        }
    }

    /**
     * 合并验证后的 Cookie 到全局请求头
     */
    private void mergeVerifyCookie(SliderVerifyUtils verifier) {
        try {
            String verifyCookie = verifier.getVerifyCookie();
            if (verifyCookie == null || verifyCookie.isEmpty()) return;

            if (!rule.has("header")) {
                rule.put("header", new JSONObject());
            }
            JSONObject hdr = headerObject();
            String existingCookie = hdr.optString("cookie", "");
            String merged = Util.mergeCookies(existingCookie, verifyCookie);
            hdr.put("cookie", merged);

            SpiderDebug.log("验证 Cookie 已合并到全局请求头");
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    // ========== Cookie 管理 ==========

    /**
     * 从 Response 中提取所有 Set-Cookie 并保存
     */
    protected void extractAllCookies(okhttp3.Response response) {
        try {
            List<String> cookies = response.headers("set-cookie");
            if (cookies.isEmpty()) return;

            StringBuilder merged = new StringBuilder();
            for (String cookie : cookies) {
                // 只取 name=value 部分，去掉 path/domain/expires 等属性
                int semiIdx = cookie.indexOf(';');
                String nvPair = semiIdx > 0 ? cookie.substring(0, semiIdx) : cookie;
                if (merged.length() > 0) merged.append("; ");
                merged.append(nvPair.trim());
            }

            if (merged.length() > 0) {
                JSONObject hdr = headerObject();
                String existing = hdr.optString("cookie", "");
                String result = Util.mergeCookies(existing, merged.toString());
                hdr.put("cookie", result);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
    }

    /**
     * 取可写的 rule.header 对象：header 配置可能是字符串（简写UA / Key:Value 复合），
     * 直接 getJSONObject 会抛「not a JSONObject」导致 Cookie 附加静默失败——
     * 统一解析成 JSONObject 后写回
     */
    private JSONObject headerObject() throws JSONException {
        Object obj = rule.opt("header");
        if (obj instanceof JSONObject) return (JSONObject) obj;
        JSONObject hdr = parseHeader(obj == null ? "" : obj.toString());
        rule.put("header", hdr);
        return hdr;
    }

    /**
     * 合并两个 Map 形式的请求头
     */
    private Map<String, String> mergeHeaders(Map<String, String> base, JSONObject extra) {
        if (extra == null) return base;
        Map<String, String> result = new HashMap<>(base);
        try {
            Iterator<String> iter = extra.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                result.put(key, extra.getString(key));
            }
        } catch (Exception e) {
            SpiderDebug.log("合并扩展请求头异常: " + e.getMessage());
        }
        return result;
    }

    // ========== 请求辅助方法 ==========

    /**
     * 带重试的 URL 获取
     */
    private String fetchUrlWithRetry(String url) {
        try {
            Map<String, String> headers = getHeaders(url);
            // 随机化延迟，避免固定模式被检测
            long delay = ANTI_CRAWLER_DELAY_MS + (long)(Math.random() * 1000);
            Thread.sleep(delay);
            return OkHttp.string(url, headers);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    /**
     * 向 URL 追加查询参数
     */
    private String appendQueryParam(String url, String key, String value) {
        try {
            URL u = new URL(url);
            String sep = u.getQuery() == null ? "?" : "&";
            return url + sep + key + "=" + URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return url + (url.contains("?") ? "&" : "?") + key + "=" + value;
        }
    }

    // ========== 编码转换工具方法 ==========
    
    /**
     * 字符串转 Hex 编码（借鉴 XYQHiker.string2Hex）
     * 
     * @param str 输入字符串
     * @return Hex 编码字符串
     */
    protected String string2Hex(String str) {
        if (str == null || str.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
    
    /**
     * 移除 HTML 标签（借鉴 XBiubiu.removeHtml）
     * 
     * @param text 包含 HTML 的文本
     * @return 纯文本
     */
    protected String removeHtml(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            return Jsoup.parse(text).text();
        } catch (Exception e) {
            return text;
        }
    }
    
    // ========== 高级 Jsoup 导航与提取 ==========
    
    /**
     * 根据规则选择元素（支持 -- 链式、|| 回退、index、range）（借鉴 XYQHiker.selectByRule）
     * 
     * @param doc Jsoup Document
     * @param rule 选择规则，支持：
     *             - 普通 CSS 选择器：.class#id
     *             - 链式操作：selector1--selector2
     *             - 回退选择：selector1||selector2
     *             - 索引选择：selector:eq(0)
     *             - 范围选择：selector:gt(0):lt(5)
     * @return 选中的 Element，未找到返回 null
     */
    protected Element selectByRule(Document doc, String rule) {
        if (doc == null || rule == null || rule.isEmpty()) return null;
        
        try {
            // 处理 || 回退选择
            if (rule.contains("||")) {
                String[] parts = rule.split("\\|\\|");
                for (String part : parts) {
                    Element result = selectByRule(doc, part.trim());
                    if (result != null) return result;
                }
                return null;
            }
            
            // 处理 -- 链式选择
            if (rule.contains("--")) {
                String[] parts = rule.split("--");
                Element current = doc;
                for (String part : parts) {
                    if (current == null) return null;
                    current = current.selectFirst(part.trim());
                }
                return current;
            }
            
            // 处理 index 选择 :eq(n)
            if (rule.contains(":eq(")) {
                Pattern p = P_SELECT_EQ;
                Matcher m = p.matcher(rule);
                if (m.matches()) {
                    String selector = m.group(1);
                    int index = Integer.parseInt(m.group(2));
                    Elements elements = doc.select(selector);
                    return (index >= 0 && index < elements.size()) ? elements.get(index) : null;
                }
            }
            
            // 处理范围选择 :gt(n):lt(m)
            if (rule.contains(":gt(") || rule.contains(":lt(")) {
                return doc.selectFirst(rule);
            }
            
            // 普通选择
            return doc.selectFirst(rule);
        } catch (Exception e) {
            SpiderDebug.log("selectByRule 异常：" + e.getMessage());
            return null;
        }
    }
    
    /**
     * 根据规则获取文本（支持 +/＋ 拼接）（借鉴 XYQHiker.getTextByRule）
     * 
     * @param doc Jsoup Document
     * @param rule 选择规则，支持多个选择器用 + 或 ＋ 连接
     * @return 拼接后的文本
     */
    protected String getTextByRule(Document doc, String rule) {
        if (doc == null || rule == null || rule.isEmpty()) return "";
        
        try {
            // 处理 + 或 ＋ 拼接
            if (rule.contains("+") || rule.contains("＋")) {
                String[] parts = rule.split("[+＋]");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    Element elem = selectByRule(doc, part.trim());
                    if (elem != null) {
                        sb.append(elem.text());
                    }
                }
                return sb.toString();
            }
            
            // 单个选择器
            Element elem = selectByRule(doc, rule);
            return elem != null ? elem.text() : "";
        } catch (Exception e) {
            SpiderDebug.log("getTextByRule 异常：" + e.getMessage());
            return "";
        }
    }
    
    // ========== JSON/HTML双模式与二次截取 ==========
    
    /**
     * 检查是否为 JSON 模式（借鉴 XYQBiu.cat_mode）
     * 
     * @return true 如果是 JSON 模式
     */
    protected boolean isJsonMode() {
        try {
            if (rule.has("cat_mode")) {
                String mode = rule.getString("cat_mode");
                return "0".equals(mode);
            }
            // 兼容性检查：json 字段
            return rule.optBoolean("json", false);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 执行二次截取（借鉴 XYQBiu.YN_twice）
     * 
     * @param content 原始内容
     * @param twicePre 截取前缀
     * @param twiceSuf 截取后缀
     * @return 截取后的内容
     */
    protected String applyTwiceCut(String content, String twicePre, String twiceSuf) {
        if (content == null || content.isEmpty()) return "";
        
        try {
            // 检查是否需要二次截取
            boolean needTwice = false;
            if (rule.has("cat_YN_twice")) {
                needTwice = "1".equals(rule.getString("cat_YN_twice"));
            } else if (rule.has("YN_twice")) {
                needTwice = rule.getBoolean("YN_twice");
            }
            
            if (!needTwice || twicePre == null || twicePre.isEmpty() || 
                twiceSuf == null || twiceSuf.isEmpty()) {
                return content;
            }
            
            // 执行二次截取
            int startIdx = content.indexOf(twicePre);
            if (startIdx == -1) return content;
            startIdx += twicePre.length();
            
            int endIdx = content.indexOf(twiceSuf, startIdx);
            if (endIdx == -1) return content;
            
            return content.substring(startIdx, endIdx);
        } catch (Exception e) {
            SpiderDebug.log("applyTwiceCut 异常：" + e.getMessage());
            return content;
        }
    }
    
    /**
     * 数组提取（借鉴 XYQBiu.cat_arr_pre/cat_arr_suf）
     * 
     * @param content 原始内容
     * @param arrPre 数组开始标记
     * @param arrSuf 数组结束标记
     * @return 提取的数组内容列表
     */
    protected List<String> extractArray(String content, String arrPre, String arrSuf) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isEmpty() || 
            arrPre == null || arrPre.isEmpty() || 
            arrSuf == null || arrSuf.isEmpty()) {
            return result;
        }
        
        try {
            int startPos = 0;
            while (result.size() < MAX_MATCH_COUNT) {
                int startIdx = content.indexOf(arrPre, startPos);
                if (startIdx == -1) break;
                startIdx += arrPre.length();

                int endIdx = content.indexOf(arrSuf, startIdx);
                if (endIdx == -1) break;

                result.add(content.substring(startIdx, endIdx));
                startPos = endIdx + arrSuf.length();
            }
        } catch (Exception e) {
            SpiderDebug.log("extractArray 异常：" + e.getMessage());
        }
        
        return result;
    }
    
    // ========== 增强请求方法 ==========
    
    /**
     * POST 表单请求（带 charset 处理）（借鉴 XYQHiker.fetchPostForm）
     * 
     * @param webUrl 目标 URL
     * @param params POST 参数
     * @param charset 字符集，默认 UTF-8
     * @return 响应内容
     */
    protected String fetchPostForm(String webUrl, Map<String, String> params, String charset) {
        if (charset == null || charset.isEmpty()) charset = "UTF-8";

        try {
            // SSRF 防护：与其他请求路径一致，拦截内网/本机/危险协议
            if (isInternalUrl(webUrl)) {
                SpiderDebug.log(safeLog("fetchPostForm SSRF blocked: " + webUrl));
                return "";
            }

            // 构建表单数据
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(entry.getKey(), charset))
                  .append("=")
                  .append(URLEncoder.encode(entry.getValue(), charset));
            }
            
            // 设置请求头
            Map<String, String> headers = getHeaders(webUrl);
            headers.put("Content-Type", "application/x-www-form-urlencoded; charset=" + charset);
            
            // 发送 POST 请求
            String response = OkHttp.post(webUrl, sb.toString(), headers);
            return response != null ? response : "";
        } catch (Exception e) {
            SpiderDebug.log("fetchPostForm 异常：" + e.getMessage());
            return "";
        }
    }
    
    /**
     * 带响应头捕获的请求（借鉴 XYQHiker.fetchWithHeaders）
     * 
     * @param webUrl 目标 URL
     * @param headers 请求头
     * @return Pair<响应内容，响应头 Map>
     */
    protected Pair<String, Map<String, List<String>>> fetchWithHeaders(String webUrl, HashMap<String, String> headers) {
        okhttp3.Response response = null;
        try {
            response = OkHttp.newCall(webUrl, headers);
            String body = response.body() != null ? response.body().string() : "";

            // 提取响应头
            Map<String, List<String>> responseHeaders = new HashMap<>();
            for (String name : response.headers().names()) {
                responseHeaders.put(name, response.headers(name));
            }

            return new Pair<>(body, responseHeaders);
        } catch (Exception e) {
            SpiderDebug.log("fetchWithHeaders 异常：" + e.getMessage());
            return new Pair<>("", new HashMap<>());
        } finally {
            if (response != null) response.close();
        }
    }
    
    // ========== 辅助工具方法 ==========
    
    /**
     * 构建年份范围（用于筛选条件）（借鉴 XYQHiker.buildYearRange）
     * 
     * @param startYear 起始年份
     * @param endYear 结束年份
     * @return 年份列表
     */
    protected List<String> buildYearRange(int startYear, int endYear) {
        List<String> years = new ArrayList<>();
        // minSdk 21 且未启用 desugaring，不能用 java.time，改用 Calendar
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        
        if (endYear <= 0) endYear = currentYear;
        if (startYear <= 0) startYear = endYear - 10;
        
        for (int year = startYear; year <= endYear; year++) {
            years.add(String.valueOf(year));
        }
        
        return years;
    }
    
    /**
     * 从 clan:// 加载外部过滤器（借鉴 XYQHiker.loadExtFilter）
     * 
     * @param url clan:// 开头的 URL
     * @return 过滤器 JSON 对象
     */
    protected JSONObject loadExtFilter(String url) {
        try {
            if (url.startsWith("clan://")) {
                // 本地文件路径
                String filePath = url.substring(7);
                String content = Util.readStringFromFile(filePath);
                return new JSONObject(content);
            } else if (url.startsWith("http")) {
                // 网络 URL
                String content = OkHttp.string(url, null);
                return new JSONObject(content);
            }
        } catch (Exception e) {
            SpiderDebug.log("loadExtFilter 异常：" + e.getMessage());
        }
        return new JSONObject();
    }

    // ==================== 图片代理（双模式 + 签名，借鉴第18次升级版/参考文件） ====================

    /**
     * 图片代理URL生成（双模式 + secretKey 签名）
     * <p>
     * 模式选择：
     * <ul>
     *   <li>baseEncodeUrl 非空：远端 base64 图片代理（前缀 + Base64(pic)，适合需要 CDN 中转的防盗链图床）</li>
     *   <li>否则：本地 proxy:// 代理（do=XBPQ&url=&referer=，由 loadPic 带 Referer 回源）</li>
     *   <li>secretKey 非空：追加 &key=MD5(pic+secretKey) 签名（loadPic 校验，防代理被恶意滥用）</li>
     * </ul>
     *
     * @param cover 原始封面URL
     * @param site  来源站点（作为 Referer 回源）
     * @return 代理包装后的封面URL
     */
    protected String fixCover(String cover, String site) {
        try {
            if (cover == null || cover.isEmpty()) return cover;
            log("fixCover site=" + site + " cover=" + cover);

            // 模式1：远端 base64 代理（防盗链图床中转）
            if (!baseEncodeUrl.isEmpty()) {
                String encoded = Base64.encodeToString(cover.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                return baseEncodeUrl + encoded;
            }

            // 模式2：本地 proxy:// 代理（loadPic 带 Referer 回源）
            StringBuilder sb = new StringBuilder("proxy://do=XBPQ")
                    .append("&url=").append(URLEncoder.encode(cover, "UTF-8"))
                    .append("&referer=").append(URLEncoder.encode(site, "UTF-8"));
            // secretKey 签名（loadPic 校验）
            if (!secretKey.isEmpty()) {
                sb.append("&key=").append(Util.md5(cover + secretKey));
            }
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log("fixCover error: " + e.getMessage());
        }
        return cover;
    }

    /** 图片代理缓存头（volatile + DCL 保证多线程懒加载安全） */
    private static volatile Map<String, String> picHeaderCache = null;

    /**
     * SSRF 防护：判断 URL 是否指向内网/本机/危险协议。
     * 用于 proxy() 外露入口（loadPic/loadM3u8/loadDanmu）及 fetch/getBL 中拦截
     * 配置可控的内网回源请求，防止探测内网服务。
     */
    private static boolean isInternalUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        if (u.isEmpty()) return false;
        if (u.startsWith("file:") || u.startsWith("gopher:") || u.startsWith("dict:")
                || u.startsWith("ftp:") || u.startsWith("jar:") || u.startsWith("netdoc:")) {
            return true;
        }
        String host = "";
        boolean isIpv6 = false;
        int idx = u.indexOf("://");
        if (idx >= 0) {
            int start = idx + 3;
            if (start < u.length() && u.charAt(start) == '[') {
                int close = u.indexOf(']', start);
                host = close > 0 ? u.substring(start + 1, close) : "";
                isIpv6 = true;
            } else {
                int end = u.length();
                for (int i = start; i < u.length(); i++) {
                    char c = u.charAt(i);
                    if (c == '/' || c == ':' || c == '?' || c == '#') { end = i; break; }
                }
                host = u.substring(start, end);
            }
        } else {
            int cut = u.indexOf('/');
            host = cut >= 0 ? u.substring(0, cut) : u;
        }
        if (host.isEmpty()) return false;
        if (!isIpv6) {
            int colon = host.indexOf(':');
            if (colon > 0) host = host.substring(0, colon);
            return isInternalHost(host);
        }
        // IPv6 字面量
        if (host.equals("::1") || host.equals("::")) return true;
        if (host.startsWith("fc") || host.startsWith("fd")) return true; // ULA
        if (host.startsWith("fe80")) return true;                        // 链路本地
        // IPv4-mapped：::ffff:127.0.0.1 复用 IPv4 判定
        if (host.startsWith("::ffff:")) return isInternalHost(host.substring(7));
        return resolvesToInternal(host);
    }

    /**
     * 主机内网判定：字面量规则 + 编码 IP 解析级兜底。
     * 十进制整数(2130706433)/八进制(0177.0.0.1)/十六进制(0x7f.0.0.1)等字符串比对无法覆盖，
     * 对疑似编码 IP 的主机追加一次解析级校验。
     */
    private static boolean isInternalHost(String host) {
        if (host.isEmpty()) return false;
        if (host.equals("localhost")) return true;
        if (isInternalIpv4(host)) return true;
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) {
            return true;
        }
        if (looksLikeEncodedIp(host) && resolvesToInternal(host)) return true;
        return false;
    }

    /** 点分十进制 IPv4 私网/保留段判定 */
    private static boolean isInternalIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        try {
            long a = Long.parseLong(parts[0]);
            long b = Long.parseLong(parts[1]);
            long c = Long.parseLong(parts[2]);
            long d = Long.parseLong(parts[3]);
            if (a > 255 || b > 255 || c > 255 || d > 255) return false;
            if (a == 10) return true;                        // 10.0.0.0/8
            if (a == 172 && b >= 16 && b <= 31) return true; // 172.16.0.0/12
            if (a == 192 && b == 168) return true;           // 192.168.0.0/16
            if (a == 127) return true;                       // 127.0.0.0/8
            if (a == 169 && b == 254) return true;           // 169.254.0.0/16 链路本地
            if (a == 0) return true;                         // 0.0.0.0/8
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 疑似编码型 IP 字面量（仅含 0-9/a-f/x/.）：
     * 命中才值得花一次解析校验；普通域名（几乎必含 g-z 字母）被快速排除，无 DNS 开销。
     */
    private static boolean looksLikeEncodedIp(String host) {
        if (host.isEmpty()) return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == '.' || c == 'x') continue;
            return false;
        }
        return true;
    }

    /**
     * 解析级兜底：把主机解析为实际 IP 后判断回环/私网/链路本地/任意地址。
     * 数字型字面量不产生真实 DNS 查询；域名解析失败视为放行（后续真实请求自然失败）。
     */
    private static boolean resolvesToInternal(String host) {
        try {
            java.net.InetAddress[] addrs = java.net.InetAddress.getAllByName(host);
            for (java.net.InetAddress addr : addrs) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                    return true;
                }
                byte[] b = addr.getAddress();
                if (b.length == 4 && (b[0] & 0xFF) == 100
                        && (b[1] & 0xFF) >= 64 && (b[1] & 0xFF) <= 127) {
                    return true; // CGNAT 100.64.0.0/10
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 安全日志：对外部输入（URL/HTML 片段）转义尖括号并截断长度，
     * 防止日志查看器渲染 HTML 造成存储型 XSS。
     */
    private static String safeLog(String s) {
        if (s == null) return "";
        // 先转义 & 避免重复处理，再转义 < 和 >
        String r = s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", " ").replace("\r", " ");
        if (r.length() > 2000) r = r.substring(0, 2000) + "...";
        return r;
    }

    /**
     * 加载图片（fixCover 的 proxy:// 回源端）
     * <p>
     * 参数兼容两种风格：
     * <ul>
     *   <li>新版：url / referer / key（与 fixCover 生成的 proxy:// 参数一致）</li>
     *   <li>旧版：pic / site（向后兼容）</li>
     * </ul>
     * secretKey 非空时强制签名校验，不匹配则拒绝回源（防代理滥用）。
     */
    public static Object[] loadPic(Map<String, String> params) {
        try {
            // 兼容新旧参数
            String pic = params.containsKey("url") ? params.get("url") : params.get("pic");
            String site = params.containsKey("referer") ? params.get("referer") : params.get("site");
            if (pic == null || pic.isEmpty()) return null;

            pic = java.net.URLDecoder.decode(pic, "UTF-8");

            // SSRF 防护：拦截内网/本机/危险协议回源
            if (isInternalUrl(pic)) {
                SpiderDebug.log(safeLog("loadPic SSRF blocked: " + pic));
                return null;
            }

            // secretKey 签名校验：fixCover 追加的 &key=MD5(pic+secretKey)，不匹配拒绝回源
            if (!secretKey.isEmpty()) {
                String reqKey = params.get("key");
                String expectKey = Util.md5(pic + secretKey);
                if (reqKey == null || !reqKey.equals(expectKey)) {
                    SpiderDebug.log("loadPic key mismatch, reject proxy fetch");
                    return null;
                }
            }

            // 每次创建新的 headers，避免 referer 被错误缓存
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", UA_PC);
            if (site != null && !site.isEmpty()) {
                headers.put("referer", site);
            }

            Object[] result = OkHttp.proxy(pic, headers);
            if (result != null && ((Integer) result[0]) == 200) {
                ByteArrayInputStream stream = new ByteArrayInputStream((byte[]) result[2]);
                Object[] proxyResult = new Object[3];
                proxyResult[0] = 200;
                proxyResult[1] = result[1];
                proxyResult[2] = stream;
                return proxyResult;
            }
        } catch (Throwable th) {
            SpiderDebug.log(th);
        }
        return null;
    }

    // ==================== 弹幕加载（借鉴第18次升级版 loadDanmu） ====================

    /**
     * 加载弹幕数据
     * <p>
     * 支持从 XML/JSON 弹幕 URL 加载数据：JSON 数组格式自动转换为 B站风格 XML 弹幕。
     * 返回 Object[]{statusCode, contentType, inputStream}
     *
     * @param map 参数表，弹幕URL键为 danmu_url（兼容 danmuUrl）
     */
    public static Object[] loadDanmu(Map<String, String> map) {
        try {
            String danmuUrl = map.get("danmu_url");
            if (danmuUrl == null || danmuUrl.isEmpty()) {
                danmuUrl = map.get("danmuUrl");
            }
            if (danmuUrl == null || danmuUrl.isEmpty()) return null;
            danmuUrl = java.net.URLDecoder.decode(danmuUrl, "UTF-8");

            // SSRF 防护：拦截内网/本机/危险协议回源
            if (isInternalUrl(danmuUrl)) {
                SpiderDebug.log(safeLog("loadDanmu SSRF blocked: " + danmuUrl));
                return null;
            }

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", UA_PC);
            // 注入类级公共请求头（弹幕接口常需携带站点 Cookie）
            if (!headerMap.isEmpty()) headers.putAll(headerMap);

            String resp = OkHttp.string(danmuUrl, headers);
            if (resp == null || resp.isEmpty()) return null;
            String xml = jsonArray2xml(resp);
            if (xml.isEmpty()) xml = resp;
            return new Object[]{200, "application/octet-stream",
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            SpiderDebug.log(safeLog("loadDanmu error: " + e.getMessage()));
            return null;
        }
    }

    /**
     * 弹幕 p 属性下标（B站风格 [time,mode,size,color,source,content,userHash]）
     * 第 6 个位置（时间戳/日期）在输出时固定为 0。
     */
    private static final int DM_TIME = 0, DM_MODE = 1, DM_SIZE = 2, DM_COLOR = 3,
            DM_SOURCE = 4, DM_CONTENT = 5, DM_USER = 6;

    /**
     * XML 特殊字符转义（& 先行，防二次编码）
     */
    private static String escapeXml(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * JSON 数组转 XML 格式（B站风格弹幕兼容）
     * 输入格式：{"list": [[time,mode,size,color,source,content,userHash], ...]}
     */
    public static String jsonArray2xml(String input) {
        if (input == null || input.isEmpty()) return "";
        try {
            JSONArray array = new JSONObject(input).optJSONArray("list");
            if (array == null) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><i>\n");
            for (int i = 0; i < array.length(); i++) {
                // 逐行容错：单条脏数据不应清空整份弹幕
                try {
                    JSONArray item = array.getJSONArray(i);
                    if (item.length() < 7) continue;
                    sb.append("<d p=\"")
                            .append(item.optString(DM_TIME))
                            .append(",").append(item.optInt(DM_MODE))
                            .append(",").append(item.optInt(DM_SIZE))
                            .append(",").append(item.optInt(DM_COLOR))
                            .append(",").append(item.optInt(DM_SOURCE))
                            .append(",0,").append(item.optString(DM_USER))
                            .append("\">").append(escapeXml(item.optString(DM_CONTENT))).append("</d>\n");
                } catch (Exception rowEx) {
                    SpiderDebug.log("jsonArray2xml 跳过第 " + i + " 条: " + rowEx.getMessage());
                }
            }
            sb.append("</i>");
            return sb.toString();
        } catch (Exception e) {
            SpiderDebug.log("jsonArray2xml error: " + e.getMessage());
            return "";
        }
    }

    // ==================== M3U8 直播流/点播流解析（借鉴第18次升级版 loadM3u8） ====================

    /**
     * 解析 M3U8 直播流/点播流
     * <p>
     * 支持特性：
     * <ul>
     *   <li>Base64 内嵌格式检测（解码后以 #EXTM3U/#EXTINF 开头视为内嵌内容）</li>
     *   <li>相对路径解析（基于 base 参数或源 URL 域名补全分片地址）</li>
     * </ul>
     * 返回 Object[]{statusCode, contentType, inputStream}
     *
     * @param map 参数表：url=M3U8地址（或Base64内嵌内容），base=相对路径基准（可选）
     */
    public static Object[] loadM3u8(Map<String, String> map) {
        try {
            String m3u8Url = map.get("url");
            String baseUrl = map.get("base");
            if (m3u8Url == null || m3u8Url.isEmpty()) return null;
            m3u8Url = java.net.URLDecoder.decode(m3u8Url, "UTF-8");

            // 检测 base64 内嵌格式：仅在疑似内嵌（无协议头且较长）时尝试解码，
            // 避免对每个普通 URL 都做无谓的 Base64 解码（P1-2 防护）
            boolean isBase64Content = false;
            if (m3u8Url.indexOf("://") < 0 && m3u8Url.length() > 200) {
                try {
                    byte[] decoded = Base64.decode(m3u8Url, Base64.DEFAULT);
                    String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                    if (decodedStr.startsWith("#EXTM3U") || decodedStr.contains("#EXTINF")) {
                        isBase64Content = true;
                        m3u8Url = decodedStr;
                    }
                } catch (Exception ignored) {
                }
            }

            // SSRF 防护：仅对真实回源（非 base64 内嵌内容）拦截内网/本机/危险协议
            if (!isBase64Content && isInternalUrl(m3u8Url)) {
                SpiderDebug.log(safeLog("loadM3u8 SSRF blocked: " + m3u8Url));
                return null;
            }

            // 获取 m3u8 内容
            String content;
            if (isBase64Content) {
                content = m3u8Url;
            } else {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("User-Agent", UA_PC);
                if (!headerMap.isEmpty()) headers.putAll(headerMap);
                content = OkHttp.string(m3u8Url, headers);
            }
            if (content == null || content.isEmpty()) return null;

            // 处理相对路径：逐行补全非 http 开头的分片地址
            StringBuilder result = new StringBuilder();
            String[] lines = content.split("\n");
            String resolvedBase = baseUrl != null && !baseUrl.isEmpty()
                    ? baseUrl : Util.extractDomain(m3u8Url) + "/";

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    result.append(line).append("\n");
                } else if (!line.startsWith("http") && !line.startsWith("//")) {
                    result.append(Util.repairUrl(resolvedBase, line)).append("\n");
                } else {
                    result.append(line).append("\n");
                }
            }

            String finalContent = result.toString();
            return new Object[]{200, "application/vnd.apple.mpegurl",
                    new ByteArrayInputStream(finalContent.getBytes(StandardCharsets.UTF_8))};
        } catch (Exception e) {
            SpiderDebug.log(safeLog("loadM3u8 error: " + e.getMessage()));
            return null;
        }
    }

    /**
     * 框架代理总入口：CatVod 框架解析 proxy://do=XBPQ 时通过 Spider.proxy(Map) 调用本方法。
     * 原目标文件未覆写此方法，导致 proxy://do=XBPQ 落到基类 Spider.proxy() 直接返回 null，
     * 图片/弹幕/流媒体代理全部失效。这里按参数特征分发：
     * <ul>
     *   <li>含 danmu_url / danmuUrl → loadDanmu（弹幕）</li>
     *   <li>含 m3u8 → loadM3u8（直播/点播流）</li>
     *   <li>其余（含 url/pic/site/referer）→ loadPic（图片回源）</li>
     * </ul>
     * 多源防串扰：headerMap/secretKey/baseEncodeUrl/staticHomeUrl 为类级共享状态，
     * 回源分发前先以「当前实例」的规则刷新一遍，确保签名校验/公共头/回源地址与发起代理请求的源一致。
     */
    @Override
    public Object[] proxy(Map<String, String> params) {
        if (params == null) return null;
        try {
            // 实例已被框架 init 过，fetchRule 命中缓存不发网络；initEnhancedConfig 仅做 JSON 解析与加锁赋值
            fetchRule();
            initEnhancedConfig();
        } catch (Exception ignored) {
        }
        if (params.containsKey("danmu_url") || params.containsKey("danmuUrl")) return loadDanmu(params);
        if (params.containsKey("m3u8")) return loadM3u8(params);
        // url 以 .m3u8 结尾时走流解析（loadPic 会把文本流当图片回源）
        String pu = params.get("url");
        if (pu != null) {
            try {
                String decoded = java.net.URLDecoder.decode(pu, "UTF-8").toLowerCase();
                if (decoded.contains(".m3u8")) return loadM3u8(params);
            } catch (Exception ignored) {
            }
        }
        return loadPic(params);
    }

    // ==================== 增强特性：配置初始化（借鉴第18次升级版） ====================

    /**
     * 初始化增强特性配置：
     * <ul>
     *   <li>openDebug 调试开关（类级"只升不降"）</li>
     *   <li>baseEncodeUrl / secretKey 图片代理双模式参数</li>
     *   <li>headerMap 类级公共请求头填充</li>
     *   <li>variableMap 常用变量初始化</li>
     *   <li>静态工具方法同步（getBL/getRV 等使用类级静态字段）</li>
     * </ul>
     */
    private void initEnhancedConfig() {
        try {
            // 实例级状态（每实例隔离，无需加锁）
            if (rule.has("openDebug")) {
                isDebug = "1".equals(rule.optString("openDebug")) || rule.optBoolean("openDebug", false);
            }
            playImage = getRuleVal("play_image");
            mergeLines = "1".equals(getRuleVal("merge_lines"));
            hotRecommend = "1".equals(getRuleVal("hot_recommend"));
            listDisplay = "1".equals(getRuleVal("list_display"));

            // variableMap 常用变量（{{变量}} 替换使用）
            variableMap.clear();
            variableMap.put("主页url", rule.optString("homeUrl", ""));
            variableMap.put("站名", rule.optString("siteName", ""));
            variableMap.put("作者", rule.optString("author", ""));
            variableMap.put("分类url", rule.optString("class_url", ""));
            variableMap.put("搜索url", rule.optString("search_url", ""));
            variableMap.put("后缀", rule.optString("domainSuffix", ""));
            variableMap.put("密钥", getRuleVal("secretKey"));

            // 类级共享状态：加锁串行化写入，避免多源并发初始化互相覆盖（P0-3 线程安全）
            synchronized (GLOBAL_STATE_LOCK) {
                debug = debug || isDebug;
                baseEncodeUrl = getRuleVal("baseEncodeUrl");
                secretKey = getRuleVal("secretKey");
                staticHomeUrl = rule.optString("homeUrl", "");

                // 类级公共请求头表填充（JSON 对象串或 Key$Value#Key$Value 格式）。
                // 仅在本源确实配置了公共头时才清空重填——无配置的源清表会把
                // 他源共享的 Cookie/Token 泼掉（多源串扰修复）
                String headerJsonStr = getRuleVal("headerJson");
                String userHeaderStr = getRuleVal("userHeader");
                if (!headerJsonStr.isEmpty() || !userHeaderStr.isEmpty()) {
                    headerMap.clear();
                    if (!headerJsonStr.isEmpty()) {
                        try {
                            JSONObject headerObj = headerJsonStr.startsWith("{")
                                    ? new JSONObject(headerJsonStr)
                                    : parseHeader(headerJsonStr);
                            Iterator<String> keys = headerObj.keys();
                            while (keys.hasNext()) {
                                String k = keys.next();
                                headerMap.put(k, headerObj.optString(k));
                            }
                        } catch (Exception e) {
                            SpiderDebug.log("headerMap parse error: " + e.getMessage());
                        }
                    }

                    // User：独立请求头（Key:Value 形式，支持 # 分隔多行），注入类级公共请求头
                    if (!userHeaderStr.isEmpty()) {
                        injectUserHeader(userHeaderStr);
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("initEnhancedConfig error: " + e.getMessage());
        }
    }

    /**
     * 解析 User 字段并注入类级公共请求头
     * 支持格式：Key:Value，多个用 # 或换行分隔；值允许包含冒号（如 http://）
     */
    private void injectUserHeader(String raw) {
        if (raw == null || raw.isEmpty()) return;
        for (String part : raw.split("#")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            int idx = part.indexOf(':');
            if (idx <= 0) continue;
            String key = part.substring(0, idx).trim();
            String val = part.substring(idx + 1).trim();
            if (!key.isEmpty() && !val.isEmpty()) {
                headerMap.put(key, val);
                // 掩码输出（Minor22）：User 头常含 token/密钥，日志仅保留前4后2字符
                SpiderDebug.log("注入 User 请求头: " + key + " -> " + maskHeaderValue(val));
            }
        }
    }

    /**
     * 敏感头值掩码（Minor22）：保留前4+后2字符，中间以 **** 代替；
     * 过短值（≤8字符）全掩码，防止短 token 被前后缀泄露。
     */
    private static String maskHeaderValue(String val) {
        if (val == null || val.isEmpty()) return "";
        if (val.length() <= 8) return "******";
        return val.substring(0, 4) + "****" + val.substring(val.length() - 2);
    }

    /**
     * 类级调试日志（受 static debug 开关门控）：
     * 任一源 openDebug=1 开启后，关键链路轨迹全局可见
     */
    public static void log(String message) {
        if (!debug) return;
        SpiderDebug.log("XBPQ[debug]: " + message);
    }

    // ==================== {{变量}} 替换（借鉴第18次升级版） ====================

    /**
     * 变量替换：将 {{key}} 替换为 variableMap 中的值
     * 支持最多 10 轮递归替换，确保嵌套变量也能正确展开
     */
    protected String resolveVariables(String template) {
        if (template == null || template.isEmpty()) return template;
        if (!template.contains("{{")) return template;
        try {
            // 注入系统变量
            if (rule != null) {
                variableMap.put("主页url", rule.optString("homeUrl", ""));
            }
            Pattern pattern = P_TEMPLATE_VAR;
            for (int iter = 0; iter < 10; iter++) {
                Matcher matcher = pattern.matcher(template);
                if (!matcher.find()) break;
                StringBuffer sb = new StringBuffer();
                matcher.reset();
                while (matcher.find()) {
                    String key = matcher.group(1).trim();
                    String val = variableMap.getOrDefault(key, "");
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(val));
                }
                matcher.appendTail(sb);
                template = sb.toString();
            }
            return template;
        } catch (Exception e) {
            SpiderDebug.log("resolveVariables error: " + e.getMessage());
            return template;
        }
    }



    // ==================== hexEscapeDecode 敏感词清理（借鉴第18次升级版/参考文件） ====================

    /**
     * JSON 转义字符解码：将 \\uXXXX 格式的 Unicode 转义序列还原为实际字符，
     * 并移除多余的换行符和反斜杠（用于清理站点对敏感词的 unicode 编码保护）
     */
    protected static String hexEscapeDecode(String input) {
        if (input == null || input.isEmpty()) return input;
        try {
            Pattern pattern = P_UNICODE_ESCAPE;
            Matcher matcher = pattern.matcher(input);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String hex = matcher.group(1);
                char c = (char) Integer.parseInt(hex, 16);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(c)));
            }
            matcher.appendTail(sb);
            String result = sb.toString();
            // 移除多余换行
            result = result.replace("\r\n", "");
            // 移除多余的连续反斜杠（非引号前的）
            result = result.replaceAll("\\\\\\+(?!\")", "");
            return result.trim();
        } catch (Exception e) {
            SpiderDebug.log("hexEscapeDecode error: " + e.getMessage());
            return input;
        }
    }

    // ==================== 错误码/成功码/失败码配置（借鉴第18次升级版） ====================

    /**
     * 检查 HTTP 状态码是否表示失败
     * 优先使用配置的 failCodes/errorCodes 列表（# 或 , 分隔），为空则回退默认 4xx/5xx 判定
     */
    public boolean isFail(int code) {
        try {
            JSONObject r = rule;
            if (r != null) {
                String codeStr = String.valueOf(code);
                String failCodes = r.optString("failCodes", "") + "#" + r.optString("errorCodes", "");
                for (String fc : failCodes.split("[#,，]")) {
                    if (!fc.trim().isEmpty() && codeStr.equals(fc.trim())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return code >= 400;
    }

    /**
     * 检查 HTTP 状态码是否表示成功
     * 配置 successCodes 列表时以列表为准，否则默认 2xx
     */
    public boolean isSuccess(int code) {
        try {
            JSONObject r = rule;
            if (r != null) {
                String successCodes = r.optString("successCodes", "");
                if (!successCodes.isEmpty()) {
                    String codeStr = String.valueOf(code);
                    for (String sc : successCodes.split("[#,，]")) {
                        if (codeStr.equals(sc.trim())) return true;
                    }
                    return false;
                }
            }
        } catch (Exception ignored) {
        }
        return code >= 200 && code < 300;
    }

    // ==================== 偏好设置菜单（借鉴第18次升级版 getPrefMenu/applyPrefMenu/insertActionTabs） ====================

    /** 偏好存储键 */
    private static final String PREF_MENU_KEY = "XBPQ_prefMenu";

    /**
     * 读取 SharedPreferences 偏好
     */
    private static String readPref(String key) {
        try {
            return Init.getString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 保存 SharedPreferences 偏好
     */
    private static void savePref(String key, String value) {
        try {
            Init.put(key, value == null ? "" : value);
        } catch (Exception ignored) {
        }
    }

    /**
     * 获取随机图标背景色号（0~99）
     */
    private String randomColor() {
        return String.valueOf(random.nextInt(100));
    }

    /**
     * 构建偏好设置默认菜单
     * 每项 {name, action, selected}；支持配置 prefMenu 自定义菜单项覆盖默认值
     */
    private JSONArray buildPrefMenu() {
        JSONArray menu = new JSONArray();
        try {
            // 配置 prefMenu 自定义菜单项（[{name, action, selected}] 数组）
            JSONArray custom = rule == null ? null : rule.optJSONArray("prefMenu");
            if (custom != null && custom.length() > 0) return custom;

            menu.put(new JSONObject().put("name", "置顶搜索和设置").put("action", "SSTop").put("selected", false));
            menu.put(new JSONObject().put("name", "显示收藏夹").put("action", "favoritesShow").put("selected", false));
            menu.put(new JSONObject().put("name", "关闭搜索记录").put("action", "offSearchCache").put("selected", false));
            menu.put(new JSONObject().put("name", "打开调试模式").put("action", "openDebug").put("selected", isDebug));
        } catch (Exception ignored) {
        }
        return menu;
    }

    /**
     * 获取偏好菜单（合并已保存的用户选择状态）
     */
    private JSONArray getPrefMenu() {
        try {
            String saved = readPref(PREF_MENU_KEY);
            JSONArray savedArr = null;
            if (saved != null && saved.length() > 0) {
                try {
                    savedArr = new JSONArray(saved);
                } catch (Exception ignored) {
                }
            }
            JSONArray defaults = buildPrefMenu();
            if (savedArr == null) return defaults;
            JSONArray merged = new JSONArray();
            for (int i = 0; i < defaults.length(); i++) {
                JSONObject item = defaults.getJSONObject(i);
                for (int j = 0; j < savedArr.length(); j++) {
                    JSONObject sv = savedArr.getJSONObject(j);
                    if (item.optString("action").equals(sv.optString("action"))) {
                        item.put("selected", sv.optBoolean("selected"));
                        break;
                    }
                }
                merged.put(item);
            }
            return merged;
        } catch (Exception e) {
            return buildPrefMenu();
        }
    }

    /**
     * 应用偏好菜单选择状态到 rule（将 {action, selected} 展开为 rule[action] 布尔键值）
     */
    private void applyPrefMenu() {
        try {
            if (rule == null) return;
            JSONArray menu = getPrefMenu();
            for (int i = 0; i < menu.length(); i++) {
                JSONObject item = menu.getJSONObject(i);
                rule.put(item.optString("action"), item.optBoolean("selected"));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 在首页插入「偏好设置」「源内搜索」action tab
     * 开启条件：配置 actionTabs=1，或偏好中 SSTop 已选择
     *
     * @param classes 原分类列表
     * @return 插入 action tab 后的分类列表
     */
    protected JSONArray insertActionTabs(JSONArray classes) {
        try {
            if (rule == null) return classes;
            boolean top = rule.optBoolean("SSTop", false);
            if (!"1".equals(getRuleVal("actionTabs")) && !top) return classes;

            // 偏好设置 tab（option 菜单 + type 2 + width 800）
            JSONObject setAct = new JSONObject();
            setAct.put("actionId", "偏好设置");
            setAct.put("title", "偏好设置");
            setAct.put("type", 2);
            setAct.put("width", 800);
            setAct.put("option", getPrefMenu());
            JSONObject setTab = new JSONObject();
            setTab.put("vod_name", "偏好设置");
            setTab.put("vod_id", "偏好设置");
            setTab.put("vod_pic", "clan://assets/set.png?bgcolor=" + randomColor());
            setTab.put("vod_tag", "action");
            setTab.put("action", setAct);

            // 源内搜索 tab（输入框）
            JSONObject input = new JSONObject();
            input.put("id", "wd");
            input.put("tip", "请输入搜索内容");
            input.put("value", "");
            JSONObject searchAct = new JSONObject();
            searchAct.put("actionId", "源内搜索");
            searchAct.put("title", "源内搜索");
            searchAct.put("type", 1);
            searchAct.put("input", new JSONArray().put(input));
            JSONObject searchTab = new JSONObject();
            searchTab.put("vod_name", "源内搜索");
            searchTab.put("vod_id", "源内搜索");
            searchTab.put("vod_pic", "clan://assets/search.png?bgcolor=" + randomColor());
            searchTab.put("vod_tag", "action");
            searchTab.put("action", searchAct);

            JSONArray result = new JSONArray();
            if (top) {
                result.put(searchTab);
                result.put(setTab);
                for (int i = 0; i < classes.length(); i++) result.put(classes.get(i));
            } else {
                for (int i = 0; i < classes.length(); i++) result.put(classes.get(i));
                result.put(searchTab);
                result.put(setTab);
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log("insertActionTabs error: " + e.getMessage());
            return classes;
        }
    }

    // ==================== 静态工具方法（借鉴第18次升级版 getBL/setBL/getRV/getCom/clan） ====================

    /**
     * 清理字符串中的干扰标记 [.*?] 和 ￥.*?￥
     */
    public static String clan(String input) {
        if (input == null || input.isEmpty()) return input;
        // 长度上限：规避对超长无闭合输入的回溯开销（ReDoS 缓解，正常标题远小于此）
        if (input.length() > 4096) input = input.substring(0, 4096);
        String s = P_CLAN_BRACKET.matcher(input).replaceAll("");
        s = P_CLAN_YUAN.matcher(s).replaceAll("");
        return s;
    }

    /**
     * 静态获取任意 URL 内容（相对路径基于 homeUrl 补全，携带公共请求头）
     */
    public static String getBL(String path) {
        try {
            if (staticHomeUrl.isEmpty()) return "";
            String fullUrl = path.startsWith("http") ? path : Util.repairUrl(staticHomeUrl, path);
            if (isInternalUrl(fullUrl)) {
                SpiderDebug.log(safeLog("getBL SSRF blocked: " + fullUrl));
                return "";
            }
            Map<String, String> headers = new HashMap<>();
            if (!headerMap.isEmpty()) headers.putAll(headerMap);
            headers.put("User-Agent", UA_PC);
            return OkHttp.string(fullUrl, headers);
        } catch (Exception e) {
            SpiderDebug.log("getBL error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 获取配置组合字符串（homeUrl，供宿主框架使用）
     */
    public static String getCom() {
        return staticHomeUrl;
    }

    /**
     * 静态设置 homeUrl（供外部回源框架调用）。
     * 兼容两种调用形态：setBL(homeUrl, null) 直接设置主页地址；
     * setBL(任意path, content) 以 content 覆盖主页地址（历史行为保留）。
     */
    public static void setBL(String path, String content) {
        synchronized (GLOBAL_STATE_LOCK) {
            staticHomeUrl = (content != null && !content.isEmpty()) ? content : (path != null ? path : "");
        }
    }

    /**
     * 静态执行规则提取（获取主页内容后按选择器提取）
     * 支持 CSS 选择器（css: 前缀或裸选择器）与 前缀&&后缀 截取规则
     */
    public static String getRV(String selector) {
        try {
            if (staticHomeUrl.isEmpty() || selector == null || selector.isEmpty()) return "";
            String html = getBL(staticHomeUrl);
            if (html == null || html.isEmpty()) return "";
            if (JsoupExtractor.isCssRule(selector)) {
                return JsoupExtractor.extractSingle(html, selector, null);
            }
            if (selector.contains("&&")) {
                String[] se = selector.split("&&", 2);
                List<String> r = subContent(html, se[0], se[1]);
                return r.isEmpty() ? "" : cleanHtml(r.get(0));
            }
            return JsoupExtractor.extractSingle(html, "css:" + selector, null);
        } catch (Exception e) {
            SpiderDebug.log("getRV error: " + e.getMessage());
            return "";
        }
    }

    // ==================== CSS 简写语法（借鉴第18次升级版 parseCssShortSyntax） ====================

    /**
     * CSS 简写语法解析：p:tag->attr 或 p:tag->text
     * <p>
     * 转换规则：
     * <ul>
     *   <li>p:div-&gt;text → div（文本提取）</li>
     *   <li>p:a-&gt;href → a@href（属性提取，交由 JsoupExtractor 的 @attr 模式处理）</li>
     *   <li>p:img-&gt;data-src → img@data-src</li>
     * </ul>
     */
    public static String parseCssShortSyntax(String selector) {
        if (selector == null || !selector.startsWith("p:") || !selector.contains("->")) return selector;
        try {
            String cssExpr = selector.substring(2); // 去掉 "p:"
            String[] parts = cssExpr.split("->");
            String tagPart = parts[0].trim();
            String attrPart = parts.length > 1 ? parts[1].trim() : "";
            if (attrPart.isEmpty() || "text".equals(attrPart)) {
                return tagPart;
            }
            return tagPart + "@" + attrPart;
        } catch (Exception e) {
            return selector;
        }
    }
}
