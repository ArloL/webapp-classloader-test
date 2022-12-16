package io.github.arlol.testing;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class WebAppClassLoaderTestTest {

	@Test
	public void testSuccessful() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		new WebAppClassLoaderTest().warPath(warPath).start();
	}

	@Test
	public void testSuccessfulKeyStore() throws Exception {
		Path warPath = getClassPathResource("webapp-test-keystore.war");
		new WebAppClassLoaderTest().warPath(warPath).start();
	}

	@Test
	public void testSuccessfulWithContextInWar() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working-context.war");
		new WebAppClassLoaderTest().warPath(warPath).start();
	}

	@Test
	public void testSuccessfulWithContextXml() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		Path contextConfig = getClassPathResource("tomcat-context-working.xml");
		new WebAppClassLoaderTest().warPath(warPath).contextConfig(contextConfig).start();
	}

	@Test(expected = WebAppClassLoaderTestException.class)
	public void testFailingWithTimeout() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		new WebAppClassLoaderTest().warPath(warPath).pingEndPoint("index.html").start();
	}

	@Test(expected = WebAppClassLoaderTestException.class)
	public void testFailingWithContextXml() throws Exception {
		Path warPath = getClassPathResource("webapp-test-working.war");
		Path contextConfig = getClassPathResource("tomcat-context-bad.xml");
		new WebAppClassLoaderTest().warPath(warPath).contextConfig(contextConfig).start();
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

	public Path getClassPathResource(String path) throws Exception {
		ClassLoader contextClassLoader = Thread.currentThread()
				.getContextClassLoader();
		URI uri = contextClassLoader.getResource(path).toURI();
		return Paths.get(uri);
	}

}
