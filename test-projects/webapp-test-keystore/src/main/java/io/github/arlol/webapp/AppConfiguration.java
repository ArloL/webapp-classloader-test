package io.github.arlol.webapp;

import java.io.InputStream;
import java.security.KeyStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class AppConfiguration {

	@Bean
	public KeyStore keyStore() throws Exception {
		KeyStore result;
		try (InputStream is = new ClassPathResource("app.truststore")
				.getInputStream()) {
			KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(is, "changeit".toCharArray());
			System.out.println("Loaded KeyStore");
			result = keyStore;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return result;
	}

}
