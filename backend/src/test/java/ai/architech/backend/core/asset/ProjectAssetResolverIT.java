package ai.architech.backend.core.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectAssetResolverIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectAssetRepository projectAssetRepository;

	@Autowired
	private ProjectAssetResolver projectAssetResolver;

	@Test
	void resolvesAPersistedAssetForItsOwningProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		byte[] content = "logo-bytes".getBytes(StandardCharsets.UTF_8);
		projectAssetRepository.saveAndFlush(
				new ProjectAsset(project.getId(), "assets/logo.png", "logo.png", "image/png", content));

		ProjectAsset resolved = projectAssetResolver.resolveRequired(project.getId(), "assets/logo.png");

		assertThat(resolved.getContent()).isEqualTo(content);
	}

	@Test
	void resolveOptionalIsEmptyForAMissingOptionalAsset() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		assertThat(projectAssetResolver.resolveOptional(project.getId(), "assets/optional-hero.png")).isEmpty();
	}

	@Test
	void resolveRequiredThrowsAndBlocksCompletionWhenTheAssetIsAbsent() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		assertThatThrownBy(() -> projectAssetResolver.resolveRequired(project.getId(), "assets/missing-required.png"))
				.isInstanceOf(MissingProjectAssetException.class);
	}

	@Test
	void neverResolvesAnAssetForAnUnrelatedProjectEvenWithAValidRef() {
		Project owningProject = projectRepository.saveAndFlush(new Project("website"));
		Project otherProject = projectRepository.saveAndFlush(new Project("website"));
		projectAssetRepository.saveAndFlush(new ProjectAsset(
				owningProject.getId(), "assets/logo.png", "logo.png", "image/png",
				"logo-bytes".getBytes(StandardCharsets.UTF_8)));

		assertThat(projectAssetResolver.resolveOptional(otherProject.getId(), "assets/logo.png")).isEmpty();
		assertThatThrownBy(() -> projectAssetResolver.resolveRequired(otherProject.getId(), "assets/logo.png"))
				.isInstanceOf(MissingProjectAssetException.class);
	}

	@Test
	void resolvingAnUnknownProjectIdNeverThrowsAnUnrelatedFailure() {
		assertThat(projectAssetResolver.resolveOptional(UUID.randomUUID(), "assets/logo.png")).isEmpty();
	}
}
