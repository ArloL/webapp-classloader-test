package io.github.arlol.testing;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.startup.Tomcat;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javassist.ClassPool;
import javassist.Loader;

public class WebAppClassLoaderTest {

	static {
		for (MemoryPoolMXBean mbean : ManagementFactory
				.getMemoryPoolMXBeans()) {
			if ("Metaspace".equals(mbean.getName())
					&& mbean.getUsage().getMax() == -1) {
				throw new IllegalStateException(
						"MaxMetaspaceSize is undefined. Include -XX:MaxMetaspaceSize=128m in JVM arguments."
				);
			}
		}
	}

	private Path catalinaBase;
	private Path warPath;
	private String pingEndPoint = "";
	private int pingStatusCode = 200;
	private long deployTimeoutInSeconds = 10;
	private long stopTimeoutInSeconds = 60;
	private long leakTestFirstTimeoutInSeconds = 30;
	private long leakTestSecondTimeoutInSeconds = 120;
	private URL contextConfig;
	private boolean testLeak = true;
	private final Map<String, String> contextParameters = new HashMap<>();
	private CustomContextConfig customContextConfig;

	private Tomcat tomcat;
	private DestroyListener destroyListener;
	private Context context;
	private WeakReference<ClassLoader> classLoaderReference;
	private int port;
	private String contextPath;

	public WebAppClassLoaderTest warPath(Path warPath) {
		this.warPath = warPath;
		return this;
	}

	public WebAppClassLoaderTest pingEndPoint(String pingEndPoint) {
		this.pingEndPoint = pingEndPoint;
		return this;
	}

	public WebAppClassLoaderTest pingStatusCode(int pingStatusCode) {
		this.pingStatusCode = pingStatusCode;
		return this;
	}

	public WebAppClassLoaderTest deployTimeoutInSeconds(
			long deployTimeoutInSeconds
	) {
		this.deployTimeoutInSeconds = deployTimeoutInSeconds;
		return this;
	}

	public WebAppClassLoaderTest stopTimeoutInSeconds(
			long stopTimeoutInSeconds
	) {
		this.stopTimeoutInSeconds = stopTimeoutInSeconds;
		return this;
	}

	/**
	 * @param leakTestFirstTimeoutInSeconds the period to wait for cleanup
	 *                                      before starting to create new
	 *                                      classes for GC pressure
	 * @return the current WebAppTest
	 */
	public WebAppClassLoaderTest leakTestFirstTimeoutInSeconds(
			long leakTestFirstTimeoutInSeconds
	) {
		this.leakTestFirstTimeoutInSeconds = leakTestFirstTimeoutInSeconds;
		return this;
	}

	/**
	 * @param leakTestSecondTimeoutInSeconds the period to wait for cleanup
	 *                                       while creating new classes to put
	 *                                       the GC under pressure
	 * @return the current WebAppTest
	 */
	public WebAppClassLoaderTest leakTestSecondTimeoutInSeconds(
			long leakTestSecondTimeoutInSeconds
	) {
		this.leakTestSecondTimeoutInSeconds = leakTestSecondTimeoutInSeconds;
		return this;
	}

	public WebAppClassLoaderTest contextConfig(Path contextConfig) {
		return this.contextConfig(contextConfig.toUri());
	}

	public WebAppClassLoaderTest contextConfig(URI contextConfig) {
		try {
			return this.contextConfig(contextConfig.toURL());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public WebAppClassLoaderTest contextConfig(URL contextConfig) {
		this.contextConfig = contextConfig;
		return this;
	}

	public WebAppClassLoaderTest testLeak(boolean testLeak) {
		this.testLeak = testLeak;
		return this;
	}

	public WebAppClassLoaderTest contextParameter(String key, String value) {
		contextParameters.put(key, value);
		return this;
	}

	public int getPort() {
		return port;
	}

	public String getContextPath() {
		return contextPath;
	}

	public void start() throws WebAppClassLoaderTestException {
		if (warPath == null) {
			throw new IllegalArgumentException("warFile cannot be null");
		}
		if (pingEndPoint == null) {
			throw new IllegalArgumentException("pingEndPoint cannot be null");
		}
		if (!Files.exists(warPath)) {
			throw new IllegalArgumentException(
					"WAR file does not exist: " + warPath
			);
		}

		tomcat = null;
		destroyListener = new DestroyListener();
		try {
			tomcat = getTomcatInstance();

			configureTomcat(tomcat);

			tomcat.start();

			port = tomcat.getConnector().getLocalPort();

			contextPath = "/" + UUID.randomUUID().toString();

			customContextConfig = new CustomContextConfig(
					contextConfig,
					port,
					contextPath,
					contextParameters
			);

			context = tomcat.addWebapp(
					tomcat.getHost(),
					contextPath,
					warPath.toAbsolutePath().toString(),
					(LifecycleListener) customContextConfig
			);

			checkContextStarted(context);

			classLoaderReference = new WeakReference<>(
					context.getLoader().getClassLoader()
			);

			ping(
					URI.create(
							"http://localhost:%s/%s/%s"
									.formatted(port, contextPath, pingEndPoint)
					)
			);

			Thread.sleep(2_500);

		} catch (IOException | IllegalStateException | LifecycleException
				| InterruptedException e) {
			shutdownTomcat();
			throw new WebAppClassLoaderTestException(e);
		}
	}

	public void stop() throws WebAppClassLoaderTestException {
		try {
			if (context != null && tomcat != null) {
				tomcat.getHost().removeChild(context);
				// it is unnecessary to check whether the context was stopped
				// since removeChild is a blocking call
				context = null;
			}

			testLeak();
		} finally {
			shutdownTomcat();
		}
	}

	public void run() throws WebAppClassLoaderTestException {
		try {
			start();
			stop();
		} finally {
			shutdownTomcat();
		}
	}

	private void checkContextStarted(Context context)
			throws LifecycleException {
		if (context.getState() != LifecycleState.STARTED) {
			throw new LifecycleException(
					"Context state is not STARTED but " + context.getStateName()
			);
		}
	}

	private void configureTomcat(Tomcat tomcat) {
		tomcat.getServer()
				.addLifecycleListener(new JreMemoryLeakPreventionListener());
		tomcat.getServer()
				.addLifecycleListener(new ThreadLocalLeakPreventionListener());
		tomcat.getServer().addLifecycleListener(destroyListener);
	}

	private void shutdownTomcat() throws WebAppClassLoaderTestException {
		try {

			Callable<Boolean> contextIsDestroyed = () -> {
				return destroyListener != null && destroyListener.isDestroyed()
						&& destroyListener.isStopped();
			};
			if (tomcat != null && !contextIsDestroyed.call()) {
				tomcat.stop();
				tomcat.destroy();
				Awaitility.await()
						.atMost(stopTimeoutInSeconds, SECONDS)
						.until(contextIsDestroyed);
			}
		} catch (Exception e) {
			throw new WebAppClassLoaderTestException(e);
		} finally {
			if (customContextConfig != null) {
				customContextConfig.configureStop();
				customContextConfig.destroy();
			}
			try {
				delete(catalinaBase);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void ping(final URI uri) throws WebAppClassLoaderTestException {
		try {
			Awaitility.await()
					.atMost(deployTimeoutInSeconds, SECONDS)
					.until((Callable<Boolean>) () -> {
						URLConnection connection = uri.toURL().openConnection();
						if (connection instanceof HttpURLConnection) {
							HttpURLConnection httpConnection = (HttpURLConnection) connection;
							return httpConnection
									.getResponseCode() == pingStatusCode;
						}
						return false;
					});
		} catch (ConditionTimeoutException e) {
			throw new WebAppClassLoaderTestException(
					"Web application not properly deployed",
					e
			);
		}
	}

	private void testLeak() throws WebAppClassLoaderTestException {
		if (!testLeak || classLoaderReference == null) {
			return;
		}

		Callable<Boolean> classLoaderReferenceIsNull = () -> classLoaderReference != null
				&& classLoaderReference.get() == null;

		forceGc(3);

		try {
			Awaitility.await()
					.atMost(leakTestFirstTimeoutInSeconds, SECONDS)
					.until(classLoaderReferenceIsNull);
		} catch (ConditionTimeoutException e) {
			// ignore
		}

		forceGc(3);

		createClassesUntil(classLoaderReferenceIsNull);

		try {
			Awaitility.await()
					.atMost(leakTestSecondTimeoutInSeconds, SECONDS)
					.until(classLoaderReferenceIsNull);
		} catch (ConditionTimeoutException e) {
			throw new WebAppClassLoaderTestException(
					"ClassLoader not GC'ed",
					e
			);
		}
	}

	private void forceGc(int n) {
		for (int i = 0; i < n; i++) {
			forceGc();
		}
	}

	@SuppressFBWarnings("DM_GC")
	private void forceGc() {
		WeakReference<Object> ref = new WeakReference<>(new Object());
		// Until garbage collection has actually been run
		while (ref.get() != null) {
			System.gc();
		}
	}

	private void createClassesUntil(
			final Callable<Boolean> classLoaderReferenceIsNull
	) {
		final ClassLoader classLoader = new Loader.Simple();
		final ClassPool pool = ClassPool.getDefault();
		new ClassCreatorThread(classLoaderReferenceIsNull, pool, classLoader)
				.start();
	}

	private Tomcat getTomcatInstance() throws IOException {
		catalinaBase = Files
				.createTempDirectory("tomcat-classloader-leak-test");

		delete(catalinaBase);

		Path appBase = catalinaBase.resolve("webapps");
		Files.createDirectories(appBase);

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(0);

		tomcat.setBaseDir(catalinaBase.toAbsolutePath().toString());
		tomcat.getHost().setAppBase(appBase.toAbsolutePath().toString());

		tomcat.enableNaming();

		return tomcat;
	}

	private static void delete(Path file) throws IOException {
		if (file == null || !Files.exists(file)) {
			return;
		}
		Files.walkFileTree(file, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(
					Path file,
					BasicFileAttributes attrs
			) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}

		});
	}

}
