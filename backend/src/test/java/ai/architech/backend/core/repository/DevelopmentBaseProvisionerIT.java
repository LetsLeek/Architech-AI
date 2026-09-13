package ai.architech.backend.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Uses real git repositories under a JUnit temp directory - proves AIW-153's actual acceptance
 * criteria (idempotent provisioning, partial-provisioning recovery, sibling base equality)
 * against real process/filesystem behavior, not mocked plumbing.
 */
@SpringBootTest
class DevelopmentBaseProvisionerIT {

	@Autowired
	private DevelopmentBaseProvisioner provisioner;

	@Test
	void firstProvisionMaterializesTheScaffoldAndCommitsIt(@TempDir Path repositoryRoot) {
		DevelopmentBaseRef base = provisioner.ensureProvisioned(repositoryRoot);

		assertThat(base.commitSha()).isNotBlank();
		assertThat(repositoryRoot.resolve("package.json")).exists();
		assertThat(repositoryRoot.resolve("src/App.tsx")).exists();
		assertThat(repositoryRoot.resolve(".git")).isDirectory();
	}

	@Test
	void containsOnlyTheGenericPlaceholderPageNotAnyCustomerSpecificImplementation(@TempDir Path repositoryRoot)
			throws IOException {
		provisioner.ensureProvisioned(repositoryRoot);

		// The materialized page's actual rendered content is the generic scaffold placeholder -
		// not any proposal-specific page a Developer execution would later replace it with.
		String homePage = Files.readString(repositoryRoot.resolve("src/pages/HomePage.tsx"));
		assertThat(homePage).contains("<main>Website Development Base</main>");
	}

	@Test
	void repeatedEnsureIsIdempotentAndReturnsTheSameBase(@TempDir Path repositoryRoot) {
		DevelopmentBaseRef first = provisioner.ensureProvisioned(repositoryRoot);
		DevelopmentBaseRef second = provisioner.ensureProvisioned(repositoryRoot);

		assertThat(second).isEqualTo(first);
		// A second commit would exist if ensureProvisioned re-committed - it must not have.
		String log = GitCommandRunner.run(repositoryRoot, "log", "--oneline");
		assertThat(log.strip().lines().count()).isEqualTo(1);
	}

	@Test
	void recoversFromAPartialProvisioningAttempt(@TempDir Path repositoryRoot) {
		// Simulates an interrupted first attempt: git init ran, but nothing was ever committed.
		GitCommandRunner.run(repositoryRoot, "init", "--quiet");

		DevelopmentBaseRef base = provisioner.ensureProvisioned(repositoryRoot);

		assertThat(base.commitSha()).isNotBlank();
		assertThat(repositoryRoot.resolve("package.json")).exists();
		String log = GitCommandRunner.run(repositoryRoot, "log", "--oneline");
		assertThat(log.strip().lines().count()).isEqualTo(1);
	}

	@Test
	void siblingWorkspacesProvisionedFromTheSameBaseShareTheExactSameHeadCommit(
			@TempDir Path repositoryRoot, @TempDir Path siblingsParent) {
		DevelopmentBaseRef base = provisioner.ensureProvisioned(repositoryRoot);

		Path workspaceA = provisioner.provisionWorkspace(repositoryRoot, base, siblingsParent.resolve("proposal-a"));
		Path workspaceB = provisioner.provisionWorkspace(repositoryRoot, base, siblingsParent.resolve("proposal-b"));
		Path workspaceC = provisioner.provisionWorkspace(repositoryRoot, base, siblingsParent.resolve("proposal-c"));

		String headA = GitCommandRunner.run(workspaceA, "rev-parse", "HEAD").strip();
		String headB = GitCommandRunner.run(workspaceB, "rev-parse", "HEAD").strip();
		String headC = GitCommandRunner.run(workspaceC, "rev-parse", "HEAD").strip();

		assertThat(headA).isEqualTo(base.commitSha());
		assertThat(headB).isEqualTo(base.commitSha());
		assertThat(headC).isEqualTo(base.commitSha());
	}

	@Test
	void provisionWorkspaceRejectsAMismatchedBaseRef(@TempDir Path repositoryRoot, @TempDir Path siblingsParent) {
		provisioner.ensureProvisioned(repositoryRoot);
		DevelopmentBaseRef wrongBase = new DevelopmentBaseRef("0000000000000000000000000000000000000000");

		assertThatThrownBy(() ->
						provisioner.provisionWorkspace(repositoryRoot, wrongBase, siblingsParent.resolve("proposal-a")))
				.isInstanceOf(RepositoryProvisioningException.class);
	}
}
