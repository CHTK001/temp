package com.chua.example.filestorage;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.impl.JdkHttpServer;
import com.chua.filestorage.support.filter.FileStorageDownloadServerFilter;
import com.chua.filestorage.support.filter.FileStorageViewServerFilter;
import com.chua.filestorage.support.setting.FileStorageCacheSetting;
import com.chua.filestorage.support.setting.FileStorageSetting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FileStorageServer {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "filestorage-live-" + UUID.randomUUID());
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("pdf-cache"));
        Files.createDirectories(root.resolve("flash"));

        Path samplePdf  = root.resolve("report.pdf");
        Path sampleDocx = root.resolve("doc.docx");
        Path sampleXlsx = root.resolve("data.xlsx");
        Path sampleImage = root.resolve("avatar.png");

        createRealPng(sampleImage);
        createMinimalXlsx(sampleXlsx);
        Files.writeString(samplePdf,  "%PDF-1.4 sample-file");
        Files.writeString(sampleDocx, "fake-docx-content");

        LocalFileStorage2 storage = new LocalFileStorage2("example", root);
        storage.put("report.pdf", samplePdf);
        storage.put("doc.docx",  sampleDocx);
        storage.put("data.xlsx", sampleXlsx);
        storage.put("avatar.png", sampleImage);

        FileStorageSetting setting = FileStorageSetting.builder()
                .openPreview(true).openDownload(true).openRange(true).openFlash(true)
                .imageFilterEnabled(true).imageFilterKey("jdk")
                .fileSettingKey("jdk").filterSettingKey("jdk")
                .cache(FileStorageCacheSetting.builder()
                        .pdfCacheDir(root.resolve("pdf-cache").toString())
                        .flashDir(root.resolve("flash").toString())
                        .flashExpireSeconds(300).build())
                .build();

        FileStorageViewServerFilter viewFilter = new FileStorageViewServerFilter(setting);
        FileStorageDownloadServerFilter downloadFilter = new FileStorageDownloadServerFilter(setting);
        viewFilter.addFileStorage("default", storage);
        downloadFilter.addFileStorage("default", storage);

        int port = Integer.parseInt(System.getProperty("port", "18088"));
        ServerSetting serverSetting = ServerSetting.defaults();
        serverSetting.setHost("0.0.0.0");
        serverSetting.setPort(port);
        Server server = new JdkHttpServer(serverSetting);
        server.start();
        server.addFilter(viewFilter);
        server.addFilter(downloadFilter);

        System.out.println("================================================");
        System.out.println("  FileStorage 实时预览服务器已启动");
        System.out.println("  地址: http://127.0.0.1:" + port);
        System.out.println("  图片: http://127.0.0.1:" + port + "/default/avatar.png?preview&size=200x200");
        System.out.println("  PDF:  http://127.0.0.1:" + port + "/default/report.pdf?preview");
        System.out.println("  Excel:http://127.0.0.1:" + port + "/default/data.xlsx?preview");
        System.out.println("  下载: http://127.0.0.1:" + port + "/default/report.pdf?download");
        System.out.println("================================================");
        System.out.println("  存储目录: " + root.toAbsolutePath());
        System.out.println("  按 Ctrl+C 停止");

        Thread.currentThread().join();
    }

    private static void createMinimalXlsx(Path path) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(path.toFile()))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("xl/workbook.xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("xl/_rels/workbook.xml.rels"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/></Relationships>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Hello</t></is></c><c r=\"B1\" t=\"inlineStr\"><is><t>Univer!</t></is></c></row></sheetData></worksheet>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("xl/styles.xml"));
            zos.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs></styleSheet>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static void createRealPng(Path path) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.setColor(new java.awt.Color(30, 41, 59));
        g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
        g.drawString("CH", 70, 110);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", path.toFile());
    }
}

class LocalFileStorage2 extends com.chua.common.support.storage.AbstractFileStorage {
    private final Path root;
    private final java.util.concurrent.ConcurrentHashMap<String, Path> fileMap = new java.util.concurrent.ConcurrentHashMap<>();
    LocalFileStorage2(String bucket, Path root) {
        super(com.chua.common.support.storage.setting.BucketSetting.builder().bucket(bucket).build());
        this.root = root;
    }
    void put(String key, Path file) { fileMap.put(key, file); }
    @Override
    public com.chua.common.support.storage.result.GetObjectResult getObject(String key) {
        Path path = key == null ? null : fileMap.get(key);
        if (path == null) return com.chua.common.support.storage.result.GetObjectResult.builder()
                .metadata(com.chua.common.support.storage.metadata.Metadata.builder().build()).build();
        try {
            return com.chua.common.support.storage.result.GetObjectResult.builder()
                    .inputStream(Files.newInputStream(path))
                    .metadata(com.chua.common.support.storage.metadata.Metadata.builder()
                            .name(path.getFileName().toString())
                            .size(Files.size(path))
                            .contentType(guessType(path.getFileName().toString())).build())
                    .build();
        } catch (Exception e) {
            return com.chua.common.support.storage.result.GetObjectResult.builder()
                    .metadata(com.chua.common.support.storage.metadata.Metadata.builder().build()).build();
        }
    }
    @Override
    public com.chua.common.support.storage.result.GetObjectResult getObject(com.chua.common.support.storage.request.GetObjectRequest r) {
        return getObject(r == null ? null : r.getKey());
    }
    @Override
    public com.chua.common.support.storage.result.PutObjectResult putObject(com.chua.common.support.storage.request.PutObjectRequest r) { return null; }
    @Override
    public com.chua.common.support.storage.result.DeleteObjectResult deleteObject(String key) { return null; }
    @Override
    public com.chua.common.support.storage.result.ExistObjectResult existObject(com.chua.common.support.storage.request.ExistObjectRequest r) { return null; }
    @Override
    public com.chua.common.support.storage.result.ListObjectResult listObject(com.chua.common.support.storage.request.ListObjectRequest r) { return null; }
    private static String guessType(String filename) {
        if (filename.endsWith(".pdf")) return "application/pdf";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (filename.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }
}
