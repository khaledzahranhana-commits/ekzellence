package com.ekzellence.watch;
import android.content.*;
import android.security.keystore.*;
import java.security.*;
import java.math.BigInteger;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import android.util.Base64;

public final class Security {
 final android.content.SharedPreferences prefs;
 final SecureRandom rng = new SecureRandom();
 public Security(Context c){prefs=c.getSharedPreferences("security",0);}
 String random(){byte[] b=new byte[32];rng.nextBytes(b);return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
 boolean configured(){return prefs.contains("hash");}
 synchronized void setup(String password) throws Exception {if(configured())throw new Exception("Already configured");if(password.length()<8)throw new Exception("Use at least 8 characters");String salt=random();prefs.edit().putString("salt",salt).putString("hash",derive(password,salt)).commit();}
 String derive(String p,String salt)throws Exception {return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(p.toCharArray(),salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),150000,256)).getEncoded(),Base64.NO_WRAP);}
 boolean verify(String p)throws Exception{return configured()&&MessageDigest.isEqual(derive(p,prefs.getString("salt","")).getBytes(),prefs.getString("hash","").getBytes());}
 String encrypt(String s)throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("watch-data")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("watch-data",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,ks.getKey("watch-data",null));return Base64.encodeToString(c.getIV(),2)+":"+Base64.encodeToString(c.doFinal(s.getBytes("UTF-8")),2);}
 String decrypt(String s)throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String[] x=s.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,ks.getKey("watch-data",null),new GCMParameterSpec(128,Base64.decode(x[0],2)));return new String(c.doFinal(Base64.decode(x[1],2)),"UTF-8");}
 SSLContext tls()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("watch-tls")){KeyPairGenerator g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");g.initialize(new KeyGenParameterSpec.Builder("watch-tls",KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).setCertificateSubject(new X500Principal("CN=eKZellence Watch" )).setCertificateSerialNumber(BigInteger.ONE).setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L)).setCertificateNotAfter(new Date(System.currentTimeMillis()+315360000000L)).build());g.generateKeyPair();}KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,null);SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(km.getKeyManagers(),null,null);return ctx;}
 byte[] certificate()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);return ks.getCertificate("watch-tls").getEncoded();}
}
