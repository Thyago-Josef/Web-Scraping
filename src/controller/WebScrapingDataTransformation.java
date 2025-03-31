package controller;

import org.apache.pdfbox.Loader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Importação explícita da classe Page do Tabula para evitar conflitos
import technology.tabula.ObjectExtractor;
import technology.tabula.RectangularTextContainer;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.Table;

public class WebScrapingDataTransformation {

    private static final String URL_ANS = "https://www.gov.br/ans/pt-br/acesso-a-informacao/participacao-da-sociedade/atualizacao-do-rol-de-procedimentos";
    private static final String DIR_ANEXOS = "anexos";
    private static final String DIR_CSV = "csv";
    private static final String DIR_FINAL = "final";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite seu nome para o arquivo final: ");
        String nome = scanner.nextLine();
        scanner.close();

        try {
            // Criar diretórios necessários
            criarDiretorios();

            // Parte 1: Web Scraping
            System.out.println("Iniciando o processo de Web Scraping...");
            List<String> anexosBaixados = baixarAnexos();
            String arquivoZip = compactarAnexos(anexosBaixados);

            // Parte 2: Transformação de Dados
            System.out.println("\nIniciando o processo de Transformação de Dados...");

            // Identificar o Anexo I
            String anexoI = null;
            for (String anexo : anexosBaixados) {
                if (anexo.toLowerCase().contains("anexo i") || anexo.toLowerCase().contains("anexo_i")) {
                    anexoI = anexo;
                    break;
                }
            }

            if (anexoI != null) {
                // Extrair tabela do PDF
                List<List<String>> tabelaRol = extrairTabelaPdf(anexoI);

                // Substituir abreviações
                substituirAbreviacoes(tabelaRol);

                // Salvar em CSV e compactar
                String arquivoFinal = salvarCsvECompactar(tabelaRol, nome);

                System.out.println("\nProcesso concluído com sucesso!");
                System.out.println("Anexos compactados: " + arquivoZip);
                System.out.println("Arquivo final: " + arquivoFinal);
            } else {
                System.out.println("Anexo I não encontrado entre os arquivos baixados.");
            }
        } catch (Exception e) {
            System.err.println("Erro durante a execução: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void criarDiretorios() throws IOException {
        String[] diretorios = {DIR_ANEXOS, DIR_CSV, DIR_FINAL};
        for (String diretorio : diretorios) {
            Path path = Paths.get(diretorio);
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
        }
    }

    private static List<String> baixarAnexos() throws IOException {
        List<String> anexosBaixados = new ArrayList<>();

        // Fazendo a requisição HTTP
        Document doc = Jsoup.connect(URL_ANS).get();

        // Encontrar os links para os anexos I e II
        Elements links = doc.select("a[href~=(?i)anexo.*\\.pdf]");

        for (Element link : links) {
            String texto = link.text().toLowerCase();
            if (texto.contains("anexo i") || texto.contains("anexo ii")) {
                String href = link.attr("href");

                // Verificar se o link é relativo ou absoluto
                if (!href.startsWith("http")) {
                    if (href.startsWith("/")) {
                        href = "https://www.gov.br" + href;
                    } else {
                        href = "https://www.gov.br/" + href;
                    }
                }

                // Extrair o nome do arquivo
                String nomeArquivo = href.substring(href.lastIndexOf("/") + 1);
                String caminhoArquivo = DIR_ANEXOS + File.separator + nomeArquivo;

                System.out.println("Baixando " + nomeArquivo + " de " + href);
                try {
                    URL url = new URL(href);
                    ReadableByteChannel rbc = Channels.newChannel(url.openStream());
                    FileOutputStream fos = new FileOutputStream(caminhoArquivo);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                    anexosBaixados.add(caminhoArquivo);
                    System.out.println("Arquivo " + nomeArquivo + " baixado com sucesso!");
                } catch (Exception e) {
                    System.out.println("Erro ao baixar o arquivo: " + e.getMessage());
                }
            }
        }

        return anexosBaixados;
    }

    private static String compactarAnexos(List<String> arquivos) throws IOException {
        String caminhoZip = DIR_ANEXOS + File.separator + "anexos.zip";

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(caminhoZip))) {
            for (String arquivo : arquivos) {
                File fileToZip = new File(arquivo);
                try (FileInputStream fis = new FileInputStream(fileToZip)) {
                    ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zos.putNextEntry(zipEntry);

                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zos.write(bytes, 0, length);
                    }
                }
            }
        }

        System.out.println("Arquivos compactados com sucesso em " + caminhoZip);
        return caminhoZip;
    }

    private static List<List<String>> extrairTabelaPdf(String caminhoAnexoI) throws IOException {
        System.out.println("Extraindo tabelas do arquivo " + caminhoAnexoI);

        List<List<String>> tabelaRol = new ArrayList<>();

        // Usar PDFBox para extrair texto do PDF
        try (PDDocument document = Loader.loadPDF(new File(caminhoAnexoI))) {
            // Primeiro, tentar com Tabula
            try {
                ObjectExtractor extractor = new ObjectExtractor(document);
                SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();

                // Usar iteração manual para evitar uso de PageIterator que causa conflito
                for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                    // Obter a página usando a API do Tabula, explicitamente usando o tipo completo
                    technology.tabula.Page page = extractor.extract(pageNum + 1);
                    List<Table> tabelas = sea.extract(page);

                    for (Table tabela : tabelas) {
                        // Verificar se é a tabela Rol de Procedimentos e Eventos em Saúde
                        boolean isRolTable = false;
                        for (List<RectangularTextContainer> linha : tabela.getRows()) {
                            String linhaStr = String.join((CharSequence) " ", (CharSequence) linha).toUpperCase(); // alterei aqui não sei se dará certo
                            if (linhaStr.contains("PROCEDIMENTO") && linhaStr.contains("ROL")) {
                                isRolTable = true;
                                break;
                            }
                        }

                        if (isRolTable || tabelaRol.isEmpty()) {
                            // Converter os RectangularTextContainer para String
                            List<List<String>> linhasConvertidas = tabela.getRows().stream()
                                    .map(linha -> linha.stream()
                                            .map(celula -> celula.getText()) // Assumindo que existe um método getText()
                                            .collect(Collectors.toList()))
                                    .collect(Collectors.toList());

                            // Adicionar as linhas convertidas à tabelaRol
                            tabelaRol.addAll(linhasConvertidas);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Erro ao extrair tabelas com Tabula: " + e.getMessage());
                // Se falhar, recorrer à extração de texto completo
                tabelaRol.clear();
            }

            // Se não conseguiu extrair com Tabula, usar extração de texto básica
            if (tabelaRol.isEmpty()) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                // Processar o texto para extrair linhas da tabela
                String[] linhas = text.split("\\r?\\n");
                for (String linha : linhas) {
                    if (linha.trim().length() > 0) {
                        List<String> colunas = Arrays.asList(linha.split("\\s{2,}"));
                        tabelaRol.add(colunas);
                    }
                }
            }
        }

        return tabelaRol;
    }

    private static void substituirAbreviacoes(List<List<String>> tabela) {
        // Mapear as abreviações para as descrições completas
        Map<String, String> mapeamento = new HashMap<>();
        mapeamento.put("OD", "Segmentação Assistencial: Odontológica");
        mapeamento.put("AMB", "Segmentação Assistencial: Ambulatorial");

        // Substituir nas células
        for (List<String> linha : tabela) {
            for (int i = 0; i < linha.size(); i++) {
                String valor = linha.get(i);
                for (Map.Entry<String, String> entry : mapeamento.entrySet()) {
                    // Substituir a abreviação exata
                    if (valor.equals(entry.getKey())) {
                        linha.set(i, entry.getValue());
                    }
                    // Substituir a abreviação dentro do texto
                    else if (valor.contains(entry.getKey())) {
                        linha.set(i, valor.replace(entry.getKey(), entry.getValue()));
                    }
                }
            }
        }
    }

    private static String salvarCsvECompactar(List<List<String>> tabela, String nome) throws IOException {
        // Salvar em CSV
        String caminhoCsv = DIR_CSV + File.separator + "tabela_rol.csv";
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(caminhoCsv), StandardCharsets.UTF_8))) {

            for (List<String> linha : tabela) {
                // Escapar valores que contêm vírgulas
                List<String> valores = new ArrayList<>();
                for (String valor : linha) {
                    if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
                        valor = "\"" + valor.replace("\"", "\"\"") + "\"";
                    }
                    valores.add(valor);
                }
                writer.write(String.join(",", valores));
                writer.newLine();
            }
        }
        System.out.println("Dados salvos em CSV: " + caminhoCsv);

        // Compactar em ZIP
        String caminhoZip = DIR_FINAL + File.separator + "Teste_" + nome + ".zip";
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(caminhoZip))) {
            File fileToZip = new File(caminhoCsv);
            try (FileInputStream fis = new FileInputStream(fileToZip)) {
                ZipEntry zipEntry = new ZipEntry("tabela_rol.csv");
                zos.putNextEntry(zipEntry);

                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zos.write(bytes, 0, length);
                }
            }
        }

        System.out.println("CSV compactado com sucesso em " + caminhoZip);
        return caminhoZip;
    }
}