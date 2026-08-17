package com.core.util;

import com.core.models.Package;
import com.core.models.embedded.PackageSnapshot;

public final class PackageUtil {
	private PackageUtil() {
	}

	public static PackageSnapshot toPackageSnapshot(Package pack) {
		if (pack == null) {
			return null;
		}
		return new PackageSnapshot(pack.getId(), pack.getScope(), pack.getDutyType(), pack.getTime(),
				pack.getDistance(), pack.getUnit(), pack.getBaseFare(), pack.getExtraPerKM(), pack.getExtraPerHS(),
				pack.getNightCharge());
	}
}
