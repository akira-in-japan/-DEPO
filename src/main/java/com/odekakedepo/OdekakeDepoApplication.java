package com.odekakedepo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// アプリの入口。main メソッドから Spring Boot が起動する
// @SpringBootApplication：このパッケージ（com.odekakedepo）の下にある @Controller・@Service などを自動で探して準備する
@SpringBootApplication
public class OdekakeDepoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OdekakeDepoApplication.class, args);
    }
}
