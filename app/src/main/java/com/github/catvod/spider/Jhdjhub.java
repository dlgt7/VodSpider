package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聚合短剧爬虫
 * 聚合9个短剧平台:甜圈、七猫、锦鲤、番茄、星芽、西饭、软鸭、百度、围观
 */
public class Jhdjhub extends Spider {

    private static final String UA = "Mozilla/5.0 (Linux; Android 9; V2196A Build/PQ3A.190705.08211809; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Mobile Safari/537.36;webank/h5face;webank/1.0;netType:NETWORK_WIFI;appVersion:416;packageName:com.jp3.xg3";
    private static final String QM_KEY = "d3dGiJc651gSQ8w1";

    // 七猫平台字符映射表
    private static final Map<String, String> CHAR_MAP = new HashMap<String, String>() {{
        put("+", "P"); put("/", "X"); put("0", "M"); put("1", "U"); put("2", "l");
        put("3", "E"); put("4", "r"); put("5", "Y"); put("6", "W"); put("7", "b");
        put("8", "d"); put("9", "J"); put("A", "9"); put("B", "s"); put("C", "a");
        put("D", "I"); put("E", "0"); put("F", "o"); put("G", "y"); put("H", "_");
        put("I", "H"); put("J", "G"); put("K", "i"); put("L", "t"); put("M", "g");
        put("N", "N"); put("O", "A"); put("P", "8"); put("Q", "F"); put("R", "k");
        put("S", "3"); put("T", "h"); put("U", "f"); put("V", "R"); put("W", "q");
        put("X", "C"); put("Y", "4"); put("Z", "p"); put("a", "m"); put("b", "B");
        put("c", "O"); put("d", "u"); put("e", "c"); put("f", "6"); put("g", "K");
        put("h", "x"); put("i", "5"); put("j", "T"); put("k", "-"); put("l", "2");
        put("m", "z"); put("n", "S"); put("o", "Z"); put("p", "1"); put("q", "V");
        put("r", "v"); put("s", "j"); put("t", "Q"); put("u", "7"); put("v", "D");
        put("w", "w"); put("x", "n"); put("y", "L"); put("z", "e");
    }};

    // 平台配置
    private static final Map<String, Map<String, String>> PLATFORM_CONFIG = new HashMap<String, Map<String, String>>() {{
        put("百度", new HashMap<String, String>() {{
            put("host", "https://api.jkyai.top");
            put("url1", "/API/bddjss.php?name=fyclass&page=fypage");
            put("url2", "/API/bddjss.php?id=fyid");
            put("search", "/API/bddjss.php?name=**&page=fypage");
        }});
        put("锦鲤", new HashMap<String, String>() {{
            put("host", "https://api.jinlidj.com");
            put("search", "/api/search");
            put("url2", "/api/detail");
        }});
        put("番茄", new HashMap<String, String>() {{
            put("host", "https://reading.snssdk.com");
            put("url1", "/reading/bookapi/bookmall/cell/change/v");
            put("url2", "https://fqgo.52dns.cc/catalog");
            put("search", "https://fqgo.52dns.cc/search");
        }});
        put("星芽", new HashMap<String, String>() {{
            put("host", "https://app.whjzjx.cn");
            put("url1", "/cloud/v2/theater/home_page?theater_class_id");
            put("url2", "/v2/theater_parent/detail");
            put("search", "/v3/search");
            put("loginUrl", "https://u.shytkjgs.com/user/v1/account/login");
        }});
        put("西饭", new HashMap<String, String>() {{
            put("host", "https://xifan-api-cn.youlishipin.com");
            put("url1", "/xifan/drama/portalPage");
            put("url2", "/xifan/drama/getDuanjuInfo");
            put("search", "/xifan/search/getSearchList");
        }});
        put("软鸭", new HashMap<String, String>() {{
            put("host", "https://api.xingzhige.com");
            put("url1", "/API/playlet");
            put("search", "/API/playlet");
        }});
        put("七猫", new HashMap<String, String>() {{
            put("host", "https://api-store.qmplaylet.com");
            put("url1", "/api/v1/playlet/index");
            put("url2", "https://api-read.qmplaylet.com/player/api/v1/playlet/info");
            put("search", "/api/v1/playlet/search");
        }});
        put("围观", new HashMap<String, String>() {{
            put("host", "https://api.drama.9ddm.com");
            put("url1", "/drama/home/shortVideoTags");
            put("url2", "/drama/home/shortVideoDetail");
            put("search", "/drama/home/search");
        }});
        put("甜圈", new HashMap<String, String>() {{
            put("host", "https://mov.cenguigui.cn");
            put("url1", "/duanju/api.php?classname");
            put("url2", "/duanju/api.php?book_id");
            put("search", "/duanju/api.php?name");
        }});
    }};

    // 平台列表
    private static final List<Map<String, String>> PLATFORM_LIST = new ArrayList<Map<String, String>>() {{
        add(new HashMap<String, String>() {{ put("name", "甜圈短剧"); put("id", "甜圈"); }});
        add(new HashMap<String, String>() {{ put("name", "七猫短剧"); put("id", "七猫"); }});
        add(new HashMap<String, String>() {{ put("name", "锦鲤短剧"); put("id", "锦鲤"); }});
        add(new HashMap<String, String>() {{ put("name", "番茄短剧"); put("id", "番茄"); }});
        add(new HashMap<String, String>() {{ put("name", "星芽短剧"); put("id", "星芽"); }});
        add(new HashMap<String, String>() {{ put("name", "西饭短剧"); put("id", "西饭"); }});
        add(new HashMap<String, String>() {{ put("name", "软鸭短剧"); put("id", "软鸭"); }});
        add(new HashMap<String, String>() {{ put("name", "百度短剧"); put("id", "百度"); }});
        add(new HashMap<String, String>() {{ put("name", "围观短剧"); put("id", "围观"); }});
    }};

    // 默认筛选配置
    private static final Map<String, String> RULE_FILTER_DEF = new HashMap<String, String>() {{
        put("百度", "逆袭");
        put("锦鲤", "");
        put("番茄", "videoseries_hot");
        put("星芽", "1");
        put("西饭", "");
        put("软鸭", "战神");
        put("七猫", "0");
        put("围观", "");
        put("甜圈", "推荐榜");
    }};

    // 筛选选项配置(Base64编码)
    private static final String FILTER_OPTIONS_B64 = "eyLnlJzlnIgiOlt7ImtleSI6ImFyZWEiLCJuYW1lIjoi5Ymn5oOFIiwidmFsdWUiOlt7Im4iOiLlhajpg6giLCJ2IjoiIn0seyJuIjoi5o6o6I2Q5qacIiwidiI6IuaOqOiNkOamnCJ9LHsibiI6IueDreaSreamnCIsInYiOiLng63mkq3mppwifSx7Im4iOiLmlrDkuabmppwiLCJ2Ijoi5paw5Lmm5qacIn0seyJuIjoi5a6M57uT5qacIiwidiI6IuWujOe7k+amnCJ9LHsibiI6Iui/nui9veamnCIsInYiOiLov57ovb3mppwifSx7Im4iOiLlhY3otLnmppwiLCJ2Ijoi5YWN6LS55qacIn0seyJuIjoi5paw5YmnIiwidiI6IuaWsOWJpyJ9LHsibiI6IumAhuiirSIsInYiOiLpgIbooq0ifSx7Im4iOiLpnLjmgLsiLCJ2Ijoi6Zy45oC7In0seyJuIjoi546w5Luj6KiA5oOFIiwidiI6IueOsOS7o+iogOaDhSJ9LHsibiI6IuaJk+iEuOiZkOa4oyIsInYiOiLmiZPohLjomZDmuKMifSx7Im4iOiLosarpl6jmganmgKgiLCJ2Ijoi6LGq6Zeo5oGp5oCoIn0seyJuIjoi56We6LGqIiwidiI6IuelnuixqiJ9LHsibiI6IumprOeUsiIsInYiOiLpqaznlLIifSx7Im4iOiLpg73luILml6XluLgiLCJ2Ijoi6YO95biC5pel5bi4In0seyJuIjoi5oiY56We5b2S5p2lIiwidiI6IuaImOelnuW9kuadpSJ9LHsibiI6IuWwj+S6uueJqSIsInYiOiLlsI/kurrniakifSx7Im4iOiLlpbPmgKfmiJDplb8iLCJ2Ijoi5aWz5oCn5oiQ6ZW/In0seyJuIjoi5aSn5aWz5Li7IiwidiI6IuWkp+Wls+S4uyJ9LHsibiI6Iuepv+i2iiIsInYiOiLnqb/otooifSx7Im4iOiLpg73luILkv67ku5kiLCJ2Ijoi6YO95biC5L+u5LuZIn0seyJuIjoi5by66ICF5Zue5b2SIiwidiI6IuW8uuiAheWbnuW9kiJ9LHsibiI6IuS6suaDhSIsInYiOiLkurLmg4UifSx7Im4iOiLlj6Too4UiLCJ2Ijoi5Y+k6KOFIn0seyJuIjoi6YeN55SfIiwidiI6IumHjeeUnyJ9LHsibiI6IumXquWpmiIsInYiOiLpl6rlqZoifSx7Im4iOiLotZjlqb/pgIbooq0iLCJ2Ijoi6LWY5am/6YCG6KKtIn0seyJuIjoi6JmQ5oGLIiwidiI6IuiZkOaBiyJ9LHsibiI6Iui/veWmuyIsInYiOiLov73lprsifSx7Im4iOiLlpKnkuIvml6DmlYwiLCJ2Ijoi5aSp5LiL5peg5pWMIn0seyJuIjoi5a625bqt5Lym55CGIiwidiI6IuWutuW6reS8pueQhiJ9LHsibiI6IuiQjOWunSIsInYiOiLokIzlrp0ifSx7Im4iOiLlj6Tpo47mnYPosIsiLCJ2Ijoi5Y+k6aOO5p2D6LCLIn0seyJuIjoi6IGM5Zy6IiwidiI6IuiBjOWcuiJ9LHsibiI6IuWlh+W5u+iEkea0niIsInYiOiLlpYflubvohJHmtJ4ifSx7Im4iOiLlvILog70iLCJ2Ijoi5byC6IO9In0seyJuIjoi5peg5pWM56We5Yy7IiwidiI6IuaXoOaVjOelnuWMuyJ9LHsibiI6IuWPpOmjjuiogOaDhSIsInYiOiLlj6Tpo47oqIDmg4UifSx7Im4iOiLkvKDmib/op4nphpIiLCJ2Ijoi5Lyg5om/6KeJ6YaSIn0seyJuIjoi546w6KiA55Sc5a6gIiwidiI6IueOsOiogOeUnOWuoCJ9LHsibiI6IuWlh+W5u+eIseaDhSIsInYiOiLlpYflubvniLHmg4UifSx7Im4iOiLkuaHmnZEiLCJ2Ijoi5Lmh5p2RIn0seyJuIjoi5Y6G5Y+y5Y+k5LujIiwidiI6IuWOhuWPsuWPpOS7oyJ9LHsibiI6IueOi+WmgyIsInYiOiLnjovlpoMifSx7Im4iOiLpq5jmiYvkuIvlsbEiLCJ2Ijoi6auY5omL5LiL5bGxIn0seyJuIjoi5aix5LmQ5ZyIIiwidiI6IuWoseS5kOWciCJ9XX1dLCLplKbpsqQiOlt7ImtleSI6ImFyZWEiLCJuYW1lIjoi5YiG57G7IiwidmFsdWUiOlt7Im4iOiLlhajpg6giLCJ2IjoiIn0seyJuIjoi5oOF5oSf5YWz57O7IiwidiI6IjEifSx7Im4iOiLmiJDplb/pgIbooq0iLCJ2IjoiMiJ9LHsibiI6IuWlh+W5u+W8guiDvSIsInYiOiIzIn0seyJuIjoi5oiY5paX54Ot6KGAIiwidiI6IjQifSx7Im4iOiLkvKbnkIbnjrDlrp4iLCJ2IjoiNSJ9LHsibiI6IuaXtuepuuepv+i2iiIsInYiOiI2In0seyJuIjoi5p2D6LCL6Lqr5Lu9IiwidiI6IjcifV19XSwi55Wq6IyEIjpbeyJrZXkiOiJhcmVhIiwibmFtZSI6IuWIhuexuyIsInZhbHVlIjpbeyJuIjoi54Ot5YmnIiwidiI6InZpZGVvc2VyaWVzX2hvdCJ9LHsibiI6IuaWsOWJpyIsInYiOiJmaXJzdG9ubGluZXRpbWVfbmV3In0seyJuIjoi6YCG6KKtIiwidiI6ImNhdGVfNzM5In0seyJuIjoi5oC76KOBIiwidiI6ImNhdGVfMjkifSx7Im4iOiLnjrDoqIAiLCJ2IjoiY2F0ZV8zIn0seyJuIjoi5omT6IS4IiwidiI6ImNhdGVfMTA1MSJ9LHsibiI6IumprOeUsiIsInYiOiJjYXRlXzI2NiJ9LHsibiI6IuixqumXqCIsInYiOiJjYXRlXzEwNTMifSx7Im4iOiLpg73luIIiLCJ2IjoiY2F0ZV8yNjEifSx7Im4iOiLnpZ7osaoiLCJ2IjoiY2F0ZV8yMCJ9XX1dLCLmmJ/oir0iOlt7ImtleSI6ImFyZWEiLCJuYW1lIjoi5YiG57G7IiwidmFsdWUiOlt7Im4iOiLliaflnLoiLCJ2IjoiMSJ9LHsibiI6IueDreaSreWJpyIsInYiOiIyIn0seyJuIjoi5Lya5ZGY5LiT5LqrIiwidiI6IjgifSx7Im4iOiLmmJ/pgInlpb3liaciLCJ2IjoiNyJ9LHsibiI6IuaWsOWJpyIsInYiOiIzIn0seyJuIjoi6Ziz5YWJ5Ymn5Zy6IiwidiI6IjUifV19XSwi6KW/6aWtIjpbeyJrZXkiOiJhcmVhIiwibmFtZSI6IuWIhuexuyIsInZhbHVlIjpbeyJuIjoi5YWo6YOoIiwidiI6IiJ9LHsibiI6IumDveW4giIsInYiOiI2OEDpg73luIIifSx7Im4iOiLpnZLmmKUiLCJ2IjoiNjhA6Z2S5pilIn0seyJuIjoi546w5Luj6KiA5oOFIiwidiI6IjgxQOeOsOS7o+iogOaDhSJ9LHsibiI6IuixqumXqCIsInYiOiI4MUDosarpl6gifSx7Im4iOiLlpKflpbPkuLsiLCJ2IjoiODBA5aSn5aWz5Li7In0seyJuIjoi6YCG6KKtIiwidiI6Ijc5QOmAhuiirSJ9LHsibiI6IuaJk+iEuOiZkOa4oyIsInYiOiI3OUDmiZPohLjomZDmuKMifSx7Im4iOiLnqb/otooiLCJ2IjoiODFA56m/6LaKIn1dfV0sIui9r+m4rSI6W3sia2V5IjoiYXJlYSIsIm5hbWUiOiLliIbnsbsiLCJ2YWx1ZSI6W3sibiI6IuWFqOmDqCIsInYiOiIifSx7Im4iOiLmiJjnpZ4iLCJ2Ijoi5oiY56WeIn0seyJuIjoi6YCG6KKtIiwidiI6IumAhuiirSJ9LHsibiI6IumcuOaAuyIsInYiOiLpnLjmgLsifSx7Im4iOiLnpZ7osaoiLCJ2Ijoi56We6LGqIn0seyJuIjoi6YO95biCIiwidiI6IumDveW4giJ9LHsibiI6IueOhOW5uyIsInYiOiLnjoTlubsifSx7Im4iOiLoqIDmg4UiLCJ2Ijoi6KiA5oOFIn1dfV0sIuS4g+eMqyI6W3sia2V5IjoiYXJlYSIsIm5hbWUiOiLliIbnsbsiLCJ2YWx1ZSI6W3sibiI6IuWFqOmDqCIsInYiOiIifSx7Im4iOiLmjqjojZAiLCJ2IjoiMCJ9LHsibiI6IuaWsOWJpyIsInYiOiItMSJ9LHsibiI6IumDveW4guaDheaEnyIsInYiOiIxMjczIn0seyJuIjoi5Y+k6KOFIiwidiI6IjEyNzIifSx7Im4iOiLpg73luIIiLCJ2IjoiNTcxIn0seyJuIjoi546E5bm75LuZ5L6gIiwidiI6IjEyODYifSx7Im4iOiLlpYflubsiLCJ2IjoiNTcwIn0seyJuIjoi5Lmh5p2RIiwidiI6IjU5MCJ9LHsibiI6IuawkeWbvSIsInYiOiI1NzMifSx7Im4iOiLlubTku6MiLCJ2IjoiNTcyIn0seyJuIjoi6Z2S5pil5qCh5ZutIiwidiI6IjEyODgifSx7Im4iOiLmrabkvqAiLCJ2IjoiMzcxIn0seyJuIjoi56eR5bm7IiwidiI6IjU5NCJ9LHsibiI6Iuacq+S4liIsInYiOiI1NTYifSx7Im4iOiLkuozmrKHlhYMiLCJ2IjoiMTI4OSJ9LHsibiI6IumAhuiirSIsInYiOiI0MDAifSx7Im4iOiLnqb/otooiLCJ2IjoiMzczIn0seyJuIjoi5aSN5LuHIiwidiI6Ijc5NSJ9LHsibiI6Iuezu+e7nyIsInYiOiI3ODcifSx7Im4iOiLmnYPosIsiLCJ2IjoiNzkwIn0seyJuIjoi6YeN55SfIiwidiI6Ijc4NCJ9LHsibiI6IuWls+aAp+aIkOmVvyIsInYiOiIxMjk0In0seyJuIjoi5omT6IS46JmQ5rijIiwidiI6IjcxNiJ9LHsibiI6IumXquWpmiIsInYiOiI0ODAifSx7Im4iOiLlvLrogIXlm57lvZIiLCJ2IjoiNDAyIn0seyJuIjoi6L+95aa754Gr6JGs5Zy6IiwidiI6IjcxNSJ9LHsibiI6IuWutuW6rSIsInYiOiI2NzAifSx7Im4iOiLpqaznlLIiLCJ2IjoiNTU4In0seyJuIjoi6IGM5Zy6IiwidiI6IjcyNCJ9LHsibiI6IuWuq+aWlyIsInYiOiIzNDMifSx7Im4iOiLpq5jmiYvkuIvlsbEiLCJ2IjoiMTI5OSJ9LHsibiI6IuWoseS5kOaYjuaYnyIsInYiOiIxMjk1In0seyJuIjoi5byC6IO9IiwidiI6IjcyNyJ9LHsibiI6IuWuheaWlyIsInYiOiIzNDIifSx7Im4iOiLmm7/ouqsiLCJ2IjoiNzEyIn0seyJuIjoi56m/5LmmIiwidiI6IjMzOCJ9LHsibiI6IuWVhuaImCIsInYiOiI3MjMifSx7Im4iOiLnp43nlLDnu4/llYYiLCJ2IjoiMTI5MSJ9LHsibiI6IuS8pueQhiIsInYiOiIxMjkzIn0seyJuIjoi56S+5Lya6K+d6aKYIiwidiI6IjEyOTAifSx7Im4iOiLoh7Tlr4wiLCJ2IjoiNDkyIn0seyJuIjoi5YG35ZCs5b+D5aOwIiwidiI6IjEyNTgifSx7Im4iOiLohJHmtJ4iLCJ2IjoiNTI2In0seyJuIjoi6LGq6Zeo5oC76KOBIiwidiI6IjYyNCJ9LHsibiI6IuiQjOWunSIsInYiOiIzNTYifSx7Im4iOiLmiJjnpZ4iLCJ2IjoiNTI3In0seyJuIjoi55yf5YGH5Y2D6YeRIiwidiI6IjgxMiJ9LHsibiI6Iui1mOWpvyIsInYiOiIzNiJ9LHsibiI6IuelnuWMuyIsInYiOiIxMjY5In0seyJuIjoi56We6LGqIiwidiI6IjM3In0seyJuIjoi5bCP5Lq654mpIiwidiI6IjEyOTYifSx7Im4iOiLlm6LlrqAiLCJ2IjoiNTQ1In0seyJuIjoi5qyi5Zac5Yak5a62IiwidiI6IjQ2NCJ9LHsibiI6IuWls+W4nSIsInYiOiI2MTcifSx7Im4iOiLpk7blj5EiLCJ2IjoiMTI5NyJ9LHsibiI6IuWFteeOiyIsInYiOiIyOCJ9LHsibiI6IuiZkOaBiyIsInYiOiIxNiJ9LHsibiI6IueUnOWuoCIsInYiOiIyMSJ9LHsibiI6IuaCrOeWkSIsInYiOiIyNyJ9LHsibiI6IuaQnueskSIsInYiOiI3OTMifSx7Im4iOiLngbXlvIIiLCJ2IjoiMTI4NyJ9XX1dLCLnmb7luqYiOlt7ImtleSI6ImFyZWEiLCJuYW1lIjoi5YiG57G7IiwidmFsdWUiOlt7Im4iOiLpgIbooq0iLCJ2Ijoi6YCG6KKtIn0seyJuIjoi5oiY56WeIiwidiI6IuaImOelniJ9LHsibiI6IumDveW4giIsInYiOiLpg73luIIifSx7Im4iOiLnqb/otooiLCJ2Ijoi56m/6LaKIn0seyJuIjoi6YeN55SfIiwidiI6IumHjeeUnyJ9LHsibiI6IuWPpOijhSIsInYiOiLlj6Too4UifSx7Im4iOiLoqIDmg4UiLCJ2Ijoi6KiA5oOFIn0seyJuIjoi6JmQ5oGLIiwidiI6IuiZkOaBiyJ9LHsibiI6IueUnOWuoCIsInYiOiLnlJzlrqAifSx7Im4iOiLnpZ7ljLsiLCJ2Ijoi56We5Yy7In0seyJuIjoi6JCM5a6dIiwidiI6IuiQjOWunSJ9XX1dfQ==";

    private Map<String, String> xingyaHeaders = new HashMap<>();

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context);
        initXingyaHeaders();
    }

    /**
     * 初始化星芽平台Headers
     */
    private void initXingyaHeaders() {
        try {
            Map<String, String> plat = PLATFORM_CONFIG.get("星芽");
            String loginUrl = plat.get("loginUrl");
            
            Map<String, String> headers = new HashMap<String, String>() {{
                put("User-Agent", "okhttp/4.10.0");
                put("platform", "1");
                put("Content-Type", "application/json");
            }};
            
            JSONObject body = new JSONObject();
            body.put("device", "24250683a3bdb3f118dff25ba4b1cba1a");
            
            String response = OkHttp.post(loginUrl, body.toString(), headers);
            JSONObject json = new JSONObject(response);
            
            String token = "";
            if (json.has("data") && !json.isNull("data")) {
                JSONObject data = json.getJSONObject("data");
                token = data.optString("token", "");
            } else {
                token = json.optString("token", "");
            }
            
            if (!TextUtils.isEmpty(token)) {
                xingyaHeaders.put("authorization", token);
                xingyaHeaders.put("User-Agent", "okhttp/3.12.11");
                xingyaHeaders.put("content-type", "application/json; charset=utf-8");
            } else {
                xingyaHeaders.put("User-Agent", "okhttp/3.12.11");
                xingyaHeaders.put("content-type", "application/json; charset=utf-8");
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            xingyaHeaders.put("User-Agent", "okhttp/3.12.11");
            xingyaHeaders.put("content-type", "application/json; charset=utf-8");
        }
    }

    /**
     * 获取星芽平台Headers
     */
    private Map<String, String> getXingyaHeaders() {
        return xingyaHeaders.containsKey("authorization") ? xingyaHeaders : getDefaultHeaders();
    }

    /**
     * 获取默认Headers
     */
    private Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "okhttp/3.12.11");
        headers.put("content-type", "application/json; charset=utf-8");
        return headers;
    }

    /**
     * 生成七猫平台签名参数
     */
    private Map<String, String> getQmParamsAndSign() throws Exception {
        String sessionId = String.valueOf(System.currentTimeMillis());
        
        JSONObject data = new JSONObject();
        data.put("static_score", "0.8");
        data.put("uuid", "00000000-7fc7-08dc-0000-000000000000");
        data.put("device-id", "20250220125449b9b8cac84c2dd3d035c9052a2572f7dd0122edde3cc42a70");
        data.put("sourceuid", "aa7de295aad621a6");
        data.put("refresh-type", "0");
        data.put("model", "22021211RC");
        data.put("client-id", "aa7de295aad621a6");
        data.put("brand", "Redmi");
        data.put("sys-ver", "12");
        data.put("phone-level", "H");
        data.put("wlb-uid", "aa7de295aad621a6");
        data.put("session-id", sessionId);
        
        String jsonStr = data.toString();
        String base64Str = Util.base64Encode(jsonStr);
        
        StringBuilder qmParams = new StringBuilder();
        for (int i = 0; i < base64Str.length(); i++) {
            String c = String.valueOf(base64Str.charAt(i));
            qmParams.append(CHAR_MAP.getOrDefault(c, c));
        }
        
        String paramsStr = "AUTHORIZATION=app-version=10001application-id=com.duoduo.readchannel=unknownis-white=net-env=5platform=androidqm-params=" + qmParams.toString() + "reg=" + QM_KEY;
        String sign = Util.md5(paramsStr);
        
        Map<String, String> result = new HashMap<>();
        result.put("qmParams", qmParams.toString());
        result.put("sign", sign);
        return result;
    }

    /**
     * 获取七猫平台Headers
     */
    private Map<String, String> getHeaderX() throws Exception {
        Map<String, String> qmData = getQmParamsAndSign();
        Map<String, String> headers = new HashMap<>();
        headers.put("net-env", "5");
        headers.put("reg", "");
        headers.put("channel", "unknown");
        headers.put("is-white", "");
        headers.put("platform", "android");
        headers.put("application-id", "com.duoduo.read");
        headers.put("authorization", "");
        headers.put("app-version", "10001");
        headers.put("user-agent", "webviewversion/0");
        headers.put("qm-params", qmData.get("qmParams"));
        headers.put("sign", qmData.get("sign"));
        return headers;
    }

    /**
     * 解析筛选选项配置
     */
    private Map<String, Object> getFilterOptions() {
        try {
            String decoded = Util.base64Decode(FILTER_OPTIONS_B64);
            JSONObject json = new JSONObject(decoded);
            Map<String, Object> result = new HashMap<>();
            
            for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                result.put(key, json.get(key));
            }
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
            return new HashMap<>();
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (Map<String, String> platform : PLATFORM_LIST) {
            classes.add(new Class(platform.get("id"), platform.get("name")));
        }
        
        JSONObject filters = new JSONObject();
        if (filter) {
            Map<String, Object> filterOptions = getFilterOptions();
            for (Map<String, String> platform : PLATFORM_LIST) {
                String platformId = platform.get("id");
                if (filterOptions.containsKey(platformId)) {
                    filters.put(platformId, filterOptions.get(platformId));
                }
            }
        }
        
        return Result.string(classes, new ArrayList<>(), filters);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return recommend();
    }

    /**
     * 推荐内容
     */
    private String recommend() throws Exception {
        int randomIndex = (int) (Math.random() * PLATFORM_LIST.size());
        Map<String, String> randomPlat = PLATFORM_LIST.get(randomIndex);
        String platId = randomPlat.get("id");

        String area = RULE_FILTER_DEF.get(platId);
        if (TextUtils.isEmpty(area)) area = "";
        final String finalArea = area;

        String videos = categoryContent(platId, "1", false, new HashMap<String, String>() {{ put("area", finalArea); }});
        JSONObject json = new JSONObject(videos);
        JSONArray list = json.optJSONArray("list");
        
        JSONArray result = new JSONArray();
        if (list != null) {
            int limit = Math.min(list.length(), 10);
            for (int i = 0; i < limit; i++) {
                JSONObject v = list.getJSONObject(i);
                v.put("vod_content", randomPlat.get("name") + " | " + v.optString("vod_remarks", ""));
                result.put(v);
            }
        }
        
        JSONObject resultJson = new JSONObject();
        resultJson.put("list", result);
        return resultJson.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Util.toInt(pg, 1);
        List<Vod> videos = new ArrayList<>();
        
        Map<String, String> plat = PLATFORM_CONFIG.get(tid);
        if (plat == null) {
            return Result.get().page(page, page + 1, 0, 0).vod(videos).string();
        }
        
        String area = "";
        if (extend != null && extend.containsKey("area") && !TextUtils.isEmpty(extend.get("area"))) {
            area = extend.get("area");
        } else if (RULE_FILTER_DEF.containsKey(tid)) {
            area = RULE_FILTER_DEF.get(tid);
        }
        
        try {
            SpiderDebug.log("CategoryContent - Platform: " + tid + ", Area: " + area + ", Page: " + page);

            if ("七猫".equals(tid)) {
                String signStr = "operation=1playlet_privacy=1tag_id=" + area + QM_KEY;
                String sign = Util.md5(signStr);
                String url = plat.get("host") + plat.get("url1") + "?tag_id=" + area + "&playlet_privacy=1&operation=1&sign=" + sign;

                Map<String, String> headers = new HashMap<>();
                headers.putAll(getHeaderX());
                headers.putAll(getDefaultHeaders());

                SpiderDebug.log("七猫 URL: " + url);
                String response = OkHttp.string(url, headers);
                SpiderDebug.log("七猫 Response: " + response);
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                JSONArray list = data != null ? data.optJSONArray("list") : null;

                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.getJSONObject(i);
                        videos.add(new Vod(
                            "七猫@" + URLEncoder.encode(item.optString("playlet_id"), "UTF-8"),
                            item.optString("title"),
                            item.optString("image_link"),
                            item.optString("total_episode_num") + "集"
                        ));
                    }
                }
            } else if ("百度".equals(tid)) {
                String url = plat.get("host") + plat.get("url1").replace("fyclass", area).replace("fypage", String.valueOf(page));
                SpiderDebug.log("百度 URL: " + url);
                String response = OkHttp.string(url, getDefaultHeaders());
                SpiderDebug.log("百度 Response: " + response);
                JSONObject json = new JSONObject(response);
                JSONArray data = json.optJSONArray("data");

                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        videos.add(new Vod(
                            "百度@" + item.optString("id"),
                            item.optString("title"),
                            item.optString("cover"),
                            "更新至" + item.optInt("totalChapterNum") + "集"
                        ));
                    }
                }
            } else if ("锦鲤".equals(tid)) {
                JSONObject body = new JSONObject();
                body.put("page", page);
                body.put("limit", 24);
                body.put("type_id", area);
                body.put("keyword", "");

                String url = plat.get("host") + plat.get("search");
                SpiderDebug.log("锦鲤 URL: " + url + ", Body: " + body.toString());
                String response = OkHttp.post(url, body.toString(), getDefaultHeaders());
                SpiderDebug.log("锦鲤 Response: " + response);
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                JSONArray list = data != null ? data.optJSONArray("list") : null;

                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.getJSONObject(i);
                        videos.add(new Vod(
                            "锦鲤@" + item.optString("vod_id"),
                            item.optString("vod_name"),
                            item.optString("vod_pic"),
                            item.optInt("vod_total") + "集"
                        ));
                    }
                }
            } else if ("番茄".equals(tid)) {
                String sessionId = new java.text.SimpleDateFormat("yyyyMMddHHmm").format(new java.util.Date());
                String url = plat.get("host") + plat.get("url1") + "?change_type=0&selected_items=" + area + "&tab_type=8&cell_id=6952850996422770718&version_tag=video_feed_refactor&device_id=1423244030195267&aid=1967&app_name=novelapp&ssmix=a&session_id=" + sessionId;

                if (page > 1) {
                    url += "&offset=" + ((page - 1) * 12);
                }

                SpiderDebug.log("番茄 URL: " + url);
                String response = OkHttp.string(url, getDefaultHeaders());
                SpiderDebug.log("番茄 Response: " + response);
                JSONObject json = new JSONObject(response);

                // 解析数据结构: res.data.cell_view.cell_data 或 res.data (数组)
                JSONArray items = null;
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    JSONObject cellView = data.optJSONObject("cell_view");
                    if (cellView != null) {
                        items = cellView.optJSONArray("cell_data");
                    }
                }
                if (items == null) {
                    items = json.optJSONArray("data");
                }

                SpiderDebug.log("番茄 Items count: " + (items != null ? items.length() : 0));
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject v = item.optJSONObject("video_data");
                        if (v == null || v.length() == 0) {
                            v = item;
                        }
                        videos.add(new Vod(
                            "番茄@" + (v.has("series_id") ? v.optString("series_id") : v.optString("book_id", "")),
                            v.optString("title"),
                            v.optString("cover", v.optString("horiz_cover", "")),
                            v.optString("sub_title", "")
                        ));
                    }
                }
            } else if ("星芽".equals(tid)) {
                String url = plat.get("host") + plat.get("url1") + "=" + area + "&type=1&class2_ids=0&page_num=" + page + "&page_size=24";
                String response = OkHttp.string(url, getXingyaHeaders());
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                JSONArray list = data != null ? data.optJSONArray("list") : null;

                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.getJSONObject(i);
                        JSONObject theater = item.optJSONObject("theater");
                        if (theater != null) {
                            videos.add(new Vod(
                                "星芽@" + plat.get("host") + plat.get("url2") + "?theater_parent_id=" + theater.optString("id"),
                                theater.optString("title"),
                                theater.optString("cover_url"),
                                theater.optInt("total") + "集"
                            ));
                        }
                    }
                }
            } else if ("西饭".equals(tid)) {
                String[] areaParts = area.split("@");
                String typeId = areaParts[0];
                String typeName = areaParts.length > 1 ? areaParts[1] : "";

                long ts = System.currentTimeMillis() / 1000;
                String url = plat.get("host") + plat.get("url1") + "?reqType=aggregationPage&offset=" + ((page - 1) * 30) + "&categoryId=" + typeId + "&quickEngineVersion=-1&scene=&categoryNames=" + URLEncoder.encode(typeName, "UTF-8") + "&categoryVersion=1&density=1.5&pageID=page_theater&version=2001001&androidVersionCode=28&requestId=" + ts + "aa498144140ef297&appId=drama&teenMode=false&userBaseMode=false&session=eyJpbmZvIjp7InVpZCI6IiIsInJ0IjoiMTc0MDY1ODI5NCIsInVuIjoiT1BHXzFlZGQ5OTZhNjQ3ZTQ1MjU4Nzc1MTE2YzFkNzViN2QwIiwiZnQiOiIxNzQwNjU4Mjk0In19&feedssession=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1dHlwIjowLCJidWlkIjoxNjMzOTY4MTI2MTQ4NjQxNTM2LCJhdWQiOiJkcmFtYSIsInZlciI6MiwicmF0IjoxNzQwNjU4Mjk0LCJ1bm0iOiJPUEdfMWVkZDk5NmE2NDdlNDUyNTg3NzUxMTZjMWQ3NWI3ZDAiLCJpZCI6IjNiMzViZmYzYWE0OTgxNDQxNDBlZjI5N2JkMDY5NGNhIiwiZXhwIjoxNzQxMjYzMDk0LCJkYyI6Imd6cXkifQ.JS3QY6ER0P2cQSxAE_OGKSMIWNAMsYUZ3mJTnEpf-Rc";

                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                JSONObject result = json.optJSONObject("result");
                JSONArray elements = result != null ? result.optJSONArray("elements") : null;

                if (elements != null) {
                    for (int i = 0; i < elements.length(); i++) {
                        JSONObject s = elements.getJSONObject(i);
                        JSONArray contents = s.optJSONArray("contents");
                        if (contents != null) {
                            for (int j = 0; j < contents.length(); j++) {
                                JSONObject v = contents.getJSONObject(j);
                                JSONObject d = v.optJSONObject("duanjuVo");
                                if (d != null) {
                                    videos.add(new Vod(
                                        "西饭@" + d.optString("duanjuId") + "#" + d.optString("source"),
                                        d.optString("title"),
                                        d.optString("coverImageUrl"),
                                        d.optInt("total") + "集"
                                    ));
                                }
                            }
                        }
                    }
                }
            } else if ("软鸭".equals(tid)) {
                String url = plat.get("host") + plat.get("url1") + "/?keyword=" + URLEncoder.encode(area, "UTF-8") + "&page=" + page;
                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                JSONArray data = json.optJSONArray("data");
                
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        String purl = item.optString("title") + "@" + item.optString("cover") + "@" + item.optString("author") + "@" + item.optString("type") + "@" + item.optString("desc") + "@" + item.optString("book_id");
                        videos.add(new Vod(
                            "软鸭@" + URLEncoder.encode(purl, "UTF-8"),
                            item.optString("title"),
                            item.optString("cover"),
                            item.optString("type")
                        ));
                    }
                }
            } else if ("围观".equals(tid)) {
                JSONObject body = new JSONObject();
                body.put("audience", "全部受众");
                body.put("page", page);
                body.put("pageSize", 30);
                body.put("searchWord", "");
                body.put("subject", "全部主题");

                String url = plat.get("host") + plat.get("search");
                SpiderDebug.log("围观 URL: " + url + ", Body: " + body.toString());
                String response = OkHttp.post(url, body.toString(), getDefaultHeaders());
                SpiderDebug.log("围观 Response: " + response);
                JSONObject json = new JSONObject(response);
                JSONArray data = json.optJSONArray("data");

                SpiderDebug.log("围观 Items count: " + (data != null ? data.length() : 0));
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        videos.add(new Vod(
                            "围观@" + item.optString("oneId"),
                            item.optString("title"),
                            item.optString("vertPoster"),
                            "集数:" + item.optInt("episodeCount") + " 播放:" + item.optInt("viewCount")
                        ));
                    }
                }
            } else if ("甜圈".equals(tid)) {
                String url = plat.get("host") + plat.get("url1") + "=" + URLEncoder.encode(area, "UTF-8") + "&offset=" + page;
                SpiderDebug.log("甜圈 URL: " + url);
                String response = OkHttp.string(url, getDefaultHeaders());
                SpiderDebug.log("甜圈 Response: " + response);
                JSONObject json = new JSONObject(response);
                JSONArray data = json.optJSONArray("data");

                SpiderDebug.log("甜圈 Items count: " + (data != null ? data.length() : 0));
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        videos.add(new Vod(
                            "甜圈@" + item.optString("book_id"),
                            item.optString("title"),
                            item.optString("cover"),
                            item.optString("sub_title", "")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        return Result.get().page(page, page + 1, videos.size(), videos.size() * (page + 1)).vod(videos).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String[] parts = id.split("@");
        String platId = parts[0];
        String did = parts.length > 1 ? parts[1] : "";
        
        Map<String, String> plat = PLATFORM_CONFIG.get(platId);
        if (plat == null) {
            Vod vod = new Vod(id, "平台不支持", "", "");
            return Result.string(vod);
        }
        
        String vodName = "未知";
        String vodPic = "";
        String vodRemarks = "";
        String vodContent = "";
        String vodPlayFrom = "";
        String vodPlayUrl = "";
        
        try {
            if ("七猫".equals(platId)) {
                String didDecoded = java.net.URLDecoder.decode(did, "UTF-8");
                String signStr = "playlet_id=" + didDecoded + QM_KEY;
                String sign = Util.md5(signStr);
                String url = plat.get("url2") + "?playlet_id=" + didDecoded + "&sign=" + sign;
                
                Map<String, String> headers = new HashMap<>();
                headers.putAll(getHeaderX());
                headers.putAll(getDefaultHeaders());
                
                String response = OkHttp.string(url, headers);
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                
                if (data != null) {
                    vodName = data.optString("title");
                    vodPic = data.optString("image_link");
                    vodRemarks = data.optInt("total_episode_num") + "集";
                    vodContent = data.optString("intro");
                    vodPlayFrom = "七猫短剧";
                    
                    JSONArray playList = data.optJSONArray("play_list");
                    StringBuilder urls = new StringBuilder();
                    if (playList != null) {
                        for (int i = 0; i < playList.length(); i++) {
                            JSONObject item = playList.getJSONObject(i);
                            if (urls.length() > 0) urls.append("#");
                            urls.append(item.optInt("sort")).append("$").append(item.optString("video_url"));
                        }
                    }
                    vodPlayUrl = urls.toString();
                }
            } else if ("百度".equals(platId)) {
                String url = plat.get("host") + plat.get("url2").replace("fyid", did);
                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                
                vodName = json.optString("title");
                JSONArray data = json.optJSONArray("data");
                if (data != null && data.length() > 0) {
                    vodPic = data.getJSONObject(0).optString("cover");
                }
                vodRemarks = "更新至:" + json.optInt("total") + "集";
                vodPlayFrom = "百度短剧";
                
                StringBuilder urls = new StringBuilder();
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        if (urls.length() > 0) urls.append("#");
                        urls.append(item.optString("title")).append("$").append(item.optString("video_id"));
                    }
                }
                vodPlayUrl = urls.toString();
            } else if ("锦鲤".equals(platId)) {
                String url = plat.get("host") + plat.get("url2") + "/" + did;
                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                
                if (data != null) {
                    vodName = data.optString("vod_name");
                    vodPic = data.optString("vod_pic");
                    vodRemarks = data.optString("vod_remarks");
                    vodPlayFrom = "锦鲤短剧";
                    
                    JSONObject player = data.optJSONObject("player");
                    StringBuilder urls = new StringBuilder();
                    if (player != null) {
                        for (java.util.Iterator<String> it = player.keys(); it.hasNext(); ) {
                            String key = it.next();
                            if (urls.length() > 0) urls.append("#");
                            urls.append(key).append("$").append(player.optString(key));
                        }
                    }
                    vodPlayUrl = urls.toString();
                }
            } else if ("番茄".equals(platId)) {
                String url = plat.get("url2") + "?book_id=" + did;
                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                
                if (data != null) {
                    JSONObject bookInfo = data.optJSONObject("book_info");
                    JSONArray itemList = data.optJSONArray("item_data_list");
                    
                    if (bookInfo != null) {
                        vodName = bookInfo.optString("book_name");
                        vodPic = bookInfo.optString("thumb_url");
                    }
                    vodRemarks = "更新至" + (itemList != null ? itemList.length() : 0) + "集";
                    vodPlayFrom = "番茄短剧";
                    
                    StringBuilder urls = new StringBuilder();
                    if (itemList != null) {
                        for (int i = 0; i < itemList.length(); i++) {
                            JSONObject item = itemList.getJSONObject(i);
                            if (urls.length() > 0) urls.append("#");
                            urls.append(item.optString("title")).append("$").append(item.optString("item_id"));
                        }
                    }
                    vodPlayUrl = urls.toString();
                }
            } else if ("星芽".equals(platId)) {
                String response = OkHttp.string(did, getXingyaHeaders());
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                
                if (data != null) {
                    vodName = data.optString("title");
                    vodPic = data.optString("cover_url");
                    vodRemarks = data.optString("desc_tags");
                    vodPlayFrom = "星芽短剧";
                    
                    JSONArray theaters = data.optJSONArray("theaters");
                    StringBuilder urls = new StringBuilder();
                    if (theaters != null) {
                        for (int i = 0; i < theaters.length(); i++) {
                            JSONObject item = theaters.getJSONObject(i);
                            if (urls.length() > 0) urls.append("#");
                            urls.append(item.optInt("num")).append("$").append(item.optString("son_video_url"));
                        }
                    }
                    vodPlayUrl = urls.toString();
                }
            } else if ("西饭".equals(platId)) {
                String[] didParts = did.split("#");
                String duanjuId = didParts[0];
                String source = didParts.length > 1 ? didParts[1] : "";
                
                String url = plat.get("host") + plat.get("url2") + "?duanjuId=" + duanjuId + "&source=" + source;
                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                JSONObject result = json.optJSONObject("result");
                
                if (result != null) {
                    vodName = result.optString("title");
                    vodPic = result.optString("coverImageUrl");
                    vodRemarks = result.optString("updateStatus").equals("over") ? result.optInt("total") + "集 已完结" : "更新" + result.optInt("total") + "集";
                    vodPlayFrom = "西饭短剧";
                    
                    JSONArray episodeList = result.optJSONArray("episodeList");
                    StringBuilder urls = new StringBuilder();
                    if (episodeList != null) {
                        for (int i = 0; i < episodeList.length(); i++) {
                            JSONObject item = episodeList.getJSONObject(i);
                            if (urls.length() > 0) urls.append("#");
                            urls.append(item.optInt("index")).append("$").append(item.optString("playUrl"));
                        }
                    }
                    vodPlayUrl = urls.toString();
                }
            } else if ("甜圈".equals(platId)) {
                String url = plat.get("host") + plat.get("url2") + "=" + did;
                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                
                vodName = json.optString("book_name");
                vodPic = json.optString("book_pic");
                vodRemarks = json.optString("duration", "");
                vodContent = json.optString("desc", "");
                vodPlayFrom = "甜圈短剧";
                
                JSONArray data = json.optJSONArray("data");
                StringBuilder urls = new StringBuilder();
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        if (urls.length() > 0) urls.append("#");
                        urls.append(item.optString("title")).append("$").append(item.optString("video_id"));
                    }
                }
                vodPlayUrl = urls.toString();
            } else if ("软鸭".equals(platId)) {
                String didDecoded = java.net.URLDecoder.decode(did, "UTF-8");
                String[] parts2 = didDecoded.split("@");
                String title = parts2.length > 0 ? parts2[0] : "";
                String img = parts2.length > 1 ? parts2[1] : "";
                String type = parts2.length > 3 ? parts2[3] : "";
                String desc = parts2.length > 4 ? parts2[4] : "";
                String bookId = parts2.length > 5 ? parts2[5] : "";
                
                String detailUrl = plat.get("host") + plat.get("url1") + "/?book_id=" + bookId;
                String response = OkHttp.string(detailUrl, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                
                vodName = title;
                vodPic = img;
                vodRemarks = type;
                vodContent = desc;
                vodPlayFrom = "软鸭短剧";
                
                JSONArray videoList = json.optJSONObject("data").optJSONArray("video_list");
                StringBuilder urls = new StringBuilder();
                if (videoList != null) {
                    for (int i = 0; i < videoList.length(); i++) {
                        JSONObject item = videoList.getJSONObject(i);
                        if (urls.length() > 0) urls.append("#");
                        urls.append(item.optString("title")).append("$").append(item.optString("video_id"));
                    }
                }
                vodPlayUrl = urls.toString();
            } else if ("围观".equals(platId)) {
                String url = plat.get("host") + plat.get("url2") + "?oneId=" + did + "&page=1&pageSize=1000";
                String response = OkHttp.string(url, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                JSONArray data = json.optJSONArray("data");
                
                if (data != null && data.length() > 0) {
                    JSONObject first = data.getJSONObject(0);
                    vodName = first.optString("title");
                    vodPic = first.optString("vertPoster");
                    vodRemarks = "共" + data.length() + "集";
                    vodPlayFrom = "围观短剧";
                    
                    StringBuilder urls = new StringBuilder();
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        if (urls.length() > 0) urls.append("#");
                        urls.append(item.optString("title")).append("第").append(item.optInt("playOrder")).append("集$").append(item.optString("playSetting"));
                    }
                    vodPlayUrl = urls.toString();
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
            vodName = "加载失败";
        }
        
        Vod vod = new Vod(id, vodName, vodPic, vodRemarks);
        vod.setVodContent(vodContent);
        vod.setVodPlayFrom(vodPlayFrom);
        vod.setVodPlayUrl(vodPlayUrl);
        
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = Util.toInt(pg, 1);
        List<Vod> videos = new ArrayList<>();
        
        // 七猫搜索
        try {
            String signStr = "operation=2playlet_privacy=1search_word=" + key + QM_KEY;
            String sign = Util.md5(signStr);
            String url = PLATFORM_CONFIG.get("七猫").get("host") + PLATFORM_CONFIG.get("七猫").get("search") + "?search_word=" + URLEncoder.encode(key, "UTF-8") + "&playlet_privacy=1&operation=2&sign=" + sign;
            
            Map<String, String> headers = new HashMap<>();
            headers.putAll(getHeaderX());
            headers.putAll(getDefaultHeaders());
            
            String response = OkHttp.string(url, headers);
            JSONObject json = new JSONObject(response);
            JSONArray list = json.optJSONObject("data").optJSONArray("list");
            
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    if (item.optString("title").toLowerCase().contains(key.toLowerCase())) {
                        videos.add(new Vod(
                            "七猫@" + URLEncoder.encode(item.optString("playlet_id"), "UTF-8"),
                            item.optString("title"),
                            item.optString("image_link"),
                            "七猫短剧｜" + item.optInt("total_episode_num") + "集"
                        ));
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        
        // 其他平台搜索
        String[] platforms = {"百度", "锦鲤", "番茄", "星芽", "西饭", "软鸭", "围观", "甜圈"};
        for (String tid : platforms) {
            try {
                Map<String, String> plat = PLATFORM_CONFIG.get(tid);
                JSONArray data = null;
                
                if ("百度".equals(tid)) {
                    String url = plat.get("host") + plat.get("search").replace("**", URLEncoder.encode(key, "UTF-8")).replace("fypage", String.valueOf(page));
                    String response = OkHttp.string(url, getDefaultHeaders());
                    data = new JSONObject(response).optJSONArray("data");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            videos.add(new Vod(
                                "百度@" + item.optString("id"),
                                item.optString("title"),
                                item.optString("cover"),
                                "百度短剧｜更新至" + item.optInt("totalChapterNum") + "集"
                            ));
                        }
                    }
                } else if ("锦鲤".equals(tid)) {
                    JSONObject body = new JSONObject();
                    body.put("page", page);
                    body.put("limit", 30);
                    body.put("keyword", key);
                    
                    String response = OkHttp.post(plat.get("host") + plat.get("search"), body.toString(), getDefaultHeaders());
                    data = new JSONObject(response).optJSONObject("data").optJSONArray("list");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            videos.add(new Vod(
                                "锦鲤@" + item.optString("vod_id"),
                                item.optString("vod_name"),
                                item.optString("vod_pic"),
                                "锦鲤短剧｜" + item.optInt("vod_total") + "集"
                            ));
                        }
                    }
                } else if ("番茄".equals(tid)) {
                    String url = plat.get("search") + "?keyword=" + URLEncoder.encode(key, "UTF-8") + "&page=" + page;
                    String response = OkHttp.string(url, getDefaultHeaders());
                    data = new JSONObject(response).optJSONArray("data");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            videos.add(new Vod(
                                "番茄@" + item.optString("series_id"),
                                item.optString("title"),
                                item.optString("cover"),
                                "番茄短剧｜" + item.optString("sub_title")
                            ));
                        }
                    }
                } else if ("星芽".equals(tid)) {
                    JSONObject body = new JSONObject();
                    body.put("text", key);
                    
                    String response = OkHttp.post(plat.get("host") + plat.get("search"), body.toString(), getXingyaHeaders());
                    data = new JSONObject(response).optJSONObject("data").optJSONObject("theater").optJSONArray("search_data");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            videos.add(new Vod(
                                "星芽@" + plat.get("host") + plat.get("url2") + "?theater_parent_id=" + item.optString("id"),
                                item.optString("title"),
                                item.optString("cover_url"),
                                "星芽短剧｜" + item.optInt("total") + "集 播放:" + item.optString("play_amount_str")
                            ));
                        }
                    }
                } else if ("西饭".equals(tid)) {
                    long ts = System.currentTimeMillis() / 1000;
                    String url = plat.get("host") + plat.get("search") + "?reqType=search&offset=" + ((page - 1) * 30) + "&keyword=" + URLEncoder.encode(key, "UTF-8") + "&quickEngineVersion=-1&scene=";
                    String response = OkHttp.string(url, getDefaultHeaders());
                    data = new JSONObject(response).optJSONObject("result").optJSONArray("elements");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject vod = data.getJSONObject(i);
                            JSONObject dj = vod.optJSONObject("duanjuVo");
                            if (dj != null) {
                                videos.add(new Vod(
                                    "西饭@" + dj.optString("duanjuId") + "#" + dj.optString("source"),
                                    dj.optString("title"),
                                    dj.optString("coverImageUrl"),
                                    "西饭短剧｜" + dj.optInt("total") + "集"
                                ));
                            }
                        }
                    }
                } else if ("软鸭".equals(tid)) {
                    String url = plat.get("host") + plat.get("search") + "/?keyword=" + URLEncoder.encode(key, "UTF-8") + "&page=" + page;
                    String response = OkHttp.string(url, getDefaultHeaders());
                    data = new JSONObject(response).optJSONArray("data");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            String purl = item.optString("title") + "@" + item.optString("cover") + "@" + item.optString("author") + "@" + item.optString("type") + "@" + item.optString("desc") + "@" + item.optString("book_id");
                            videos.add(new Vod(
                                "软鸭@" + URLEncoder.encode(purl, "UTF-8"),
                                item.optString("title"),
                                item.optString("cover"),
                                "软鸭短剧｜" + item.optString("type")
                            ));
                        }
                    }
                } else if ("围观".equals(tid)) {
                    JSONObject body = new JSONObject();
                    body.put("audience", "");
                    body.put("page", page);
                    body.put("pageSize", 30);
                    body.put("searchWord", key);
                    body.put("subject", "");
                    
                    String response = OkHttp.post(plat.get("host") + plat.get("search"), body.toString(), getDefaultHeaders());
                    data = new JSONObject(response).optJSONArray("data");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            videos.add(new Vod(
                                "围观@" + item.optString("oneId"),
                                item.optString("title"),
                                item.optString("vertPoster"),
                                "围观短剧｜集数:" + item.optInt("episodeCount") + " 播放:" + item.optInt("viewCount")
                            ));
                        }
                    }
                } else if ("甜圈".equals(tid)) {
                    String url = plat.get("host") + plat.get("search") + "=" + URLEncoder.encode(key, "UTF-8") + "&offset=" + page;
                    String response = OkHttp.string(url, getDefaultHeaders());
                    data = new JSONObject(response).optJSONArray("data");
                    
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            videos.add(new Vod(
                                "甜圈@" + item.optString("book_id"),
                                item.optString("title"),
                                item.optString("cover"),
                                "甜圈短剧｜" + item.optString("sub_title", "无简介")
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        
        return Result.get().page(page, page + 1, videos.size(), videos.size() * (page + 1)).vod(videos).string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (flag.contains("七猫")) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", id);
            return result.toString();
        }
        
        if (flag.contains("百度")) {
            String response = OkHttp.string("https://api.jkyai.top/API/bddjss.php?video_id=" + id, getDefaultHeaders());
            JSONObject json = new JSONObject(response);
            JSONObject data = json.optJSONObject("data");
            
            if (data != null) {
                JSONArray qualities = data.optJSONArray("qualities");
                if (qualities != null) {
                    String[] order = {"1080p", "sc", "sd"};
                    Map<String, String> qMap = new HashMap<String, String>() {{
                        put("1080p", "蓝光");
                        put("sc", "超清");
                        put("sd", "标清");
                    }};
                    
                    List<String> urls = new ArrayList<>();
                    for (String k : order) {
                        for (int i = 0; i < qualities.length(); i++) {
                            JSONObject q = qualities.getJSONObject(i);
                            if (k.equals(q.optString("quality"))) {
                                urls.add(qMap.get(k));
                                urls.add(q.optString("download_url"));
                                break;
                            }
                        }
                    }
                    
                    JSONObject result = new JSONObject();
                    result.put("parse", 0);
                    result.put("url", new JSONArray(urls));
                    return result.toString();
                }
            }
        }
        
        if (flag.contains("甜圈")) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "https://mov.cenguigui.cn/duanju/api.php?video_id=" + id + "&type=mp4");
            return result.toString();
        }
        
        if (flag.contains("锦鲤")) {
            try {
                Map<String, String> headers = new HashMap<>();
                headers.put("referer", "https://www.jinlidj.com/");
                
                String html = OkHttp.string(id + "&auto=1", headers);
                Matcher matcher = Pattern.compile("let data\\s*=\\s*({[^;]*});").matcher(html);
                if (matcher.find()) {
                    JSONObject data = new JSONObject(matcher.group(1));
                    if (data.has("url")) {
                        JSONObject result = new JSONObject();
                        result.put("parse", 0);
                        result.put("url", data.optString("url"));
                        return result.toString();
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        
        if (flag.contains("番茄")) {
            try {
                String response = OkHttp.string("https://fqgo.52dns.cc/video?item_ids=" + id, getDefaultHeaders());
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                
                if (data != null) {
                    JSONObject idData = data.optJSONObject(id);
                    if (idData != null) {
                        String videoModel = idData.optString("video_model");
                        JSONObject model = new JSONObject(videoModel);
                        JSONObject videoList = model.optJSONObject("video_list");
                        if (videoList != null) {
                            JSONObject video1 = videoList.optJSONObject("video_1");
                            if (video1 != null) {
                                String mainUrl = video1.optString("main_url");
                                if (!TextUtils.isEmpty(mainUrl)) {
                                    JSONObject result = new JSONObject();
                                    result.put("parse", 0);
                                    result.put("url", Util.base64Decode(mainUrl));
                                    return result.toString();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log(e);
            }
        }
        
        if (flag.contains("软鸭")) {
            String response = OkHttp.string(PLATFORM_CONFIG.get("软鸭").get("host") + "/API/playlet/?video_id=" + id + "&quality=1080p", getDefaultHeaders());
            JSONObject json = new JSONObject(response);
            
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", json.optJSONObject("data").optJSONObject("video").optString("url", ""));
            return result.toString();
        }
        
        if (flag.contains("围观")) {
            try {
                JSONObject ps = new JSONObject(id);
                List<String> urls = new ArrayList<>();
                
                if (ps.has("super")) {
                    urls.add("超清");
                    urls.add(ps.optString("super"));
                }
                if (ps.has("high")) {
                    urls.add("高清");
                    urls.add(ps.optString("high"));
                }
                if (ps.has("normal")) {
                    urls.add("流畅");
                    urls.add(ps.optString("normal"));
                }
                
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", new JSONArray(urls));
                return result.toString();
            } catch (Exception e) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", id);
                return result.toString();
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", id);
        return result.toString();
    }
}