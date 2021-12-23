package com.jinkeen.lifeplus.log.parser;

import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Security;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import kotlin.text.Charsets;

public class LogParserProtocol {

    private static final String TAG = "LogParserProtocol";

    private static final char ENCRYPT_CONTENT_START = '\1';

    private static final String AES_ALGORITHM_TYPE = "AES/CBC/NoPadding";

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    static {
        initialize();
    }

    private ByteBuffer wrap;

    public LogParserProtocol(File logFile) {
        try {
            wrap = ByteBuffer.wrap(IOUtils.toByteArray(new FileInputStream(logFile)));
        } catch (IOException e) {
            Log.e(TAG, "日志解析创建异常", e);
        }
    }

    public String process() {
        final StringBuilder builder = new StringBuilder();
        while (wrap.hasRemaining()) {
            while (wrap.get() == ENCRYPT_CONTENT_START) {
                byte[] encrypt = new byte[wrap.getInt()];
                if (tryGetEncryptContent(encrypt)) {
                    final String ret = decryptAndAppendFile(encrypt);
                    if (ret != null) builder.append(ret);
                }
            }
        }
        return builder.toString();
    }

    private boolean tryGetEncryptContent(byte[] encrypt) {
        try {
            wrap.get(encrypt);
        } catch (java.nio.BufferUnderflowException e) {
            Log.e(TAG, "尝试获取加密内容异常", e);
            return false;
        }
        return true;
    }

    private String decryptAndAppendFile(byte[] encrypt) {
        try {
            Cipher aesEncryptCipher = Cipher.getInstance(AES_ALGORITHM_TYPE);
            Tuple<String, String> secureParam = getSecureParam();
            if (secureParam == null) return null;
            SecretKeySpec secretKeySpec = new SecretKeySpec(secureParam.first.getBytes(), "AES");
            aesEncryptCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(secureParam.second.getBytes()));
            byte[] compressed = aesEncryptCipher.doFinal(encrypt);
            byte[] plainText = decompress(compressed);
            return new String(plainText, Charsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "解密并附加文件异常", e);
        }
        return null;
    }

    private static byte[] decompress(byte[] contentBytes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            IOUtils.copy(new GZIPInputStream(new ByteArrayInputStream(contentBytes)), out);
            return out.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "解压异常", e);
        }
        return new byte[0];
    }

    private static Tuple<String, String> getSecureParam() {
        try {
            Properties properties = PropertiesLoaderUtils.loadAllProperties("secure.properties");
            Tuple<String, String> tuple = new Tuple<>();
            tuple.first = properties.getProperty("AES_KEY");
            tuple.second = properties.getProperty("IV");
            return tuple;
        } catch (IOException e) {
            Log.e(TAG, "获取安全参数异常", e);
        }
        return null;
    }

    // BouncyCastle作为安全提供，防止我们加密解密时候因为jdk内置的不支持改模式运行报错。
    private static void initialize() {
        if (initialized.get()) return;
        Security.addProvider(new BouncyCastleProvider());
        initialized.set(true);
    }
}
