package ai.architech.backend.core.verification;

/**
 * One authorized external network target - a host plus the exact purpose it is authorized for
 * (a Runtime Profile capability, an authorized project asset, or an Integration Contract
 * binding). A host authorized for one purpose does not authorize a different, undeclared
 * purpose against that same host - "Authorized Integration Contract targets are allowed only for
 * the declared operation/binding" is AIW-157's own requirement, not merely "the host is on a
 * list somewhere."
 */
public record AuthorizedExternalTarget(String host, String authorizedPurpose) {}
