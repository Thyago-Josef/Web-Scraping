package controller;


import org.apache.pdfbox.Loader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WebScrapingDataTransformation {
    public static void main(String[] args) {
        try {
            String url = "https://www.gov.br/ans/pt-br/acesso-a-informacao/participacao-da-sociedade/atualizacao-do-rol-de-procedimentos";
            downloadAndZipAttachments(url);

            // Check if the "AnexoI.pdf" file exists before processing
            File anexoFile = new File("AnexoI.pdf");
            if (!anexoFile.exists()) {
                System.err.println("Error: AnexoI.pdf not found in the current directory.");
                System.err.println("This file should be downloaded from the website or placed manually in the working directory.");
                return;
            }


            extractAndTransformPdfData("AnexoI.pdf", "Teste_SeuNome.zip"); // Substitua "SeuNome"
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void downloadAndZipAttachments(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        Elements pdfLinks = doc.select("a[href$=.pdf]");
        Path tempDir = Files.createTempDirectory("pdf_attachments");

        System.out.println("Downloading PDF files from: " + url);


        for (org.jsoup.nodes.Element link : pdfLinks) {
            String fileUrl = link.absUrl("href");
            String fileName = Paths.get(new URL(fileUrl).getPath()).getFileName().toString();
            Path filePath = tempDir.resolve(fileName);
            //Files.copy(new URL(fileUrl).openStream(), filePath);


            // Save the AnexoI.pdf to the current directory as well
            if (fileName.equals("AnexoI.pdf")) {
                System.out.println("Downloading AnexoI.pdf to current directory...");
                Files.copy(new URL(fileUrl).openStream(), Paths.get("AnexoI.pdf"));
            }


            // Verifica se o arquivo já existe antes de copiar
            if (!Files.exists(filePath)) {
                System.out.println("Downloading: " + fileName);
                Files.copy(new URL(fileUrl).openStream(), filePath);
            }
        }
        zipFiles(tempDir.toFile(), "attachments.zip");

//        Files.walk(tempDir).forEach(path -> {
//            try {
//                Files.delete(path);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
//        Files.delete(tempDir);

        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            System.out.println("Deleting: " + path);
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete: " + path);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error while cleaning up temporary directory");
            e.printStackTrace();
        }
    }



    public static void zipFiles(File directoryToZip, String zipFileName) throws IOException {
        System.out.println("Creating zip file: " + zipFileName);
        FileOutputStream fos = new FileOutputStream(zipFileName);
        ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(fos));
////        Files.walk(directoryToZip.toPath())
////                .filter(path -> !Files.isDirectory(path))
////                .forEach(path -> {
////                    ZipEntry zipEntry = new ZipEntry(directoryToZip.toPath().relativize(path).toString());
//                    try {
//                        zipOut.putNextEntry(zipEntry);
//                        Files.copy(path, zipOut);
//                        zipOut.closeEntry();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                });
//        zipOut.close();
//        fos.close();
//    }


        try {
            Files.walk(directoryToZip.toPath())
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(directoryToZip.toPath().relativize(path).toString());
                        try {
                            zipOut.putNextEntry(zipEntry);
                            Files.copy(path, zipOut);
                            zipOut.closeEntry();
                            System.out.println("Added to zip: " + path.getFileName());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } finally {
            zipOut.close();
            fos.close();
        }
    }

    public static void extractAndTransformPdfData(String pdfFilePath, String zipFileName) throws IOException {
        //PDDocument document = PDDocument.load(new File(pdfFilePath));
        System.out.println("Processing PDF: " + pdfFilePath);
        File pdfFile = new File(pdfFilePath);

        if (!pdfFile.exists()) {
            throw new IOException("PDF file not found: " + pdfFilePath);
        }
        PDDocument document = Loader.loadPDF(new File(pdfFilePath));
        PDFTextStripper textStripper = new PDFTextStripper();
        String text = textStripper.getText(document);
        document.close();

        String tableRegex = "(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+(.*?)\\s+(OD|AMB)\\s+(.*?)\\s+(.*?)\\s+(.*?)\\s+(.*)"; // Regex para extrair dados da tabela
        Pattern pattern = Pattern.compile(tableRegex);
        Matcher matcher = pattern.matcher(text);

        File csvFile = new File("output.csv");
        PrintWriter writer = new PrintWriter(csvFile);
        writer.println("Codigo,Descricao,Tipo,Unidade,Porte,Valor,Observacao"); // Cabeçalho CSV

        int rowCount = 0;
        while (matcher.find()) {
            String tipo = matcher.group(3);
            if (tipo.equals("OD")) {
                tipo = "Seg. Odontologica";
            } else if (tipo.equals("AMB")) {
                tipo = "Seg Ambulatorial";
            }
            writer.println(matcher.group(1) + "," + matcher.group(2) + "," + tipo + "," + matcher.group(4) + "," + matcher.group(5) + "," + matcher.group(6) + "," + matcher.group(7));
            rowCount++;
        }
        writer.close();
        System.out.println("Extracted " + rowCount + " rows to CSV file");

        zipFiles(csvFile.getParentFile(), zipFileName);
        csvFile.delete();
        System.out.println("Created final zip file: " + zipFileName);

    }
}
