package kr.co.seoulit.his.labimagingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling — CommonCodeCache의 주기적 갱신(@Scheduled)을 위해 필요하다.
 */
@EnableScheduling
@SpringBootApplication
public class LabImagingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabImagingServiceApplication.class, args);
    }

}
