package issue.tracker.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the issue tracking system (Spring Boot).
 */
@SpringBootApplication
public class SystemApplication {

	/**
	 * Starts the application.
	 *
	 * @param args command line arguments passed to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(SystemApplication.class, args);
	}

}
