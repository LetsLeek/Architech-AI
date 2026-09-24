package ai.architech.backend.core.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Core/Workflow-owned repository provisioning and immutable Development Base (B0) lifecycle for
 * one Website project (AIW-153). The Developer Agent never calls this itself - remote
 * configuration, repository credentials and Git orchestration stay Core authority (matching
 * AIW-139's own opposite half: {@code core.sandbox} is the Developer's bounded, read-mostly Git
 * surface; this class is what puts a repository there in the first place).
 *
 * <p>V1 provisions a local repository only - wiring a real remote host (GitHub/Azure Repos/...)
 * is a separate, explicitly deferred concern requiring real credentials/API access, not part of
 * what this ticket's own acceptance criteria (idempotent provisioning, base identity equality,
 * partial-provisioning recovery) actually needs to prove.
 */
@Component
public class DevelopmentBaseProvisioner {

	private static final String COMMIT_MESSAGE = "Development Base (B0): design-neutral scaffold";
	private static final String AUTHOR_NAME = "Architech Platform";
	private static final String AUTHOR_EMAIL = "platform@architech.ai";

	private final ScaffoldMaterializer scaffoldMaterializer;

	DevelopmentBaseProvisioner(ScaffoldMaterializer scaffoldMaterializer) {
		this.scaffoldMaterializer = scaffoldMaterializer;
	}

	/**
	 * Idempotent: a repository that already has a Development Base commit is returned as-is,
	 * never re-materialized or re-committed. A directory whose {@code .git} exists but has no
	 * commit yet (a prior provisioning attempt that was interrupted after {@code git init} but
	 * before the commit landed) is detected and completed rather than left stuck or duplicated.
	 */
	public DevelopmentBaseRef ensureProvisioned(Path repositoryRoot) {
		Optional<String> existingHead = currentHeadCommit(repositoryRoot);
		if (existingHead.isPresent()) {
			return new DevelopmentBaseRef(existingHead.get());
		}

		try {
			Files.createDirectories(repositoryRoot);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create repository root " + repositoryRoot, e);
		}

		if (!isGitRepository(repositoryRoot)) {
			GitCommandRunner.run(repositoryRoot, "init", "--quiet");
		}
		GitCommandRunner.run(repositoryRoot, "config", "user.name", AUTHOR_NAME);
		GitCommandRunner.run(repositoryRoot, "config", "user.email", AUTHOR_EMAIL);

		scaffoldMaterializer.materializeInto(repositoryRoot);

		GitCommandRunner.run(repositoryRoot, "add", "-A");
		GitCommandRunner.run(repositoryRoot, "commit", "--quiet", "-m", COMMIT_MESSAGE);

		return new DevelopmentBaseRef(requireHeadCommit(repositoryRoot));
	}

	/**
	 * Clones an isolated workspace for one proposal-scoped execution from the provisioned
	 * repository. Every sibling (A/B/C) execution calls this against the exact same {@code base}
	 * - the returned workspace's own HEAD is verified to match it exactly, proving sibling base
	 * equality rather than merely assuming the clone succeeded correctly.
	 */
	public Path provisionWorkspace(Path repositoryRoot, DevelopmentBaseRef base, Path targetWorkspaceRoot) {
		Path cloneWorkingDirectory = repositoryRoot.getParent() != null ? repositoryRoot.getParent() : repositoryRoot;
		GitCommandRunner.run(
				cloneWorkingDirectory, "clone", "--quiet", repositoryRoot.toString(), targetWorkspaceRoot.toString());

		String actualHead = requireHeadCommit(targetWorkspaceRoot);
		if (!actualHead.equals(base.commitSha())) {
			throw new RepositoryProvisioningException(
					"Provisioned workspace HEAD (" + actualHead + ") does not match the requested Development Base ("
							+ base.commitSha() + ")");
		}
		return targetWorkspaceRoot;
	}

	private boolean isGitRepository(Path root) {
		return Files.isDirectory(root.resolve(".git"));
	}

	private Optional<String> currentHeadCommit(Path repositoryRoot) {
		if (!isGitRepository(repositoryRoot)) {
			return Optional.empty();
		}
		try {
			return Optional.of(requireHeadCommit(repositoryRoot));
		} catch (RepositoryProvisioningException e) {
			// git init already ran but no commit exists yet - a prior provisioning attempt was
			// interrupted before the commit landed; ensureProvisioned resumes it, not this method.
			return Optional.empty();
		}
	}

	private String requireHeadCommit(Path repositoryRoot) {
		return GitCommandRunner.run(repositoryRoot, "rev-parse", "HEAD").strip();
	}
}
