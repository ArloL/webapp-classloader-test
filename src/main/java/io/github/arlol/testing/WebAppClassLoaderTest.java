package io.github.arlol.testing;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionTimeoutException;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.Loader;

public class WebAppClassLoaderTest {

	private static final Logger LOG = LoggerFactory
			.getLogger(WebAppClassLoaderTest.class);
	private static final File CATALINA_BASE = new File("target/tomcat-tmp");
	private static final int DEPLOY_DURATION = 10;

	private File warFile;
	private String pingEndPoint = "index.jsp";
	private File dumpFile;
	private long deployDuration = DEPLOY_DURATION;
	private File contextFile;
	private boolean testLeak = true;

	public WebAppClassLoaderTest warFile(File warFile) {
		this.warFile = warFile;
		return this;
	}

	public WebAppClassLoaderTest pingEndPoint(String pingEndPoint) {
		this.pingEndPoint = pingEndPoint;
		return this;
	}

	public WebAppClassLoaderTest dumpFile(File dumpFile) {
		this.dumpFile = dumpFile;
		return this;
	}

	public WebAppClassLoaderTest deployDuration(long deployDuration) {
		this.deployDuration = deployDuration;
		return this;
	}

	public WebAppClassLoaderTest contextFile(File contextFile) {
		this.contextFile = contextFile;
		return this;
	}

	public WebAppClassLoaderTest testLeak(boolean testLeak) {
		this.testLeak = testLeak;
		return this;
	}

	public void run() throws WebAppClassLoaderTestException {
		checkArguments();

		Tomcat tomcat = null;
		final DestroyListener destroyListener = new DestroyListener();
		try {
			tomcat = getTomcatInstance();

			Context context = tomcat
					.addWebapp("/test", warFile.getAbsolutePath());

			configureContext(context);

			configureTomcat(tomcat, destroyListener);

			tomcat.start();

			checkContextStarted(context);

			final WeakReference<ClassLoader> classLoaderReference = new WeakReference<>(
					context.getLoader().getClassLoader()
			);

			int port = tomcat.getConnector().getLocalPort();

			final URL url = new URL(
					"http",
					"localhost",
					port,
					"/test/" + pingEndPoint
			);
			ping(url);

			tomcat.getHost().removeChild(context);
			context = null;

			testLeak(classLoaderReference);

		} catch (LifecycleException e) {
			throw new WebAppClassLoaderTestException(e);
		} catch (MalformedURLException e) {
			throw new WebAppClassLoaderTestException(e);
		} finally {
			shutdownTomcat(tomcat, destroyListener);
		}
	}

	private void checkArguments() {
		if (warFile == null) {
			throw new IllegalArgumentException("warFile cannot be null");
		}
		if (pingEndPoint == null) {
			throw new IllegalArgumentException("pingEndPoint cannot be null");
		}
		if (!warFile.exists()) {
			throw new IllegalArgumentException(
					"WAR file does not exist: " + warFile.getAbsolutePath()
			);
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

	private void configureTomcat(
			Tomcat tomcat,
			final DestroyListener destroyListener
	) {
		tomcat.getServer()
				.addLifecycleListener(new JreMemoryLeakPreventionListener());
		tomcat.getServer()
				.addLifecycleListener(new ThreadLocalLeakPreventionListener());
		tomcat.getServer().addLifecycleListener(destroyListener);
	}

	private void shutdownTomcat(
			Tomcat tomcat,
			final DestroyListener destroyListener
	) throws WebAppClassLoaderTestException {
		try {
			if (tomcat != null) {
				tomcat.stop();
				tomcat.destroy();
				await().atMost(Duration.ONE_MINUTE)
						.until(new Callable<Boolean>() {

							@Override
							public Boolean call() throws Exception {
								return destroyListener.isDestroyed()
										&& destroyListener.isStopped();
							}

						});
			}
		} catch (LifecycleException e) {
			throw new WebAppClassLoaderTestException(e);
		} finally {
			delete(CATALINA_BASE);
		}
	}

	private void configureContext(Context context)
			throws MalformedURLException {
		if (contextFile != null) {
			context.setConfigFile(contextFile.toURI().toURL());
		}

		if (context instanceof StandardContext) {
			StandardContext standardContext = (StandardContext) context;
			standardContext.setClearReferencesHttpClientKeepAliveThread(true);
			standardContext.setClearReferencesStopThreads(true);
			standardContext.setClearReferencesStopTimerThreads(true);
		}
	}

	private void ping(final URL url) throws WebAppClassLoaderTestException {
		LOG.info("Pinging {}", url);

		try {
			await().atMost(new Duration(deployDuration, SECONDS))
					.pollInterval(Duration.ONE_SECOND)
					.until(new Callable<Boolean>() {

						@Override
						public Boolean call() throws Exception {
							URLConnection connection = url.openConnection();
							if (connection instanceof HttpURLConnection) {
								HttpURLConnection httpConnection = (HttpURLConnection) connection;
								return httpConnection.getResponseCode() == 200;
							}
							return false;
						}

					});
		} catch (ConditionTimeoutException e) {
			throw new WebAppClassLoaderTestException(
					"Web application not properly deployed",
					e
			);
		}
	}

	private void testLeak(final WeakReference<ClassLoader> classLoaderReference)
			throws WebAppClassLoaderTestException {
		if (!testLeak) {
			return;
		}
		LOG.info("Waiting for GC");

		Callable<Boolean> classLoaderReferenceIsNull = new Callable<Boolean>() {

			@Override
			public Boolean call() throws Exception {
				return classLoaderReference.get() == null;
			}

		};

		System.gc();

		createClassesUntil(classLoaderReferenceIsNull);

		try {
			await().atMost(Duration.TWO_MINUTES)
					.until(classLoaderReferenceIsNull);
		} catch (ConditionTimeoutException e) {
			if (dumpFile != null) {
				LOG.error(
						"ClassLoader not GC'ed. Dumping heap for further analysis.",
						e
				);
				dumpHeap(dumpFile);
			}
			throw new WebAppClassLoaderTestException(
					"ClassLoader not GC'ed",
					e
			);
		}
	}

	private void createClassesUntil(
			final Callable<Boolean> classLoaderReferenceIsNull
	) {
		final ClassLoader classLoader = new Loader.Simple();
		final ClassPool pool = ClassPool.getDefault();
		new Thread("classCreator") {

			@Override
			public void run() {
				try {
					while (!classLoaderReferenceIsNull.call()) {
						CtClass makeClass = pool
								.makeClass("de.test." + UUID.randomUUID());
						makeClass.toClass(
								classLoader,
								this.getClass().getProtectionDomain()
						);
					}
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}
				LOG.info("Done filling PermGen/Metaspace");
			}

		}.start();
	}

	private static void dumpHeap(final File dumpFile)
			throws WebAppClassLoaderTestException {
		String name = ManagementFactory.getRuntimeMXBean().getName();
		String pid = name.substring(0, name.indexOf("@"));
		String[] cmd = { "jmap", "-dump:file=" + dumpFile.getAbsolutePath(),
				pid };
		LOG.info(Arrays.asList(cmd).toString());
		try {
			Runtime.getRuntime().exec(cmd).waitFor();
		} catch (IOException e) {
			throw new WebAppClassLoaderTestException("Could not dump heap", e);
		} catch (InterruptedException e) {
			throw new WebAppClassLoaderTestException("Could not dump heap", e);
		}
	}

	private static Tomcat getTomcatInstance() {
		if (CATALINA_BASE.exists() && !delete(CATALINA_BASE)) {
			throw new IllegalStateException(
					"Unable to delete existing temporary directory for test"
			);
		}
		if (!CATALINA_BASE.mkdirs() && !CATALINA_BASE.isDirectory()) {
			throw new IllegalStateException(
					"Unable to create temporary directory for test"
			);
		}

		File appBase = new File(CATALINA_BASE, "webapps");
		if (!appBase.mkdir() && !appBase.isDirectory()) {
			throw new IllegalStateException(
					"Unable to create appBase for test"
			);
		}

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(0);

		tomcat.setBaseDir(CATALINA_BASE.getAbsolutePath());
		tomcat.getHost().setAppBase(appBase.getAbsolutePath());

		tomcat.enableNaming();

		return tomcat;
	}

	private static boolean delete(File file) {
		// Check if file is directory/folder
		if (file.isDirectory()) {
			try {
				// Delete directory
				FileUtils.deleteDirectory(file);
				return true;
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
				return false;
			}
		} else {
			// Delete the file if it is not a folder
			return file.delete();
		}
	}

}
