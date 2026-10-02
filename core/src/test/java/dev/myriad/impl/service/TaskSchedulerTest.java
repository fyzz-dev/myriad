package dev.myriad.impl.service;

import dev.myriad.api.service.Tasks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TaskSchedulerTest {
	@Test
	void laterRunsAfterTheGivenTicks() {
		TaskScheduler s = new TaskScheduler();
		List<String> log = new ArrayList<>();
		s.later(this, 2, () -> log.add("ran"));
		s.tick(); // the first tasks run: the wait starts
		assertTrue(log.isEmpty());
		s.tick();
		assertTrue(log.isEmpty());
		s.tick();
		assertEquals(List.of("ran"), log);
		assertFalse(s.isBusy(this));
	}

	@Test
	void sequenceRunsStepsInOrderAndWaitsForConditions() {
		TaskScheduler s = new TaskScheduler();
		List<String> log = new ArrayList<>();
		AtomicBoolean ready = new AtomicBoolean();
		s.sequence(this).run(() -> log.add("open")).waitUntil(ready::get, 10).run(() -> log.add("move")).wait(1).run(() -> log.add("close"))
			.onFinish(() -> log.add("done")).start();
		s.tick();
		assertEquals(List.of("open"), log);
		s.tick();
		assertEquals(List.of("open"), log);
		ready.set(true);
		s.tick(); // condition met: move, then start waiting one tick
		assertEquals(List.of("open", "move"), log);
		s.tick();
		assertEquals(List.of("open", "move", "close", "done"), log);
	}

	@Test
	void waitUntilTimesOut() {
		TaskScheduler s = new TaskScheduler();
		List<String> log = new ArrayList<>();
		s.sequence(this).waitUntil(() -> false, 3).run(() -> log.add("never")).onTimeout(() -> log.add("timeout")).start();
		for (int i = 0; i < 5; i++) s.tick();
		assertEquals(List.of("timeout"), log);
	}

	@Test
	void cancellingAnOwnerStopsItsTasks() {
		TaskScheduler s = new TaskScheduler();
		List<String> log = new ArrayList<>();
		Object other = new Object();
		Tasks.Handle h = s.every(this, 1, () -> log.add("mine"));
		s.every(other, 1, () -> log.add("other"));
		s.tick();
		s.tick();
		s.cancel(this);
		assertTrue(h.isDone());
		s.tick();
		assertEquals(List.of("mine", "other", "mine", "other", "other"), log);
		assertTrue(s.isBusy(other));
	}

	@Test
	void aFailingTaskIsDroppedWithoutStoppingOthers() {
		TaskScheduler s = new TaskScheduler();
		List<String> log = new ArrayList<>();
		s.later(this, 0, () -> {
			throw new RuntimeException("boom");
		});
		s.later(this, 0, () -> log.add("fine"));
		s.tick();
		s.tick();
		assertEquals(List.of("fine"), log);
	}
}
