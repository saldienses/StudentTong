package com.tihai.utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class InstallRootTest {
 @TempDir Path temp;
 @Test void isolatedRootsWithUnicodeAndSpaces() throws Exception {
  String old=System.getProperty("studenttong.root");
  try{
   Path a=Files.createDirectories(temp.resolve("安装 A")), b=Files.createDirectories(temp.resolve("安装 B"));
   System.setProperty("studenttong.root",a.toString());Files.createDirectories(InstallRoot.path("config"));Files.write(InstallRoot.path("config/instance-id.txt"),"first".getBytes(StandardCharsets.UTF_8));
   assertEquals(a.resolve("data/accounts.json"),InstallRoot.path("data/accounts.json"));assertEquals("first",InstallRoot.installationId());
   System.setProperty("studenttong.root",b.toString());assertEquals("",InstallRoot.installationId());assertEquals(b.resolve("data/accounts.json"),InstallRoot.path("data/accounts.json"));
  }finally{if(old==null)System.clearProperty("studenttong.root");else System.setProperty("studenttong.root",old);}
 }
}
