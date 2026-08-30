package io.github.arlol.testing;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
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

	@Test
	public void testInterruptedStartKeepsTheInterruptFlag() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		WebAppClassLoaderTest test = new WebAppClassLoaderTest()
				.warPath(warPath);

		AtomicReference<Exception> thrown = new AtomicReference<>();
		AtomicBoolean interrupted = new AtomicBoolean();
		Thread worker = new Thread(() -> {
			try {
				test.start();
			} catch (Exception e) {
				thrown.set(e);
			} finally {
				interrupted.set(Thread.currentThread().isInterrupted());
			}
		}, "interrupted-start");
		worker.start();

		awaitDeployed(test);
		worker.interrupt();
		worker.join(SECONDS.toMillis(60));

		assertFalse("start() never returned", worker.isAlive());
		assertTrue("interrupt status was swallowed", interrupted.get());
		Exception exception = thrown.get();
		assertEquals(
				WebAppClassLoaderTestException.class,
				exception == null ? null : exception.getClass()
		);
		assertEquals(
				InterruptedException.class,
				exception.getCause().getClass()
		);
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

	@Test
	public void testDeleteQuietlyRemovesTheWholeTree() throws Exception {
		Path directory = Files.createTempDirectory("delete-quietly");
		Path file = Files.createFile(
				Files.createDirectory(directory.resolve("webapps"))
						.resolve("some.war")
		);

		WebAppClassLoaderTest.deleteQuietly(directory);

		assertFalse("left behind: " + file, Files.exists(directory));
	}

	@Test
	public void testDeleteQuietlySwallowsFailures() throws Exception {
		Path directory = Files.createTempDirectory("undeletable");
		Path file = Files.createFile(directory.resolve("some.war"));
		assumeTrue(
				"needs POSIX permissions",
				Files.getFileStore(directory)
						.supportsFileAttributeView(PosixFileAttributeView.class)
		);
		Files.setPosixFilePermissions(
				directory,
				PosixFilePermissions.fromString("r-xr-xr-x")
		);
		try {
			assumeFalse("running as root", Files.isWritable(directory));

			WebAppClassLoaderTest.deleteQuietly(directory);

			assertTrue("deletion should have failed", Files.exists(file));
		} finally {
			Files.setPosixFilePermissions(
					directory,
					PosixFilePermissions.fromString("rwxr-xr-x")
			);
			WebAppClassLoaderTest.deleteQuietly(directory);
		}
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

		HttpResponse<String> response = get(test);
		assertEquals(200, response.statusCode());
		assertTrue(
				"unexpected body: " + response.body(),
				response.body().contains("Hello World!")
		);
	}

	/**
	 * Waits until the WAR answers requests, which means start() has got past
	 * its ping and is in its final sleep.
	 */
	private void awaitDeployed(WebAppClassLoaderTest test) {
		Awaitility.await()
				.atMost(30, SECONDS)
				.ignoreExceptions()
				.until(
						() -> test.getPort() > 0
								&& get(test).statusCode() == 200
				);
	}

	private HttpResponse<String> get(WebAppClassLoaderTest test)
			throws Exception {
		URI uri = URI.create(
				"http://localhost:%s%s/"
						.formatted(test.getPort(), test.getContextPath())
		);
		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(
					HttpRequest.newBuilder(uri).build(),
					BodyHandlers.ofString()
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
