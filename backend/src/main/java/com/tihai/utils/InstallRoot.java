package com.tihai.utils;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.io.*;
/** All mutable files belong to the selected installation, never another running copy. */
public final class InstallRoot {
 public static Path root(){String value=System.getProperty("studenttong.root");if(value==null||value.trim().isEmpty())value=System.getenv("STUDYDESK_ROOT");return Paths.get(value==null||value.trim().isEmpty()?".":value).toAbsolutePath().normalize();}
 public static Path path(String relative){return root().resolve(relative).normalize();}
 public static String installationId(){try{return new String(Files.readAllBytes(path("config/instance-id.txt")),StandardCharsets.UTF_8).trim();}catch(IOException e){return "";}}
 public static String browser(){String value=System.getenv("STUDYDESK_BROWSER_BINARY");if(value!=null&&!value.trim().isEmpty()&&Files.isRegularFile(Paths.get(value)))return value;try{String relative=new String(Files.readAllBytes(path("config/browser-path.txt")),StandardCharsets.UTF_8).trim();Path p=root().resolve(relative).normalize();if(p.startsWith(root())&&Files.isRegularFile(p))return p.toString();}catch(IOException ignored){}return path("runtime/browsers/chromium/chrome.exe").toString();}
}
