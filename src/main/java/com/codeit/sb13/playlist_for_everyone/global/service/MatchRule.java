package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MatchRule {
    private final Pattern prefix;
    private final Pattern postfix;
    private final Pattern pattern;

    @Builder
    public MatchRule(
            Object prefix,
            Object postfix,
            Object pattern
    ){
        this.prefix = toPattern(prefix);
        this.postfix = toPattern(postfix);
        this.pattern = toPattern(pattern);
    }

    @Override
    public String toString(){
        return String.format("prefix: %s, postfix: %s, pattern: %s", prefix.pattern(), postfix.pattern(), pattern.pattern());
    }


    /**
     * target 에 해당 패턴이 존재하는지 확인하는 매서드
     * @param target 탐색 할 문자열
     * @return 문자열에 해당 패턴이 한개 이상 존재하면 true
     */
    public boolean check(String target){
        return !extract(target, prefix, pattern, postfix).isEmpty();
    }
    static public boolean check(String target, Object pattern){
        return check(target, null, pattern, null);
    }
    static public boolean check(String target, Object prefix, Object pattern){
        return check(target, prefix, pattern, null);
    }
    static public boolean check(String target, Object prefix, Object pattern, Object postfix){
        return !extract(target, prefix, pattern, postfix).isEmpty();
    }

    /**
     * target 존재하는 문자열을 추출하는 매서드.
     * 패턴은 String or Pattern 으로 주입할 수 있다.
     * 패턴이 정규표현식 형식 이 아닐 시, 해당 문자열이 정확하게 존재해야 하는 표현식으로 치환된다.
     * @param target 탐색 할 문자열
     * @return 문자열에 존재하는 패턴을 문자열 인덱스 순서대로 반환.
     */
    public List<String> extract(String target){
        return extract(target, prefix, pattern, postfix);
    }

    /**
     * target 존재하는 문자열을 추출하는 매서드.
     * 패턴은 String or Pattern 으로 주입할 수 있다.
     * 패턴이 정규표현식 형식 이 아닐 시, 해당 문자열이 정확하게 존재해야 하는 표현식으로 치환된다.
     * @param target 탐색 할 문자열
     * @param pattern 탐색 할 패턴
     * @return 문자열에 존재하는 패턴을 문자열 인덱스 순서대로 반환.
     */
    static public List<String> extract(String target, Object pattern){
        return extract(target, null, pattern, null);
    }

    /**
     * target 존재하는 문자열을 추출하는 매서드.
     * 패턴은 String or Pattern 으로 주입할 수 있다.
     * 패턴이 정규표현식 형식 이 아닐 시, 해당 문자열이 정확하게 존재해야 하는 표현식으로 치환된다.
     * @param target 탐색 할 문자열
     * @param prefix 탐색 할 패턴의 앞부분
     * @param pattern 탐색 할 패턴
     * @return 문자열에 존재하는 패턴을 문자열 인덱스 순서대로 반환.
     */
    static public List<String> extract(String target, Object prefix, Object pattern){
        return extract(target, prefix, pattern, null);
    }

    /**
     * target 존재하는 문자열을 추출하는 매서드.
     * 패턴은 String or Pattern 으로 주입할 수 있다.
     * 패턴이 정규표현식 형식 이 아닐 시, 해당 문자열이 정확하게 존재해야 하는 표현식으로 치환된다.
     * @param target 탐색 할 문자열
     * @param prefix 탐색 할 패턴의 앞부분
     * @param pattern 탐색 할 패턴
     * @param postfix 탐색 할 패턴의 뒷부분
     * @return 문자열에 존재하는 패턴을 문자열 인덱스 순서대로 반환.
     */
    static public List<String> extract(String target, Object prefix, Object pattern, Object postfix){
        Pattern pre = toPattern(prefix, true);
        Pattern pat = toPattern(pattern);
        Pattern post = toPattern(postfix, true);
        int start = start(target, pre);
        int end = end(target, post);

        String trimmed = target.substring(start, end);

        Matcher matcher = pat.matcher(trimmed);

        List<String> result = new ArrayList<>();
        while (matcher.find()){
            result.add(matcher.group());
        }
        return result;
    }

    static private Pattern toPattern(Object origin){ return toPattern(origin, false); }

    static private Pattern toPattern(Object origin, boolean quote){
        if (origin == null) return null;
        if (origin instanceof Pattern pattern) return pattern;
        if (!(origin instanceof String originString)) return null;
        if (originString.isBlank()) return null;
        try {
            if(quote) throw new PatternSyntaxException("quote", originString, 0);
            return Pattern.compile(originString);
        } catch (PatternSyntaxException e) {
            return Pattern.compile(Pattern.quote(originString));
        }
    }

    static private int start(String target, Pattern pre){
        int index = 0;
        if (pre != null) {
            Matcher matcher = pre.matcher(target);
            if (matcher.find())
                index = matcher.end();
        }
        return index;
    }

    static private int end(String target, Pattern post){
        int index = target.length();
        if (post != null) {
            Matcher matcher = post.matcher(target);
            while (matcher.find()) {
                index = matcher.start();
            }
        }
        return index;
    }
}
