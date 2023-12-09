package io.github.arlol.testing;

import java.util.UUID;
import java.util.concurrent.Callable;

import javassist.ClassPool;
import javassist.CtClass;

public class ClassCreatorThread extends Thread {

	private final Callable<Boolean> classLoaderReferenceIsNull;
	private final ClassPool pool;
	private final ClassLoader classLoader;

	ClassCreatorThread(
			Callable<Boolean> classLoaderReferenceIsNull,
			ClassPool pool,
			ClassLoader classLoader
	) {
		super("classCreator");
		this.classLoaderReferenceIsNull = classLoaderReferenceIsNull;
		this.pool = pool;
		this.classLoader = classLoader;
	}

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
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

}
