package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.image.ImageQualityAssessor;
import com.chua.deeplearning.support.model.ImageQualityInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 验证 LaplacianImageQualityAssessor 可通过 SPI(provider=laplacian) 创建并评估。
 *@author CH`n *
 * @since 4.0.0.42
 */
public final class LaplacianQualitySpiExample {

    /** 创建 LaplacianQualitySpiDiag 实例 */
    private LaplacianQualitySpiDiag() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        ImageQualityAssessor assessor = ImageQualityAssessor.create("laplacian", (String) null);
        log.info("SPI 创建成功: " + assessor.getClass().getSimpleName());

        String[] files = {
                "很不清楚的文字图片用于测试文字高清修复模型1.png",
                "很不清楚的文字图片用于测试文字高清修复模型2.png"
        };
        for (String name : files) {
            byte[] img = Files.readAllBytes(Path.of("G:\\images", name));
            ImageQualityInfo info = assessor.assess(img);
            System.out.printf("%-20s blur=%.1f bright=%.1f contrast=%.1f sharpOk=%s brightOk=%s score=%.2f %s%n",
                    name, info.blurScore(), info.brightness(), info.contrast(),
                    info.sharpnessOk(), info.brightnessOk(), info.overallScore(), info.message());
        }
    }
}
