package bitcask;

import exception.KeyNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitCaskTest {

	@TempDir
	Path tempDir;


	@AfterEach
	void cleanAfter() throws IOException {
		deleteSegmentFiles();
	}

	@Test
	void putThenGetReturnsStoredValue() throws IOException {
		BitCask bitCask = new BitCaskImpl(tempDir);

		bitCask.put("user", "alice");

		assertEquals("alice", bitCask.get("user"));
	}

	@Test
	void putWithSameKeyReturnsLatestValue() throws IOException {
		BitCask bitCask = new BitCaskImpl(tempDir);

		bitCask.put("user", "alice");
		bitCask.put("user", "bob");

		assertEquals("bob", bitCask.get("user"));
	}

	@Test
	void getForMissingKeyThrowsKeyNotFoundException() {
		BitCask bitCask = new BitCaskImpl(tempDir);

		assertThrows(KeyNotFoundException.class, () -> bitCask.get("missing"));
	}

	private void deleteSegmentFiles() throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("."), "seg_*.dat")) {
			for (Path file : stream) {
				Files.deleteIfExists(file);
			}
		}
	}
}
