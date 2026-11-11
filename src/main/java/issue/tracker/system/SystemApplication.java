package issue.tracker.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа issue tracking system (Spring Boot).
 */
@SpringBootApplication
public class SystemApplication {

	/**
	 * Запускает приложение.
	 *
	 * @param args аргументы командной строки, передаваемые в Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(SystemApplication.class, args);
	}

}
