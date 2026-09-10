package com.core.services;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.core.dtos.pub.AppVersionCheckResponse;

/*
 * Backend-authoritative force-update gate for the Chauffeur app. The
 * client never decides whether it must update -- it only ever reports its
 * own installed version and trusts forceUpdate verbatim, so the minimum
 * supported version can be raised centrally (config change + redeploy,
 * no app-store review needed) the moment a truly breaking client bug is
 * found.
 *
 * Config-driven rather than a new DB table/admin screen -- there is no
 * existing settings UI for this, and one new property pair is the smallest
 * change that satisfies "backend-controlled" without a redeploy-per-driver
 * or a speculative admin feature nothing asked for yet.
 */
@Service
public class AppVersionService {

	private final String minimumSupportedVersion;
	private final String latestVersion;

	public AppVersionService(
			@Value("${app.chauffeur.min-supported-version}") String minimumSupportedVersion,
			@Value("${app.chauffeur.latest-version}") String latestVersion) {
		this.minimumSupportedVersion = minimumSupportedVersion;
		this.latestVersion = latestVersion;
	}

	private static final Pattern DOTTED_NUMERIC_VERSION = Pattern.compile("^\\d+(\\.\\d+)*$");

	/*
	 * installedVersion missing or unparseable never forces an update --
	 * this endpoint must fail safe. A driver on a malformed/unreported
	 * version is left alone rather than locked out by a client-reporting
	 * bug or an unexpected version string shape. Format is validated
	 * up front (not just "parses to something") -- compare()'s per-segment
	 * parse treats a non-numeric segment as 0, so an unvalidated garbage
	 * string like "not-a-version" would parse as "0" and compare as LOWER
	 * than any real minimum version, incorrectly forcing an update for
	 * exactly the malformed-report case this must protect against.
	 */
	public AppVersionCheckResponse check(String installedVersion) {
		boolean forceUpdate = installedVersion != null
				&& DOTTED_NUMERIC_VERSION.matcher(installedVersion.trim()).matches()
				&& compare(installedVersion, minimumSupportedVersion) < 0;
		return new AppVersionCheckResponse(minimumSupportedVersion, latestVersion, forceUpdate);
	}

	/*
	 * Segment-by-segment numeric comparison of dotted version strings
	 * ("1.2.10" vs "1.3.0") -- a lexical string compare would incorrectly
	 * rank "1.9.0" above "1.10.0". Missing trailing segments compare as 0
	 * ("1.2" == "1.2.0"). Non-numeric segments/garbage input parse as 0
	 * rather than throwing, keeping this fail-safe by construction.
	 */
	static int compare(String a, String b) {
		int[] pa = parse(a);
		int[] pb = parse(b);
		int len = Math.max(pa.length, pb.length);
		for (int i = 0; i < len; i++) {
			int va = i < pa.length ? pa[i] : 0;
			int vb = i < pb.length ? pb[i] : 0;
			if (va != vb) {
				return Integer.compare(va, vb);
			}
		}
		return 0;
	}

	private static int[] parse(String version) {
		String[] parts = version.trim().split("\\.");
		int[] nums = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			String digitsOnly = parts[i].replaceAll("[^0-9]", "");
			nums[i] = digitsOnly.isEmpty() ? 0 : Integer.parseInt(digitsOnly);
		}
		return nums;
	}
}
