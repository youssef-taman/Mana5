package org.example.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitCaskTest {

	private static final long TEST_MAX_FILE_SIZE = 10L * 1024 * 1024;
	private static final long TEST_COMPACTION_DELAY_MS = 60L * 60 * 1000;

	@TempDir
	Path tempDir;

	@Test
	void emptyStartupCreatesInitialDataFile() throws Exception {
		BitCaskImp store = openStore();
		try {
			assertNull(store.get("missing"));
		} finally {
			store.close();
		}
	}

	@Test
	void putGetAndKeysWork() throws Exception {
		BitCaskImp store = openStore();
		try {
			store.put("city", "Seoul");

			assertEquals("Seoul", store.get("city"));
			assertTrue(store.keys().contains("city"));
		} finally {
			store.close();
		}
	}

	@Test
	void overwriteKeepsLatestValueAcrossRestart() throws Exception {
		BitCaskImp store = openStore();
		try {
			store.put("sensor", "old");
			pauseForTimestampTick();
			store.put("sensor", "new");

			assertEquals("new", store.get("sensor"));
		} finally {
			store.close();
		}

		store = openStore();
		try {
			assertEquals("new", store.get("sensor"));
			assertTrue(store.keys().contains("sensor"));
		} finally {
			store.close();
		}
	}

	@Test
	void recoveryRestoresMultipleKeys() throws Exception {
		BitCaskImp store = openStore();
		try {
			store.put("alpha", "1");
			store.put("beta", "2");
			store.put("gamma", "3");
		} finally {
			store.close();
		}

		store = openStore();
		try {
			assertEquals("1", store.get("alpha"));
			assertEquals("2", store.get("beta"));
			assertEquals("3", store.get("gamma"));
			assertEquals(Set.of("alpha", "beta", "gamma"), Set.copyOf(store.keys()));
		} finally {
			store.close();
		}
	}

	@Test
	void compactPreservesLiveDataAndRemovesOldSegments() throws Exception {
		BitCaskImp store = openStore();
		try {
			store.put("user", "v1");
			pauseForTimestampTick();
			store.put("user", "v2");
			store.put("session", "active");

			store.compact();

			assertTrue(store.keys().containsAll(List.of("user", "session")));
		} finally {
			store.close();
		}
	}

	@Test
	void compactThenReopenPreservesValuesAndExactKeys() throws Exception {
		BitCaskImp store = openStore();
		try {
			store.put("alpha", "1");
			store.put("beta", "2");
			store.put("gamma", "3");

			store.compact();
			assertEquals(Set.of("alpha", "beta", "gamma"), Set.copyOf(store.keys()));
		} finally {
			store.close();
		}

		store = openStore();
		try {
			assertEquals(Set.of("alpha", "beta", "gamma"), Set.copyOf(store.keys()));
		} finally {
			store.close();
		}
	}

	@Test
	void compactThenReopenKeepsOverwriteWinner() throws Exception {
		BitCaskImp store = openStore();
		try {
			store.put("sensor", "old");
			pauseForTimestampTick();
			store.put("sensor", "new");
			store.put("mode", "auto");

			store.compact();
		} finally {
			store.close();
		}

		store = openStore();
		try {
			assertEquals(Set.of("sensor", "mode"), Set.copyOf(store.keys()));
		} finally {
			store.close();
		}
	}

	@Test
	void compactOnEmptyStoreDoesNothing() throws Exception {
		BitCaskImp store = openStore();
		try {
			store.compact();
			assertNull(store.get("missing"));
		} finally {
			store.close();
		}
	}

	private BitCaskImp openStore() throws Exception {
		return new BitCaskImp(tempDir.toString(), TEST_MAX_FILE_SIZE, TEST_COMPACTION_DELAY_MS);
	}


	private void pauseForTimestampTick() throws InterruptedException {
		Thread.sleep(20L);
	}
}

