package com.tihai.utils;
import org.springframework.jdbc.core.JdbcTemplate;import java.util.*;
/** Immutable request scope. Workers keep their explicit account; UI selection never mutates a running worker. */
public final class AccountScope {
 private static final ThreadLocal<Map<String,Object>> CURRENT=new ThreadLocal<>();
 public static void bind(Map<String,Object> a){CURRENT.set(new LinkedHashMap<>(a));}public static void clear(){CURRENT.remove();}
 public static boolean scoped(){return CURRENT.get()!=null;}public static String id(){return value("id");}public static String platform(){return value("platform");}public static String account(){return value("account");}
 private static String value(String k){return CURRENT.get()==null?"":String.valueOf(CURRENT.get().getOrDefault(k,""));}
 public static String cacheKey(){return scoped()?platform()+":"+id():"background";}
 public static boolean allows(String a){return !scoped()||("chaoxing".equals(platform())&&account().equals(a));}
 public static void require(String a){if(!allows(a))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"任务不属于当前账号");}
 public static List<Map<String,Object>> accounts(JdbcTemplate db){return scoped()?db.queryForList("SELECT DISTINCT login_account FROM th_wk_queue WHERE login_account=?", "chaoxing".equals(platform())?account():"__no_account__"):db.queryForList("SELECT DISTINCT login_account FROM th_wk_queue");}
}
