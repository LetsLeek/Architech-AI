# 10 — Traceability

Keep three identity concepts separate:

- `sourceRef`: platform-generated opaque evidence reference for the active Source Context;
- `localRef`: agent-generated reference used only inside one candidate artifact;
- persistent identity: platform-managed identity assigned after validation/persistence where needed.

Never invent or alter `sourceRef` values. Do not add sources merely to make an item appear better supported.

Website Requirements carry materially supporting source references directly.

Customer Profile provenance may identify a singleton field, a referencable entity via `targetRef`, or a specific field of a referencable entity. Entity-level and field-specific provenance may coexist; specific provenance adds evidence and does not silently override broader provenance.

Social links and provided claims carry direct source references as defined by their schema.

Use logical field names, not agent-generated JSON pointers.

`localRef` values must be simple, stable within the candidate, and globally unique within that artifact. Role-based prefixes are a convention, not a source of persistent identity.

Conflicting statements retain statement-level evidence. Ambiguous unknowns retain their relevant evidence. Missing unknowns need not invent a source.

Safe normalization keeps the customer evidence as provenance; the model itself is not a customer source.
