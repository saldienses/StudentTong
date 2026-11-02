package com.studenttong.mobile;
import org.json.*;import java.util.*;
public final class LocationHistoryManager {
 final Store store;final String aid;public LocationHistoryManager(Store s,String id)throws Exception{s.account(id);store=s;aid=id;}
 public JSONArray list(String key){try{return new JSONArray(store.setting(aid,"location."+key,"[]"));}catch(Exception e){return new JSONArray();}}
 public void add(String key,JSONObject value)throws Exception{JSONObject item=new JSONObject(value.toString());if(!item.has("id"))item.put("id",UUID.randomUUID().toString());item.put("savedAt",System.currentTimeMillis());JSONArray before=list(key),out=new JSONArray().put(item);for(int i=0;i<before.length()&&out.length()<100;i++)if(!before.getJSONObject(i).optString("id").equals(item.optString("id")))out.put(before.get(i));store.setSetting(aid,"location."+key,out.toString());}
 public void delete(String key,String id)throws Exception{JSONArray old=list(key),out=new JSONArray();for(int i=0;i<old.length();i++)if(!old.getJSONObject(i).optString("id").equals(id))out.put(old.get(i));store.setSetting(aid,"location."+key,out.toString());}
}
