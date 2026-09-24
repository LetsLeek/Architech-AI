package ai.architech.backend.core.asset;

import static org.assertj.core.api.Assertions.assertThat;

import ai.architech.backend.core.project.Project;
import ai.architech.backend.core.project.ProjectRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectAssetRepositoryIT {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectAssetRepository projectAssetRepository;

	@Test
	void savesAndReloadsAssetMetadataAndContentByProjectAndRef() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		byte[] content = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

		projectAssetRepository.saveAndFlush(
				new ProjectAsset(project.getId(), "assets/logo.png", "logo.png", "image/png", content));

		ProjectAsset reloaded = projectAssetRepository
				.findByProjectIdAndAssetRef(project.getId(), "assets/logo.png")
				.orElseThrow();
		assertThat(reloaded.getFilename()).isEqualTo("logo.png");
		assertThat(reloaded.getContentType()).isEqualTo("image/png");
		assertThat(reloaded.getSizeBytes()).isEqualTo(content.length);
		assertThat(reloaded.getContent()).isEqualTo(content);
		assertThat(reloaded.getCreatedAt()).isNotNull();
	}

	@Test
	void findsNothingForAnUnknownAssetRef() {
		Project project = projectRepository.saveAndFlush(new Project("website"));

		assertThat(projectAssetRepository.findByProjectIdAndAssetRef(project.getId(), "assets/missing.png"))
				.isEmpty();
	}

	@Test
	void neverResolvesAnAssetAcrossProjectsEvenWithTheSameRef() {
		Project projectA = projectRepository.saveAndFlush(new Project("website"));
		Project projectB = projectRepository.saveAndFlush(new Project("website"));
		byte[] content = "a-content".getBytes(StandardCharsets.UTF_8);
		projectAssetRepository.saveAndFlush(
				new ProjectAsset(projectA.getId(), "assets/shared-name.png", "shared-name.png", "image/png", content));

		assertThat(projectAssetRepository.findByProjectIdAndAssetRef(projectB.getId(), "assets/shared-name.png"))
				.isEmpty();
	}

	@Test
	void rejectsADuplicateAssetRefWithinTheSameProject() {
		Project project = projectRepository.saveAndFlush(new Project("website"));
		byte[] content = "content".getBytes(StandardCharsets.UTF_8);
		projectAssetRepository.saveAndFlush(
				new ProjectAsset(project.getId(), "assets/dup.png", "dup.png", "image/png", content));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> projectAssetRepository.saveAndFlush(
						new ProjectAsset(project.getId(), "assets/dup.png", "dup-again.png", "image/png", content)))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}
}
