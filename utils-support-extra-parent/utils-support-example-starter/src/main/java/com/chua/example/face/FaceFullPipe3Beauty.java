import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.common.support.vector.MemoryVectorStorage;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import java.nio.file.*;
import java.util.List;

/**
 * 3peoplebeauty.jpg 全流程：检测→裁剪→输出到各模型子目录。
 */
public class FaceFullPipe3Beauty {
    public static void main(String[] args) throws Exception {
        String imgPath = "D:\\images\\3peoplebeauty.jpg";
        String outBase = "D:\\images\\output";
        byte[] img = Files.readAllBytes(Path.of(imgPath));

        // ── ① 检测（Branch 双检测器合并）──
        FacePipeline face = FacePipeline.builder()
                .detector("yolo-face-detector")
                .anime("anime-face-detector")
                .feature("arc-face")
                .vectorStorage(new MemoryVectorStorage(512, VectorCompareAlgorithm.cosine()))
                .minConfidence(0.45f)
                .build();

        List<FaceDetectionHit> hits = face.detect(img);
        System.out.println("detected: " + hits.size() + " faces");

        // ── ② 保存标注图 + 裁剪人脸 ──
        Path detDir = Path.of(outBase, "yolo-face-detector");
        Files.createDirectories(detDir);

        // 标注图
        var gfp = Class.forName("com.chua.deeplearning.support.draw.DrawerPipeline");
        var dp = gfp.getConstructor(float.class).newInstance(0.5f);
        var targetM = gfp.getMethod("target", byte[].class);
        var boxesM = gfp.getMethod("boxes", java.util.List.class, java.util.List.class);
        var doneM = gfp.getMethod("done");

        java.util.List<com.chua.deeplearning.support.model.DetectionInfo> dinfos = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            var b = hits.get(i).box();
            dinfos.add(new com.chua.deeplearning.support.model.DetectionInfo(
                    "face", b.confidence(), b.x(), b.y(), b.width(), b.height()));
            labels.add(String.format("face_%d %.2f", i + 1, b.confidence()));
        }
        targetM.invoke(dp, img);
        boxesM.invoke(dp, dinfos, labels);
        @SuppressWarnings("unchecked")
        byte[] drawn = (byte[]) doneM.invoke(dp);
        Files.write(Path.of(detDir.toString(), "3peoplebeauty_annotated.png"), drawn);
        System.out.println("annotated -> yolo-face-detector/3peoplebeauty_annotated.png");

        // 裁剪脸
        Path cropDir = Path.of(outBase, "yolo-face-detector", "crops");
        Files.createDirectories(cropDir);
        for (int i = 0; i < hits.size(); i++) {
            byte[] faceImg = hits.get(i).faceImage();
            if (faceImg != null && faceImg.length > 0) {
                Path fp = Path.of(cropDir.toString(), "face_" + (i + 1) + ".png");
                Files.write(fp, faceImg);
                System.out.println("crop_" + (i + 1) + " -> " + fp.getFileName()
                        + " (" + faceImg.length / 1024 + "KB)");
            }
        }

        // 特征
        float[] feat = face.extractFeature(img);
        if (feat != null && feat.length > 0) {
            System.out.println("feature dim=" + feat.length);
        }

        System.out.println("[DONE] pipeline complete for 3peoplebeauty.jpg");
        System.exit(0);
    }
}
