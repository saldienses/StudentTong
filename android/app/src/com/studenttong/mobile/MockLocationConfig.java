package com.studenttong.mobile;
import org.json.*;
public final class MockLocationConfig {
 public final PreviewLocation point;public final long interval;public final double altitude;public final float speed,bearing;public final String aid,reason;public final JSONObject before;
 public MockLocationConfig(JSONObject j)throws Exception{point=PreviewLocation.parse(j.getJSONObject("point"));aid=j.getString("aid");if(!aid.matches("[a-f0-9-]{36}"))throw new Exception("账号无效");interval=j.optLong("interval",2000);altitude=j.optDouble("altitude",0);speed=(float)j.optDouble("speed",0);bearing=(float)j.optDouble("bearing",0);reason=j.optString("reason");before=j.optJSONObject("before");if(interval<1000||interval>60000||!Double.isFinite(altitude)||Math.abs(altitude)>20000||!Float.isFinite(speed)||speed<0||speed>100||!Float.isFinite(bearing)||bearing<0||bearing>=360||reason.isEmpty())throw new Exception("参数无效：间隔1至60秒、速度0至100m/s、方向0至360度（不含360），须填写修正原因");}
 public JSONObject json()throws Exception{return new JSONObject().put("aid",aid).put("point",point.json()).put("interval",interval).put("altitude",altitude).put("speed",speed).put("bearing",bearing).put("reason",reason).put("before",before);}
}
