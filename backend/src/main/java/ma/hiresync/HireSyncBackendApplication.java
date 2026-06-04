package ma.hiresync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
public class HireSyncBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HireSyncBackendApplication.class, args);
	}
}
