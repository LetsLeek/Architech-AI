# Artifact Candidate and Persistence Contract — V1

## Candidate vs Canonical Artifact

Raw or parsed AI output is a candidate, not a canonical artifact.

Invalid candidates may be retained with AgentExecution/audit data for debugging, cost analysis, and traceability, but must not be registered as valid canonical ArtifactVersions.

## Atomic Required Outputs

All required outputs of one Requirements Agent attempt form a single atomic validation and persistence unit.

For V1 those outputs are:
- `customer-profile`;
- `website-requirements`.

If either required candidate fails required validation, neither candidate becomes canonical.

When both pass, persistence should be transactional or provide equivalent atomic semantics so downstream consumers cannot observe a half-persisted requirements result.

## Versioning

Validated outputs become versioned artifacts linked to the AgentExecution that produced them.

Persistent platform identity, artifact version identity, and any cross-version identity are platform responsibilities. Agent-generated `localRef` values remain candidate/artifact-local and must not be treated as persistent database identity.

## Audit Separation

The platform should preserve the distinction between:

- execution/audit history: model call, candidate output, validation issues, tokens, cost, timestamps, attempts, failure reasons;
- canonical artifact history: validated versioned business/project artifacts;
- generated project Git history: website code and project-specific repository documentation.

Do not use Git as a substitute for AgentExecution audit history.

## Upstream Authority

Downstream artifacts and agents must not silently correct or overwrite canonical upstream artifacts.

A correction to canonical requirements/customer facts requires a new validated artifact version through an authorized workflow, not an undocumented downstream mutation.
