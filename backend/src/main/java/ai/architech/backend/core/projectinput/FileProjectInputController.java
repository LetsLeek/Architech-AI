package ai.architech.backend.core.projectinput;

import ai.architech.backend.core.project.ProjectRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects/{projectId}/files")
class FileProjectInputController {

	private final ProjectRepository projectRepository;
	private final FileProjectInputRepository fileProjectInputRepository;

	FileProjectInputController(
			ProjectRepository projectRepository, FileProjectInputRepository fileProjectInputRepository) {
		this.projectRepository = projectRepository;
		this.fileProjectInputRepository = fileProjectInputRepository;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<FileProjectInputResponse> upload(
			@PathVariable UUID projectId, @RequestParam("file") MultipartFile file) {
		requireProjectExists(projectId);

		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
		}

		FileProjectInput input = new FileProjectInput(
				projectId, file.getOriginalFilename(), file.getContentType(), readBytes(file));
		FileProjectInput saved = fileProjectInputRepository.save(input);
		return ResponseEntity.status(HttpStatus.CREATED).body(FileProjectInputResponse.from(saved));
	}

	@GetMapping
	List<FileProjectInputResponse> list(@PathVariable UUID projectId) {
		requireProjectExists(projectId);

		return fileProjectInputRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
				.map(FileProjectInputResponse::from)
				.toList();
	}

	private void requireProjectExists(UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with id " + projectId);
		}
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read uploaded file", e);
		}
	}
}
