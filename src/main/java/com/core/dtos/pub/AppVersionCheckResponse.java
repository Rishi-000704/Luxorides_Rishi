package com.core.dtos.pub;

/*
 * Deliberately minimal -- served with NO authentication (see
 * PublicAppVersionController), so only version numbers already visible in
 * the published app store listing are exposed. No build metadata, no
 * internal config, no secrets.
 */
public record AppVersionCheckResponse(
		String minimumSupportedVersion,
		String latestVersion,
		boolean forceUpdate
) {
}
