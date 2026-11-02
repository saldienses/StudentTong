package com.studenttong.mobile;
import org.json.*;
/** WGS84 map data only. This type cannot submit attendance or represent a system fix. */
public final class PreviewLocation {
 public final double latitude,longitude,accuracy;public final String label;
 public PreviewLocation(double lat,double lon,double accuracy,String label){if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||!Double.isFinite(accuracy)||accuracy<1||accuracy>2000)throw new IllegalArgumentException("坐标或精度无效：纬度±90，经度±180，精度1至2000米");latitude=lat;longitude=lon;this.accuracy=accuracy;this.label=label==null?"选中位置":label.substring(0,Math.min(180,label.length()));}
 public JSONObject json(){try{return new JSONObject().put("latitude",latitude).put("longitude",longitude).put("accuracy",accuracy).put("label",label).put("crs","WGS84");}catch(Exception e){throw new IllegalStateException(e);}}
 public static PreviewLocation parse(JSONObject j){if(!"WGS84".equals(j.optString("crs","WGS84")))throw new IllegalArgumentException("仅接受WGS84坐标");return new PreviewLocation(j.optDouble("latitude"),j.optDouble("longitude"),j.optDouble("accuracy",10),j.optString("label","选中位置"));}
}
