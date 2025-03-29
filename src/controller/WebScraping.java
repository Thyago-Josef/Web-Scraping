package controller;


import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.util.Collections;

public class WebScraping {
    public static void main(String[] args) {
        scrapeData();
    }

    private static void scrapeData() {
        //Caminho do +
        System.setProperty("webdriver.edge.driver", "resources/msedgedriver.exe");

        EdgeOptions options = new EdgeOptions();

        //nao exibe o navegador
       // options.addArguments("--headless=new");


        //para corrigir possiveis erros na execução
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        //evitar detecao dos sites
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", null);


        //tamanho da janela
        options.addArguments("window-size=1600,800");

        //para ajudar a não ser identificado como bot
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36");

        WebDriver driver = new EdgeDriver(options = options);

        driver.get("https://amazon.com.br");


    }
}
