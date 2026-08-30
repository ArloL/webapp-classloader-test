package io.github.arlol.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.Test;

public class WebAppClassLoaderTestTest {

	@Test
	public void testSuccessful() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		WebAppClassLoaderTest test = new WebAppClassLoaderTest()
				.warPath(warPath);
		test.start();
		assertDeployed(test);
	}

	@Test
	public void testSuccessfulKeyStore() throws Exception {
		Path warPath = getClassPathResource("webapp-test-keystore.war");
		WebAppClassLoaderTest test = new WebAppClassLoaderTest()
				.warPath(warPath);
		test.start();
		assertDeployed(test);
	}

	@Test
	public void testSuccessfulWithContextInWar() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working-context.war");
		WebAppClassLoaderTest test = new WebAppClassLoaderTest()
				.warPath(warPath);
		test.start();
		assertDeployed(test);
	}

	@Test
	public void testSuccessfulWithContextXml() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		Path contextConfig = getClassPathResource("tomcat-context-working.xml");
		WebAppClassLoaderTest test = new WebAppClassLoaderTest()
				.warPath(warPath)
				.contextConfig(contextConfig);
		test.start();
		assertDeployed(test);
	}

	@Test(expected = WebAppClassLoaderTestException.class)
	public void testFailingWithTimeout() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		new WebAppClassLoaderTest().warPath(warPath)
				.pingEndPoint("index.html")
				.start();
	}

	@Test(expected = WebAppClassLoaderTestException.class)
	public void testFailingWithContextXml() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		Path contextConfig = getClassPathResource("tomcat-context-bad.xml");
		new WebAppClassLoaderTest().warPath(warPath)
				.contextConfig(contextConfig)
				.start();
	}

	@Test(expected = WebAppClassLoaderTestException.class)
	public void testFailingBadWebXML() throws Exception {
		Path warPath = getClassPathResource("webapp-test-bad-web-xml.war");
		new WebAppClassLoaderTest().warPath(warPath).start();
	}

	@Test(expected = WebAppClassLoaderTestException.class)
	public void testFailingBadContext() throws Exception {
		Path warPath = getClassPathResource("webapp-test-bad-context.war");
		new WebAppClassLoaderTest().warPath(warPath).start();
	}

	/**
	 * A started test has to expose the port and context path it deployed the
	 * WAR under, and that deployment has to answer requests.
	 */
	private void assertDeployed(WebAppClassLoaderTest test) throws Exception {
		assertTrue("port not assigned", test.getPort() > 0);
		String contextPath = test.getContextPath();
		assertTrue(
				"unexpected context path: " + contextPath,
				contextPath.startsWith("/")
		);
		UUID.fromString(contextPath.substring(1));

		URI uri = URI.create(
				"http://localhost:%s%s/".formatted(test.getPort(), contextPath)
		);
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder(uri).build(),
					BodyHandlers.ofString()
			);
			assertEquals(200, response.statusCode());
			assertTrue(
					"unexpected body: " + response.body(),
					response.body().contains("Hello World!")
			);
		}
	}

	public Path getClassPathResource(String path) throws Exception {
		ClassLoader contextClassLoader = Thread.currentThread()
				.getContextClassLoader();
		URI uri = contextClassLoader.getResource(path).toURI();
		return Path.of(uri);
	}

}
