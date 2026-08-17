package com.core.bootstrap;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.core.models.CityGarage;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.Driver;
import com.core.models.Employee;
import com.core.models.FleetVehicle;
import com.core.models.MasterVehicle;
import com.core.models.Org;
import com.core.models.User;
import com.core.models.embedded.AddressSnapshot;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Money;
import com.core.models.embedded.Name;
import com.core.models.Package;
import com.core.models.Passenger;
import com.core.models.enums.AccountType;
import com.core.models.enums.Authority;
import com.core.models.enums.DutyType;
import com.core.models.enums.OrgStatus;
import com.core.models.enums.OwnershipType;
import com.core.models.enums.PackageScope;
import com.core.models.enums.VehicleStatus;
import com.core.repositories.*;

@Component
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

	@Value("${app.seed.enabled:false}")
	private boolean seedEnabled;

	private final OrgRepository orgRepository;
	private final UserRepository userRepository;
	private final EmployeeRepository employeeRepository;
	private final ClientRepository clientRepository;
	private final ClientBillingEntityRepository clientBillingEntityRepository;
	private final MasterVehicleRepository masterVehicleRepository;
	private final FleetVehicleRepository fleetVehicleRepository;
	private final PackageRepository packageRepository;
	private final PassengerRepository passengerRepository;
	private final DriverRepository driverRepository;
	private final CityGarageRepository garageRepository;
	private final Test test;

	@Override
	public void run(String... args) {

		test.testAirport();

		if (!seedEnabled) {
			return;
		}

		// 🚫 Already seeded → exit
		if (orgRepository.count() > 0) {
			return;
		}

		System.out.println("Seeding dummy data");

		try {
			Org org = seedOrg();
			seedUsersAndEmployees();
			List<ClientBillingEntity> billingEntities = seedClientBillingEntities(org);
			List<Client> clients = seedClients(org, billingEntities);
			seedPassengers(org, clients);
			seedDrivers(org, clients);
			List<MasterVehicle> masterVehicles = seedMasterVehicles(org);
			seedFleetVehicles(org, clients, masterVehicles);
			seedPackages(org, clients, masterVehicles);
			this.seedGarage();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		System.out.println("✅ Fleetovo dummy data seeded successfully");
	}

	private void seedGarage() {
		AddressSnapshot delhiGarage = new AddressSnapshot(
				"Farm 47, Umbrella Estate Rd, D Block, Kapas Hera Estate, New Delhi, Delhi 110037, India",
				"ChIJ13caZtoZDTkReHKnLsCvPAQ", 28.5194283, 77.0910545);
		AddressSnapshot mumbaiGarage = new AddressSnapshot(
				"MCGM Park, Tunga Village, Chandivali, Andheri East, Mumbai, Maharashtra 400072, India",
				"ChIJXS-EfQDJ5zsRP38tiQGx2fY", 19.11809402407151, 72.89229160876648);

		garageRepository.saveAll(List.of(new CityGarage(null, "demo", "Delhi", delhiGarage),
				new CityGarage(null, "demo", "Mumbai", mumbaiGarage)));
	}

	private Org seedOrg() {

		Org luxorides = new Org();
		luxorides.setOrgId("luxorides");
		luxorides.setOrgName("Luxorides");
		luxorides.setAddress(new DisplayAddress("CP, Delhi", "New Delhi", "Delhi", "123123", "IN"));
		luxorides.setPan("ABCDE1234F");
		luxorides.setCin("XXXXX");
		luxorides.setGstin("09ABCDE1234F1Z5");
		luxorides.setPhone("+918840844024");
		luxorides.setEmail("admin@luxorides.com");
		luxorides.setStatus(OrgStatus.ACTIVE);
		orgRepository.save(luxorides);

		Org demo = new Org();
		demo.setOrgId("demo");
		demo.setOrgName("Fleetovo Demo Org");
		demo.setAddress(new DisplayAddress("CP, Delhi", "New Delhi", "Delhi", "123123", "IN"));
		demo.setPan("ABCDE1234F");
		demo.setCin("XXXXX");
		demo.setGstin("09ABCDE1234F1Z5");
		demo.setPhone("+918840844024");
		demo.setEmail("demo@fleetovo.com");
		demo.setStatus(OrgStatus.ACTIVE);

		return orgRepository.save(demo);
	}

	private void seedUsersAndEmployees() {
		
		List<Authority> allAuthorities = List.of(Authority.values());

		// RAHUL
		User luxoUser1 = new User();
		luxoUser1.setAccountType(AccountType.EMPLOYEE);
		luxoUser1.setPhone("+919718776886");
		luxoUser1.setEnabled(true);
		// password is Rahul$0101
		luxoUser1.setPassword("$2a$12$sLLG6Nev92yY1KQh5W//VOdFAPOD2mckn9XiKFk5ucv7ED.YpeC1G");
		luxoUser1.setOrgId("luxorides");
		luxoUser1.setAuthorities(allAuthorities);
		luxoUser1 = userRepository.save(luxoUser1);

		Employee luxoEmp1 = new Employee();
		luxoEmp1.setUserId(luxoUser1.getId());
		luxoEmp1.setName(new Name("Mr", "Rahul", "Nain"));
		luxoEmp1.setPhone(luxoUser1.getPhone());
		luxoEmp1.setOrgId("luxorides");

		employeeRepository.save(luxoEmp1);

		// AADITYA
		User luxoUser2 = new User();
		luxoUser2.setAccountType(AccountType.EMPLOYEE);
		luxoUser2.setPhone("+919044469238");
		luxoUser2.setEnabled(true);
		// password is Aaditya#0987
		luxoUser2.setPassword("$2a$12$276LpgTqurmBV47Wc/bQ.OrGcHq2Zsm7ZQ.8thLPMGZ0hsk8tNAgW");
		luxoUser2.setOrgId("luxorides");
		luxoUser2.setAuthorities(allAuthorities);
		luxoUser2 = userRepository.save(luxoUser2);

		Employee luxoEmp2 = new Employee();
		luxoEmp2.setUserId(luxoUser2.getId());
		luxoEmp2.setName(new Name("Mr", "Aaditya", "Mishra"));
		luxoEmp2.setPhone(luxoUser2.getPhone());
		luxoEmp2.setOrgId("luxorides");

		employeeRepository.save(luxoEmp2);

		// SHIDHARTH
		User luxoUser3 = new User();
		luxoUser3.setAccountType(AccountType.EMPLOYEE);
		luxoUser3.setPhone("+918287568015");
		luxoUser3.setEnabled(true);
		// password is Sid#1234
		luxoUser3.setPassword("$2a$12$/UkpvT322.u0BxDz8vcX8.IklxHYP.C8mbHCReLGBcxRo5vY.l61C");
		luxoUser3.setOrgId("luxorides");
		luxoUser3 = userRepository.save(luxoUser3);

		Employee luxoEmp3 = new Employee();
		luxoEmp3.setUserId(luxoUser3.getId());
		luxoEmp3.setName(new Name("Mr", "Sidharth", "Patel"));
		luxoEmp3.setPhone(luxoUser3.getPhone());
		luxoEmp3.setOrgId("luxorides");

		employeeRepository.save(luxoEmp3);

		// VIJAY
		User luxoUser4 = new User();
		luxoUser4.setAccountType(AccountType.EMPLOYEE);
		luxoUser4.setPhone("+917701820975");
		luxoUser4.setEnabled(true);
		// password is Luxorides#1234
		luxoUser4.setPassword("$2a$12$UumnvuuIVRUQmlgQEtR0nenBOw40SVLaSkr4KdOrihlN.LteL6jZC");
		luxoUser4.setOrgId("luxorides");
		luxoUser4 = userRepository.save(luxoUser4);

		Employee luxoEmp4 = new Employee();
		luxoEmp4.setUserId(luxoUser4.getId());
		luxoEmp4.setName(new Name("Mr", "Vijay", "Kohliya"));
		luxoEmp4.setPhone(luxoUser4.getPhone());
		luxoEmp4.setOrgId("luxorides");

		employeeRepository.save(luxoEmp4);

		User adminUser = new User();
		adminUser.setAccountType(AccountType.EMPLOYEE);
		adminUser.setPhone("+918840844024");
		adminUser.setEnabled(true);
		// password is demo
		adminUser.setPassword("$2a$12$tjkwHqmRgTLPaJRzOlNWre0fbxGNyU08Y9DvUTawJI.eYl1tEsUH.");
		adminUser.setOrgId("demo");
		adminUser.setAuthorities(allAuthorities);
		adminUser = userRepository.save(adminUser);

		Employee emp = new Employee();
		emp.setId("e-admin");
		emp.setUserId(adminUser.getId());
		emp.setName(new Name("Mr", "Amit", "Mishra"));
		emp.setPhone("+918840844024");
		emp.setOrgId("demo");

		employeeRepository.save(emp);
	}

	private List<ClientBillingEntity> seedClientBillingEntities(Org org) {

		return clientBillingEntityRepository.saveAll(List.of(
				new ClientBillingEntity(null, org.getOrgId(), "ABC Corp", "ABC Technologies Pvt Ltd",
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), "CIN123",
						"09ABCDE1234F1Z5", "+918840844020", "billing@abc.com", "IT"),

				new ClientBillingEntity(null, org.getOrgId(), "XYZ Ltd", "XYZ Logistics Ltd",
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), "CIN456",
						"07ABCDE1234F1Z6", "+918840844021", "accounts@xyz.com", "Logistics"),

				new ClientBillingEntity(null, org.getOrgId(), "DUMMY", "Demo Corp Pvt. Ltd.",
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null, "07ABCDE9999F1Z9",
						"+918840844022", null, "Individual")));
	}

	private List<Client> seedClients(Org org, List<ClientBillingEntity> entities) {
		return clientRepository.saveAll(List.of(
				new Client(null, org.getOrgId(), null, new Name("Mr.", "Rahul", "Sharma"), "rahul@mail.com",
						"+918840844023", new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null,
						false, List.of(entities.get(0).getId()), null, null),

				new Client(null, org.getOrgId(), null, new Name("Mr.", "Aditya", "Sharma"), "amit@mail.com",
						"+918840844028", new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null,
						false, List.of(entities.get(0).getId()), null, null),

				new Client(null, org.getOrgId(), null, new Name("Ms.", "HQDC", ""), "corp@mail.com", "+918840844025",
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null, true,
						List.of(entities.get(2).getId(), entities.get(1).getId()), null, null)));
	}

	private void seedPassengers(Org org, List<Client> clients) {

		passengerRepository
				.saveAll(
						clients.stream()
								.flatMap(client -> List.of(new Passenger(null, org.getOrgId(), client.getId(),
										new Name("Mr.", "Mazanoo", "Prasad"), "+918840844025", client.getEmail()),
										new Passenger(null, org.getOrgId(), client.getId(),
												new Name("Miss", "Laila", "Batliwala"), "+918840844026", null),
										new Passenger(null, org.getOrgId(), client.getId(),
												new Name("Mrs.", "Rinku", "Devi"), "+918840844027", null))
										.stream())
								.toList());
	}

	private void seedDrivers(Org org, List<Client> clients) {

		driverRepository.saveAll(List.of(

				// ORG driver
				new Driver(null, org.getOrgId(), null, new Name("Mr.", "Mahesh", "Singh"),
						new Name("Mr.", "Suresh", "Singh"), "MALE", "+918840844028", null,
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null,
						"DL-1420110012345", null, OwnershipType.ORG, null),

				// CLIENT drivers
				new Driver(null, org.getOrgId(), clients.get(2).getId(), new Name("Mr.", "Ramesh", "Prasad"),
						new Name("Mr.", "Kumesh", "Kumar"), "MALE", "+918840844029", null,
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null,
						"DL-1420110067890", null, OwnershipType.CLIENT, null),

				new Driver(null, org.getOrgId(), clients.get(2).getId(), new Name("Mr.", "Manoj", "Bajpeyi"),
						new Name("Mr.", "Mahaveer", "Prasad"), "MALE", "+918840844030", null,
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null,
						"DL-1420110098765", null, OwnershipType.CLIENT, null),

				new Driver(null, org.getOrgId(), clients.get(1).getId(), new Name("Mr.", "Umakant", "Pandey"),
						new Name("Mr.", "Umesh", "Prasad"), "MALE", "+918840844031", null,
						new DisplayAddress("Noida, UP, 201301", "Noida", "UP", "201301", "IN"), null,
						"DL-1420110024680", null, OwnershipType.CLIENT, null)));
	}

	private List<MasterVehicle> seedMasterVehicles(Org org) {
		return masterVehicleRepository.saveAll(List.of(
				new MasterVehicle(null, org.getOrgId(), "Audi A3", "audi-a3-cabriolet.webp", null, null, null,
						"Cabriolet", "Audi", "2", "5", "Automatic", null, null, "2023", null, null, null, null, null,
						"audi-a3-cabriolet", 5, 90, true, VehicleStatus.PUBLISHED, "Premium Convertible"),

				new MasterVehicle(null, org.getOrgId(), "Audi A4", "audi-a4-sedan.webp", null, null, null, "Sedan",
						"Audi", "5", "4", "Manual", null, null, "2022", null, null, null, null, null, "audi-a4-sedan",
						4, 80, true, VehicleStatus.PUBLISHED, "Premium Sedan"),

				new MasterVehicle(null, org.getOrgId(), "Audi A6", "audi-a6-sedan.webp", null, null, null, "Sedan",
						"Audi", "5", "4", "Manual", null, null, "2022", null, null, null, null, null, "audi-a6-sedan",
						4, 80, true, VehicleStatus.PUBLISHED, "Premium Sedan"),

				new MasterVehicle(null, org.getOrgId(), "Audi Q7", "audi-q7.webp", null, null, null, "SUV", "Audi", "7",
						"5", "Automatic", null, null, "2023", null, null, null, null, null, "audi-q7", 5, 90, true,
						VehicleStatus.PUBLISHED, "Premium SUV"),

				new MasterVehicle(null, org.getOrgId(), "BMW 5-Series", "bmw-5-series.webp", null, null, null, "Sedan",
						"BMW", "5", "4", "Manual", null, null, "2022", null, null, null, null, null, "bmw-5-series", 4,
						80, true, VehicleStatus.PUBLISHED, "Luxury Sedan"),

				new MasterVehicle(null, org.getOrgId(), "BMW X7", "bmw-ix.webp", null, null, null, "SUV", "BMW", "7",
						"5", "Automatic", null, null, "2023", null, null, null, null, null, "bmw-x7", 5, 90, true,
						VehicleStatus.PUBLISHED, "Premium SUV"),

				new MasterVehicle(null, org.getOrgId(), "Ford Mustang", "ford-mustang.webp", null, null, null, "Sedan",
						"Ford", "5", "4", "Manual", null, null, "2022", null, null, null, null, null, "ford-mustang", 4,
						80, true, VehicleStatus.PUBLISHED, "Economy Sedan"),

				new MasterVehicle(null, org.getOrgId(), "Innova Crysta", null, null, null, null, "SUV", "Toyota", "7",
						"5", "Automatic", null, null, "2023", null, null, null, null, null, "innova-crysta", 5, 90,
						true, VehicleStatus.PUBLISHED, "Premium SUV"),

				new MasterVehicle(null, org.getOrgId(), "Jaguar XF", "jaguar-xf.webp", null, null, null, "Sedan",
						"Jaguar", "5", "4", "Manual", null, null, "2022", null, null, null, null, null, "jaguar-xf", 4,
						80, true, VehicleStatus.PUBLISHED, "Economy Sedan"),

				new MasterVehicle(null, org.getOrgId(), "Jaguar XJL", "jaguar-xjl.webp", null, null, null, "SUV",
						"Jaguar", "7", "5", "Automatic", null, null, "2023", null, null, null, null, null, "jaguar-xjl",
						5, 90, true, VehicleStatus.PUBLISHED, "Premium SUV"),

				new MasterVehicle(null, org.getOrgId(), "Land Rover Defender", "land-rover-defender.webp", null, null,
						null, "Sedan", "Land Rover", "5", "4", "Manual", null, null, "2022", null, null, null, null,
						null, "land-rover-defender", 4, 80, true, VehicleStatus.PUBLISHED, "Economy Sedan"),

				new MasterVehicle(null, org.getOrgId(), "Mercedes E-Class", "mercedes-benz-e-class.webp", null, null,
						null, "SUV", "Mercedes", "7", "5", "Automatic", null, null, "2023", null, null, null, null,
						null, "mercedes-benz-e-class", 5, 90, true, VehicleStatus.PUBLISHED, "Premium SUV"),

				new MasterVehicle(null, org.getOrgId(), "Porsche Boxster", "porsche-boxster.webp", null, null, null,
						"Sedan", "Porsche", "5", "4", "Manual", null, null, "2022", null, null, null, null, null,
						"porsche-boxster", 4, 80, true, VehicleStatus.PUBLISHED, "Economy Sedan"),

				new MasterVehicle(null, org.getOrgId(), "Porsche Cayenne", "porsche-cayenne.webp", null, null, null,
						"Convertible", "Porsche", "12", "4", "Manual", null, null, "2021", null, null, null, null, null,
						"porsche-cayenne", 4, 70, true, VehicleStatus.PUBLISHED, "Group Travel")));
	}

	private void seedFleetVehicles(Org org, List<Client> clients, List<MasterVehicle> masters) {

		fleetVehicleRepository.saveAll(List.of(
				new FleetVehicle(null, org.getOrgId(), masters.get(0).getId(), clients.get(2).getId(), "DL01AB1234",
						new AddressSnapshot(), OwnershipType.CLIENT, null, null),

				new FleetVehicle(null, org.getOrgId(), masters.get(1).getId(), clients.get(2).getId(), "DL02CD5678",
						new AddressSnapshot(), OwnershipType.CLIENT, null, null),

				new FleetVehicle(null, org.getOrgId(), masters.get(2).getId(), null, "DL03EF9999",
						new AddressSnapshot(), OwnershipType.ORG, null, null)));
	}

	private void seedPackages(Org org, List<Client> clients, List<MasterVehicle> masters) {

		packageRepository.saveAll(List.of(
				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(0).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(1).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(2).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(3).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(4).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(5).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(6).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(7).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(8).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(9).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(10).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(11).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(12).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(0).getId(), DutyType.LOCAL, 8,
						"HOUR", 80, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true, "Delhi",
						null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(0).getId(), DutyType.TRANSFER,
						4, "HOUR", 40, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true,
						"Delhi", null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(1).getId(), DutyType.TRANSFER,
						4, "HOUR", 40, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true,
						"Delhi", null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(2).getId(), DutyType.TRANSFER,
						4, "HOUR", 40, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true,
						"Delhi", null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(3).getId(), DutyType.TRANSFER,
						4, "HOUR", 40, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true,
						"Delhi", null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(4).getId(), DutyType.TRANSFER,
						4, "HOUR", 40, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true,
						"Delhi", null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(5).getId(), DutyType.TRANSFER,
						4, "HOUR", 40, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true,
						"Delhi", null, null),

				new Package(null, org.getOrgId(), PackageScope.MASTER, null, masters.get(1).getId(), DutyType.TRANSFER,
						4, "HOUR", 40, Money.INR(4500f), Money.INR(200f), Money.INR(500f), Money.INR(500f), true,
						"Delhi", null, null)));
	}

}
