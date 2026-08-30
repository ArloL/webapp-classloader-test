package io.github.arlol.testing;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import javassist.ClassPool;
import javassist.Loader;

public class ClassCreatorThreadTest {

	@Test
	public void testCreatesClassesUntilTheClassLoaderIsCollected() {
		AtomicInteger calls = new AtomicInteger();

		classCreator(() -> calls.incrementAndGet() > 2).run();

		assertEquals(3, calls.get());
	}

	@Test
	public void testStopsCreatingClassesWhenTheConditionFails() {
		AtomicInteger calls = new AtomicInteger();

		classCreator(() -> {
			if (calls.incrementAndGet() == 2) {
				throw new IllegalStateException("condition failed");
			}
			return false;
		}).run();

		assertEquals(2, calls.get());
	}

	private ClassCreatorThread classCreator(Callable<Boolean> condition) {
		return new ClassCreatorThread(
				condition,
				ClassPool.getDefault(),
				new Loader.Simple()
		);
	}

}
