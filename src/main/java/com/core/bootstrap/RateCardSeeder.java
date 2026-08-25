package com.core.bootstrap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.core.models.MasterVehicle;
import com.core.models.Package;
import com.core.models.embedded.Money;
import com.core.models.enums.DutyType;
import com.core.models.enums.PackageScope;
import com.core.models.enums.VehicleStatus;
import com.core.repositories.MasterVehicleRepository;
import com.core.repositories.PackageRepository;

import lombok.RequiredArgsConstructor;

/*
 * Loads the real chauffeur-drive rate card (as opposed to DevDataSeeder's dummy
 * ₹4,500-flat-fee sample vehicles) from the source rate-card PDFs the client provided.
 * Transcribed by hand from those PDFs -- see the "roster" comment on each dataset for
 * which source file it came from and how confident that transcription is.
 *
 * Idempotent and runs on every startup regardless of the "already seeded" org guard in
 * DevDataSeeder, same pattern as seedConsoleSmsProviderIfMissing/
 * seedMockPaymentGatewayIfMissing: checked by MasterVehicle.slug (unique per roster),
 * so re-running never duplicates rows.
 */
@Component
@RequiredArgsConstructor
public class RateCardSeeder {

	private final MasterVehicleRepository masterVehicleRepository;
	private final PackageRepository packageRepository;

	/*
	 * One row per vehicle: name, brand, category, local(8h/80km), outstation(per km,
	 * billed at minimum 250km/day), airportTransfer(4h/40km), wedding(12h/100km),
	 * postlimitPerKm, postlimitPerHour, nightAllowance, forSale.
	 * null = "-" in the source PDF (no package offered for that duty type).
	 *
	 * Roster: carwiseratelist.pdf -- one flat table, no city split, high transcription
	 * confidence.
	 */
	private static final Object[][] STANDARD_ROSTER = {
			{ "Audi A3", "Audi", "Cabriolet", 18500, 180, 12000, 35000, 185, 1850, 1500, true },
			{ "Audi A4+ Sedan", "Audi", "Sedan", null, null, null, 18000, 150, 1500, null, true },
			{ "Audi A6 Sedan", "Audi", "Sedan", 12500, 120, 8500, 35000, 125, 1250, 1500, true },
			{ "Audi A8 Sedan", "Audi", "Sedan", null, 325, 22500, null, 350, 3500, 3500, true },
			{ "Audi Q3 SUV", "Audi", "SUV", null, 150, null, 28500, 150, 1500, 1500, false },
			{ "Audi Q5 SUV", "Audi", "SUV", null, 200, null, 51000, 200, 2000, 2000, false },
			{ "Audi Q7", "Audi", "SUV", 22500, 220, 18500, 85000, 225, 2250, 2200, true },
			{ "Bentley Flying Spur", "Bentley", "Sedan", null, 1500, 28500, null, null, 15000, 15000, false },
			{ "BMW 3 Series", "BMW", "Sedan", null, null, null, 28500, 280, 2800, 2500, false },
			{ "BMW 5 Series", "BMW", "Sedan", 13500, 130, 9500, 35000, 135, 1350, 1500, true },
			{ "BMW 7 Series", "BMW", "Sedan", 28500, 280, 22500, 150000, 285, 2800, 2500, true },
			{ "BMW iX", "BMW", "SUV", 28500, 250, 18500, null, 280, 2850, 2500, true },
			{ "BMW X7", "BMW", "SUV", 25500, 240, 19500, null, 255, 2550, 2500, true },
			{ "Chrysler Limousine", "Chrysler", "Stretched Vehicle", null, 500, 95000, 125000, 500, 5000, 5000, false },
			{ "Ford Endeavour SUV", "Ford", "SUV", 8500, 80, 5500, 18500, 85, 850, 1000, false },
			{ "Ford Mustang", "Ford", "Sports", 125000, 500, 80000, 125000, 500, 5000, 5000, true },
			{ "Honda City", "Honda", "Sedan", 4000, 38, 2750, null, 40, 400, 500, true },
			{ "Hummer Premium SUV", "General Motors", "SUV", 65000, 500, 65000, 75000, 500, 5000, 5000, false },
			{ "Jaguar XE Sedan", "Jaguar", "Sedan", null, null, null, 28500, 280, 2800, 2500, false },
			{ "Jaguar XF", "Jaguar", "Sedan", 14500, 140, 10500, 35000, 145, 1450, 1500, true },
			{ "Jaguar XJL", "Jaguar", "Sedan", 28500, 280, null, 65000, 285, 2850, 4000, true },
			{ "Kia Carnival", "Kia", "MPV", 12500, 120, 8500, 21000, 125, 1250, 1000, true },
			{ "Land Rover Defender", "Land Rover", "SUV", 25500, 220, 19500, 65000, 225, 2550, 2000, true },
			{ "Maruti Suzuki Ciaz", "Maruti Suzuki", "Sedan", 4000, 38, 2750, null, 40, 400, 500, true },
			{ "Maruti Suzuki Dzire", "Maruti Suzuki", "Sedan", 2500, 25, 1750, null, 25, 250, 300, true },
			{ "Mercedes Benz C Class 300 Cabriolet", "Mercedes", "Cabriolet", 35000, 350, null, 40000, 350, 3500, 3500, true },
			{ "Mercedes Benz CLA", "Mercedes", "Sedan", null, null, null, 28500, 280, 2800, 2500, false },
			{ "Mercedes Benz CLE", "Mercedes", "Cabriolet", 38500, 130, 9500, 120000, 385, 3850, 10000, true },
			{ "Mercedes Benz E Class", "Mercedes", "Sedan", 13500, 130, 9500, 35000, 135, 1350, 1500, true },
			{ "Mercedes Benz G Wagon", "Mercedes", "Sports SUV", 150000, 750, 125000, 150000, 750, 7500, 5000, true },
			{ "Mercedes Benz GLE 450", "Mercedes", "SUV", 19500, 190, 15500, 55000, 195, 1950, 1800, true },
			{ "Mercedes Benz GLS", "Mercedes", "SUV", 25500, 240, 19500, 85000, 255, 2550, 2500, true },
			{ "Mercedes Benz Sprinter", "Mercedes", "VAN", 22500, 220, 18500, null, 225, 2250, 2200, true },
			{ "Mercedes Benz V-Class", "Mercedes", "MPV", 25500, 240, 18500, 125000, 255, 2500, 2500, true },
			{ "Mercedes Maybach S580", "Mercedes", "Sedan", 55000, 500, 40500, 150000, 550, 5500, 5000, true },
			{ "Mercedes S-Class", "Mercedes", "Sedan", 25500, 240, 19500, 150000, 255, 2550, 2500, true },
			{ "Porsche Boxster", "Porsche", "Cabriolet Sports", 125000, 500, 80000, 150000, 500, 5000, 5000, false },
			{ "Porsche Cayenne", "Porsche", "Sports SUV", 150000, 750, 125000, 125000, 750, 7500, 5000, false },
			{ "Range Rover Autobiography", "Range Rover", "SUV", 55000, 500, 40500, 150000, 550, 5500, 5000, true },
			{ "Range Rover Evoque SUV", "Range Rover", "SUV", null, 250, null, 35000, 250, 2500, 2500, false },
			{ "Range Rover Velar", "Range Rover", "SUV", 30000, 280, null, 65000, 300, 3000, 4000, true },
			{ "Rolls Royce 1939 Vintage (Red)", "Rolls Royce", "Vintage", null, null, null, null, null, 5000, 5000, false },
			{ "Rolls Royce Ghost", "Rolls Royce", "Sedan", 350000, 3500, 350000, 550000, 3500, 35000, 35000, true },
			{ "Rolls Royce Phantom Coupe", "Rolls Royce", "Sedan", null, null, 350000, null, null, 35000, 35000, false },
			{ "Toyota Camry Hybrid", "Toyota", "Sedan", 8500, 80, 5500, 18500, 85, 850, 1000, true },
			{ "Toyota Corolla Altis", "Toyota", "Sedan", 4000, 38, 2750, null, 40, 400, 500, true },
			{ "Toyota Etios", "Toyota", "Sedan", 2500, 25, 1750, null, 25, 250, 300, true },
			{ "Toyota Fortuner", "Toyota", "SUV", 8500, 80, 5500, 15000, 85, 850, 1000, true },
			{ "Toyota HiAce", "Toyota", "VAN", 13500, 130, 11500, null, 135, 1350, 1500, true },
			{ "Toyota Hycross", "Toyota", "SUV", 6500, 60, 4000, null, 65, 650, 800, true },
			{ "Toyota Innova Crysta", "Toyota", "SUV", 4000, 38, 2750, null, 40, 400, 500, true },
			{ "Toyota Land Cruiser", "Toyota", "MPV", 30500, 300, 25500, 125000, 305, 3050, 3000, true },
			{ "Toyota Vellfire", "Toyota", "MPV", 25500, 240, 19500, 125000, 255, 2550, 2500, true },
			{ "Volvo 9600 (With Washroom)", "Volvo", "Bus", 28500, 250, null, null, null, 2850, 4000, true },
	};

	/*
	 * The customer app's Explorer/Trending catalog (VehicleCatalogService,
	 * ExploreView) filters Package.location against a fixed city list and defaults to
	 * "Delhi" -- an untagged or made-up location string would never surface here, so
	 * this has to be a real city from that list, not a label of convenience.
	 */
	private static final String DELHI_LOCATION = "Delhi";
	private static final String STANDARD_SLUG_PREFIX = "std-";

	public void seedIfMissing(String orgId) {
		seedRoster(orgId, STANDARD_ROSTER, DELHI_LOCATION, STANDARD_SLUG_PREFIX);
	}

	private void seedRoster(String orgId, Object[][] roster, String location, String slugPrefix) {
		for (Object[] row : roster) {
			String name = (String) row[0];
			String slug = slugPrefix + slugify(name);

			if (masterVehicleRepository.findByOrgIdAndSlug(orgId, slug).isPresent()) {
				continue;
			}

			MasterVehicle vehicle = new MasterVehicle();
			vehicle.setOrgId(orgId);
			vehicle.setName(name);
			vehicle.setBrand((String) row[1]);
			vehicle.setCategory((String) row[2]);
			vehicle.setSlug(slug);
			vehicle.setChauffeurDriven(true);
			vehicle.setStatus(Boolean.TRUE.equals(row[10]) ? VehicleStatus.PUBLISHED : VehicleStatus.DRAFT);
			vehicle.setRemarks("Rate card: " + location);
			// Without these, every row here defaults to null and sorts behind the
			// existing dummy sample vehicles (popularity=90) in findTrendingByLocation's
			// ORDER BY popularity DESC -- these real vehicles would never actually
			// surface on the app's default landing view otherwise.
			vehicle.setRating(4);
			vehicle.setPopularity(60);

			vehicle = masterVehicleRepository.save(vehicle);

			List<Package> packages = new ArrayList<>();

			addPackageIfPriced(packages, orgId, vehicle.getId(), DutyType.LOCAL, 8, "HOUR", 80,
					row[3], row[7], row[8], row[9], location, row[10]);

			// Outstation is quoted per-km with a 250km/day minimum in the source rate card --
			// baseFare is that minimum day's charge (rate x 250), extraPerKM is the same
			// per-km rate for distance beyond the minimum.
			addOutstationPackageIfPriced(packages, orgId, vehicle.getId(), row[4], row[7], row[8], row[9], location, row[10]);

			addPackageIfPriced(packages, orgId, vehicle.getId(), DutyType.TRANSFER, 4, "HOUR", 40,
					row[5], row[7], row[8], row[9], location, row[10]);

			addPackageIfPriced(packages, orgId, vehicle.getId(), DutyType.WEDDING, 12, "HOUR", 100,
					row[6], row[7], row[8], row[9], location, row[10]);

			if (!packages.isEmpty()) {
				packageRepository.saveAll(packages);
			}
		}
	}

	private void addPackageIfPriced(
			List<Package> out, String orgId, String masterVehicleId, DutyType dutyType,
			Integer time, String unit, Integer distance,
			Object baseFare, Object perKm, Object perHour, Object nightAllowance,
			String location, Object forSale
	) {
		if (baseFare == null) {
			return;
		}

		out.add(new Package(
				null, orgId, PackageScope.MASTER, null, masterVehicleId, dutyType,
				time, unit, distance,
				money(baseFare), money(perKm), money(perHour), money(nightAllowance),
				Boolean.TRUE.equals(forSale), location, null, null
		));
	}

	private void addOutstationPackageIfPriced(
			List<Package> out, String orgId, String masterVehicleId,
			Object perKmRate, Object perKm, Object perHour, Object nightAllowance,
			String location, Object forSale
	) {
		if (perKmRate == null) {
			return;
		}

		BigDecimal minimumDailyCharge = BigDecimal.valueOf((Integer) perKmRate).multiply(BigDecimal.valueOf(250));

		out.add(new Package(
				null, orgId, PackageScope.MASTER, null, masterVehicleId, DutyType.OUTSTATION,
				null, "KM", 250,
				Money.INR(minimumDailyCharge), money(perKm), money(perHour), money(nightAllowance),
				Boolean.TRUE.equals(forSale), location, null, null
		));
	}

	private Money money(Object value) {
		if (value == null) {
			return Money.INR(BigDecimal.ZERO);
		}
		return Money.INR(BigDecimal.valueOf((Integer) value));
	}

	private String slugify(String name) {
		return name.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}
}
