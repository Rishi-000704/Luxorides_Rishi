package com.core.models.enums;

/*
 * Drives both the access-token expiry tier (see FileAccessTokenService) and,
 * for PUBLIC, whether the issuing code needs to run an ownership check
 * before minting a token at all. Deliberately just three tiers -- see
 * Phase P0.2 report for why a finer-grained model wasn't justified.
 */
public enum FileAccessCategory {
	/** No sensitivity beyond "don't allow arbitrary filesystem access" -- e.g. vehicle marketing photos shown on public estimate links or the catalog. Longer-lived token. */
	PUBLIC,
	/** Ordinary authenticated-context private files -- profile photos, a driver's photo shown to their assigned customer, etc. */
	PRIVATE,
	/** Highest sensitivity -- KYC/identity document scans. Shortest-lived token. */
	KYC
}
