package com.ruoyi.opc.common.crypto;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.NoIvGenerator;

/**
 * Jasypt 加解密 CLI 工具。
 * <p>
 * 用法（在 opc-common 模块根目录）：
 * <pre>
 *   # 加密：传入明文，输出 ENC(...) 包裹的密文
 *   mvn -q exec:java -Dexec.mainClass=com.ruoyi.opc.common.crypto.JasyptCipherTool \
 *       -Dexec.args="encrypt 'OpcEncrypt!2026' 'mySecret123'"
 *
 *   # 解密：从 ENC(...) 中还原明文（调试用）
 *   mvn -q exec:java -Dexec.mainClass=com.ruoyi.opc.common.crypto.JasyptCipherTool \
 *       -Dexec.args="decrypt 'OpcEncrypt!2026' 'ENC(0f8a9b...)'"
 * </pre>
 *
 * 密码必须与 Nacos 部署时使用的 JASYPT_PASSWORD 环境变量值一致。
 */
public final class JasyptCipherTool {

    private static final String DEFAULT_ALGORITHM = "PBEWithMD5AndDES";

    private JasyptCipherTool() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: <encrypt|decrypt> <password> <value>");
            System.exit(1);
        }

        String mode = args[0];
        String password = args[1];
        String value = args.length >= 3 ? args[2] : "";

        StandardPBEStringEncryptor enc = buildEncryptor(password);
        String result;
        switch (mode) {
            case "encrypt":
                result = "ENC(" + enc.encrypt(value) + ")";
                break;
            case "decrypt":
                String raw = stripEnc(value);
                result = enc.decrypt(raw);
                break;
            default:
                System.err.println("Unknown mode: " + mode);
                System.exit(1);
                return;
        }
        System.out.println(result);
    }

    /**
     * 用指定密码构造一个 PBE 加密器（与 jasypt-spring-boot-starter 默认行为一致）。
     */
    public static StandardPBEStringEncryptor buildEncryptor(String password) {
        StandardPBEStringEncryptor enc = new StandardPBEStringEncryptor();
        enc.setAlgorithm(DEFAULT_ALGORITHM);
        enc.setIvGenerator(new NoIvGenerator());
        enc.setPassword(password);
        return enc;
    }

    /**
     * 去除 ENC(...) 包裹，便于解密函数直接接受原始密文或 ENC(...) 格式。
     */
    public static String stripEnc(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.startsWith("ENC(") && v.endsWith(")")) {
            return v.substring(4, v.length() - 1);
        }
        return v;
    }
}