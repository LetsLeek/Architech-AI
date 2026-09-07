package ai.architech.backend.core.evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Assigns and persists opaque {@link SourceRef}s for a {@link SourceContext}'s items.
 * Idempotent per snapshot: an item that already has a ref (e.g. a retry reusing the same
 * evidence snapshot) keeps that exact ref rather than getting a new one - refs are stable
 * for the lifetime of a snapshot, not regenerated per attempt.
 */
@Component
public class SourceRefAssigner {

	private final SourceRefRepository sourceRefRepository;

	SourceRefAssigner(SourceRefRepository sourceRefRepository) {
		this.sourceRefRepository = sourceRefRepository;
	}

	public ReferencedSourceContext assignRefs(SourceContext context) {
		Map<UUID, String> existingRefsBySourceItemId =
				sourceRefRepository.findByEvidenceSnapshotId(context.evidenceSnapshotId()).stream()
						.collect(Collectors.toMap(SourceRef::getSourceItemId, SourceRef::getRef));

		List<ReferencedSourceItem> items = new ArrayList<>();
		int nextIndex = existingRefsBySourceItemId.size() + 1;

		for (SourceItem item : context.items()) {
			String ref = existingRefsBySourceItemId.get(item.id());
			if (ref == null) {
				ref = "SRC-" + nextIndex++;
				sourceRefRepository.save(new SourceRef(context.evidenceSnapshotId(), ref, item.id(), item.origin()));
			}
			items.add(new ReferencedSourceItem(ref, item.origin(), item.content()));
		}

		return new ReferencedSourceContext(context.evidenceSnapshotId(), List.copyOf(items));
	}
}
