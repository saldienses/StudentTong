package com.tihai.utils;
import java.nio.file.*;import java.io.*;import java.util.*;import java.util.stream.*;
/** Retry transient Windows delete-pending directory handles; never touches installation data. */
public final class WindowsTestCleanup {
 public static void remove(Path dir)throws Exception{Path base=Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize(),target=dir.toAbsolutePath().normalize();if(target.equals(base)||!target.startsWith(base)||!target.getFileName().toString().startsWith("junit"))throw new IOException("Unexpected test cleanup target");IOException last=null;for(int n=0;n<30;n++){if(!Files.exists(target))return;List<Path> paths;try(Stream<Path> stream=Files.walk(target)){paths=stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());}for(Path p:paths)try{Files.deleteIfExists(p);}catch(IOException e){last=e;}if(!Files.exists(target))return;Thread.sleep(50);}throw last==null?new IOException("Test directory cleanup failed"):last;}
}
