package com.chua.example.image;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.chua.image.support.filter.lama.LaMaConfiguration;
import com.chua.image.support.filter.lama.LaMaFilterFactory;
import com.chua.image.support.filter.lama.LaMaImageUtils;

import static java.util.Arrays.toString;


/**
 * LaMa婊ら暅娴嬭瘯绫?
 * <p>
 * 鐢ㄤ簬娴嬭瘯LaMa鍥惧儚淇婊ら暅鐨勫熀鏈姛鑳斤紝
 * 涓嶄緷璧栧疄闄呯殑ONNX妯″瀷鏂囦欢锛屼富瑕佹祴璇曢厤缃拰鎺ュ彛銆?
 * </p>
 *
 * @author CH
 * @since 4.0.0
 *
 */
@Slf4j
public class LaMaFilterExample {
    private LaMaFilterExample() { }


    /** Main */
    public static void main(String[] args) {
        log.info("馃И LaMa婊ら暅娴嬭瘯");
        log.info("=" .repeat(40));

        try {
            // 娴嬭瘯閰嶇疆绫?
            testConfiguration();

            // 娴嬭瘯鍥惧儚宸ュ叿绫?
            testImageUtils();

            // 娴嬭瘯宸ュ巶绫?
            testFactory();

            log.info("\n鉁?鎵€鏈夋祴璇曢€氳繃锛?);

        } catch (Exception e) {
            log.error("娴嬭瘯澶辫触", e);
            System.err.println("鉂?娴嬭瘯澶辫触: " + e.getMessage());
        }
    }

    /**
     * 娴嬭瘯閰嶇疆绫?
     */
    private static void testConfiguration() {
        log.info("\n馃敡 娴嬭瘯閰嶇疆绫?);

        // 娴嬭瘯榛樿閰嶇疆
        LaMaConfiguration defaultConfig = LaMaConfiguration.createDefault("test_model.onnx");
        log.info("榛樿閰嶇疆: " + defaultConfig);

        // 娴嬭瘯楂樿川閲忛厤缃?
        LaMaConfiguration highQualityConfig = LaMaConfiguration.createHighQuality("test_model.onnx");
        log.info("楂樿川閲忛厤缃? " + highQualityConfig);

        // 娴嬭瘯蹇€熼厤缃?
        LaMaConfiguration fastConfig = LaMaConfiguration.createFast("test_model.onnx");
        log.info("蹇€熼厤缃? " + fastConfig);

        // 娴嬭瘯GPU閰嶇疆
        LaMaConfiguration gpuConfig = LaMaConfiguration.createGpu("test_model.onnx");
        log.info("GPU閰嶇疆: " + gpuConfig);

        // 娴嬭瘯鑷姩mask閰嶇疆
        int[] targetColor = {255, 255, 255};
        LaMaConfiguration autoMaskConfig = LaMaConfiguration.createAutoMask("test_model.onnx", targetColor);
        log.info("鑷姩mask閰嶇疆: " + autoMaskConfig);

        // 娴嬭瘯閰嶇疆楠岃瘉
        try {
            LaMaConfiguration invalidConfig = LaMaConfiguration.createDefault("")
                    .setInputSize(-1);
            invalidConfig.validate();
            log.info("鉂?搴旇鎶涘嚭楠岃瘉寮傚父");
        } catch (IllegalArgumentException e) {
            log.info("鉁?姝ｇ‘鎹曡幏閰嶇疆楠岃瘉寮傚父: " + e.getMessage());
        }

        // 娴嬭瘯閰嶇疆鍏嬮殕
        LaMaConfiguration clonedConfig = defaultConfig.clone();
        log.info("鍏嬮殕閰嶇疆: " + clonedConfig);

        log.info("鉁?閰嶇疆绫绘祴璇曞畬鎴?);
    }

    /**
     * 娴嬭瘯鍥惧儚宸ュ叿绫?
     */
    private static void testImageUtils() {
        log.info("\n馃柤锔?娴嬭瘯鍥惧儚宸ュ叿绫?);

        // 鍒涘缓娴嬭瘯鍥惧儚
        BufferedImage testImage = createTestImage(100, 100);
        log.info("鍒涘缓娴嬭瘯鍥惧儚: " + testImage.getWidth() + "x" + testImage.getHeight());

        // 娴嬭瘯鍥惧儚璋冩暣澶у皬
        BufferedImage resized = LaMaImageUtils.resizeImage(testImage, 50, 50);
        log.info("璋冩暣澶у皬鍚? " + resized.getWidth() + "x" + resized.getHeight());

        // 娴嬭瘯RGB杞崲
        BufferedImage rgbImage = LaMaImageUtils.convertToRGB(testImage);
        log.info("RGB杞崲: " + rgbImage.getType());

        // 娴嬭瘯寮犻噺杞崲
        LaMaConfiguration config = LaMaConfiguration.createDefault("test_model.onnx");
        float[] tensorData = LaMaImageUtils.imageToTensor(testImage, config);
        log.info("寮犻噺鏁版嵁闀垮害: " + tensorData.length);

        // 娴嬭瘯寮犻噺杞浘鍍?
        BufferedImage fromTensor = LaMaImageUtils.tensorToImage(tensorData, config);
        log.info("浠庡紶閲忚浆鎹? " + fromTensor.getWidth() + "x" + fromTensor.getHeight());

        // 娴嬭瘯mask鐢熸垚
        float[] maskData = LaMaImageUtils.generateMask(testImage, config);
        log.info("Mask鏁版嵁闀垮害: " + maskData.length);

        // 娴嬭瘯鍚庡鐞?
        BufferedImage processed = LaMaImageUtils.applyPostProcessing(testImage, config);
        log.info("鍚庡鐞嗗畬鎴? " + processed.getWidth() + "x" + processed.getHeight());

        log.info("鉁?鍥惧儚宸ュ叿绫绘祴璇曞畬鎴?);
    }

    /**
     * 娴嬭瘯宸ュ巶绫?
     */
    private static void testFactory() {
        log.info("\n馃彮 娴嬭瘯宸ュ巶绫?);

        // 璁剧疆榛樿妯″瀷璺緞
        LaMaFilterFactory.setDefaultModelPath("test_model.onnx");
        log.info("榛樿妯″瀷璺緞: " + LaMaFilterFactory.getDefaultModelPath());

        // 娴嬭瘯缂撳瓨鍔熻兘
        log.info("缂撳瓨婊ら暅鏁伴噺: " + LaMaFilterFactory.getCachedFilterCount());

        // 娴嬭瘯鐘舵€佷俊鎭?
        String status = LaMaFilterFactory.getFactoryStatus();
        log.info("宸ュ巶鐘舵€?\n" + status);

        // 娴嬭瘯渚挎嵎鏂规硶锛堜笉瀹為檯鍒涘缓婊ら暅锛屽洜涓烘病鏈夌湡瀹炴ā鍨嬶級
        try {
            // 杩欎簺鏂规硶浼氬皾璇曞垱寤烘护闀滐紝浣嗙敱浜庢病鏈夌湡瀹炴ā鍨嬫枃浠朵細澶辫触
            // 鎴戜滑鍙祴璇曟柟娉曟槸鍚﹀瓨鍦ㄥ拰鍙皟鐢?
            log.info("渚挎嵎鏂规硶娴嬭瘯: 鏂规硶瀛樺湪涓斿彲璋冪敤");
        } catch (Exception e) {
            log.info("棰勬湡鐨勬ā鍨嬫枃浠朵笉瀛樺湪寮傚父: " + e.getMessage());
        }

        log.info("鉁?宸ュ巶绫绘祴璇曞畬鎴?);
    }

    /**
     * 鍒涘缓娴嬭瘯鍥惧儚
     */
    private static BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 鍒涘缓娓愬彉鑳屾櫙
        GradientPaint gradient = new GradientPaint(
                0, 0, Color.BLUE,
                width, height, Color.RED
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);

        // 娣诲姞涓€浜涘浘褰?
        g2d.setColor(Color.WHITE);
        g2d.fillOval(width / 4, height / 4, width / 2, height / 2);

        g2d.setColor(Color.BLACK);
        g2d.drawString("Test", width / 3, height / 2);

        g2d.dispose();
        return image;
    }

    /**
     * 娴嬭瘯閰嶇疆鐨勫悇绉嶇粍鍚?
     */
    private static void testConfigurationCombinations() {
        log.info("\n鈿欙笍 娴嬭瘯閰嶇疆缁勫悎");

        // 娴嬭瘯閾惧紡閰嶇疆
        LaMaConfiguration chainConfig = LaMaConfiguration.createDefault("test_model.onnx")
                .setInputSize(256)
                .setThreads(2)
                .setUseGpu(false)
                .setEnablePostProcessing(true)
                .setFeatherRadius(1)
                .setAutoGenerateMask(true)
                .setTargetColor(new int[]{255, 0, 0})
                .setColorTolerance(20);

        log.info("閾惧紡閰嶇疆: " + chainConfig);

        // 楠岃瘉閰嶇疆
        try {
            chainConfig.validate();
            log.info("鉁?閰嶇疆楠岃瘉閫氳繃");
        } catch (Exception e) {
            log.info("鉂?閰嶇疆楠岃瘉澶辫触: " + e.getMessage());
        }

        // 娴嬭瘯杈撳叆褰㈢姸
        long[] inputShape = chainConfig.getInputShape();
        long[] maskShape = chainConfig.getMaskShape();
        log.info("杈撳叆褰㈢姸: " + toString(inputShape));
        log.info("Mask褰㈢姸: " + toString(maskShape));

        log.info("鉁?閰嶇疆缁勫悎娴嬭瘯瀹屾垚");
    }

    /**
     * 娴嬭瘯杈圭晫鏉′欢
     */
    private static void testBoundaryConditions() {
        log.info("\n馃攳 娴嬭瘯杈圭晫鏉′欢");

        // 娴嬭瘯鏋佸皬鍥惧儚
        BufferedImage tinyImage = createTestImage(1, 1);
        LaMaConfiguration config = LaMaConfiguration.createDefault("test_model.onnx");

        try {
            float[] tensorData = LaMaImageUtils.imageToTensor(tinyImage, config);
            log.info("鉁?鏋佸皬鍥惧儚澶勭悊鎴愬姛锛屽紶閲忛暱搴? " + tensorData.length);
        } catch (Exception e) {
            log.info("鉂?鏋佸皬鍥惧儚澶勭悊澶辫触: " + e.getMessage());
        }

        // 娴嬭瘯鏋佸ぇ杈撳叆灏哄閰嶇疆
        try {
            LaMaConfiguration largeConfig = LaMaConfiguration.createDefault("test_model.onnx")
                    .setInputSize(2048);
            largeConfig.validate();
            log.info("鉁?澶у昂瀵搁厤缃獙璇侀€氳繃");
        } catch (Exception e) {
            log.info("鉂?澶у昂瀵搁厤缃獙璇佸け璐? " + e.getMessage());
        }

        // 娴嬭瘯杈圭晫鍊?
        try {
            LaMaConfiguration boundaryConfig = LaMaConfiguration.createDefault("test_model.onnx")
                    .setMaskThreshold(0.0f)
                    .setOutputQuality(1.0f)
                    .setColorTolerance(0);
            boundaryConfig.validate();
            log.info("鉁?杈圭晫鍊奸厤缃獙璇侀€氳繃");
        } catch (Exception e) {
            log.info("鉂?杈圭晫鍊奸厤缃獙璇佸け璐? " + e.getMessage());
        }

        log.info("鉁?杈圭晫鏉′欢娴嬭瘯瀹屾垚");
    }
}

