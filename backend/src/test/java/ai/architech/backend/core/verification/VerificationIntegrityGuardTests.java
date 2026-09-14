package ai.architech.backend.core.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ai.architech.backend.core.sandbox.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Fixture-driven coverage for {@link VerificationIntegrityGuard} - the "protected baseline"
 * comparison is mocked to a small, controlled scaffold fixture rather than the real classpath
 * scaffold, so these tests stay fast and independent of the real scaffold's own exact content.
 */
@ExtendWith(MockitoExtension.class)
class VerificationIntegrityGuardTests {

	@Mock
	private ResourceLoader resourceLoader;

	@TempDir
	Path root;

	private VerificationIntegrityGuard guard;

	private static final String SCAFFOLD_TSCONFIG =
			"""
			{"compilerOptions": {"strict": true, "noUnusedLocals": true, "noUnusedParameters": true, "noFallthroughCasesInSwitch": true}}""";

	private static final String SCAFFOLD_OXLINTRC =
			"""
			{"rules": {"react/rules-of-hooks": "error", "react/only-export-components": ["warn", {}]}}""";

	@BeforeEach
	void setUp() throws IOException {
		lenient().when(resourceLoader.getResource(anyString())).thenAnswer(invocation -> {
			String location = invocation.getArgument(0, String.class);
			String content = location.endsWith("tsconfig.app.json") ? SCAFFOLD_TSCONFIG : SCAFFOLD_OXLINTRC;
			return mockResource(content);
		});
		guard = new VerificationIntegrityGuard(resourceLoader);
		Files.createDirectories(root.resolve("src"));
	}

	@Test
	void passesWhenTheWorkspaceMatchesTheProtectedBaselineExactly() throws IOException {
		writeWorkspaceTsconfig(SCAFFOLD_TSCONFIG);
		writeWorkspaceOxlintrc(SCAFFOLD_OXLINTRC);
		writeTestFile("App.test.tsx");

		assertThat(guard.findIntegrityViolations(new Workspace(root))).isEmpty();
	}

	@Test
	void passesWhenTheWorkspaceAddsExtraNonProtectedConfigOnTop() throws IOException {
		writeWorkspaceTsconfig(
				"""
				{"compilerOptions": {"strict": true, "noUnusedLocals": true, "noUnusedParameters": true, "noFallthroughCasesInSwitch": true, "paths": {"@/*": ["./src/*"]}}}""");
		writeWorkspaceOxlintrc(
				"""
				{"rules": {"react/rules-of-hooks": "error", "react/only-export-components": ["warn", {}], "typescript/no-explicit-any": "error"}}""");
		writeTestFile("App.test.tsx");
		writeTestFile("HomePage.test.tsx");

		assertThat(guard.findIntegrityViolations(new Workspace(root))).isEmpty();
	}

	@Test
	void failsWhenTypeScriptStrictModeIsDisabled() throws IOException {
		writeWorkspaceTsconfig(
				"""
				{"compilerOptions": {"strict": false, "noUnusedLocals": true, "noUnusedParameters": true, "noFallthroughCasesInSwitch": true}}""");
		writeWorkspaceOxlintrc(SCAFFOLD_OXLINTRC);
		writeTestFile("App.test.tsx");

		assertThat(guard.findIntegrityViolations(new Workspace(root)))
				.anySatisfy(issue -> assertThat(issue).contains("strict").contains("weakened"));
	}

	@Test
	void failsWhenTsconfigIsMissingEntirely() throws IOException {
		writeWorkspaceOxlintrc(SCAFFOLD_OXLINTRC);
		writeTestFile("App.test.tsx");

		assertThat(guard.findIntegrityViolations(new Workspace(root)))
				.anySatisfy(issue -> assertThat(issue).contains("tsconfig.app.json"));
	}

	@Test
	void failsWhenARequiredLintRuleIsDowngradedToWarn() throws IOException {
		writeWorkspaceTsconfig(SCAFFOLD_TSCONFIG);
		writeWorkspaceOxlintrc(
				"""
				{"rules": {"react/rules-of-hooks": "warn", "react/only-export-components": ["warn", {}]}}""");
		writeTestFile("App.test.tsx");

		assertThat(guard.findIntegrityViolations(new Workspace(root)))
				.anySatisfy(issue -> assertThat(issue).contains("react/rules-of-hooks").contains("weakened"));
	}

	@Test
	void failsWhenARequiredLintRuleIsRemovedEntirely() throws IOException {
		writeWorkspaceTsconfig(SCAFFOLD_TSCONFIG);
		writeWorkspaceOxlintrc("""
				{"rules": {}}""");
		writeTestFile("App.test.tsx");

		assertThat(guard.findIntegrityViolations(new Workspace(root)))
				.anySatisfy(issue -> assertThat(issue).contains("react/rules-of-hooks"));
	}

	@Test
	void failsWhenAllBaselineTestFilesWereDeleted() throws IOException {
		writeWorkspaceTsconfig(SCAFFOLD_TSCONFIG);
		writeWorkspaceOxlintrc(SCAFFOLD_OXLINTRC);
		// No test files written at all.

		assertThat(guard.findIntegrityViolations(new Workspace(root)))
				.anySatisfy(issue -> assertThat(issue).contains("baseline test tooling"));
	}

	@Test
	void passesWhenTheDeveloperReplacesTheScaffoldTestWithADifferentRealTest() throws IOException {
		writeWorkspaceTsconfig(SCAFFOLD_TSCONFIG);
		writeWorkspaceOxlintrc(SCAFFOLD_OXLINTRC);
		// The scaffold's own App.test.tsx is gone, but a real replacement test exists.
		writeTestFile("HomePage.test.tsx");

		assertThat(guard.findIntegrityViolations(new Workspace(root))).isEmpty();
	}

	private void writeWorkspaceTsconfig(String content) throws IOException {
		Files.writeString(root.resolve("tsconfig.app.json"), content, StandardCharsets.UTF_8);
	}

	private void writeWorkspaceOxlintrc(String content) throws IOException {
		Files.writeString(root.resolve(".oxlintrc.json"), content, StandardCharsets.UTF_8);
	}

	private void writeTestFile(String fileName) throws IOException {
		Files.writeString(root.resolve("src").resolve(fileName), "// test", StandardCharsets.UTF_8);
	}

	private Resource mockResource(String content) throws IOException {
		Resource resource = Mockito.mock(Resource.class);
		when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(content);
		return resource;
	}
}
