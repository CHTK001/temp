package com.chua.example.crypto;

import com.chua.common.support.lang.algorithm.cipher.Sm2Cipher;
import com.chua.common.support.lang.algorithm.cipher.Sm4Cipher;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

/**
 * 国密加解密综合示例 — 演示 SM2 / SM4 算法的能力矩阵。
 *
 * <p>本示例基于 {@code com.chua.common.support.lang.algorithm.cipher} 包下的 SPI 接口，
 * 通过 BouncyCastle 提供者（"bc"）完成密钥生成、加解密、签名验签等核心能力演示，
 * 并提供自检流程用于验证 SPI 实现可用性。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行模式：执行全部自检
 *   java CryptoExample
 *
 *   # 仅测试 SM2
 *   java CryptoExample --type sm2
 *
 *   # 仅测试 SM4
 *   java CryptoExample --type sm4
 *
 *   # 打印帮助
 *   java CryptoExample --help
 * </pre>
 *
 * <h2>SPI 类型与能力</h2>
 * <table border="1">
 *   <tr><th>算法</th><th>SPI</th><th>提供者</th><th>密钥生成</th><th>加密</th><th>解密</th><th>签名</th><th>验签</th></tr>
 *   <tr><td>SM2</td><td>{@link Sm2Cipher}</td><td>bc</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>SM4</td><td>{@link Sm4Cipher}</td><td>bc</td><td>❌</td><td>✅</td><td>✅</td><td>❌</td><td>❌</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class CryptoExample {

    /**
     * SM2 自检明文
     */
    private static final String SM2_PLAINTEXT = "Hello, SM2 国密非对称加密!";

    /**
     * SM4 自检明文
     */
    private static final String SM4_PLAINTEXT = "Hello, SM4 国密对称加密!";

    /**
     * SM4 16 字节密钥（128 位）
     */
    private static final byte[] SM4_KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    /**
     * 算法类型：SM2
     */
    private static final String TYPE_SM2 = "sm2";

    /**
     * 算法类型：SM4
     */
    private static final String TYPE_SM4 = "sm4";

    /**
     * 算法类型：全部
     */
    private static final String TYPE_ALL = "all";

    /**
     * SPI 提供者：BouncyCastle
     */
    private static final String PROVIDER_BC = "bc";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 主入口：根据命令行参数运行指定算法自检。
     *
     * @param args 命令行参数，args[0]=算法类型（sm2 / sm4 / all），默认 all
     */
    public static void main(String[] args) {
        String type = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty())
                ? args[0].toLowerCase()
                : TYPE_ALL;

        if ("--help".equals(type) || "-h".equals(type)) {
            printHelp();
            return;
        }

        CryptoExample example = new CryptoExample();
        boolean passed = example.runTest(type);
        log.info("[CryptoExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 启动自检流程：根据算法类型分发到对应的测试方法。
     *
     * @param type 算法类型（sm2 / sm4 / all）
     * @return true 表示所选算法全部自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = TYPE_ALL;
        }
        switch (type.toLowerCase()) {
            case TYPE_SM2 -> {
                return testSm2();
            }
            case TYPE_SM4 -> {
                return testSm4();
            }
            case TYPE_ALL -> {
                return testSm2() && testSm4();
            }
            default -> {
                log.error("[CryptoExample] 未知算法类型: {}, 支持: sm2 / sm4 / all", type);
                return false;
            }
        }
    }

    /**
     * SM2 自检：覆盖密钥生成、加密、解密、签名、验签。
     *
     * @return true 表示加解密原文一致且验签通过
     */
    public boolean testSm2() {
        log.info("===== SM2 自检开始 =====");
        try {
            Sm2Cipher sm2 = Sm2Cipher.create(PROVIDER_BC);

            // 1. 生成密钥对
            KeyPair keyPair = sm2.generateKeyPair();
            byte[] publicKey = keyPair.getPublic().getEncoded();
            byte[] privateKey = keyPair.getPrivate().getEncoded();
            log.info("  [sm2] 生成密钥对成功, publicKeyLen={}, privateKeyLen={}",
                    publicKey.length, privateKey.length);

            // 2. 加密
            byte[] plaintext = SM2_PLAINTEXT.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = sm2.encrypt(publicKey, plaintext);
            log.info("  [sm2] 加密成功, ciphertextLen={}, base64={}",
                    ciphertext.length, Base64.getEncoder().encodeToString(ciphertext));

            // 3. 解密
            byte[] decrypted = sm2.decrypt(privateKey, ciphertext);
            String decryptedStr = new String(decrypted, StandardCharsets.UTF_8);
            boolean decryptOk = SM2_PLAINTEXT.equals(decryptedStr);
            log.info("  [sm2] 解密成功, decrypted={}, match={}", decryptedStr, decryptOk);

            // 4. 签名
            byte[] signature = sm2.sign(privateKey, plaintext);
            log.info("  [sm2] 签名成功, signatureLen={}, base64={}",
                    signature.length, Base64.getEncoder().encodeToString(signature));

            // 5. 验签
            boolean verified = sm2.verify(publicKey, plaintext, signature);
            log.info("  [sm2] 验签结果: {}", verified);

            boolean passed = decryptOk && verified;
            log.info("===== SM2 自检结束: {} =====", (passed ? "PASS" : "FAIL"));
            return passed;
        } catch (Exception e) {
            log.error("[CryptoExample] SM2 self-test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * SM4 自检：覆盖字节模式与字符串模式（Base64 编码）的加解密。
     *
     * @return true 表示加解密原文一致
     */
    public boolean testSm4() {
        log.info("===== SM4 自检开始 =====");
        try {
            Sm4Cipher sm4 = Sm4Cipher.create(PROVIDER_BC);

            // 1. 字节模式加解密
            byte[] plaintext = SM4_PLAINTEXT.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = sm4.encrypt(SM4_KEY, plaintext);
            log.info("  [sm4] 字节模式加密成功, ciphertextLen={}, base64={}",
                    ciphertext.length, Base64.getEncoder().encodeToString(ciphertext));

            byte[] decrypted = sm4.decrypt(SM4_KEY, ciphertext);
            String decryptedStr = new String(decrypted, StandardCharsets.UTF_8);
            boolean byteModeOk = SM4_PLAINTEXT.equals(decryptedStr);
            log.info("  [sm4] 字节模式解密成功, decrypted={}, match={}", decryptedStr, byteModeOk);

            // 2. 字符串模式加解密（Base64 编码）
            String encryptedStr = sm4.encryptToString(SM4_KEY, SM4_PLAINTEXT);
            log.info("  [sm4] 字符串模式加密成功, base64={}", encryptedStr);

            String decryptedString = sm4.decryptToString(SM4_KEY, encryptedStr);
            boolean strModeOk = SM4_PLAINTEXT.equals(decryptedString);
            log.info("  [sm4] 字符串模式解密成功, decrypted={}, match={}", decryptedString, strModeOk);

            boolean passed = byteModeOk && strModeOk;
            log.info("===== SM4 自检结束: {} =====", (passed ? "PASS" : "FAIL"));
            return passed;
        } catch (Exception e) {
            log.error("[CryptoExample] SM4 self-test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        log.info("CryptoExample — 国密 SM2/SM4 加解密示例");
        log.info("");
        log.info("用法: java CryptoExample [选项]");
        log.info("");
        log.info("选项:");
        log.info("  sm2       仅测试 SM2 算法（密钥生成/加解密/签名验签）");
        log.info("  sm4       仅测试 SM4 算法（字节模式/字符串模式加解密）");
        log.info("  all       测试全部算法（默认）");
        log.info("  --help    显示此帮助");
    }
}
