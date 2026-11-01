package com.studenttong.mobile;
import android.security.keystore.*;import android.util.Base64;import java.security.KeyStore;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;
public final class Secret {
 static javax.crypto.SecretKey key()throws Exception{KeyStore s=KeyStore.getInstance("AndroidKeyStore");s.load(null);if(!s.containsAlias("studenttong.ai")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("studenttong.ai",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (javax.crypto.SecretKey)s.getKey("studenttong.ai",null);}
 public static String encrypt(String text)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(text.getBytes("UTF-8")),Base64.NO_WRAP);}
 public static String decrypt(String data)throws Exception{if(data==null||data.isEmpty())return "";String[] a=data.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(a[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(a[1],Base64.NO_WRAP)),"UTF-8");}
}
