# Example customer inputs

Realistic free-text customer descriptions for manually trying out the platform end-to-end (create a project → paste one of these into "Free-text evidence" → "Start Requirements Analysis").

Requires a real AI provider to actually succeed - see `.env.example` for `ANTHROPIC_API_KEY` / `SPRING_PROFILES_ACTIVE=real-ai`. Against the mock provider (the default), every run fails output-contract validation deterministically, which is also useful to see (proves the validation pipeline rejects bad output).

- `bakery.txt` - straightforward, no ambiguity or conflicting information. A good first try; this exact text was used to verify AIW-127's real end-to-end success.
- `hair-salon.txt` - a service business with online booking as the standout functional requirement.
- `yoga-studio.txt` - deliberately contains an ambiguity (an unclear detail) and a conflict (two contradictory statements about the same fact), to see the `unknowns`/`conflicts` sections actually populate in the resulting Customer Profile / Website Requirements.

Feel free to add more example files here - anything that's realistic customer-provided evidence is useful for exercising the pipeline.
