package dev.myriad.impl.update;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateSourceTest {
	@Test
	void readsTheRepoFromGitHubUrls() {
		assertEquals(Optional.of("fyzz-dev/myriad"), UpdateSource.gitHubRepo("https://github.com/fyzz-dev/myriad"));
		assertEquals(Optional.of("fyzz-dev/myriad"), UpdateSource.gitHubRepo("https://github.com/fyzz-dev/myriad/"));
		assertEquals(Optional.of("fyzz-dev/myriad"), UpdateSource.gitHubRepo("https://github.com/fyzz-dev/myriad.git"));
		assertEquals(Optional.of("fyzz-dev/myriad"), UpdateSource.gitHubRepo("https://www.github.com/fyzz-dev/myriad/tree/master/core"));
		assertEquals(Optional.of("a/my.addon"), UpdateSource.gitHubRepo("http://github.com/a/my.addon#readme"));
	}

	@Test
	void ignoresOtherUrls() {
		assertEquals(Optional.empty(), UpdateSource.gitHubRepo("https://gitlab.com/a/b"));
		assertEquals(Optional.empty(), UpdateSource.gitHubRepo("https://github.com/a"));
		assertEquals(Optional.empty(), UpdateSource.gitHubRepo("https://example.com/github.com/a/b"));
	}

	@Test
	void theCustomValueWinsOverSources() {
		assertEquals(Optional.of("a/releases"), UpdateSource.resolve("a/releases", "https://github.com/a/code"));
		assertEquals(Optional.of("a/releases"), UpdateSource.resolve("https://github.com/a/releases", null));
		assertEquals(Optional.of("a/code"), UpdateSource.resolve(null, "https://github.com/a/code"));
		assertEquals(Optional.of("a/code"), UpdateSource.resolve(true, "https://github.com/a/code"));
	}

	@Test
	void falseOptsOut() {
		assertEquals(Optional.empty(), UpdateSource.resolve(false, "https://github.com/a/code"));
		assertEquals(Optional.empty(), UpdateSource.resolve(null, null));
	}
}
