package io.github.arlol.testing;

import java.io.File;
import java.net.URL;

import org.junit.Test;

public class WebAppTestTest {

	@Test
	public void testSuccessful() throws Exception {
		File warFile = getClassPathResource("webapp-test-working.war");
		new WebAppTest().warFile(warFile).run();
	}

	@Test
	public void testSuccessfulKeyStore() throws Exception {
		File warFile = getClassPathResource("webapp-test-keystore.war");
		new WebAppTest().warFile(warFile).run();
	}

	@Test
	public void testSuccessfulWithContextInWar() throws Exception {
		File warFile = getClassPathResource("webapp-test-working-context.war");
		new WebAppTest().warFile(warFile).run();
	}

	@Test
	public void testSuccessfulWithContextXml() throws Exception {
		File warFile = getClassPathResource("webapp-test-working.war");
		File contextFile = getClassPathResource("tomcat-context-working.xml");
		new WebAppTest().warFile(warFile).contextFile(contextFile).run();
	}

	@Test(expected = WebAppTestException.class)
	public void testFailingWithTimeout() throws Exception {
		File warFile = getClassPathResource("webapp-test-working.war");
		new WebAppTest().warFile(warFile).pingEndPoint("index.html").run();
	}

	@Test(expected = WebAppTestException.class)
	public void testFailingWithContextXml() throws Exception {
		File warFile = getClassPathResource("webapp-test-working.war");
		File contextFile = getClassPathResource("tomcat-context-bad.xml");
		new WebAppTest().warFile(warFile).contextFile(contextFile).run();
	}

	@Test(expected = WebAppTestException.class)
	public void testFailingBadWebXML() throws Exception {
		File warFile = getClassPathResource("webapp-test-bad-web-xml.war");
		new WebAppTest().warFile(warFile).run();
	}

	@Test(expected = WebAppTestException.class)
	public void testFailingBadContext() throws Exception {
		File warFile = getClassPathResource("webapp-test-bad-context.war");
		new WebAppTest().warFile(warFile).run();
	}

	public File getClassPathResource(String path) throws Exception {
		ClassLoader contextClassLoader =
		        Thread.currentThread().getContextClassLoader();
		URL resource = contextClassLoader.getResource(path);
		return new File(resource.toURI());
	}

}
