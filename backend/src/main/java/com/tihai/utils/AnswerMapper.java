package com.tihai.utils;
import java.io.IOException;
import java.util.*;
public final class AnswerMapper {
    private AnswerMapper(){}
    private static String option(String answer,List<String> options) throws IOException {
        String a=answer.trim();
        for(String o:options){String text=o.trim();if(text.isEmpty())continue;
            if((a.matches("(?i)[A-Z]")&&text.substring(0,1).equalsIgnoreCase(a))||text.equals(a)||text.replaceFirst("^[A-Za-z][.、．)）\\s]+","").equals(a))return text.substring(0,1).toUpperCase(Locale.ROOT);
        }
        throw new IOException("答案无法对应选项，本次小测未提交");
    }
    public static String map(String type,String raw,List<String> options) throws IOException {
        if(raw==null||raw.trim().isEmpty())throw new IOException("未获取可用答案，本次小测未提交");String a=raw.trim();
        switch(type){
            case "single":return option(a,options);
            case "multiple":
                String[] parts=a.matches("[A-Z]{2,}")?a.split(""):a.split("#+");SortedSet<String> labels=new TreeSet<>();for(String part:parts)if(!part.trim().isEmpty())labels.add(option(part,options));if(labels.isEmpty())throw new IOException("多选题答案为空，本次小测未提交");return String.join("",labels);
            case "judgement":
                if(Arrays.asList("正确","对","true","是","√").contains(a.toLowerCase(Locale.ROOT)))return "true";
                if(Arrays.asList("错误","错","false","否","×").contains(a.toLowerCase(Locale.ROOT)))return "false";
                throw new IOException("判断题答案不明确，本次小测未提交");
            case "fill":case "short":return a;
            default:throw new IOException("暂不支持该题型，本次小测未提交");
        }
    }
}
